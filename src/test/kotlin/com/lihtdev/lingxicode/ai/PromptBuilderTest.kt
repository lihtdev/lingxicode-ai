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
        assertTrue(messages[0].content.contains("Conventional Commits"))
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
    fun `系统提示词包含可选 body 规范说明`() {
        val messages = PromptBuilder.buildCommitMessages(listOf("a.kt"), "diff", "zh")
        assertTrue(messages[0].content.contains("可选 body"))
        assertTrue(messages[0].content.contains("无序列表"))
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
    fun `解释提示词允许有序列表`() {
        val messages = PromptBuilder.buildExplainCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        assertTrue(messages[0].content.contains("有序列表"), "允许语法应与渲染子集对齐（有序列表）")
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

    @Test
    fun `评审消息结构为 system 加 user 两条`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "函数", "greet", "fun greet() {}", "zh")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test
    fun `中文评审提示词按重要性降序包含总体评价十维度与总结`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        val expected = listOf(
            "总体评价", "正确性与潜在 Bug", "安全性", "并发安全", "健壮性与异常处理",
            "性能", "资源管理", "设计与架构", "可维护性", "可读性", "代码规范", "总结",
        )
        var previousIndex = -1
        expected.forEach { title ->
            val index = system.indexOf("## $title")
            assertTrue(index >= 0, "应包含标题 $title")
            assertTrue(index > previousIndex, "标题 $title 应按重要性降序排列")
            previousIndex = index
        }
        assertTrue(system.contains("中文"), "应要求中文输出")
    }

    @Test
    fun `英文评审提示词包含对应英文标题序列`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "function", null, "code", "en")
        val system = messages[0].content
        listOf(
            "Overall Assessment", "Correctness & Potential Bugs", "Security", "Concurrency Safety",
            "Robustness & Exception Handling", "Performance", "Resource Management",
            "Design & Architecture", "Maintainability", "Readability", "Code Style", "Summary",
        ).forEach { title ->
            assertTrue(system.contains("## $title"), "应包含标题 $title")
        }
        assertTrue(system.contains("English"))
    }

    @Test
    fun `评审提示词约束维度内有问题才给改进建议且不编造问题`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        val system = messages[0].content
        assertTrue(system.contains("**改进建议**"), "有问题时应给出维度内改进建议")
        assertTrue(system.contains("无明显问题"), "无问题时应写占位文案")
        assertTrue(system.contains("不要为凑数编造问题"), "应约束不编造问题")
    }

    @Test
    fun `英文评审提示词维度内建议与占位文案同步切换`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "function", null, "code", "en")
        val system = messages[0].content
        assertTrue(system.contains("**Suggestions**"))
        assertTrue(system.contains("No issues found"))
    }

    @Test
    fun `评审提示词允许有序列表`() {
        val messages = PromptBuilder.buildReviewCode("Kotlin", "A.kt", "函数", null, "code", "zh")
        assertTrue(messages[0].content.contains("有序列表"), "允许语法应与渲染子集对齐（有序列表）")
    }

    @Test
    fun `评审用户消息包含语言文件符号与代码且符号名为空时无括号残留`() {
        val withName = PromptBuilder.buildReviewCode("Python", "calc.py", "函数", "add", "def add(a, b):", "zh")
        val userWithName = withName[1].content
        assertTrue(userWithName.contains("Python"))
        assertTrue(userWithName.contains("calc.py"))
        assertTrue(userWithName.contains("函数（add）"))
        assertTrue(userWithName.contains("待评审代码：\ndef add(a, b):"))

        val withoutName = PromptBuilder.buildReviewCode("Python", "calc.py", "代码块", null, "x = 1", "zh")
        val userWithoutName = withoutName[1].content
        assertTrue(userWithoutName.contains("符号类型：代码块\n"), "symbolName 为 null 时不应残留括号")
        assertTrue(!userWithoutName.contains("（）"), "不应出现空括号")
    }

    @Test
    fun `逐行解释消息结构为 system 加 user 两条`() {
        val messages = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", "greet", "fun greet() {}", "zh")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
    }

    @Test
    fun `中文逐行解释提示词要求单个代码围栏输出`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("只包含一个"), "应要求整篇回答为单个围栏")
        assertTrue(system.contains("```"), "应包含围栏标记示例")
        assertTrue(system.contains("围栏之外不得输出"), "应禁止围栏外的任何内容")
    }

    @Test
    fun `中文逐行解释提示词要求注释位于代码上方且对齐`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("上方"), "注释应位于代码行上方")
        assertTrue(system.contains("相同缩进"), "注释应与被注释代码行对齐")
    }

    @Test
    fun `中文逐行解释提示词要求代码原样保留`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("原样保留"), "应要求代码原样保留")
        assertTrue(system.contains("不得改写"), "应禁止改写代码")
    }

    @Test
    fun `中文逐行解释提示词约定跳过无实义行与原有注释行`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("空行"), "应约定跳过空行")
        assertTrue(system.contains("右括号"), "应约定跳过纯闭括号行")
        assertTrue(system.contains("原有注释行"), "原有注释行不应叠加解释注释")
    }

    @Test
    fun `中文逐行解释提示词给出行注释语法示例`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("//"), "应给出 C 系行注释语法示例")
        assertTrue(system.contains("#"), "应给出脚本系行注释语法示例")
        assertTrue(system.contains("--"), "应给出 SQL 行注释语法示例")
    }

    @Test
    fun `中文逐行解释提示词禁止注释之外的其他修改并要求中文正文`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "函数", null, "code", "zh")[0].content
        assertTrue(system.contains("不加行号"), "应禁止添加行号")
        assertTrue(system.contains("中文（简体）"), "应要求中文注释正文")
    }

    @Test
    fun `英文逐行解释提示词含围栏约束与英文注释要求`() {
        val system = PromptBuilder.buildExplainLineByLine("Kotlin", "A.kt", "function", null, "code", "en")[0].content
        assertTrue(system.contains("exactly one"), "应要求单个围栏")
        assertTrue(system.contains("English"), "应要求英文注释正文")
        assertTrue(system.contains("verbatim"), "应要求代码原样保留")
    }

    @Test
    fun `逐行解释用户消息包含语言文件符号与代码且符号名为空时无括号残留`() {
        val withName = PromptBuilder.buildExplainLineByLine("Python", "calc.py", "函数", "add", "def add(a, b):", "zh")
        val userWithName = withName[1].content
        assertTrue(userWithName.contains("Python"))
        assertTrue(userWithName.contains("calc.py"))
        assertTrue(userWithName.contains("函数（add）"))
        assertTrue(userWithName.contains("待逐行解释代码：\ndef add(a, b):"))

        val withoutName = PromptBuilder.buildExplainLineByLine("Python", "calc.py", "代码块", null, "x = 1", "zh")
        val userWithoutName = withoutName[1].content
        assertTrue(userWithoutName.contains("符号类型：代码块\n"), "symbolName 为 null 时不应残留括号")
        assertTrue(!userWithoutName.contains("（）"), "不应出现空括号")
    }
}