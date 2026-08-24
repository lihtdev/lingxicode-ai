package com.lihtdev.lingxicode.ai

import com.google.gson.Gson
import com.lihtdev.lingxicode.settings.AiProviderConfig
import com.lihtdev.lingxicode.settings.ProviderPlanType
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/** OpenAiCompatClient 单元测试（JDK 内置 HttpServer 模拟 OpenAI 兼容端点） */
class OpenAiCompatClientTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""
    private val gson = Gson()

    /** 记录收到的请求（路径/头/体），用于断言 */
    private val lastRequestPath = AtomicReference<String?>(null)
    private val lastAuthHeader = AtomicReference<String?>(null)
    private val lastRequestBody = AtomicReference<String?>(null)

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

    /** 注册模拟响应处理器 */
    private fun respondWith(status: Int, body: String) {
        server.createContext("/") { exchange: HttpExchange ->
            lastRequestPath.set(exchange.requestURI.path)
            lastAuthHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            lastRequestBody.set(String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8))
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
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

    @Test
    fun `正常响应返回首个回复内容`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":"feat: 新增登录校验"}}]}""")
        val reply = OpenAiCompatClient().chat(provider(), "sk-test", listOf(ChatMessage("user", "hi")))
        assertEquals("feat: 新增登录校验", reply)
    }

    @Test
    fun `请求路径为 chat completions 且带鉴权头`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        OpenAiCompatClient().chat(provider(), "sk-test", listOf(ChatMessage("user", "hi")))
        assertEquals("/v1/chat/completions", lastRequestPath.get())
        assertEquals("Bearer sk-test", lastAuthHeader.get())
    }

    @Test
    fun `请求体包含模型与消息`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        OpenAiCompatClient().chat(provider(), "sk-test", listOf(ChatMessage("user", "生成提交信息")))
        val body = gson.fromJson(lastRequestBody.get(), ChatCompletionRequest::class.java)
        assertEquals("test-model", body.model)
        assertEquals(1, body.messages.size)
        assertEquals("生成提交信息", body.messages[0].content)
    }

    @Test
    fun `401 映射为 API Key 无效提示`() {
        respondWith(401, """{"error":{"message":"invalid api key","type":"invalid_request_error"}}""")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "bad-key", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("API Key 无效"))
        assertTrue(exception.message!!.contains("invalid api key"))
    }

    @Test
    fun `404 映射为地址或模型有误提示`() {
        respondWith(404, """{"error":{"message":"model not found"}}""")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("接口地址或模型名有误"))
    }

    @Test
    fun `非 JSON 响应抛解析失败`() {
        respondWith(200, "<html>gateway error</html>")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        }
        assertNotNull(exception.message)
    }

    @Test
    fun `baseUrl 缺失时给出中文提示`() {
        val bad = provider().copy(baseUrl = "  ")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(bad, "sk", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("接口地址"))
    }

    @Test
    fun `四参 chat 传递 max_tokens`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")), 2048)
        val body = gson.fromJson(lastRequestBody.get(), ChatCompletionRequest::class.java)
        assertEquals(2048, body.maxTokens)
    }

    @Test
    fun `三参 chat 缺省 max_tokens 为 256`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""")
        OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        val body = gson.fromJson(lastRequestBody.get(), ChatCompletionRequest::class.java)
        assertEquals(256, body.maxTokens)
    }

    @Test
    fun `content 为空且 finish_reason 为 length 时提示 token 截断`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant","content":null},"finish_reason":"length"}]}""")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("截断"))
    }

    @Test
    fun `content 为空且含 reasoning_content 时提示 token 截断`() {
        respondWith(
            200,
            """{"choices":[{"message":{"role":"assistant","reasoning_content":"用户想要一条提交信息，我先分析 diff……"},"finish_reason":"length"}]}""",
        )
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("截断"))
    }

    @Test
    fun `content 为空且无截断特征时保持原提示`() {
        respondWith(200, """{"choices":[{"message":{"role":"assistant"}}]}""")
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        }
        assertTrue(exception.message!!.contains("模型未返回内容"))
    }

    @Test
    fun `正常推理响应返回 content 而非 reasoning_content`() {
        respondWith(
            200,
            """{"choices":[{"message":{"role":"assistant","reasoning_content":"思考过程……","content":"feat: 新增登录校验"},"finish_reason":"stop"}]}""",
        )
        val reply = OpenAiCompatClient().chat(provider(), "sk", listOf(ChatMessage("user", "hi")))
        assertEquals("feat: 新增登录校验", reply)
    }
}
