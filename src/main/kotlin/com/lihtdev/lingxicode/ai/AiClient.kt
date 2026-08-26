package com.lihtdev.lingxicode.ai

import com.lihtdev.lingxicode.settings.AiProviderConfig

/**
 * AI 客户端抽象。
 * v1 仅有一个实现 [OpenAiCompatClient]（预设与自定义提供商统一走 OpenAI 兼容协议），
 * 预留接口以便后续引入其他协议实现。
 */
interface AiClient {

    /**
     * 发送对话请求，返回首个回复文本。
     * @throws AiClientException 配置缺失、网络失败、HTTP 错误或响应异常时抛出（message 为用户可读中文）
     */
    fun chat(provider: AiProviderConfig, apiKey: String, messages: List<ChatMessage>): String

    /**
     * 发送对话请求并指定输出 token 上限，返回首个回复文本。
     * 长文本功能（如代码解释）使用本方法以获得足够输出长度。
     * @param maxTokens 模型回复的最大 token 数
     * @throws AiClientException 配置缺失、网络失败、HTTP 错误或响应异常时抛出（message 为用户可读中文）
     */
    fun chat(provider: AiProviderConfig, apiKey: String, messages: List<ChatMessage>, maxTokens: Int): String

    /**
     * 获取提供商支持的模型列表（OpenAI 兼容 `GET {baseUrl}/models`）。
     * @return 模型名列表（按字母升序排列）
     * @throws AiClientException 配置缺失、网络失败、HTTP 错误或响应异常时抛出
     */
    fun listModels(provider: AiProviderConfig, apiKey: String): List<String>

    /**
     * 流式对话请求：`POST {baseUrl}/chat/completions`，请求体含 `stream=true`，
     * 响应为 SSE（逐帧 `data: {...}`）。增量经 [listener] 回调（后台线程触发，
     * 实现方不得直接操作 Swing 组件），完整结果经返回值表达。
     *
     * @param maxTokens 模型回复的最大 token 数
     * @param listener 增量回调（content / reasoning 分开推送）
     * @param isCancelled 取消轮询：返回 true 时中止读取并抛 [InterruptedException]
     * @return 累计完整结果（正文 / 思考过程 / 终止原因）
     * @throws InterruptedException 用户取消（原样上抛，与非流式 [chat] 一致）
     * @throws AiClientException 网络失败、HTTP 错误、SSE 解析异常或全空响应时抛出；
     * HTTP 错误时异常携带 [AiClientException.httpStatus]，供上层降级判定
     */
    fun chatStreaming(
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        listener: ChatStreamListener,
        isCancelled: () -> Boolean = { false },
    ): ChatStreamResult

    companion object {
        /** 输出 token 默认上限（提交信息等短文本功能） */
        const val DEFAULT_MAX_TOKENS = 256
    }
}

/**
 * AI 调用异常：message 面向用户展示（中文）。
 */
class AiClientException : RuntimeException {

    /** HTTP 状态码；网络失败/解析失败等非 HTTP 错误为 null（供上层流式降级判定） */
    val httpStatus: Int?

    constructor(message: String, httpStatus: Int? = null) : super(message) {
        this.httpStatus = httpStatus
    }

    constructor(message: String, cause: Throwable, httpStatus: Int? = null) : super(message, cause) {
        this.httpStatus = httpStatus
    }
}
