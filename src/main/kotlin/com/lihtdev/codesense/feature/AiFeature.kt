package com.lihtdev.codesense.feature

import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.settings.AppSettingsState

/**
 * AI 功能抽象：每个 AI 功能（提交信息生成、代码解释等）一个实现。
 *
 * 后续新功能只需实现此接口并经 plugin.xml 的 `codesense.aiFeature` 扩展点注册，
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
}
