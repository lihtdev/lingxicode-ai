package com.lihtdev.lingxicode.feature

import com.intellij.openapi.project.Project
import com.lihtdev.lingxicode.ai.ChatMessage
import com.lihtdev.lingxicode.ai.PromptBuilder
import com.lihtdev.lingxicode.ai.ResponseCleaner
import com.lihtdev.lingxicode.code.CodeContext
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AppSettingsState
import com.lihtdev.lingxicode.ui.AiStreamingDialog

/**
 * 「代码解释」功能：选区/符号 → prompt → 结构化解释 → 非模态对话框展示。
 *
 * 覆盖默认清洗为 [ResponseCleaner.cleanMarkdown]（保留 Markdown 结构），
 * 覆盖输出 token 上限为 [EXPLAIN_MAX_TOKENS]（解释为长文本）。
 */
class ExplainCodeFeature : AiFeature {

    override val id: String = "explain-code"

    override val displayName: String = LingxiCodeBundle.message("feature.explainCode")

    override val maxOutputTokens: Int = EXPLAIN_MAX_TOKENS

    override fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage> {
        val ctx = context as CodeContext
        val kindName = LingxiCodeBundle.message(ctx.symbolKind.bundleKey)
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

    override fun createStreamView(project: Project, context: Any): AiStreamView {
        // 请求发起前先弹对话框，边生成边展示（含思考过程折叠面板）
        val ctx = context as CodeContext
        return AiStreamingDialog(ctx.project, dialogTitle(ctx)).apply { show() }
    }

    override fun handleResult(result: String, context: Any) {
        // 非流式兜底路径（理论上仅在功能关闭流式时触发）
        val ctx = context as CodeContext
        AiStreamingDialog(ctx.project, dialogTitle(ctx)).apply {
            onContentDelta(result)
            onCompleted(result)
            show()
        }
    }

    private fun dialogTitle(ctx: CodeContext): String {
        val subject = ctx.symbolName ?: LingxiCodeBundle.message(ctx.symbolKind.bundleKey)
        return LingxiCodeBundle.message("explain.dialog.title", subject, ctx.language)
    }

    companion object {
        /** 解释输出的最大 token 数（推理模型思考链也计入该配额，需为长文本 + 思考留足余量） */
        const val EXPLAIN_MAX_TOKENS = 16384
    }
}