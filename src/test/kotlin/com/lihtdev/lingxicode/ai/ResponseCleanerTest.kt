package com.lihtdev.lingxicode.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ResponseCleaner 单元测试 */
class ResponseCleanerTest {

    @Test
    fun `纯文本保留完整内容`() {
        assertEquals("feat: 新增用户登录校验", ResponseCleaner.clean("feat: 新增用户登录校验"))
    }

    @Test
    fun `去除 markdown 围栏`() {
        val raw = "```\nfeat: 新增用户登录校验\n```"
        assertEquals("feat: 新增用户登录校验", ResponseCleaner.clean(raw))
    }

    @Test
    fun `去除带语言标注的围栏`() {
        val raw = "```text\nfeat: 修复分页越界\n```"
        assertEquals("feat: 修复分页越界", ResponseCleaner.clean(raw))
    }

    @Test
    fun `去除包裹引号`() {
        assertEquals("fix: 空指针异常", ResponseCleaner.clean("\"fix: 空指针异常\""))
    }

    @Test
    fun `多行输出保留完整内容（含 body 与 footer）`() {
        val raw = """
            feat: 新增导出功能

            支持 CSV 和 Excel 两种格式导出。

            BREAKING CHANGE: 导出 API 签名变更，原有的 export() 方法已移除
        """.trimIndent()
        val cleaned = ResponseCleaner.clean(raw)
        assertTrue(cleaned.startsWith("feat: 新增导出功能"))
        assertTrue(cleaned.contains("BREAKING CHANGE"))
    }

    @Test
    fun `cleanFirstLine 仅返回首行`() {
        val raw = """
            feat: 新增导出功能

            详细说明……
        """.trimIndent()
        assertEquals("feat: 新增导出功能", ResponseCleaner.cleanFirstLine(raw))
    }

    @Test
    fun `空白输入返回空串`() {
        assertEquals("", ResponseCleaner.clean("   \n  \n"))
    }

    @Test
    fun `cleanFirstLine 空白输入返回空串`() {
        assertEquals("", ResponseCleaner.cleanFirstLine("   \n  \n"))
    }

    @Test
    fun `格式校验合法样例`() {
        assertTrue(ResponseCleaner.isConventional("feat: 新增功能"))
        assertTrue(ResponseCleaner.isConventional("fix(ui): 修复按钮错位"))
        assertTrue(ResponseCleaner.isConventional("refactor!: 重构数据层（破坏性变更）"))
        assertTrue(ResponseCleaner.isConventional("feat(api)!: 重新设计认证接口"))
    }

    @Test
    fun `格式校验非法样例`() {
        assertFalse(ResponseCleaner.isConventional("新增了一个功能"))
        assertFalse(ResponseCleaner.isConventional("feat 新增功能"))
        assertFalse(ResponseCleaner.isConventional(""))
    }

    @Test
    fun `cleanMarkdown 去除整篇包裹围栏`() {
        val raw = "```markdown\n## 概述\n一段说明\n```"
        val cleaned = ResponseCleaner.cleanMarkdown(raw)
        assertTrue(cleaned.startsWith("## 概述"))
        assertTrue(cleaned.endsWith("一段说明"))
    }

    @Test
    fun `cleanMarkdown 保留内部代码围栏与缩进`() {
        val raw = "## 示例\n\n```kotlin\nfun main() {\n    println()\n}\n```"
        val cleaned = ResponseCleaner.cleanMarkdown(raw)
        assertTrue(cleaned.contains("```kotlin"))
        assertTrue(cleaned.contains("    println()"))
    }

    @Test
    fun `cleanMarkdown 无包裹围栏时保持原样`() {
        assertEquals("## 概述\n说明内容", ResponseCleaner.cleanMarkdown("## 概述\n说明内容"))
    }
}