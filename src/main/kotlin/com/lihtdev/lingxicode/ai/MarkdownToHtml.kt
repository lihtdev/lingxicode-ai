package com.lihtdev.lingxicode.ai

/**
 * Markdown 子集 → HTML 转换（纯函数，可单测）。
 *
 * 仅支持「代码解释」结果所约定的子集：1-3 级标题、加粗、行内代码、
 * ``` 代码围栏、无序列表（- / *）、空行分段的段落。与 [PromptBuilder.buildExplainCode]
 * 对模型的输出约束保持一致；不依赖任何第三方 Markdown 库。
 */
object MarkdownToHtml {

    /** 将说明结果 Markdown 转为可在只读 HTML 视图中展示的 HTML 片段 */
    fun convert(markdown: String): String {
        val out = StringBuilder()
        val lines = markdown.lines()
        var i = 0
        var inList = false

        fun closeList() {
            if (inList) {
                out.append("</ul>\n")
                inList = false
            }
        }

        while (i < lines.size) {
            val trimmed = lines[i].trim()

            when {
                trimmed.isEmpty() -> {
                    closeList()
                    i++
                }

                trimmed.startsWith("```") -> {
                    closeList()
                    i++ // 跳过起始围栏
                    val codeLines = ArrayList<String>()
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // 跳过结束围栏
                    out.append("<pre><code>").append(escape(codeLines.joinToString("\n")))
                        .append("</code></pre>\n")
                }

                headingLevel(trimmed) > 0 -> {
                    closeList()
                    val level = headingLevel(trimmed)
                    val text = trimmed.dropWhile { it == '#' }.trim()
                    out.append("<h").append(level).append(">").append(inline(text))
                        .append("</h").append(level).append(">\n")
                    i++
                }

                isListItem(trimmed) -> {
                    if (!inList) {
                        out.append("<ul>\n")
                        inList = true
                    }
                    out.append("<li>").append(inline(trimmed.drop(2).trim())).append("</li>\n")
                    i++
                }

                else -> {
                    closeList()
                    // 累积连续普通文本行作为一段
                    val sb = StringBuilder()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.isEmpty() || headingLevel(t) > 0 || t.startsWith("```") || isListItem(t)) break
                        if (sb.isNotEmpty()) sb.append(' ')
                        sb.append(t)
                        i++
                    }
                    out.append("<p>").append(inline(sb.toString())).append("</p>\n")
                }
            }
        }
        closeList()
        return out.toString().trim()
    }

    private fun headingLevel(trimmed: String): Int = when {
        trimmed.startsWith("### ") -> 3
        trimmed.startsWith("## ") -> 2
        trimmed.startsWith("# ") -> 1
        else -> 0
    }

    private fun isListItem(trimmed: String): Boolean = trimmed.startsWith("- ") || trimmed.startsWith("* ")

    /** 转义 + 行内标记（行内代码、加粗） */
    private fun inline(text: String): String {
        var s = escape(text)
        s = s.replace(INLINE_CODE_REGEX, "<code>$1</code>")
        s = s.replace(BOLD_REGEX, "<strong>$1</strong>")
        return s
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
}