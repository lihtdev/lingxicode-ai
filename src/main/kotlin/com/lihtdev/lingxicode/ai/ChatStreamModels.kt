package com.lihtdev.lingxicode.ai

import com.google.gson.annotations.SerializedName

/**
 * chat/completions 流式响应 chunk（OpenAI 兼容 SSE 格式）。
 *
 * 每帧形如：`data: {"choices":[{"delta":{...},"finish_reason":null}]}`
 */
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
) {
    data class ChunkChoice(
        val delta: DeltaMessage? = null,
        @SerializedName("finish_reason") val finishReason: String? = null,
    )
}

/**
 * chunk 增量消息。思考过程字段用 Gson alternate 同时兼容两种字段名：
 * - `reasoning_content`（DeepSeek R1 系及多数国内兼容端点）
 * - `reasoning`（OpenRouter 等变体）
 */
data class DeltaMessage(
    val content: String? = null,
    @SerializedName(value = "reasoning_content", alternate = ["reasoning"])
    val reasoning: String? = null,
)

/**
 * 流式调用最终结果（由客户端逐帧累计）。
 *
 * @param content 累计正文
 * @param reasoning 累计思考过程（模型未输出则为 null）
 * @param finishReason 终止原因（stop / length 等；EOF 无终止帧时为 null）
 */
data class ChatStreamResult(
    val content: String,
    val reasoning: String?,
    val finishReason: String?,
)

/**
 * 流式增量回调契约。回调在后台线程触发，实现方不得直接操作 Swing 组件；
 * 完成经返回值表达、失败经异常表达，以缩小回调面。
 */
interface ChatStreamListener {

    /** 正文增量到达 */
    fun onContentDelta(delta: String) {}

    /** 思考过程增量到达 */
    fun onReasoningDelta(delta: String) {}
}
