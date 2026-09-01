package com.lihtdev.lingxicode.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lihtdev.lingxicode.ai.CodeHighlighter
import com.lihtdev.lingxicode.ai.MarkdownToHtml
import com.lihtdev.lingxicode.feature.AiStreamView
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
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
 * 结构：北区「思考状态」单行（图标 + 最新一行思考，完成后整行隐藏）
 * + 中区只读 HTML 正文（增量重渲 + 滚动跟随）+ 南区图标化状态条。
 *
 * 所有 [AiStreamView] 回调在 EDT 触发；回调首行守卫对话框已关闭（静默 no-op）。
 */
class AiStreamingDialog(
    private val project: Project,
    dialogTitle: String,
) : DialogWrapper(project, /* canBeParent = */ false), AiStreamView {

    /** 累计正文原文（EDT 串行写；完成后替换为清洗后定稿） */
    private val contentRaw = StringBuilder()

    /** 累计思考过程原文（仅用于提取最新一行，不全量展示） */
    private val reasoningRaw = StringBuilder()

    /** 完成后的定稿文本（复制全文用；流式期间为 null，复制原文） */
    private var finalText: String? = null

    // ---- 组件（createCenterPanel 中初始化） ----
    private lateinit var thinkingRow: JPanel
    private lateinit var latestLineLabel: JBLabel
    private lateinit var editorPane: JEditorPane
    private lateinit var scrollPane: JBScrollPane
    private lateinit var progressIcon: AsyncProcessIcon
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

    /** 北区：思考状态单行（初始不可见，首个 reasoning 增量到达时展示；完成后整行隐藏） */
    private fun buildThinkingPanel(): JComponent {
        val titleLabel = JBLabel(
            LingxiCodeBundle.message("stream.thinking.ongoing"),
            AllIcons.Actions.IntentionBulb,
            JBLabel.LEFT,
        )
        titleLabel.isOpaque = false

        latestLineLabel = zeroMinWidthLabel()
        latestLineLabel.isOpaque = false
        latestLineLabel.font = JBFont.label().deriveFont((JBFont.label().size - 1).toFloat())
        latestLineLabel.foreground = UIUtil.getContextHelpForeground()

        thinkingRow = JPanel(BorderLayout(JBUI.scale(8), 0))
        thinkingRow.background = THINKING_ROW_BACKGROUND
        thinkingRow.border = JBUI.Borders.empty(4, 10)
        thinkingRow.add(titleLabel, BorderLayout.WEST)
        thinkingRow.add(latestLineLabel, BorderLayout.CENTER)
        // 初始隐藏：模型未输出思考过程时不占空间
        thinkingRow.isVisible = false
        return thinkingRow
    }

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

    /** 南区：图标化状态条（生成中转圈 / 已完成绿勾 / 已取消警告 / 失败错误） */
    private fun buildStatusBar(): JComponent {
        progressIcon = AsyncProcessIcon("streaming")

        statusLabel = zeroMinWidthLabel()
        statusLabel.text = LingxiCodeBundle.message("stream.generating")
        statusLabel.isOpaque = false
        statusLabel.font = JBFont.label().deriveFont((JBFont.label().size - 1).toFloat())
        statusLabel.foreground = UIUtil.getContextHelpForeground()

        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        row.isOpaque = false
        row.add(progressIcon)
        row.add(statusLabel)

        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.border = BorderFactory.createCompoundBorder(
            // 与正文之间一条主题细分隔线
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(6, 2, 0, 2),
        )
        panel.add(row, BorderLayout.CENTER)
        return panel
    }

    // ---- AiStreamView 回调（EDT） ----

    override fun onContentDelta(delta: String) {
        if (isDisposed) return
        contentRaw.append(delta)
        renderContent()
    }

    override fun onReasoningDelta(delta: String) {
        if (isDisposed) return
        if (!thinkingRow.isVisible) {
            // 首个思考增量：展示单行思考状态
            thinkingRow.isVisible = true
        }
        reasoningRaw.append(delta)
        // 只展示最新一行作为「正在思考」状态提示（单行截断，不全量展示）
        val latestLine = reasoningRaw.toString()
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        latestLineLabel.text = if (latestLine.length > LATEST_LINE_MAX_CHARS) {
            latestLine.take(LATEST_LINE_MAX_CHARS) + "…"
        } else {
            latestLine
        }
    }

    override fun onCompleted(cleaned: String) {
        if (isDisposed) return
        finalText = cleaned
        contentRaw.setLength(0)
        contentRaw.append(cleaned)
        renderContent()
        // 思考状态行整行隐藏，正文独占窗口
        thinkingRow.isVisible = false
        progressIcon.isVisible = false
        statusLabel.icon = AllIcons.General.InspectionsOK
        statusLabel.text = LingxiCodeBundle.message("stream.completed")
        statusLabel.foreground = STATUS_SUCCESS_COLOR
    }

    override fun onFailed(errorMessage: String) {
        if (isDisposed) return
        progressIcon.isVisible = false
        statusLabel.icon = AllIcons.General.Error
        statusLabel.text = LingxiCodeBundle.message("stream.failed", errorMessage)
        statusLabel.foreground = UIUtil.getErrorForeground()
    }

    override fun onCancelled() {
        if (isDisposed) return
        progressIcon.isVisible = false
        statusLabel.icon = AllIcons.General.Warning
        statusLabel.text = LingxiCodeBundle.message("stream.cancelled")
        statusLabel.foreground = STATUS_WARNING_COLOR
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
        // 每次重渲都重取当前配色方案的语法四色：流式期间切换主题自动跟随
        editorPane.text = buildHtml(
            MarkdownToHtml.convert(raw, codeHighlightColors()),
            editorPane.foreground,
            CODE_BLOCK_BACKGROUND,
        )
        editorPane.caretPosition = 0
        if (wasAtBottom) {
            // setText 后布局未必立即完成，maximum 可能还是旧值；延后一拍再滚到底
            javax.swing.SwingUtilities.invokeLater { bar.value = bar.maximum }
        }
    }

    /**
     * 从当前编辑器配色方案取代码语法高亮四色（关键字/字符串/数字/注释，
     * 注释优先 LINE_COMMENT、回落 BLOCK_COMMENT）。
     * 某 key 无 foregroundColor 则该类别为 null（不上色）；四色全空返回 null，
     * convert 走纯转义，输出与不高亮完全一致（等效降级开关）。
     */
    private fun codeHighlightColors(): CodeHighlighter.HighlightColors? {
        val scheme = EditorColorsManager.getInstance().globalScheme
        fun hexOf(key: TextAttributesKey): String? =
            scheme.getAttributes(key)?.foregroundColor?.let { hex(it) }
        val keyword = hexOf(DefaultLanguageHighlighterColors.KEYWORD)
        val string = hexOf(DefaultLanguageHighlighterColors.STRING)
        val number = hexOf(DefaultLanguageHighlighterColors.NUMBER)
        val comment = hexOf(DefaultLanguageHighlighterColors.LINE_COMMENT)
            ?: hexOf(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        if (keyword == null && string == null && number == null && comment == null) return null
        return CodeHighlighter.HighlightColors(keyword, string, number, comment)
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
        // 引用块使用上下文辅助色；链接使用平台当前主题链接色（主题感知）
        val helpHex = hex(UIUtil.getContextHelpForeground())
        val linkHex = hex(JBUI.CurrentTheme.Link.Foreground.ENABLED)
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
              a { color: $linkHex; }
              blockquote { margin: 10px 0 10px 12px; color: $helpHex; }
              hr { color: $separatorHex; background-color: $separatorHex; }
              table { margin: 12px 0; }
              td { border: 1px solid $separatorHex; padding: 4px 8px; }
              th { border: 1px solid $separatorHex; padding: 4px 8px; font-weight: bold; background-color: $bgHex; }
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

    /**
     * 创建最小宽度为 0 的标签：流式思考文案 / 错误文案会突然变长，JLabel 的最小尺寸
     * 等于完整文本宽度，平台 DialogRootPane 会按内容最小尺寸调用 setMinimumSize
     * 把对话框自动加宽，故将此类动态长文本标签的最小宽度固定为 0（空间不足时文本裁剪）。
     */
    private fun zeroMinWidthLabel(): JBLabel = object : JBLabel() {
        override fun getMinimumSize(): Dimension = Dimension(0, super.getMinimumSize().height)
    }

    private fun hex(color: Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)

    companion object {
        /** 代码块底色（浅色主题浅灰 / 深色主题深灰），主题感知 */
        private val CODE_BLOCK_BACKGROUND = JBColor(Color(0xF2F2F2), Color(0x2B2B2B))

        /** 二级标题底部分隔线颜色（浅色主题浅灰 / 深色主题暗灰），主题感知 */
        private val HEADING_SEPARATOR = JBColor(Color(0xDDDDDD), Color(0x555555))

        /** 思考状态行底色（卡片感），主题感知 */
        private val THINKING_ROW_BACKGROUND = JBColor(Color(0xF5F7FA), Color(0x3C3F41))

        /** 状态条「已完成」成功色，主题感知 */
        private val STATUS_SUCCESS_COLOR = JBColor(Color(0x59A869), Color(0x499C54))

        /** 状态条「已取消」警告色，主题感知 */
        private val STATUS_WARNING_COLOR = JBColor(Color(0x8A6D00), Color(0xBBB529))

        /** 思考状态行「最新一行」截断长度（字符数） */
        private const val LATEST_LINE_MAX_CHARS = 100

        /** 判定「滚动条贴底」的阈值（px） */
        private const val SCROLL_BOTTOM_THRESHOLD = 30

        /** 系统可用字体族集合（懒加载一次，供字体栈挑选） */
        private val availableFontFamilies: Set<String> by lazy {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames.toHashSet()
        }
    }
}
