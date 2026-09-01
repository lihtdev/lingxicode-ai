package com.lihtdev.lingxicode.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CodeHighlighter 单元测试 */
class CodeHighlighterTest {

    /** 测试用四色（keyword / string / number / comment） */
    private val colors = CodeHighlighter.HighlightColors("#cc7832", "#6a8759", "#6897bb", "#808080")

    private fun keywordSpan(text: String) = "<span style=\"color:#cc7832\">$text</span>"

    private fun stringSpan(text: String) = "<span style=\"color:#6a8759\">$text</span>"

    private fun numberSpan(text: String) = "<span style=\"color:#6897bb\">$text</span>"

    private fun commentSpan(text: String) = "<span style=\"color:#808080\">$text</span>"

    private fun spanCount(html: String): Int = Regex("<span").findAll(html).count()

    @Test
    fun `关键字着色其余原样`() {
        assertEquals(
            keywordSpan("fun") + " main()",
            CodeHighlighter.highlight("fun main()", "kotlin", colors),
        )
    }

    @Test
    fun `字符串含转义引号整体着色`() {
        val code = "val s = \"a\\\"b\""
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        // "a\"b" 应在一个 string span 内（转义引号未截断 span）
        assertTrue(html.contains(stringSpan("\"a\\\"b\"")), "转义引号字符串应为单个 span: $html")
        assertEquals(2, spanCount(html), "应恰好 2 个 span（val + 字符串）: $html")
    }

    @Test
    fun `未闭合字符串止于行尾`() {
        // 流式中间态：第一行字符串未闭合，第二行正常解析
        val code = "val s = \"abc\nval t = 1"
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        assertTrue(html.contains(stringSpan("\"abc")), "未闭合字符串应止于行尾: $html")
        assertTrue(html.contains(keywordSpan("val") + " t = " + numberSpan("1")), "第二行应正常着色: $html")
    }

    @Test
    fun `块注释跨行整体着色`() {
        val code = "/* 说明\n<b> */ val"
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        assertTrue(html.contains(commentSpan("/* 说明\n&lt;b&gt; */")), "块注释应整体着色且内部转义: $html")
        assertTrue(html.endsWith(" " + keywordSpan("val")), "注释闭合后的关键字应正常着色: $html")
    }

    @Test
    fun `行注释至行尾`() {
        val code = "val x = 1 // 说明\nval y = 2"
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        assertTrue(html.contains(commentSpan("// 说明") + "\n"), "行注释应至行尾: $html")
        assertTrue(html.contains("\n" + keywordSpan("val") + " y"), "下一行不应被注释吞掉: $html")
    }

    @Test
    fun `数字各形态`() {
        listOf("0x1F", "3.14", "2L", "1e5", "1_000").forEach { literal ->
            val html = CodeHighlighter.highlight(literal, "kotlin", colors)
            assertEquals(numberSpan(literal), html, "数字字面量 $literal 应整体着色")
        }
        // 1.toString() 的 '.' 后非数字：只吃 1
        assertEquals(numberSpan("1") + ".toString()", CodeHighlighter.highlight("1.toString()", "kotlin", colors))
    }

    @Test
    fun `HTML 特殊字符逐 token 转义`() {
        val code = "val s = \"<b>\" // <i>"
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        assertTrue(html.contains("&lt;b&gt;"), "字符串内 <b> 应转义: $html")
        assertTrue(html.contains(commentSpan("// &lt;i&gt;")), "注释内 <i> 应转义: $html")
        assertFalse(html.contains("<b>") || html.contains("<i>"), "不应有裸 HTML 标签: $html")
    }

    @Test
    fun `未知语言降级纯转义`() {
        assertEquals("&lt;a&gt;", CodeHighlighter.highlight("<a>", "mermaid", colors))
    }

    @Test
    fun `语言 id 大小写不敏感`() {
        val code = "fun main()"
        val expected = CodeHighlighter.highlight(code, "kotlin", colors)
        assertEquals(expected, CodeHighlighter.highlight(code, "Kotlin", colors))
        assertEquals(expected, CodeHighlighter.highlight(code, "KOTLIN", colors))
    }

    @Test
    fun `SQL 关键字大小写不敏感且双写引号转义`() {
        val upper = CodeHighlighter.highlight("SELECT * FROM t", "sql", colors)
        val lower = CodeHighlighter.highlight("select * from t", "sql", colors)
        // 大小写不敏感指判定不敏感：两种写法都着色，输出各自保留原文大小写
        assertTrue(upper.contains(keywordSpan("SELECT")), "大写 SQL 关键字应着色: $upper")
        assertTrue(lower.contains(keywordSpan("select")), "小写 SQL 关键字应着色: $lower")

        val sql = CodeHighlighter.highlight("'don''t'", "sql", colors)
        assertEquals(stringSpan("'don''t'"), sql, "SQL 双写引号应整体为单个字符串 span")
    }

    @Test
    fun `多 token 顺序不重叠`() {
        val html = CodeHighlighter.highlight("val x = 10 // note", "kotlin", colors)
        val keywordIdx = html.indexOf(keywordSpan("val"))
        val numberIdx = html.indexOf(numberSpan("10"))
        val commentIdx = html.indexOf(commentSpan("// note"))
        assertTrue(keywordIdx in 0 until numberIdx, "关键字 span 应最先: $html")
        assertTrue(numberIdx < commentIdx, "数字 span 其后: $html")
        assertEquals(3, spanCount(html), "应恰好 3 个 span: $html")
    }

    @Test
    fun `null 颜色类别不上色`() {
        val onlyKeyword = CodeHighlighter.HighlightColors("#cc7832", null, null, null)
        val html = CodeHighlighter.highlight("val s = \"a\" // c", "kotlin", onlyKeyword)
        assertTrue(html.contains(keywordSpan("val")), "关键字应着色: $html")
        assertFalse(html.contains(stringSpan("\"a\"")), "字符串不应着色: $html")
        assertFalse(html.contains(commentSpan("// c")), "注释不应着色: $html")
        assertEquals(1, spanCount(html), "应只有 1 个 span: $html")
    }

    @Test
    fun `Kotlin 三引号原始字符串跨行`() {
        val code = "\"\"\"ab\ncd\"\"\""
        val html = CodeHighlighter.highlight(code, "kotlin", colors)
        assertEquals(stringSpan("\"\"\"ab\ncd\"\"\""), html, "三引号字符串应跨行整体着色")
    }

    @Test
    fun `中文注释与中文标识符`() {
        val html = CodeHighlighter.highlight("val 用户名 = 1 // 中文说明", "kotlin", colors)
        assertTrue(html.contains(commentSpan("// 中文说明")), "中文注释应整体着色: $html")
        // 中文标识符不染关键字色（整体作为普通 token 原样输出）
        assertTrue(html.contains(" 用户名 "), "中文标识符应原样保留: $html")
        assertFalse(html.contains("color:#cc7832\">用户名"), "中文标识符不应误染关键字色: $html")
    }

    @Test
    fun `别名映射`() {
        assertTrue(CodeHighlighter.highlight("def f():", "py", colors).contains(keywordSpan("def")))
        assertTrue(CodeHighlighter.highlight("func main()", "golang", colors).contains(keywordSpan("func")))
        assertTrue(CodeHighlighter.highlight("if x; then fi", "bash", colors).contains(keywordSpan("fi")))
        // json / yaml 只染 true/false/null
        assertTrue(CodeHighlighter.highlight("ok: true", "yml", colors).contains(keywordSpan("true")))
        assertFalse(CodeHighlighter.highlight("name: value", "yml", colors).contains("<span"))
    }

    @Test
    fun `全 null 四色输出与纯转义逐字节一致`() {
        // 降级即旧行为的最强锚点：四色全空时 highlight 等价于 escape（& < > 转义，引号不转义）
        val allNull = CodeHighlighter.HighlightColors(null, null, null, null)
        val code = "val s = \"<b>\" // <i>"
        assertEquals("val s = \"&lt;b&gt;\" // &lt;i&gt;", CodeHighlighter.highlight(code, "kotlin", allNull))
    }

    @Test
    fun `超长输入熔断降级纯转义`() {
        // 超过 MAX_HIGHLIGHT_CHARS（100_000）熔断：宁可不发色不可卡 EDT
        val code = "<a>".repeat(33_334) // 100_002 字符
        assertEquals("&lt;a&gt;".repeat(33_334), CodeHighlighter.highlight(code, "kotlin", colors))
    }
}
