package com.lihtdev.lingxicode.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AiProviderConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI 兼容协议客户端（唯一协议实现，预设与自定义厂商共用）。
 *
 * 请求：POST {baseUrl}/chat/completions
 * 响应：choices[0].message.content
 *
 * 注意：必须在后台线程调用（禁止 EDT 网络请求），由调用方（Task.Backgroundable）保证。
 */
class OpenAiCompatClient : AiClient {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    override fun chat(provider: AiProviderConfig, apiKey: String, messages: List<ChatMessage>): String =
        chat(provider, apiKey, messages, AiClient.DEFAULT_MAX_TOKENS)

    override fun chat(
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): String {
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.baseUrlNotSet"))
        }
        if (provider.model.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.modelNotSet"))
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    gson.toJson(ChatCompletionRequest(provider.model, messages, maxTokens = maxTokens)),
                    Charsets.UTF_8,
                ),
            )
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            throw e // 用户取消，直接向上传递
        } catch (e: Exception) {
            throw AiClientException(
                LingxiCodeBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        }

        if (response.statusCode() / 100 != 2) {
            throw AiClientException(mapError(response.statusCode(), response.body()))
        }

        val parsed = try {
            gson.fromJson(response.body(), ChatCompletionResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException(LingxiCodeBundle.message("error.parseFailed", e.message ?: ""), e)
        }
        val choice = parsed.choices.firstOrNull()
        val content = choice?.message?.content
        if (content.isNullOrEmpty()) {
            throw AiClientException(emptyContentError(choice))
        }
        return content
    }

    override fun listModels(provider: AiProviderConfig, apiKey: String): List<String> {
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.baseUrlNotSet"))
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/models"))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Authorization", "Bearer $apiKey")
            .GET()
            .build()

        val response: HttpResponse<String> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            throw e // 用户取消，直接向上传递
        } catch (e: Exception) {
            throw AiClientException(
                LingxiCodeBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        }

        if (response.statusCode() / 100 != 2) {
            throw AiClientException(mapError(response.statusCode(), response.body()))
        }

        val parsed = try {
            gson.fromJson(response.body(), ModelsResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException(LingxiCodeBundle.message("error.parseFailed", e.message ?: ""), e)
        }
        return parsed.data.map { it.id.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    /** 将 HTTP 状态码与错误体映射为用户可读提示 */
    private fun mapError(status: Int, body: String): String {
        val detail = extractErrorMessage(body)
        val base = when (status) {
            401 -> LingxiCodeBundle.message("error.http401")
            403 -> LingxiCodeBundle.message("error.http403")
            404 -> LingxiCodeBundle.message("error.http404")
            429 -> LingxiCodeBundle.message("error.http429")
            else -> LingxiCodeBundle.message("error.httpOther", status.toString())
        }
        return if (detail != null) "$base$detail" else base
    }

    /** 尝试从错误响应体提取 error.message */
    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val error = gson.fromJson(body, ChatError::class.java)
            error.error?.message?.let { "：$it" }
        } catch (_: Exception) {
            "：${body.take(200)}"
        }
    }

    /**
     * 根据 content 为空时的响应特征，生成具体原因提示：
     * finish_reason=length 或存在 reasoning_content，说明推理模型的思考过程
     * 耗尽了 max_tokens 输出配额（content 未开始生成即被截断）。
     */
    private fun emptyContentError(choice: ChatCompletionResponse.Choice?): String {
        val isTruncated = choice?.finishReason == FINISH_REASON_LENGTH ||
            !choice?.message?.reasoningContent.isNullOrEmpty()
        return if (isTruncated) {
            LingxiCodeBundle.message("error.truncatedByMaxTokens")
        } else {
            LingxiCodeBundle.message("error.emptyResponse")
        }
    }

    companion object {
        /** 连接超时（秒） */
        const val CONNECT_TIMEOUT_SECONDS = 10L

        /** 请求超时（秒） */
        const val REQUEST_TIMEOUT_SECONDS = 60L

        /** finish_reason 取值：因 max_tokens 上限被截断 */
        private const val FINISH_REASON_LENGTH = "length"
    }
}
