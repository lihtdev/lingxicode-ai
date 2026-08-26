package com.lihtdev.lingxicode.feature

/**
 * 流式渲染视图生命周期契约（代码解释 / 代码评审对话框等实现）。
 *
 * 全部方法在 EDT 回调；实现方必须在每个回调内自行守卫
 * 「视图已被用户关闭」（如 isDisposed），关闭后的回调应静默 no-op。
 */
interface AiStreamView {

    /** 正文增量到达（service 已按时间窗合并，调用频率有上限） */
    fun onContentDelta(delta: String)

    /** 思考过程增量到达（不展示思考过程的功能可空实现） */
    fun onReasoningDelta(delta: String)

    /** 生成完成：cleaned 为 feature.cleanResponse 清洗后的最终文本 */
    fun onCompleted(cleaned: String)

    /** 生成失败（非取消）：展示错误态，保留已生成部分 */
    fun onFailed(errorMessage: String)

    /** 用户取消：保留已生成部分 */
    fun onCancelled()

    companion object {
        /** 空实现：供不需要任何 UI 副作用的场景（如测试）复用 */
        val NOOP: AiStreamView = object : AiStreamView {
            override fun onContentDelta(delta: String) = Unit
            override fun onReasoningDelta(delta: String) = Unit
            override fun onCompleted(cleaned: String) = Unit
            override fun onFailed(errorMessage: String) = Unit
            override fun onCancelled() = Unit
        }
    }
}
