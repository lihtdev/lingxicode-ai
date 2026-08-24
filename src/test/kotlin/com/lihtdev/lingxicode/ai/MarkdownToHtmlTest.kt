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
    fun `相邻普通行合为一段`() {
        assertEquals("<p>hello world</p>", MarkdownToHtml.convert("hello\nworld"))
    }

    @Test
    fun `HTML 特殊字符被转义`() {
        assertTrue(MarkdownToHtml.convert("`<script>`").contains("&lt;script&gt;"))
    }

    @Test
    fun `空输入返回空串`() {
        assertEquals("", MarkdownToHtml.convert(""))
        assertEquals("", MarkdownToHtml.convert("   \n  \n"))
    }
}