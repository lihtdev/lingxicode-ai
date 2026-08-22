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
        assertTrue(messages[0].content.contains("Conventional Commits 1.0.0"))
        assertTrue(messages[0].content.contains("https://www.conventionalcommits.org/en/v1.0.0/"))
    }

    @Test
    fun `英文模式下系统提示词要求 English 描述`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "en")
        assertTrue(messages[0].content.contains("English"))
    }

    @Test
    fun `系统提示词包含所有标准 type 列表`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "zh")
        val content = messages[0].content
        val expectedTypes = listOf("feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore")
        expectedTypes.forEach { type ->
            assertTrue(content.contains(type), "系统提示词应包含 type: $type")
        }
    }

    @Test
    fun `系统提示词包含破坏性变更标记说明`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "zh")
        assertTrue(messages[0].content.contains("!"))
        assertTrue(messages[0].content.contains("BREAKING CHANGE"))
    }

    @Test
    fun `系统提示词包含 scope 可选说明`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "zh")
        assertTrue(messages[0].content.contains("scope"))
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