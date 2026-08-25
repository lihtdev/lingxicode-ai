package com.lihtdev.lingxicode.ai

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

    @Test
    fun `解释消息结构为 system 加 user 两条`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", "greet", "fun greet() {}", "zh")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test
    fun `中文解释提示词包含五个中文标题`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        listOf("概述", "作用与用途", "核心逻辑", "关键成分", "注意事项").forEach { title ->
            assertTrue(system.contains("## $title"), "应包含标题 $title")
        }
    }

    @Test
    fun `英文解释提示词包含五个英文标题`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "function", null, "code", "en")
        val system = messages[0].content
        listOf("Overview", "Purpose", "Key Logic", "Key Elements", "Notes").forEach { title ->
            assertTrue(system.contains("## $title"), "应包含标题 $title")
        }
        assertTrue(system.contains("English"))
    }

    @Test
    fun `中文解释提示词包含条件性流程图章节约定`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        assertTrue(system.contains("## 流程图"), "应包含条件性第六标题 流程图")
        assertTrue(system.contains("仅当"), "应说明流程图的条件触发")
        assertTrue(system.contains("多分支"), "应列举复杂控制流特征")
    }

    @Test
    fun `英文解释提示词包含条件性 Flowchart 章节约定`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "function", null, "code", "en")
        assertTrue(messages[0].content.contains("## Flowchart"), "应包含条件性第六标题 Flowchart")
    }

    @Test
    fun `解释提示词约束流程图宽度与规模上限`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        assertTrue(system.contains("72"), "应约束流程图每行宽度上限")
        assertTrue(system.contains("30 行"), "应约束流程图总行数上限")
    }

    @Test
    fun `解释提示词允许流程图使用代码围栏`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        assertTrue(system.contains("绘制流程图"), "围栏用途应放宽到流程图")
        assertTrue(system.contains("无语言标注"), "流程图围栏应要求无语言标注")
    }

    @Test
    fun `解释用户消息包含语言文件符号类型与代码`() {
        val messages = PromptBuilder.buildExplainCode("Python", "calc.py", "函数", "add", "def add(a, b):", "zh")
        val user = messages[1].content
        assertTrue(user.contains("Python"))
        assertTrue(user.contains("calc.py"))
        assertTrue(user.contains("函数"))
        assertTrue(user.contains("add"))
        assertTrue(user.contains("def add(a, b):"))
    }
}