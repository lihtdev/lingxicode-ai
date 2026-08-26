package com.lihtdev.lingxicode.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lihtdev.lingxicode.ai.MarkdownToHtml
import com.lihtdev.lingxicode.feature.AiStreamView
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

/**
 * 流式 AI 结果对话框（代码解释 / 代码评审共用，非模态）。
 *
 * 结构：北区「思考过程」可折叠面板（浅色实时流入，完成且用户未手动操作时自动收起）
 * + 中区只读 HTML 正文（增量重渲 + 滚动跟随）+ 南区状态条。
 *
 * 所有 [AiStreamView] 回调在 EDT 触发；回调首行守卫对话框已关闭（静默 no-op）。
 */
class AiStreamingDialog(
    private val project: Project,
    dialogTitle: String,
) : DialogWrapper(project, /* canBeParent = */ false), AiStreamView {

    /** 累计正文原文（EDT 串行写；完成后替换为清洗后定稿） */
    private val contentRaw = StringBuilder()

    /** 累计思考过程原文（EDT 串行写） */
    private val reasoningRaw = StringBuilder()

    /** 完成后的定稿文本（复制全文用；流式期间为 null，复制原文） */
    private var finalText: String? = null

    /** 用户是否手动操作过思考面板（决定完成后是否自动收起） */
    private var reasoningTouchedByUser = false

    // ---- 组件（createCenterPanel 中初始化） ----
    private lateinit var thinkingPanel: JPanel
    private lateinit var thinkingContent: JBTextArea
    private lateinit var thinkingToggleLabel: JBLabel
    private lateinit var editorPane: JEditorPane
    private lateinit var scrollPane: JBScrollPane
    private lateinit var statusLabel: JBLabel

    init {
        title = dialogTitle
        // 真正非模态：允许用户边看流式结果边继续编辑代码；同时保证后台
        // nonModal 调制态的增量刷新（Alarm/invokeLater）不被模态上下文推迟
        isModal = false
        // 长文本支持拖拽放大窗口阅读
        isResizable = true
        init()
        setOKButtonText(LingxiCodeBundle.message("explain.dialog.close"))
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))
        panel.isOpaque = false
        panel.add(buildThinkingPanel(), BorderLayout.NORTH)
        panel.add(buildContentPanel(), BorderLayout.CENTER)
        panel.add(buildStatusBar(), BorderLayout.SOUTH)
        panel.preferredSize = JBUI.size(960, 640)
        return panel
    }

    /** 北区：思考过程折叠面板（初始不可见，首个 reasoning 增量到达时展示并展开） */
    private fun buildThinkingPanel(): JComponent {
        thinkingContent = JBTextArea()
        thinkingContent.isEditable = false
        thinkingContent.lineWrap = true
        thinkingContent.wrapStyleWord = true
        thinkingContent.isOpaque = false
        thinkingContent.font = JBFont.label().deriveFont((JBFont.label().size - 1).toFloat())
        thinkingContent.foreground = UIUtil.getContextHelpForeground()

        val contentScroll = JBScrollPane(thinkingContent)
        contentScroll.border = null
        contentScroll.verticalScrollBar.unitIncrement = JBUI.scale(16)
        contentScroll.preferredSize = JBUI.size(Short.MAX_VALUE.toInt(), THINKING_PANEL_MAX_HEIGHT)

        thinkingToggleLabel = JBLabel(LingxiCodeBundle.message("stream.thinking"), AllIcons.General.ChevronDown, JBLabel.LEFT)
        thinkingToggleLabel.isOpaque = false
        thinkingToggleLabel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        thinkingToggleLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                reasoningTouchedByUser = true
                toggleThinking()
            }
        })

        val header = JPanel(BorderLayout())
        header.isOpaque = false
        header.add(thinkingToggleLabel, BorderLayout.WEST)
        header.border = BorderFactory.createEmptyBorder(0, 0, JBUI.scale(4), 0)

        thinkingPanel = JPanel(BorderLayout())
        thinkingPanel.isOpaque = false
        thinkingPanel.add(header, BorderLayout.NORTH)
        thinkingPanel.add(contentScroll, BorderLayout.CENTER)
        // 初始隐藏：模型未输出思考过程时不占空间
        thinkingPanel.isVisible = false
        return thinkingPanel
    }

    /** 切换思考内容区展开/收起（chevron 随状态变化）。以滚动视口的可见性为准。 */
    private fun toggleThinking() {
        // 注意：thinkingContent 自身的 visible 恒为 true（收起改的是其父容器滚动视口），
        // 判断展开态必须读父容器，否则面板收起后无法再次展开
        val expanded = thinkingContent.parent.isVisible
        thinkingContent.parent.isVisible = !expanded
        thinkingToggleLabel.icon = if (expanded) AllIcons.General.ChevronRight else AllIcons.General.ChevronDown
    }

    /** 思考内容区当前是否展开（以滚动视口可见性为准） */
    private fun isThinkingExpanded(): Boolean = thinkingContent.parent.isVisible

    /** 中区：只读 HTML 正文（样式迁移自 CodeExplainDialog） */
    private fun buildContentPanel(): JComponent {
        val fg = UIUtil.getLabelForeground()
        editorPane = JEditorPane()
        editorPane.contentType = "text/html"
        editorPane.isEditable = false
        editorPane.isOpaque = false
        editorPane.foreground = fg
        editorPane.text = buildHtml("", fg, CODE_BLOCK_BACKGROUND)

        scrollPane = JBScrollPane(editorPane)
        scrollPane.border = null
        // 加大长内容滚动步长（默认步长发涩）
        scrollPane.verticalScrollBar.unitIncrement = JBUI.scale(16)
        return scrollPane
    }

    /** 南区：状态条（生成中 / 已完成 / 已取消 / 失败） */
    private fun buildStatusBar(): JComponent {
        statusLabel = JBLabel(LingxiCodeBundle.message("stream.generating"))
        statusLabel.isOpaque = false
        statusLabel.font = JBFont.label().deriveFont((JBFont.label().size - 1).toFloat())
        statusLabel.foreground = UIUtil.getContextHelpForeground()
        statusLabel.border = BorderFactory.createEmptyBorder(JBUI.scale(6), 0, 0, 0)
        return statusLabel
    }

    // ---- AiStreamView 回调（EDT） ----

    override fun onContentDelta(delta: String) {
        if (isDisposed) return
        contentRaw.append(delta)
        renderContent()
    }

    override fun onReasoningDelta(delta: String) {
        if (isDisposed) return
        if (!thinkingPanel.isVisible) {
            // 首个思考增量：展示面板并默认展开
            thinkingPanel.isVisible = true
            thinkingContent.parent.isVisible = true
            thinkingToggleLabel.icon = AllIcons.General.ChevronDown
        }
        reasoningRaw.append(delta)
        thinkingContent.text = reasoningRaw.toString()
        // 思考区滚动跟随到底
        thinkingContent.caretPosition = thinkingContent.document.length
    }

    override fun onCompleted(cleaned: String) {
        if (isDisposed) return
        finalText = cleaned
        contentRaw.setLength(0)
        contentRaw.append(cleaned)
        renderContent()
        statusLabel.text = LingxiCodeBundle.message("stream.completed")
        // 用户未手动操作过思考面板 → 自动收起（面板保留可再展开）
        if (thinkingPanel.isVisible && !reasoningTouchedByUser && isThinkingExpanded()) {
            toggleThinking()
        }
    }

    override fun onFailed(errorMessage: String) {
        if (isDisposed) return
        statusLabel.text = LingxiCodeBundle.message("stream.failed", errorMessage)
        statusLabel.foreground = UIUtil.getErrorForeground()
    }

    override fun onCancelled() {
        if (isDisposed) return
        statusLabel.text = LingxiCodeBundle.message("stream.cancelled")
    }

    /**
     * 全量重渲正文：Markdown → HTML → setText。
     * 渲染前记录滚动条是否贴底；渲染后贴底则跟随到底，否则保持用户位置。
     */
    private fun renderContent() {
        val raw = contentRaw.toString()
        if (raw.isEmpty()) return
        val bar = scrollPane.verticalScrollBar
        val wasAtBottom = bar.value >= bar.maximum - bar.visibleAmount - SCROLL_BOTTOM_THRESHOLD
        editorPane.text = buildHtml(MarkdownToHtml.convert(raw), editorPane.foreground, CODE_BLOCK_BACKGROUND)
        editorPane.caretPosition = 0
        if (wasAtBottom) {
            // setText 后布局未必立即完成，maximum 可能还是旧值；延后一拍再滚到底
            javax.swing.SwingUtilities.invokeLater { bar.value = bar.maximum }
        }
    }

    override fun createLeftSideActions(): Array<Action> {
        val copyAction = object : AbstractAction(LingxiCodeBundle.message("explain.dialog.copy")) {
            override fun actionPerformed(e: ActionEvent) {
                // 流式期间复制原文；完成后复制清洗后定稿
                val text = finalText ?: contentRaw.toString()
                CopyPasteManager.getInstance().setContents(StringSelection(text))
                AiInvocationService.notifyInfo(project, LingxiCodeBundle.message("explain.dialog.copied"))
            }
        }
        return arrayOf(copyAction)
    }

    /** 仅保留「关闭」按钮（复制操作置于左侧） */
    override fun createActions(): Array<Action> = arrayOf(okAction)

    /**
     * 组装带样式的 HTML。字号从 JBFont 派生，随 IDE 字体缩放联动。
     * 注意：JEditorPane 的 HTMLEditorKit 仅支持有限 CSS 子集
     * （font 系列、color、background-color、margin、padding；border 部分支持，
     * 不支持时静默降级），不支持 border-radius、line-height 与后代选择器，
     * 故 code 样式全局生效（与 pre 内代码底色一致、视觉无缝）。
     */
    private fun buildHtml(body: String, fg: Color, codeBg: Color): String {
        val fgHex = hex(fg)
        val bgHex = hex(codeBg)
        val separatorHex = hex(HEADING_SEPARATOR)
        // 长文本阅读场景：正文在标签字号上加大一档；标题层级差与段间距拉大，
        // 二级标题带底部分隔线，保证「大标题 / 小标题 / 正文」的视觉层次
        val base = JBFont.label().size
        val bodySize = base + 1
        val h2Size = base + 6
        val h3Size = base + 3
        val codeSize = base
        // JEditorPane 的 CSS font-family 只解析第一个字体名（不像浏览器按栈回退），
        // 故运行时从优先级栈挑首个可用字体：英文字体为先，中文字体殿后；
        // 中文文字由系统字体回退渲染（Windows 下 Segoe UI 的系统默认搭配即微软雅黑）
        val bodyFamily = firstAvailableFont(
            "Dialog",
            "PingFang SC", "Noto Sans SC", "Microsoft YaHei UI",
        )
        val codeFamily = firstAvailableFont(
            "Monospaced",
            "JetBrains Mono", "Consolas", "Menlo", "DejaVu Sans Mono", "Courier New",
        )
        return """
            <html>
            <head>
            <style>
              body { font-family: $bodyFamily; font-size: ${bodySize}px; color: $fgHex; padding: 8px 20px 16px; }
              h2 { font-size: ${h2Size}px; font-weight: bold; margin: 22px 0 8px; padding-bottom: 4px; border-bottom: 1px solid $separatorHex; }
              h3 { font-size: ${h3Size}px; font-weight: bold; margin: 14px 0 6px; }
              p { margin: 10px 0; }
              ul { margin: 10px 0; padding-left: 24px; }
              ol { margin: 10px 0; padding-left: 24px; }
              li { margin: 6px 0; }
              code { font-family: $codeFamily; font-size: ${codeSize}px; color: $fgHex; background-color: $bgHex; padding: 1px 3px; }
              pre { background-color: $bgHex; padding: 12px; margin: 12px 0; }
              strong { font-weight: bold; }
            </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    /** 从优先级栈中挑第一个系统可用的字体族；均不可用时返回 [fallback] */
    private fun firstAvailableFont(fallback: String, vararg families: String): String =
        families.firstOrNull { it in availableFontFamilies } ?: fallback

    private fun hex(color: Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)

    companion object {
        /** 代码块底色（浅色主题浅灰 / 深色主题深灰），主题感知 */
        private val CODE_BLOCK_BACKGROUND = JBColor(Color(0xF2F2F2), Color(0x2B2B2B))

        /** 二级标题底部分隔线颜色（浅色主题浅灰 / 深色主题暗灰），主题感知 */
        private val HEADING_SEPARATOR = JBColor(Color(0xDDDDDD), Color(0x555555))

        /** 思考面板内容区最大高度（px） */
        private val THINKING_PANEL_MAX_HEIGHT = 160

        /** 判定「滚动条贴底」的阈值（px） */
        private const val SCROLL_BOTTOM_THRESHOLD = 30

        /** 系统可用字体族集合（懒加载一次，供字体栈挑选） */
        private val availableFontFamilies: Set<String> by lazy {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames.toHashSet()
        }
    }
}
