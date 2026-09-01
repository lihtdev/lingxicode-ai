package com.lihtdev.lingxicode.ai

/**
 * Markdown 子集 → HTML 转换（纯函数，可单测）。
 *
 * 仅支持「代码解释」结果所约定的子集：1-3 级标题、加粗、行内代码、
 * ``` 代码围栏、无序列表（- / *）、有序列表（1. / 1)）、空行分段的段落。
 * 列表按行首缩进支持多级嵌套（相对缩进：比当前层更深即进入下一层，
 * 兼容 2/4 空格缩进风格），输出 `<li>父<ul><li>子</li></ul></li>` 结构。
 * 与 [PromptBuilder.buildExplainCode] 对模型的输出约束保持一致；不依赖任何第三方 Markdown 库。
 */
object MarkdownToHtml {

    /** 列表层级栈中的一层：该层列表的行首缩进宽度与标签（ul / ol） */
    private data class ListLevel(val indent: Int, val tag: String)

    /**
     * 嵌套列表状态机：按行首缩进维护层级栈并向 [out] 输出标签，栈内缩进自底向顶严格递增。
     * li 惰性闭合——新兄弟项 / 弹层 / 收尾时才补 `</li>`，以产出
     * `<li>父<ul><li>子</li></ul></li>` 的嵌套结构。
     */
    private class ListNestingWriter(private val out: StringBuilder) {
        private val stack = ArrayDeque<ListLevel>()

        /** 弹出一层列表：闭合该层最后一个 li 与列表标签；宿主父 li 留待父层事件闭合 */
        private fun closeListLevel() {
            val level = stack.removeLast()
            out.append("</li></").append(level.tag).append('>')
        }

        /** 弹出所有比 [indent] 更深的层级（[indent] 传 -1 即全部闭合） */
        fun closeListsTo(indent: Int) {
            while (stack.isNotEmpty() && stack.last().indent > indent) closeListLevel()
        }

        fun closeAllLists() = closeListsTo(-1)

        /** 当前最深层是否为有序列表（供「仅编号 1 可开新列表」判定） */
        fun inOrderedList(): Boolean = stack.lastOrNull()?.tag == "ol"

        /**
         * 处理一个列表项：按缩进对齐层级栈后输出 `<li>正文`。
         * 与栈顶同层则先闭合上一个兄弟项；比栈顶更深则在宿主 li 内开启嵌套列表；
         * 同层但类型不同（ul ↔ ol）则整层闭合后另起新列表。
         */
        fun appendListItem(indent: Int, tag: String, content: String) {
            closeListsTo(indent)
            val top = stack.lastOrNull()
            when {
                top == null || top.indent < indent -> {
                    out.append('<').append(tag).append('>')
                    stack.addLast(ListLevel(indent, tag))
                }

                top.indent == indent && top.tag != tag -> {
                    closeListLevel()
                    out.append('<').append(tag).append('>')
                    stack.addLast(ListLevel(indent, tag))
                }

                else -> out.append("</li>") // 同层同类型：闭合上一个兄弟项
            }
            out.append("<li>").append(content)
        }
    }

    /**
     * 将说明结果 Markdown 转为可在只读 HTML 视图中展示的 HTML 片段。
     *
     * @param codeColors 代码块语法高亮四色；为 null（默认）时代码块保持纯文本转义，
     *   仅带语言标注的围栏会走高亮（无标注的流程图围栏不受影响）
     */
    fun convert(markdown: String, codeColors: CodeHighlighter.HighlightColors? = null): String {
        val out = StringBuilder()
        val lists = ListNestingWriter(out)
        val lines = markdown.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            // 有序列表正文（仅编号 1 可开启新列表；为 null 时按普通文本处理）
            val orderedText = orderedItemText(trimmed, lists.inOrderedList())

            when {
                trimmed.isEmpty() -> {
                    lists.closeAllLists()
                    i++
                }

                trimmed.startsWith("```") -> {
                    lists.closeAllLists()
                    i = appendFencedCode(out, lines, i, codeColors)
                }

                headingLevel(trimmed) > 0 -> {
                    lists.closeAllLists()
                    val level = headingLevel(trimmed)
                    val text = trimmed.dropWhile { it == '#' }.trim()
                    out.append("<h").append(level).append(">").append(inline(text))
                        .append("</h").append(level).append(">\n")
                    i++
                }

                isUnorderedItem(trimmed) -> {
                    lists.appendListItem(indentWidth(line), "ul", inline(trimmed.drop(2).trim()))
                    i++
                }

                orderedText != null -> {
                    lists.appendListItem(indentWidth(line), "ol", inline(orderedText))
                    i++
                }

                else -> i = appendParagraph(out, lists, lines, i)
            }
        }
        lists.closeAllLists()
        return out.toString().trim()
    }

    /** 输出 ``` 围栏代码块（原样保留内部行），返回处理后的下一行下标 */
    private fun appendFencedCode(
        out: StringBuilder,
        lines: List<String>,
        start: Int,
        codeColors: CodeHighlighter.HighlightColors?,
    ): Int {
        // 语言标注：起始围栏行 ``` 后的 info string 首词（如 ```kotlin 的 kotlin）；
        // 空则不高亮（流程图围栏无标注），带参数标注（```kotlin hl_lines="1"）只取语言词
        val language = lines[start].trim().drop(3).trim().substringBefore(' ')
        var i = start + 1 // 跳过起始围栏
        val codeLines = ArrayList<String>()
        while (i < lines.size && !lines[i].trim().startsWith("```")) {
            codeLines.add(lines[i])
            i++
        }
        i++ // 跳过结束围栏
        val code = codeLines.joinToString("\n")
        out.append("<pre><code>").append(
            if (codeColors != null && language.isNotEmpty()) {
                CodeHighlighter.highlight(code, language, codeColors)
            } else {
                escape(code)
            }
        ).append("</code></pre>\n")
        return i
    }

    /** 累积连续普通文本行作为一段输出，返回处理后的下一行下标 */
    private fun appendParagraph(
        out: StringBuilder,
        lists: ListNestingWriter,
        lines: List<String>,
        start: Int,
    ): Int {
        lists.closeAllLists()
        val sb = StringBuilder()
        var i = start
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
        return i
    }

    /** 行首缩进宽度：空格计 1、制表符计 4，用于列表嵌套层级判定 */
    private fun indentWidth(line: String): Int {
        var width = 0
        for (ch in line) {
            when (ch) {
                ' ' -> width++
                '\t' -> width += 4
                else -> return width
            }
        }
        return width
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
