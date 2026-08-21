package com.lihtdev.codesense.ai

import com.lihtdev.codesense.settings.AiProviderConfig

/**
 * AI 客户端抽象。
 * v1 仅有一个实现 [OpenAiCompatClient]（预设与自定义厂商统一走 OpenAI 兼容协议），
 * 预留接口以便后续引入其他协议实现。
 */
interface AiClient {

    /**
     * 发送对话请求，返回首个回复文本。
     * @throws AiClientException 配置缺失、网络失败、HTTP 错误或响应异常时抛出（message 为用户可读中文）
     */
    fun chat(provider: AiProviderConfig, apiKey: String, messages: List<ChatMessage>): String
}

/**
 * AI 调用异常：message 面向用户展示（中文）。
 */
class AiClientException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
