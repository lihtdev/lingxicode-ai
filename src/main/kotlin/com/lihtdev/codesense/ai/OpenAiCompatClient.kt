package com.lihtdev.codesense.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AiProviderConfig
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

    override fun chat(provider: AiProviderConfig, apiKey: String, messages: List<ChatMessage>): String {
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw AiClientException(CodeSenseBundle.message("error.baseUrlNotSet"))
        }
        if (provider.model.isBlank()) {
            throw AiClientException(CodeSenseBundle.message("error.modelNotSet"))
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    gson.toJson(ChatCompletionRequest(provider.model, messages)),
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
                CodeSenseBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        }

        if (response.statusCode() / 100 != 2) {
            throw AiClientException(mapError(response.statusCode(), response.body()))
        }

        val parsed = try {
            gson.fromJson(response.body(), ChatCompletionResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException(CodeSenseBundle.message("error.parseFailed", e.message ?: ""), e)
        }
        return parsed.firstContent()
            ?: throw AiClientException(CodeSenseBundle.message("error.emptyResponse"))
    }

    /** 将 HTTP 状态码与错误体映射为用户可读提示 */
    private fun mapError(status: Int, body: String): String {
        val detail = extractErrorMessage(body)
        val base = when (status) {
            401 -> CodeSenseBundle.message("error.http401")
            403 -> CodeSenseBundle.message("error.http403")
            404 -> CodeSenseBundle.message("error.http404")
            429 -> CodeSenseBundle.message("error.http429")
            else -> CodeSenseBundle.message("error.httpOther", status.toString())
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

    companion object {
        /** 连接超时（秒） */
        const val CONNECT_TIMEOUT_SECONDS = 10L

        /** 请求超时（秒） */
        const val REQUEST_TIMEOUT_SECONDS = 60L
    }
}
