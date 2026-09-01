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
 * 「逐行解释」功能：选区/符号 → prompt → 单个代码围栏（原代码 + 逐行注释）→ 非模态对话框展示。
 *
 * 与 [ExplainCodeFeature] 的概览式解释互补：
 * - 清洗用 [ResponseCleaner.cleanFencedCode]（合法输出本身即一个围栏，必须保留围栏行，
 *   不能用会剥掉外层围栏的 [ResponseCleaner.cleanMarkdown]）；
 * - 覆盖输出 token 上限为 [EXPLAIN_LINE_BY_LINE_MAX_TOKENS]（输出为代码原样 + 逐行注释，
 *   约为代码两倍量级，需为长输出 + 思考链留足余量）。
 */
class ExplainLineByLineFeature : AiFeature {

    override val id: String = "explain-line-by-line"

    override val displayName: String = LingxiCodeBundle.message("feature.explainLineByLine")

    override val maxOutputTokens: Int = EXPLAIN_LINE_BY_LINE_MAX_TOKENS

    override fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage> {
        val ctx = context as CodeContext
        val kindName = LingxiCodeBundle.message(ctx.symbolKind.bundleKey)
        return PromptBuilder.buildExplainLineByLine(
            language = ctx.language,
            fileName = ctx.fileName,
            symbolKindName = kindName,
            symbolName = ctx.symbolName,
            code = ctx.code,
            outputLanguage = settings.outputLanguage,
        )
    }

    override fun cleanResponse(raw: String): String = ResponseCleaner.cleanFencedCode(raw)

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
        return LingxiCodeBundle.message("explainLineByLine.dialog.title", subject, ctx.language)
    }

    companion object {
        /** 逐行解释输出的最大 token 数（代码原样 + 逐行注释约为代码两倍量级；推理模型思考链也计入该配额） */
        const val EXPLAIN_LINE_BY_LINE_MAX_TOKENS = 32768
    }
}
