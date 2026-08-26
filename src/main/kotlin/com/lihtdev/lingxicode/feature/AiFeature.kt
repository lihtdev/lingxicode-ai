package com.lihtdev.lingxicode.feature

import com.intellij.openapi.project.Project
import com.lihtdev.lingxicode.ai.ChatMessage
import com.lihtdev.lingxicode.ai.ResponseCleaner
import com.lihtdev.lingxicode.settings.AppSettingsState

/**
 * AI 功能抽象：每个 AI 功能（提交信息生成、代码解释等）一个实现。
 *
 * 后续新功能只需实现此接口并经 plugin.xml 的 `lingxicode.aiFeature` 扩展点注册，
 * 无需改动执行层（AiInvocationService）与 AI 层（OpenAiCompatClient）。
 *
 * v1 仅有 [CommitMessageFeature]；「AI 代码解释」等以此框架后续接入
 * （context = 选中文本/当前文件，结果以弹窗/文档注释形式展示）。
 */
interface AiFeature {

    /** 功能唯一标识 */
    val id: String

    /** 功能展示名（用于进度与通知文案） */
    val displayName: String

    /**
     * 输出 token 上限：决定模型回复的最大长度。
     * 默认 256（提交信息等短文本）；代码解释等长文本功能可覆写为更大值。
     *
     * 注意：推理模型（thinking）的思考 token 也计入该配额——思考阶段耗尽配额时
     * content 为空（finish_reason=length），因此面向推理模型的功能需留足余量。
     */
    val maxOutputTokens: Int
        get() = 256

    /**
     * 组装提示词（在后台线程调用，可执行耗时读取，如 diff 内容）。
     *
     * @param context 功能上下文（各功能自定义类型，如 [CommitFeatureContext]）
     * @param settings 当前应用设置
     */
    fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage>

    /**
     * 处理生成结果（在 EDT 调用，可安全更新 UI）。
     *
     * @param result 清洗后的模型输出
     * @param context 功能上下文
     */
    fun handleResult(result: String, context: Any)

    /**
     * 创建流式展示视图（在 EDT、后台请求发起前调用一次）。
     *
     * 返回 null（默认）= 该功能不做流式 UI，仅在完成时回调 [handleResult]
     * （即原有非流式行为，保证新功能零成本兼容）。
     * 返回视图后，增量/完成/失败/取消均回调视图，[handleResult] 不再被调用。
     */
    fun createStreamView(project: Project, context: Any): AiStreamView? = null

    /**
     * 清洗模型原始输出（在调用 [handleResult] 前执行）。
     *
     * 默认按 [ResponseCleaner.clean] 清洗（适合提交信息等纯文本输出）；
     * 需要保留 Markdown 结构的功能（如代码解释）可覆写为 [ResponseCleaner.cleanMarkdown]。
     */
    fun cleanResponse(raw: String): String = ResponseCleaner.clean(raw)
}
