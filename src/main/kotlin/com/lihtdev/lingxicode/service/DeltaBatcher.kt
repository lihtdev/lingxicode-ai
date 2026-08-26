package com.lihtdev.lingxicode.service

/**
 * 流式增量合并器（纯逻辑、线程安全）。
 *
 * 后台线程（SSE 回调）经 [offer] 累积 content / reasoning 增量；
 * EDT 刷新时经 [drain] 原子取走并清空。时间窗节流不在此类
 * （由 AiInvocationService 侧的 Alarm 调度保证间隔），本类只负责合并与原子取走。
 */
class DeltaBatcher {

    private val lock = Any()
    private val content = StringBuilder()
    private val reasoning = StringBuilder()

    /**
     * 追加增量（contentDelta / reasoningDelta 均可为 null 表示该通道无内容）。
     * @return 是否存在待刷新内容
     */
    fun offer(contentDelta: String?, reasoningDelta: String?): Boolean {
        synchronized(lock) {
            if (contentDelta != null) content.append(contentDelta)
            if (reasoningDelta != null) reasoning.append(reasoningDelta)
            return content.isNotEmpty() || reasoning.isNotEmpty()
        }
    }

    /**
     * 取走并清空累积的增量（应在 EDT 调用）。
     * @return (content 增量, reasoning 增量)，无内容时对应项为空串
     */
    fun drain(): Pair<String, String> {
        synchronized(lock) {
            val result = content.toString() to reasoning.toString()
            content.setLength(0)
            reasoning.setLength(0)
            return result
        }
    }

    /** 是否有待刷新内容（完成前强制 flush 判断用） */
    fun hasPending(): Boolean {
        synchronized(lock) {
            return content.isNotEmpty() || reasoning.isNotEmpty()
        }
    }
}
