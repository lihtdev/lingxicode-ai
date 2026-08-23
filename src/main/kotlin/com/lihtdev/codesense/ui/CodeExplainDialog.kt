package com.lihtdev.codesense.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.service.AiInvocationService
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
        init()
        setOKButtonText(CodeSenseBundle.message("explain.dialog.close"))
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

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    override fun createLeftSideActions(): Array<Action> {
        val copyAction = object : AbstractAction(CodeSenseBundle.message("explain.dialog.copy")) {
            override fun actionPerformed(e: ActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(markdown))
                AiInvocationService.notifyInfo(project, CodeSenseBundle.message("explain.dialog.copied"))
            }
        }
        return arrayOf(copyAction)
    }

    /** 仅保留「关闭」按钮（复制操作置于左侧） */
    override fun createActions(): Array<Action> = arrayOf(okAction)

    private fun buildHtml(body: String, fg: Color, codeBg: Color): String {
        val fgHex = hex(fg)
        val bgHex = hex(codeBg)
        return """
            <html>
            <head>
            <style>
              body { font-family: sans-serif; font-size: 14px; color: $fgHex; }
              h2 { font-size: 17px; margin: 18px 0 8px; }
              h3 { font-size: 15px; margin: 14px 0 6px; }
              p { margin: 6px 0; }
              ul { margin: 6px 0; padding-left: 22px; }
              li { margin: 3px 0; }
              code { font-family: monospace; font-size: 13px; color: $fgHex; }
              pre { background: $bgHex; padding: 10px; font-size: 13px; overflow: auto; }
              strong { font-weight: bold; }
            </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun hex(color: Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)

    companion object {
        /** 代码块底色（浅色主题浅灰 / 深色主题深灰），主题感知 */
        private val CODE_BLOCK_BACKGROUND = JBColor(Color(0xF2F2F2), Color(0x2B2B2B))
    }
}