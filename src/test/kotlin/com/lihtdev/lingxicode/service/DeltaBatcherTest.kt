package com.lihtdev.lingxicode.service

import com.lihtdev.lingxicode.ai.AiClientException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** DeltaBatcher（流式增量合并器）单元测试 */
class DeltaBatcherTest {

    @Test
    fun `offer 后 drain 取走并清空`() {
        val batcher = DeltaBatcher()
        batcher.offer("正文A", null)
        batcher.offer("正文B", "思考A")
        assertTrue(batcher.hasPending())
        val (content, reasoning) = batcher.drain()
        assertEquals("正文A正文B", content)
        assertEquals("思考A", reasoning)
        assertFalse(batcher.hasPending())
    }

    @Test
    fun `空 drain 返回空串`() {
        val batcher = DeltaBatcher()
        val (content, reasoning) = batcher.drain()
        assertEquals("", content)
        assertEquals("", reasoning)
    }

    @Test
    fun `仅 reasoning 增量时 content 为空`() {
        val batcher = DeltaBatcher()
        batcher.offer(null, "思考")
        val (content, reasoning) = batcher.drain()
        assertEquals("", content)
        assertEquals("思考", reasoning)
    }

    @Test
    fun `多线程 offer 后 drain 原子取走全部内容`() {
        val batcher = DeltaBatcher()
        val threads = 8
        val perThread = 100
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            executor.submit {
                start.await()
                repeat(perThread) { batcher.offer("x", "y") }
            }
        }
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)
        val (content, reasoning) = batcher.drain()
        assertEquals(threads * perThread, content.length)
        assertEquals(threads * perThread, reasoning.length)
        // 二次 drain 应为空（已原子取走清空）
        assertEquals("", batcher.drain().first)
    }
}

/** StreamFallbackPolicy（流式→非流式回退判定）单元测试 */
class StreamFallbackPolicyTest {

    private fun httpError(status: Int) = AiClientException("错误", httpStatus = status)

    @Test
    fun `400 可回退`() {
        assertTrue(StreamFallbackPolicy.shouldFallback(httpError(400), receivedAnyContentDelta = false))
    }

    @Test
    fun `404 可回退`() {
        assertTrue(StreamFallbackPolicy.shouldFallback(httpError(404), receivedAnyContentDelta = false))
    }

    @Test
    fun `401 不回退（重试无意义）`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(401), receivedAnyContentDelta = false))
    }

    @Test
    fun `403 不回退（重试无意义）`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(403), receivedAnyContentDelta = false))
    }

    @Test
    fun `429 不回退（重试无意义）`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(429), receivedAnyContentDelta = false))
    }

    @Test
    fun `5xx 不回退（非流式大概率同样失败）`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(500), receivedAnyContentDelta = false))
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(503), receivedAnyContentDelta = false))
    }

    @Test
    fun `无 httpStatus 的网络错误不回退`() {
        assertFalse(
            StreamFallbackPolicy.shouldFallback(
                AiClientException("网络失败"),
                receivedAnyContentDelta = false,
            ),
        )
    }

    @Test
    fun `已向用户展示正文增量后不回退（避免内容重复）`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(httpError(400), receivedAnyContentDelta = true))
    }

    @Test
    fun `非 AiClientException 不回退`() {
        assertFalse(StreamFallbackPolicy.shouldFallback(RuntimeException("其他异常"), receivedAnyContentDelta = false))
    }
}
