package com.lihtdev.codesense.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ChatModels DTO 的 Gson 序列化/反解析测试 */
class ChatModelsTest {

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    @Test
    fun `请求体序列化使用下划线命名的 max_tokens`() {
        val request = ChatCompletionRequest(
            model = "GLM-5.3",
            messages = listOf(ChatMessage("system", "系统提示"), ChatMessage("user", "用户输入")),
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"max_tokens\""))
        assertFalse(json.contains("\"maxTokens\""))
        assertTrue(json.contains("\"GLM-5.3\""))
    }

    @Test
    fun `中文内容不被 HTML 转义`() {
        val request = ChatCompletionRequest("m", listOf(ChatMessage("user", "生成<提交>信息")))
        val json = gson.toJson(request)
        assertFalse(json.contains("\\u003c"))
        assertTrue(json.contains("生成<提交>信息"))
    }

    @Test
    fun `自定义 maxTokens 序列化到 max_tokens`() {
        val request = ChatCompletionRequest("m", listOf(ChatMessage("user", "x")), maxTokens = 4096)
        val json = gson.toJson(request)
        assertTrue(json.contains("\"max_tokens\":4096"))
    }

    @Test
    fun `响应反解析提取首个回复内容`() {
        val json = """{"choices":[{"message":{"role":"assistant","content":"feat: 新增导出"}}]}"""
        val response = gson.fromJson(json, ChatCompletionResponse::class.java)
        assertEquals("feat: 新增导出", response.firstContent())
    }

    @Test
    fun `空 choices 返回 null`() {
        val response = gson.fromJson("""{"choices":[]}""", ChatCompletionResponse::class.java)
        assertEquals(null, response.firstContent())
    }

    @Test
    fun `错误体反解析提取错误信息`() {
        val json = """{"error":{"message":"quota exceeded","type":"billing"}}"""
        val error = gson.fromJson(json, ChatError::class.java)
        assertEquals("quota exceeded", error.error?.message)
        assertEquals("billing", error.error?.type)
    }
}
