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
        val message: ChatMessage? = null,
    )

    /** 提取首个回复文本 */
    fun firstContent(): String? = choices.firstOrNull()?.message?.content
}

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
