package com.lihtdev.codesense.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
            throw AiClientException("接口地址（baseUrl）未配置，请在设置中填写")
        }
        if (provider.model.isBlank()) {
            throw AiClientException("模型名称未配置，请在设置中填写")
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
            throw AiClientException("网络请求失败：${e.javaClass.simpleName}：${e.message}", e)
        }

        if (response.statusCode() / 100 != 2) {
            throw AiClientException(mapError(response.statusCode(), response.body()))
        }

        val parsed = try {
            gson.fromJson(response.body(), ChatCompletionResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException("响应解析失败（非预期 JSON 结构）：${e.message}", e)
        }
        return parsed.firstContent()
            ?: throw AiClientException("模型未返回内容（响应缺少 choices[0].message.content）")
    }

    /** 将 HTTP 状态码与错误体映射为用户可读中文提示 */
    private fun mapError(status: Int, body: String): String {
        val detail = extractErrorMessage(body)
        return when (status) {
            401 -> "API Key 无效（HTTP 401）$detail"
            403 -> "无访问权限，请检查 API Key 与套餐（HTTP 403）$detail"
            404 -> "接口地址或模型名有误（HTTP 404）$detail"
            429 -> "请求过于频繁或额度不足（HTTP 429）$detail"
            else -> "请求失败（HTTP $status）$detail"
        }
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
