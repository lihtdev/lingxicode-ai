package com.lihtdev.lingxicode.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** MarkdownToHtml 单元测试（受限子集转换） */
class MarkdownToHtmlTest {

    @Test
    fun `二级标题转为 h2`() {
        assertTrue(MarkdownToHtml.convert("## 概述").contains("<h2>概述</h2>"))
    }

    @Test
    fun `三级标题转为 h3`() {
        assertTrue(MarkdownToHtml.convert("### 核心逻辑").contains("<h3>核心逻辑</h3>"))
    }

    @Test
    fun `加粗转为 strong`() {
        assertTrue(MarkdownToHtml.convert("这是**重点**内容").contains("<strong>重点</strong>"))
    }

    @Test
    fun `行内代码转为 code`() {
        assertTrue(MarkdownToHtml.convert("调用 `foo()` 方法").contains("<code>foo()</code>"))
    }

    @Test
    fun `代码围栏保留缩进`() {
        val html = MarkdownToHtml.convert("```kotlin\nfun main() {\n    println()\n}\n```")
        assertTrue(html.contains("<pre><code>fun main() {\n    println()\n}</code></pre>"))
    }

    @Test
    fun `无序列表转为 ul li`() {
        val html = MarkdownToHtml.convert("- 甲\n- 乙")
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>甲</li>"))
        assertTrue(html.contains("<li>乙</li>"))
    }

    @Test
    fun `有序列表转为 ol li`() {
        val html = MarkdownToHtml.convert("1. 甲\n2. 乙")
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li>甲</li>"))
        assertTrue(html.contains("<li>乙</li>"))
        assertTrue(html.contains("</ol>"))
    }

    @Test
    fun `嵌套无序列表体现层级`() {
        val html = MarkdownToHtml.convert("- 甲\n  - 乙\n- 丙")
        assertTrue(html.contains("<li>甲<ul><li>乙</li></ul></li>"), "子项应嵌套在父项 li 内，实际：$html")
        assertTrue(html.contains("<li>丙</li>"), "实际：$html")
    }

    @Test
    fun `无序列表内嵌套有序列表`() {
        val html = MarkdownToHtml.convert("- 甲\n  1. 乙\n  2. 丙")
        assertTrue(html.contains("<li>甲<ol><li>乙</li><li>丙</li></ol></li>"), "实际：$html")
    }

    @Test
    fun `三级嵌套后回到顶层`() {
        val html = MarkdownToHtml.convert("- a\n  - b\n    - c\n- d")
        assertTrue(html.contains("<li>a<ul><li>b<ul><li>c</li></ul></li></ul></li>"), "实际：$html")
        assertTrue(html.contains("<li>d</li>"), "实际：$html")
    }

    @Test
    fun `四空格缩进风格识别为嵌套`() {
        val html = MarkdownToHtml.convert("- 甲\n    - 乙")
        assertTrue(html.contains("<li>甲<ul><li>乙</li></ul></li>"), "实际：$html")
    }

    @Test
    fun `制表符缩进识别为嵌套`() {
        val html = MarkdownToHtml.convert("- 甲\n\t- 乙")
        assertTrue(html.contains("<li>甲<ul><li>乙</li></ul></li>"), "实际：$html")
    }

    @Test
    fun `嵌套层内同层切换列表类型`() {
        val html = MarkdownToHtml.convert("- a\n  - b\n  1. c")
        assertTrue(html.contains("<li>a<ul><li>b</li></ul><ol><li>c</li></ol></li>"), "实际：$html")
    }

    @Test
    fun `非 1 起始的编号行按普通段落处理`() {
        // CommonMark 规则：仅编号 1 可开启有序列表，避免「2026. 计划」被吞掉编号
        assertEquals("<p>2026. 发布计划</p>", MarkdownToHtml.convert("2026. 发布计划"))
    }

    @Test
    fun `有序列表兼容右括号写法`() {
        val html = MarkdownToHtml.convert("1) 甲")
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li>甲</li>"))
    }

    @Test
    fun `无序与有序列表相邻时分别闭合`() {
        val html = MarkdownToHtml.convert("- 甲\n1. 乙")
        val ulEnd = html.indexOf("</ul>")
        val olStart = html.indexOf("<ol>")
        assertTrue(ulEnd >= 0 && olStart >= 0, "应同时存在 ul 闭合与 ol 开始标签")
        assertTrue(ulEnd < olStart, "ul 应先闭合再打开 ol")
    }

    @Test
    fun `相邻普通行合为一段`() {
        assertEquals("<p>hello world</p>", MarkdownToHtml.convert("hello\nworld"))
    }

    @Test
    fun `HTML 特殊字符被转义`() {
        assertTrue(MarkdownToHtml.convert("`<script>`").contains("&lt;script&gt;"))
    }

    @Test
    fun `围栏内 Unicode 制表符原样保留`() {
        // ASCII 流程图渲染依赖：Unicode 框线字符不被转义、缩进与换行保留在 pre 块内
        val html = MarkdownToHtml.convert("```\n┌─校验─┐\n│ 通过 │\n└──────┘\n```")
        assertTrue(html.contains("<pre><code>"))
        assertTrue(html.contains("┌─校验─┐\n│ 通过 │\n└──────┘"))
    }

    @Test
    fun `空输入返回空串`() {
        assertEquals("", MarkdownToHtml.convert(""))
        assertEquals("", MarkdownToHtml.convert("   \n  \n"))
    }
}