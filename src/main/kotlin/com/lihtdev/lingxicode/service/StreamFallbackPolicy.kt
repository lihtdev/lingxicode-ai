package com.lihtdev.lingxicode.service

import com.lihtdev.lingxicode.ai.AiClientException

/**
 * 流式 → 非流式回退判定（纯函数）。
 *
 * 仅当流式请求已到达服务器并收到「可重试类」4xx 错误、且尚未向用户展示
 * 任何正文增量时，降级为非流式重试才有意义：
 * - 401/403/429（鉴权/限流）重试无意义，直接报错；
 * - 5xx 与网络错误下非流式大概率同样失败，不回退；
 * - 已流出部分正文后失败不回退——保留已生成部分并展示错误，
 *   体验优于推倒重来（且降级会造成正文重复）。
 *
 * 注意 [receivedAnyContentDelta] 只统计 content 增量：reasoning-only
 * 阶段失败时降级不会造成正文重复（正文才是清洗/回填对象）。
 */
object StreamFallbackPolicy {

    /** 可回退的 4xx 状态码之外需要排除的重试无意义状态码 */
    private val NON_RETRYABLE_STATUS = setOf(401, 403, 429)

    fun shouldFallback(error: Throwable, receivedAnyContentDelta: Boolean): Boolean {
        if (receivedAnyContentDelta) return false
        if (error !is AiClientException) return false
        val status = error.httpStatus ?: return false
        return status in 400..499 && status !in NON_RETRYABLE_STATUS
    }
}
