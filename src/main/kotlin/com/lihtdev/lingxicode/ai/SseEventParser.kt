package com.lihtdev.lingxicode.ai

import com.google.gson.Gson
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle

/**
 * SSE 行级解析器（纯逻辑、线程封闭、无平台依赖）。
 *
 * 由调用方逐行喂入（已剥离行尾符，CRLF 由 BufferedReader.readLine 天然处理），
 * 空行触发事件产出。兼容性处理：
 * - `data:` 后容忍零或一空格；
 * - 多行 data 以 `\n` 聚合（SSE 规范）；
 * - 忽略注释行（`:` 开头，厂商用作心跳 keep-alive）与 `event:`/`id:`/`retry:` 行；
 * - `data: [DONE]` 产出 [SseEvent.Done] 哨兵。
 */
class SseEventParser {

    /** 当前事件聚合中的 data 行缓冲（null 表示尚未开始聚合） */
    private var dataLines: MutableList<String>? = null

    /**
     * 喂入一行（不含行尾符）。
     * @return 事件凑齐（空行触发）时返回 [SseEvent]，否则 null
     */
    fun feedLine(line: String): SseEvent? {
        if (line.isEmpty()) {
            // 空行：结束当前事件；无 data 行或载荷为空（心跳/分隔）时不产出
            val lines = dataLines
            dataLines = null
            return dispatch(lines)
        }
        if (line.startsWith(":")) return null // 注释行（心跳）
        if (line.startsWith("data:")) {
            val value = line.removePrefix("data:").removePrefix(" ")
            val lines = dataLines ?: mutableListOf<String>().also { dataLines = it }
            lines.add(value)
        }
        // 其余字段行（event:/id:/retry: 等）忽略
        return null
    }

    /**
     * 流 EOF 时调用：尚有未以空行结尾的 data 行时产出残余事件，否则 null。
     */
    fun finish(): SseEvent? {
        val lines = dataLines
        dataLines = null
        return dispatch(lines)
    }

    /** 聚合 data 行产出事件；无行或空载荷（含纯哨兵判断）时返回 null */
    private fun dispatch(lines: MutableList<String>?): SseEvent? {
        if (lines == null || lines.isEmpty()) return null
        val payload = lines.joinToString("\n")
        if (payload.isEmpty()) return null
        if (payload == DONE_SENTINEL) return SseEvent.Done
        return SseEvent.Data(payload)
    }

    private companion object {
        /** OpenAI 兼容流式的终止哨兵 */
        const val DONE_SENTINEL = "[DONE]"
    }
}

/** SSE 事件 */
sealed interface SseEvent {

    /** data 事件（载荷为非哨兵字符串） */
    data class Data(val payload: String) : SseEvent

    /** `data: [DONE]` 终止事件 */
    object Done : SseEvent
}

/** 单帧解析结果（content / reasoning 增量与终止原因，均可为 null） */
data class ChatChunkDelta(
    val content: String?,
    val reasoning: String?,
    val finishReason: String?,
)

/**
 * SSE data 载荷 → chunk 增量（纯函数）。
 *
 * role-only 首帧 / 无 choices / 全字段为 null 的帧返回 null（调用方跳过）；
 * 非 JSON 载荷抛 [AiClientException]（与非流式路径的解析失败同语义，保守报错）。
 */
object ChatChunkDecoder {

    private val gson: Gson = Gson()

    fun decode(payload: String): ChatChunkDelta? {
        val chunk = try {
            gson.fromJson(payload, ChatCompletionChunk::class.java)
        } catch (e: Exception) {
            throw AiClientException(
                LingxiCodeBundle.message("error.parseFailed", payload.take(200)),
                e,
            )
        } ?: return null // 载荷字面 "null" 等非对象内容：无可解码增量
        val choice = chunk.choices.firstOrNull() ?: return null
        val delta = choice.delta
        val content = delta?.content
        val reasoning = delta?.reasoning
        val finishReason = choice.finishReason
        if (content == null && reasoning == null && finishReason == null) return null
        return ChatChunkDelta(content, reasoning, finishReason)
    }
}
