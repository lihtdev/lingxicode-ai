package com.lihtdev.lingxicode.feature

import com.lihtdev.lingxicode.ai.ChatMessage
import com.lihtdev.lingxicode.ai.MarkdownToHtml
import com.lihtdev.lingxicode.ai.PromptBuilder
import com.lihtdev.lingxicode.ai.ResponseCleaner
import com.lihtdev.lingxicode.code.CodeContext
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AppSettingsState
import com.lihtdev.lingxicode.ui.CodeExplainDialog

/**
 * 「代码评审」功能：选区/符号 → prompt → 多维度评审报告 → 非模态对话框展示。
 *
 * 覆盖默认清洗为 [ResponseCleaner.cleanMarkdown]（保留 Markdown 结构），
 * 覆盖输出 token 上限为 [REVIEW_MAX_TOKENS]（十维度报告为长文本）。
 */
class ReviewCodeFeature : AiFeature {

    override val id: String = "review-code"

    override val displayName: String = LingxiCodeBundle.message("feature.reviewCode")

    override val maxOutputTokens: Int = REVIEW_MAX_TOKENS

    override fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage> {
        val ctx = context as CodeContext
        val kindName = LingxiCodeBundle.message(ctx.symbolKind.bundleKey)
        return PromptBuilder.buildReviewCode(
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
        val ctx = context as CodeContext
        val html = MarkdownToHtml.convert(result)
        CodeExplainDialog(ctx.project, dialogTitle(ctx), result, html).show()
    }

    private fun dialogTitle(ctx: CodeContext): String {
        val subject = ctx.symbolName ?: LingxiCodeBundle.message(ctx.symbolKind.bundleKey)
        return LingxiCodeBundle.message("review.dialog.title", subject, ctx.language)
    }

    companion object {
        /** 评审报告的最大 token 数（推理模型思考链也计入该配额，需为长文本 + 思考留足余量） */
        const val REVIEW_MAX_TOKENS = 16384
    }
}
