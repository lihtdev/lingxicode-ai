package com.lihtdev.lingxicode.git

/**
 * 变更种类（与平台 Change.Type 解耦，便于纯函数单测）。
 */
enum class ChangeKind(val label: String) {
    NEW("新增"),
    MODIFIED("修改"),
    DELETED("删除"),
    RENAMED("重命名"),
    UNKNOWN("变更"),
}

/**
 * 单文件 diff section 格式化（纯函数，可单测）。
 * 截断策略：单文件最多 [MAX_LINES_PER_FILE] 行参与 diff，超出中间截断。
 */
object DiffFormatter {

    /** 单文件最多参与 diff 的行数 */
    const val MAX_LINES_PER_FILE = 400

    /**
     * 格式化单个文件的 diff section。
     *
     * @param filePath 文件路径
     * @param kind 变更种类
     * @param beforeText 变更前内容（新文件为 null）
     * @param afterText 变更后内容（删除文件为 null）
     */
    fun formatFileSection(filePath: String, kind: ChangeKind, beforeText: String?, afterText: String?): String {
        val truncatedBefore = truncateLines(beforeText)
        val truncatedAfter = truncateLines(afterText)
        return buildString {
            appendLine("$filePath（${kind.label}）")
            if (kind == ChangeKind.DELETED) {
                appendLine("（整文件删除，内容略）")
            } else {
                val diffLines = SimpleLineDiff.unifiedDiff(truncatedBefore, truncatedAfter)
                if (diffLines.isEmpty()) {
                    appendLine("（内容无行级差异）")
                } else {
                    diffLines.forEach { appendLine(it) }
                }
            }
            appendLine()
        }
    }

    /** 超过行数上限时截断（保留前 MAX_LINES_PER_FILE 行并注明） */
    private fun truncateLines(text: String?): String? {
        if (text == null) return null
        val lines = text.lines()
        return if (lines.size <= MAX_LINES_PER_FILE) {
            text
        } else {
            lines.take(MAX_LINES_PER_FILE).joinToString("\n") +
                "\n…（超出 $MAX_LINES_PER_FILE 行，已截断）"
        }
    }
}
