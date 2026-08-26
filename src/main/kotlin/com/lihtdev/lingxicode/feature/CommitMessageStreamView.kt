package com.lihtdev.lingxicode.feature

import com.intellij.openapi.vcs.CommitMessageI

/**
 * 提交信息流式回填视图（无对话框）：
 * 正文增量实时回填提交消息框，思考过程增量直接丢弃（按用户决策，提交信息不展示思考过程）。
 *
 * 全部回调在 EDT 触发。流式期间回填累计原文；完成后用清洗后定稿替换
 * （修正流式期间未清洗的形态，如模型偶尔输出的代码围栏残留）。
 * 失败/取消保留已回填的部分文本，不回滚（错误提示由 service 的通知负责）。
 */
class CommitMessageStreamView(
    private val commitMessage: CommitMessageI,
) : AiStreamView {

    /** 累计正文原文（EDT 串行写） */
    private val contentRaw = StringBuilder()

    /**
     * 守卫「宿主提交对话框已关闭」：生成期间用户可能关掉提交框，
     * 此后 setCommitMessage 会对已释放组件操作，静默 no-op（契约同对话框侧的 isDisposed 守卫）。
     */
    private fun safeSetCommitMessage(text: String) {
        try {
            commitMessage.setCommitMessage(text)
        } catch (_: Exception) {
            // 宿主已释放：不再回填
        }
    }

    override fun onContentDelta(delta: String) {
        contentRaw.append(delta)
        safeSetCommitMessage(contentRaw.toString())
    }

    override fun onReasoningDelta(delta: String) {
        // 提交信息功能不展示思考过程：直接丢弃
    }

    override fun onCompleted(cleaned: String) {
        // 定稿：清洗后文本替换（service 已按 150ms 节流，重复 set 开销可忽略）
        safeSetCommitMessage(cleaned)
    }

    override fun onFailed(errorMessage: String) {
        // 保留已回填的部分文本，不回滚
    }

    override fun onCancelled() {
        // 保留已回填的部分文本，不回滚
    }
}
