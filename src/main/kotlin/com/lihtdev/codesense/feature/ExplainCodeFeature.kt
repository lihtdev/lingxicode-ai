package com.lihtdev.codesense.feature

import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.ai.MarkdownToHtml
import com.lihtdev.codesense.ai.PromptBuilder
import com.lihtdev.codesense.ai.ResponseCleaner
import com.lihtdev.codesense.code.ExplainCodeContext
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettingsState
import com.lihtdev.codesense.ui.CodeExplainDialog

/**
 * 「代码解释」功能：选区/符号 → prompt → 结构化解释 → 非模态对话框展示。
 *
 * 覆盖默认清洗为 [ResponseCleaner.cleanMarkdown]（保留 Markdown 结构），
 * 覆盖输出 token 上限为 [EXPLAIN_MAX_TOKENS]（解释为长文本）。
 */
class ExplainCodeFeature : AiFeature {

    override val id: String = "explain-code"

    override val displayName: String = CodeSenseBundle.message("feature.explainCode")

    override val maxOutputTokens: Int = EXPLAIN_MAX_TOKENS

    override fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage> {
        val ctx = context as ExplainCodeContext
        val kindName = CodeSenseBundle.message(ctx.symbolKind.bundleKey)
        return PromptBuilder.buildExplainCode(
            language = ctx.language,
            fileName = ctx.fileName,
            symbolKindName = kindName,
            symbolName = ctx.symbolName,
            code = ctx.code,
            outputLanguage = settings.outputLanguage,
        )
    }

    override fun cleanResponse(raw: String): String = ResponseCleaner.cleanMarkdown(raw)

    override fun handleResult(result: String, context: Any) {
        val ctx = context as ExplainCodeContext
        val html = MarkdownToHtml.convert(result)
        CodeExplainDialog(ctx.project, dialogTitle(ctx), result, html).show()
    }

    private fun dialogTitle(ctx: ExplainCodeContext): String {
        val subject = ctx.symbolName ?: CodeSenseBundle.message(ctx.symbolKind.bundleKey)
        return CodeSenseBundle.message("explain.dialog.title", subject, ctx.language)
    }

    companion object {
        /** 解释输出的最大 token 数（长文本，远大于默认 256） */
        const val EXPLAIN_MAX_TOKENS = 2048
    }
}