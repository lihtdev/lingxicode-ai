package com.lihtdev.lingxicode.ai

/**
 * Markdown 子集 → HTML 转换（纯函数，可单测）。
 *
 * 仅支持「代码解释」结果所约定的子集：1-3 级标题、加粗、行内代码、
 * ``` 代码围栏、无序列表（- / *）、有序列表（1. / 1)）、空行分段的段落。与 [PromptBuilder.buildExplainCode]
 * 对模型的输出约束保持一致；不依赖任何第三方 Markdown 库。
 */
object MarkdownToHtml {

    /** 将说明结果 Markdown 转为可在只读 HTML 视图中展示的 HTML 片段 */
    fun convert(markdown: String): String {
        val out = StringBuilder()
        val lines = markdown.lines()
        var i = 0
        var openListTag: String? = null

        fun closeList() {
            openListTag?.let { out.append("</").append(it).append(">\n") }
            openListTag = null
        }

        /** 切换到指定列表标签（类型不同则先闭合旧列表），幂等 */
        fun openList(tag: String) {
            if (openListTag == tag) return
            closeList()
            out.append("<").append(tag).append(">\n")
            openListTag = tag
        }

        while (i < lines.size) {
            val trimmed = lines[i].trim()
            // 有序列表正文（仅编号 1 可开启新列表；为 null 时按普通文本处理）
            val orderedText = orderedItemText(trimmed, openListTag == "ol")

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

                isUnorderedItem(trimmed) -> {
                    openList("ul")
                    out.append("<li>").append(inline(trimmed.drop(2).trim())).append("</li>\n")
                    i++
                }

                orderedText != null -> {
                    openList("ol")
                    out.append("<li>").append(inline(orderedText)).append("</li>\n")
                    i++
                }

                else -> {
                    closeList()
                    // 累积连续普通文本行作为一段
                    val sb = StringBuilder()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.isEmpty() || headingLevel(t) > 0 || t.startsWith("```") ||
                            isUnorderedItem(t) || orderedItemText(t, inOrderedList = false) != null
                        ) break
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

    private fun isUnorderedItem(trimmed: String): Boolean = trimmed.startsWith("- ") || trimmed.startsWith("* ")

    /**
     * 有序列表项（`1. ` / `1) ` 两种写法），返回编号标记后的正文；非列表项返回 null。
     * 遵循 CommonMark 规则：仅编号 1 可开启新列表（[inOrderedList] = false 时），
     * 避免「2026. 计划」这类普通文本行被误判为列表项并吞掉编号。
     */
    private fun orderedItemText(trimmed: String, inOrderedList: Boolean): String? {
        val match = ORDERED_ITEM_REGEX.find(trimmed) ?: return null
        if (!inOrderedList && match.groupValues[1] != "1") return null
        return match.groupValues[2]
    }

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
    private val ORDERED_ITEM_REGEX = Regex("""^(\d+)[.)]\s+(.*)$""")
}
