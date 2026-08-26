package com.lihtdev.lingxicode.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SseEventParser（SSE 行级解析）与 ChatChunkDecoder（chunk 载荷解码）单元测试 */
class SseEventParserTest {

    // ---- SseEventParser ----

    @Test
    fun `单行 data 帧在空行后产出事件`() {
        val parser = SseEventParser()
        assertNull(parser.feedLine("""data: {"a":1}"""))
        val event = parser.feedLine("")
        assertTrue(event is SseEvent.Data)
        assertEquals("""{"a":1}""", (event as SseEvent.Data).payload)
    }

    @Test
    fun `data 后无空格也能解析`() {
        val parser = SseEventParser()
        assertNull(parser.feedLine("""data:{"a":1}"""))
        val event = parser.feedLine("")
        assertEquals("""{"a":1}""", (event as SseEvent.Data).payload)
    }

    @Test
    fun `多行 data 以换行聚合`() {
        val parser = SseEventParser()
        parser.feedLine("data: 第一行")
        parser.feedLine("data: 第二行")
        val event = parser.feedLine("")
        assertEquals("第一行\n第二行", (event as SseEvent.Data).payload)
    }

    @Test
    fun `DONE 哨兵产出 Done 事件`() {
        val parser = SseEventParser()
        parser.feedLine("data: [DONE]")
        assertTrue(parser.feedLine("") is SseEvent.Done)
    }

    @Test
    fun `注释行被忽略`() {
        val parser = SseEventParser()
        assertNull(parser.feedLine(": keep-alive"))
        assertNull(parser.feedLine(""))
    }

    @Test
    fun `event id retry 行被忽略`() {
        val parser = SseEventParser()
        assertNull(parser.feedLine("event: message"))
        assertNull(parser.feedLine("id: 1"))
        assertNull(parser.feedLine("retry: 1000"))
        parser.feedLine("data: payload")
        assertEquals("payload", (parser.feedLine("") as SseEvent.Data).payload)
    }

    @Test
    fun `finish 产出无空行结尾的残余事件`() {
        val parser = SseEventParser()
        parser.feedLine("data: tail")
        val event = parser.finish()
        assertEquals("tail", (event as SseEvent.Data).payload)
    }

    @Test
    fun `finish 无残余时返回 null`() {
        val parser = SseEventParser()
        parser.feedLine("data: x")
        parser.feedLine("")
        assertNull(parser.finish())
    }

    @Test
    fun `data 值为空字符串的事件不产出`() {
        val parser = SseEventParser()
        // 空 data 行 + 空行：无有效内容，不产出事件
        parser.feedLine("data:")
        assertNull(parser.feedLine(""))
    }

    // ---- ChatChunkDecoder ----

    @Test
    fun `解码 content 增量`() {
        val delta = ChatChunkDecoder.decode("""{"choices":[{"delta":{"content":"feat:"}}]}""")
        assertEquals("feat:", delta?.content)
        assertNull(delta?.reasoning)
        assertNull(delta?.finishReason)
    }

    @Test
    fun `解码 reasoning_content 增量`() {
        val delta = ChatChunkDecoder.decode("""{"choices":[{"delta":{"reasoning_content":"思考中"}}]}""")
        assertEquals("思考中", delta?.reasoning)
        assertNull(delta?.content)
    }

    @Test
    fun `解码 reasoning 别名增量`() {
        val delta = ChatChunkDecoder.decode("""{"choices":[{"delta":{"reasoning":"别名思考"}}]}""")
        assertEquals("别名思考", delta?.reasoning)
    }

    @Test
    fun `解码 finish_reason 终止帧`() {
        val delta = ChatChunkDecoder.decode("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")
        assertEquals("stop", delta?.finishReason)
    }

    @Test
    fun `role-only 首帧返回 null`() {
        assertNull(ChatChunkDecoder.decode("""{"choices":[{"delta":{"role":"assistant"}}]}"""))
    }

    @Test
    fun `无 choices 返回 null`() {
        assertNull(ChatChunkDecoder.decode("""{"id":"x","object":"chat.completion.chunk"}"""))
    }

    @Test
    fun `非 JSON 载荷抛 AiClientException`() {
        val exception = org.junit.jupiter.api.assertThrows<AiClientException> {
            ChatChunkDecoder.decode("<html>error</html>")
        }
        assertFalse(exception.message.isNullOrEmpty())
    }
}
