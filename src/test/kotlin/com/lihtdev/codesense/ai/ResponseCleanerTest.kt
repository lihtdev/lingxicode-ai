package com.lihtdev.codesense.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ResponseCleaner 单元测试 */
class ResponseCleanerTest {

    @Test
    fun `纯文本直接取首行`() {
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
    fun `多行输出取第一行非空文本`() {
        val raw = "\n\nfeat: 新增导出功能\n\n以下是说明……"
        assertEquals("feat: 新增导出功能", ResponseCleaner.clean(raw))
    }

    @Test
    fun `空白输入返回空串`() {
        assertEquals("", ResponseCleaner.clean("   \n  \n"))
    }

    @Test
    fun `格式校验合法样例`() {
        assertTrue(ResponseCleaner.isConventional("feat: 新增功能"))
        assertTrue(ResponseCleaner.isConventional("fix(ui): 修复按钮错位"))
        assertTrue(ResponseCleaner.isConventional("refactor!: 重构数据层（破坏性变更）"))
    }

    @Test
    fun `格式校验非法样例`() {
        assertFalse(ResponseCleaner.isConventional("新增了一个功能"))
        assertFalse(ResponseCleaner.isConventional("feat 新增功能"))
        assertFalse(ResponseCleaner.isConventional(""))
    }
}
