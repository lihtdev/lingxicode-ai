package com.lihtdev.codesense.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PromptBuilder 单元测试 */
class PromptBuilderTest {

    @Test
    fun `消息结构为 system 加 user 两条`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("src/A.kt"), "diff 内容", "zh")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test
    fun `中文模式下系统提示词要求中文描述`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "zh")
        assertTrue(messages[0].content.contains("中文"))
        assertTrue(messages[0].content.contains("Conventional Commits"))
        // 只输出一行的约束
        assertTrue(messages[0].content.contains("只输出提交信息"))
    }

    @Test
    fun `英文模式下系统提示词要求 English 描述`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "en")
        assertTrue(messages[0].content.contains("English"))
    }

    @Test
    fun `用户消息包含文件清单与 diff`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("src/A.kt", "src/B.kt"), "+new line", "zh")
        val user = messages[1].content
        assertTrue(user.contains("- src/A.kt"))
        assertTrue(user.contains("- src/B.kt"))
        assertTrue(user.contains("+new line"))
    }
}
