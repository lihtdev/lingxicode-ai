package com.lihtdev.lingxicode.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

/**
 * 「代码解释」结果对话框（非模态）。
 *
 * 以只读 HTML 视图展示渲染后的解释，提供「复制全文」与「关闭」。
 * 非模态设计允许用户一边阅读解释、一边继续浏览/编辑代码。
 */
class CodeExplainDialog(
    private val project: Project,
    dialogTitle: String,
    private val markdown: String,
    private val htmlBody: String,
) : DialogWrapper(project, /* canBeParent = */ false) {

    init {
        title = dialogTitle
        // 长解释文本支持拖拽放大窗口阅读
        isResizable = true
        init()
        setOKButtonText(LingxiCodeBundle.message("explain.dialog.close"))
    }

    override fun createCenterPanel(): JComponent {
        val fg = UIUtil.getLabelForeground()
        val html = buildHtml(htmlBody, fg, CODE_BLOCK_BACKGROUND)

        val editorPane = JEditorPane()
        editorPane.contentType = "text/html"
        editorPane.isEditable = false
        editorPane.isOpaque = false
        editorPane.foreground = fg
        editorPane.text = html
        editorPane.caretPosition = 0

        val scrollPane = JBScrollPane(editorPane)
        scrollPane.border = null
        scrollPane.preferredSize = JBUI.size(760, 520)
        // 加大长内容滚动步长（默认步长发涩）
        scrollPane.verticalScrollBar.unitIncrement = JBUI.scale(16)

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    override fun createLeftSideActions(): Array<Action> {
        val copyAction = object : AbstractAction(LingxiCodeBundle.message("explain.dialog.copy")) {
            override fun actionPerformed(e: ActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(markdown))
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
     * （font 系列、color、background-color、margin、padding），不支持
     * border-radius、line-height 与后代选择器，故 code 样式全局生效
     * （与 pre 内代码底色一致、视觉无缝）。
     */
    private fun buildHtml(body: String, fg: Color, codeBg: Color): String {
        val fgHex = hex(fg)
        val bgHex = hex(codeBg)
        // 长文本阅读场景：正文在标签字号上加大一档，标题层级差拉大，行距放松降低阅读负担
        val base = JBFont.label().size
        val bodySize = base + 1
        val h2Size = base + 4
        val h3Size = base + 2
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
              body { font-family: $bodyFamily; font-size: ${bodySize}px; color: $fgHex; padding: 2px 16px 12px; }
              h2 { font-size: ${h2Size}px; font-weight: bold; margin: 16px 0 6px; }
              h3 { font-size: ${h3Size}px; font-weight: bold; margin: 12px 0 4px; }
              p { margin: 8px 0; }
              ul { margin: 8px 0; padding-left: 24px; }
              ol { margin: 8px 0; padding-left: 24px; }
              li { margin: 4px 0; }
              code { font-family: $codeFamily; font-size: ${codeSize}px; color: $fgHex; background-color: $bgHex; padding: 1px 3px; }
              pre { background-color: $bgHex; padding: 10px; margin: 10px 0; }
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

        /** 系统可用字体族集合（懒加载一次，供字体栈挑选） */
        private val availableFontFamilies: Set<String> by lazy {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .availableFontFamilyNames.toHashSet()
        }
    }
}