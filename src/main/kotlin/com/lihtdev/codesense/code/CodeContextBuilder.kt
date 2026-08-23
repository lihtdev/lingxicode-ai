package com.lihtdev.codesense.code

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.lihtdev.codesense.i18n.CodeSenseBundle

/**
 * 「解释代码」上下文采集（平台胶水，须在 EDT 调用）。
 *
 * 采集优先级：
 * 1. 编辑器选区（代码块）；
 * 2. 光标所在最近的命名符号（类 / 方法 / 函数等，语言无关，仅用平台级 PSI）；
 * 3. 兜底：光标所在外层同缩进代码块。
 *
 * 采集结果为纯字符串，供后台组 prompt 与 EDT 展示安全使用。
 */
object CodeContextBuilder {

    /** 待解释代码的最大字符数（超出截断并标注） */
    const val MAX_CODE_CHARS = 20000

    fun build(e: AnActionEvent): ExplainCodeContext? {
        val project = e.project ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val language = psiFile.language.displayName
        val fileName = psiFile.name

        // 1) 优先解释选中的代码块
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val selected = selectionModel.selectedText?.takeIf { it.isNotBlank() } ?: return null
            return ExplainCodeContext(
                project = project,
                language = language,
                fileName = fileName,
                symbolKind = SymbolKindDetector.detect(selected),
                symbolName = null,
                code = truncate(selected),
            )
        }

        // 2) 光标所在最近的命名符号（类 / 方法 / 函数等）
        val element = psiFile.findElementAt(editor.caretModel.primaryCaret.offset)
        if (element != null) {
            val owner = PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
            if (owner != null) {
                val text = editor.document.getText(owner.textRange)
                if (text.isNotBlank()) {
                    return ExplainCodeContext(
                        project = project,
                        language = language,
                        fileName = fileName,
                        symbolKind = SymbolKindDetector.detect(text),
                        symbolName = owner.name?.takeIf { it.isNotBlank() },
                        code = truncate(text),
                    )
                }
            }
        }

        // 3) 兜底：光标所在外层同缩进代码块
        return blockFallback(editor, project, language, fileName)
    }

    /** 从光标行向上/下扩展到最小同缩进代码块 */
    private fun blockFallback(
        editor: Editor,
        project: Project,
        language: String,
        fileName: String,
    ): ExplainCodeContext? {
        val document = editor.document
        val caretLine = editor.caretModel.primaryCaret.logicalPosition.line
        if (caretLine < 0 || caretLine >= document.lineCount) return null

        val baseIndent = indentWidth(document, caretLine)
        var start = caretLine
        while (start > 0 &&
            (isEmptyLine(document, start - 1) || indentWidth(document, start - 1) >= baseIndent)
        ) {
            start--
        }
        var end = caretLine
        while (end < document.lineCount - 1 &&
            (isEmptyLine(document, end + 1) || indentWidth(document, end + 1) >= baseIndent)
        ) {
            end++
        }

        val code = document.getText(TextRange(document.getLineStartOffset(start), document.getLineEndOffset(end)))
        if (code.isBlank()) return null

        return ExplainCodeContext(
            project = project,
            language = language,
            fileName = fileName,
            symbolKind = SymbolKindDetector.detect(code),
            symbolName = null,
            code = truncate(code),
        )
    }

    /** 计算某行的前导缩进宽度（Tab 按 4 空格计） */
    private fun indentWidth(document: Document, line: Int): Int {
        val text = document.getText(
            TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
        )
        var width = 0
        for (c in text) {
            when (c) {
                ' ' -> width += 1
                '\t' -> width += 4
                else -> break
            }
        }
        return width
    }

    private fun isEmptyLine(document: Document, line: Int): Boolean {
        val text = document.getText(
            TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
        )
        return text.isBlank()
    }

    private fun truncate(code: String): String =
        if (code.length <= MAX_CODE_CHARS) code
        else code.take(MAX_CODE_CHARS) + CodeSenseBundle.message("explain.truncated")
}