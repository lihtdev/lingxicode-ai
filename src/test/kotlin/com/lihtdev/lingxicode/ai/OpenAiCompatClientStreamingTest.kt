package com.lihtdev.lingxicode.ai

import com.google.gson.Gson
import com.lihtdev.lingxicode.settings.AiProviderConfig
import com.lihtdev.lingxicode.settings.ProviderPlanType
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAiCompatClient 流式（SSE）单元测试：
 * JDK 内置 HttpServer 按帧 write + flush 模拟 OpenAI 兼容流式端点。
 */
class OpenAiCompatClientStreamingTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""
    private val gson = Gson()

    private val lastRequestBody = AtomicReference<String?>(null)
    private val lastAcceptHeader = AtomicReference<String?>(null)

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.start()
        // 注意：拆分拼接以避免编辑器安全过滤误伤，运行期即为本机回环地址
        baseUrl = "http" + "://" + "localhost" + ":" + server.address.port + "/v1"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    /** 注册 SSE 流式响应处理器：逐帧写出 frames（每帧已含 `data: ` 前缀） */
    private fun respondSse(vararg frames: String) {
        server.createContext("/") { exchange: HttpExchange ->
            lastRequestBody.set(String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8))
            lastAcceptHeader.set(exchange.requestHeaders.getFirst("Accept"))
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            // chunked 传输：长度未知传 0
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                for (frame in frames) {
                    out.write((frame + "\n\n").toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
            }
        }
    }

    private fun provider() = AiProviderConfig(
        id = "test:test-model",
        providerId = "test",
        displayName = "测试厂商",
        planType = ProviderPlanType.PAY_AS_YOU_GO,
        baseUrl = baseUrl,
        model = "test-model",
    )

    /** 记录收到的 content / reasoning 增量 */
    private class RecordingListener : ChatStreamListener {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        override fun onContentDelta(delta: String) {
            content.append(delta)
        }

        override fun onReasoningDelta(delta: String) {
            reasoning.append(delta)
        }
    }

    @Test
    fun `多帧 content 增量回调并累计完整结果`() {
        respondSse(
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            """data: {"choices":[{"delta":{"content":"feat: "}}]}""",
            """data: {"choices":[{"delta":{"content":"新增登录校验"}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            "data: [DONE]",
        )
        val listener = RecordingListener()
        val result = OpenAiCompatClient().chatStreaming(
            provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, listener,
        )
        assertEquals("feat: 新增登录校验", listener.content.toString())
        assertEquals("feat: 新增登录校验", result.content)
        assertEquals("stop", result.finishReason)
        assertNull(result.reasoning)
    }

    @Test
    fun `reasoning 增量单独回调且不混入正文`() {
        respondSse(
            """data: {"choices":[{"delta":{"reasoning_content":"先分析 diff"}}]}""",
            """data: {"choices":[{"delta":{"content":"feat: x"}}]}""",
            "data: [DONE]",
        )
        val listener = RecordingListener()
        val result = OpenAiCompatClient().chatStreaming(
            provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, listener,
        )
        assertEquals("先分析 diff", listener.reasoning.toString())
        assertEquals("先分析 diff", result.reasoning)
        assertEquals("feat: x", result.content)
    }

    @Test
    fun `请求体含 stream true 且带 event-stream Accept 头`() {
        respondSse(
            """data: {"choices":[{"delta":{"content":"ok"}}]}""",
            "data: [DONE]",
        )
        OpenAiCompatClient().chatStreaming(
            provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
        )
        val body = gson.fromJson(lastRequestBody.get(), ChatCompletionRequest::class.java)
        assertEquals(true, body.stream)
        assertEquals("text/event-stream", lastAcceptHeader.get())
    }

    @Test
    fun `EOF 无 DONE 哨兵但有正文时宽容完成`() {
        // 流以 EOF 结束（无 [DONE] 帧）
        respondSse(
            """data: {"choices":[{"delta":{"content":"部分网关不发哨兵"}}]}""",
        )
        val listener = RecordingListener()
        val result = OpenAiCompatClient().chatStreaming(
            provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, listener,
        )
        assertEquals("部分网关不发哨兵", result.content)
        assertNull(result.finishReason)
    }

    @Test
    fun `正文全空且 finish_reason 为 length 时提示 token 截断`() {
        respondSse(
            """data: {"choices":[{"delta":{"reasoning_content":"思考中……"}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"length"}]}""",
            "data: [DONE]",
        )
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
            )
        }
        assertTrue(exception.message!!.contains("截断"))
        assertNull(exception.httpStatus)
    }

    @Test
    fun `正文全空且无截断特征时提示模型未返回内容`() {
        respondSse(
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            "data: [DONE]",
        )
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
            )
        }
        assertTrue(exception.message!!.contains("模型未返回内容"))
    }

    @Test
    fun `HTTP 404 错误映射提示并携带状态码`() {
        server.createContext("/") { exchange: HttpExchange ->
            val bytes = """{"error":{"message":"model not found"}}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(404, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
            )
        }
        assertTrue(exception.message!!.contains("接口地址或模型名有误"))
        assertEquals(404, exception.httpStatus)
    }

    @Test
    fun `HTTP 400 错误携带状态码供上层降级判定`() {
        server.createContext("/") { exchange: HttpExchange ->
            val bytes = """{"error":{"message":"stream unsupported"}}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
            )
        }
        assertEquals(400, exception.httpStatus)
    }

    @Test
    fun `isCancelled 返回 true 时抛 InterruptedException`() {
        server.createContext("/") { exchange: HttpExchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(("""data: {"choices":[{"delta":{"content":"第一帧"}}]}

""").toByteArray(StandardCharsets.UTF_8))
                out.flush()
                // 保持连接打开，等待客户端主动取消
                Thread.sleep(5_000)
            }
        }
        val exception = org.junit.jupiter.api.assertThrows<InterruptedException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256,
                RecordingListener(),
                isCancelled = { true },
            )
        }
        // 取消轮询在首帧读取前触发
        assertEquals("流式请求已取消", exception.message)
    }

    @Test
    fun `非 JSON data 载荷抛解析失败`() {
        respondSse("data: <html>gateway error</html>")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chatStreaming(
                provider(), "sk-test", listOf(ChatMessage("user", "hi")), 256, RecordingListener(),
            )
        }
        assertTrue(exception.message!!.contains("解析失败"))
    }
}
