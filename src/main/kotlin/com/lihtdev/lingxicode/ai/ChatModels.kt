package com.lihtdev.lingxicode.ai

import com.google.gson.annotations.SerializedName

/**
 * 对话消息（OpenAI 兼容格式）。
 */
data class ChatMessage(
    val role: String,
    val content: String,
)

/**
 * chat/completions 请求体（OpenAI 兼容格式）。
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int = 256,
)

/**
 * chat/completions 响应体（OpenAI 兼容格式）。
 */
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
) {
    data class Choice(
        val message: ResponseMessage? = null,
        @SerializedName("finish_reason") val finishReason: String? = null,
    )

    /** 提取首个回复文本 */
    fun firstContent(): String? = choices.firstOrNull()?.message?.content
}

/**
 * 响应侧消息（OpenAI 兼容格式）。
 *
 * 与请求侧 [ChatMessage] 分离：content 可为空——推理模型（thinking）的思考过程
 * 计入 max_tokens 配额，配额耗尽时 finish_reason="length" 且 content 为 null，
 * 思考内容放在 reasoning_content 字段。
 */
data class ResponseMessage(
    val content: String? = null,
    @SerializedName("reasoning_content") val reasoningContent: String? = null,
)

/**
 * chat/completions 错误响应体（OpenAI 兼容格式）。
 */
data class ChatError(
    val error: ErrorBody? = null,
) {
    data class ErrorBody(
        val message: String? = null,
        val type: String? = null,
    )
}

/**
 * models 列表响应体（OpenAI 兼容格式）。
 * GET {baseUrl}/models → {"object":"list","data":[{"id":"model-a",...},...]}
 */
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList(),
) {
    data class ModelInfo(
        val id: String = "",
    )
}
