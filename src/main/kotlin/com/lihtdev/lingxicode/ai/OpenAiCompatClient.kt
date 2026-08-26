package com.lihtdev.lingxicode.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AiProviderConfig
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI 兼容协议客户端（唯一协议实现，预设与自定义厂商共用）。
 *
 * 非流式请求：POST {baseUrl}/chat/completions → choices[0].message.content
 * 流式请求：同端点 + stream=true → SSE 逐帧 choices[0].delta.content / delta.reasoning_content
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
        val baseUrl = requireValidBaseUrl(provider)
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
            throw AiClientException(mapError(response.statusCode(), response.body()), httpStatus = response.statusCode())
        }

        val parsed = try {
            gson.fromJson(response.body(), ChatCompletionResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException(LingxiCodeBundle.message("error.parseFailed", e.message ?: ""), e)
        }
        val choice = parsed.choices.firstOrNull()
        val content = choice?.message?.content
        if (content.isNullOrEmpty()) {
            throw AiClientException(emptyContentError(choice?.finishReason, !choice?.message?.reasoningContent.isNullOrEmpty()))
        }
        return content
    }

    override fun chatStreaming(
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        listener: ChatStreamListener,
        isCancelled: () -> Boolean,
    ): ChatStreamResult {
        val baseUrl = requireValidBaseUrl(provider)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            // JDK 的请求超时按「响应头到达」计算；body 读取阶段由空闲看门狗兜底
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer $apiKey")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    gson.toJson(
                        ChatCompletionRequest(provider.model, messages, maxTokens = maxTokens, stream = true),
                    ),
                    Charsets.UTF_8,
                ),
            )
            .build()

        val response: HttpResponse<InputStream> = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: InterruptedException) {
            throw e // 用户取消，直接向上传递
        } catch (e: Exception) {
            throw AiClientException(
                LingxiCodeBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        }

        if (response.statusCode() / 100 != 2) {
            // 读出错误体后关闭流，再走统一错误映射（携带状态码供上层降级判定）
            val errorBody = try {
                response.body().use { String(it.readBytes(), Charsets.UTF_8) }
            } catch (_: Exception) {
                ""
            }
            throw AiClientException(mapError(response.statusCode(), errorBody), httpStatus = response.statusCode())
        }

        return try {
            readSseStream(response.body(), listener, isCancelled)
        } catch (e: InterruptedException) {
            throw e // 用户取消，直接向上传递
        } catch (e: AiClientException) {
            throw e
        } catch (e: Exception) {
            throw AiClientException(
                LingxiCodeBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        }
    }

    /**
     * 逐行读取 SSE 流并分发增量（后台线程）。
     *
     * - 每行前轮询 [isCancelled]，取消时关闭流并抛 [InterruptedException]；
     * - 空闲看门狗：超过 [STREAM_IDLE_TIMEOUT_SECONDS] 无数据则关闭流，
     *   迫使阻塞读抛 IOException，映射为流式超时提示；
     * - 终止宽容性：`[DONE]` 正常结束；EOF 无哨兵但已有正文时宽容视为完成
     *   （部分兼容网关不发哨兵）。
     */
    private fun readSseStream(
        body: InputStream,
        listener: ChatStreamListener,
        isCancelled: () -> Boolean,
    ): ChatStreamResult {
        val parser = SseEventParser()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var finishReason: String? = null
        val timedOut = AtomicBoolean(false)
        val watchdogFuture = AtomicReference<ScheduledFuture<*>?>(null)

        fun armWatchdog() {
            watchdogFuture.getAndSet(
                streamWatchdog.schedule({
                    timedOut.set(true)
                    runCatching { body.close() } // 迫使阻塞 read 抛 IOException
                }, STREAM_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )?.cancel(false)
        }

        fun handleData(payload: String) {
            val delta = ChatChunkDecoder.decode(payload) ?: return
            if (delta.content != null) {
                content.append(delta.content)
                listener.onContentDelta(delta.content)
            }
            if (delta.reasoning != null) {
                reasoning.append(delta.reasoning)
                listener.onReasoningDelta(delta.reasoning)
            }
            if (delta.finishReason != null) {
                finishReason = delta.finishReason
            }
        }

        armWatchdog()
        try {
            BufferedReader(InputStreamReader(body, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (isCancelled()) throw InterruptedException("流式请求已取消")
                    val line = reader.readLine() ?: break
                    armWatchdog()
                    when (val event = parser.feedLine(line)) {
                        is SseEvent.Data -> handleData(event.payload)
                        SseEvent.Done -> return completeStream(content, reasoning, finishReason)
                        null -> Unit
                    }
                }
                // EOF：处理未以空行结尾的残余事件
                when (val event = parser.finish()) {
                    is SseEvent.Data -> handleData(event.payload)
                    SseEvent.Done -> return completeStream(content, reasoning, finishReason)
                    null -> Unit
                }
            }
            // EOF 无 [DONE] 哨兵：已有正文时宽容视为完成
            return completeStream(content, reasoning, finishReason)
        } catch (e: IOException) {
            // 读阻塞期间用户取消：连接被看门狗/取消关闭后 readLine 抛 IOException，
            // 此时优先按取消处理（而非误报为超时/网络错误）
            if (isCancelled()) throw InterruptedException("流式请求已取消")
            if (timedOut.get()) {
                throw AiClientException(LingxiCodeBundle.message("error.streamIdleTimeout"))
            }
            throw AiClientException(
                LingxiCodeBundle.message("error.networkFailed", e.javaClass.simpleName, e.message ?: ""),
                e,
            )
        } finally {
            watchdogFuture.get()?.cancel(false)
        }
    }

    /** 校验收尾：正文为空时报具体原因，否则组装完整结果 */
    private fun completeStream(content: StringBuilder, reasoning: StringBuilder, finishReason: String?): ChatStreamResult {
        if (content.isEmpty()) {
            throw AiClientException(emptyContentError(finishReason, reasoning.isNotEmpty()))
        }
        return ChatStreamResult(
            content = content.toString(),
            reasoning = reasoning.toString().ifEmpty { null },
            finishReason = finishReason,
        )
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
            throw AiClientException(mapError(response.statusCode(), response.body()), httpStatus = response.statusCode())
        }

        val parsed = try {
            gson.fromJson(response.body(), ModelsResponse::class.java)
        } catch (e: Exception) {
            throw AiClientException(LingxiCodeBundle.message("error.parseFailed", e.message ?: ""), e)
        }
        return parsed.data.map { it.id.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    /** 校验 baseUrl 与模型名，返回规范化 baseUrl（去尾斜杠） */
    private fun requireValidBaseUrl(provider: AiProviderConfig): String {
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.baseUrlNotSet"))
        }
        if (provider.model.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.modelNotSet"))
        }
        return baseUrl
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
     * finish_reason=length 或存在思考内容，说明推理模型的思考过程
     * 耗尽了 max_tokens 输出配额（content 未开始生成即被截断）。
     */
    private fun emptyContentError(finishReason: String?, hasReasoning: Boolean): String {
        val isTruncated = finishReason == FINISH_REASON_LENGTH || hasReasoning
        return if (isTruncated) {
            LingxiCodeBundle.message("error.truncatedByMaxTokens")
        } else {
            LingxiCodeBundle.message("error.emptyResponse")
        }
    }

    companion object {
        /** 连接超时（秒） */
        const val CONNECT_TIMEOUT_SECONDS = 10L

        /** 请求超时（秒）——非流式为总时长；流式仅覆盖到响应头到达 */
        const val REQUEST_TIMEOUT_SECONDS = 60L

        /** 流式空闲超时（秒）：连续无数据超过该时长即中断连接 */
        const val STREAM_IDLE_TIMEOUT_SECONDS = 60L

        /** finish_reason 取值：因 max_tokens 上限被截断 */
        private const val FINISH_REASON_LENGTH = "length"

        /**
         * 流式空闲看门狗调度器（全局共享 daemon 单线程，避免每次请求建线程）。
         * 仅负责超时关闭连接，无长驻业务逻辑。
         */
        private val streamWatchdog: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "LingxiCodeAI-StreamWatchdog").apply { isDaemon = true }
            }
        }
    }
}
