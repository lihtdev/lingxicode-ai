package com.lihtdev.codesense.ai

/**
 * 提示词组装（纯函数，可单测）。
 */
object PromptBuilder {

    /**
     * 组装「提交信息生成」功能的对话消息。
     *
     * @param fileList 变更文件路径清单
     * @param diffText 变更 diff 文本
     * @param outputLanguage 输出语言（"zh" 中文 / 其他值英文）
     */
    fun buildCommitMessages(fileList: List<String>, diffText: String, outputLanguage: String): List<ChatMessage> {
        val language = if (outputLanguage.equals("en", ignoreCase = true)) "English" else "中文（简体）"
        val system = """
            你是一名资深软件工程师，负责为 Git 提交生成高质量的提交信息。
            请严格遵循 Conventional Commits 1.0.0 规范（https://www.conventionalcommits.org/en/v1.0.0/），
            根据用户提供的代码变更（diff），生成一条规范的提交信息。

            规范要求：
            1. 提交信息结构：
               <type>[optional scope]: <description>
               [optional body]
               [optional footer(s)]

            2. type 必须是以下之一：feat、fix、docs、style、refactor、perf、test、build、ci、chore。
               - feat: 新功能
               - fix: 缺陷修复
               - docs: 仅文档变更
               - style: 代码风格（不影响代码逻辑的空白、格式等）
               - refactor: 重构（既非新功能，也非缺陷修复）
               - perf: 性能优化
               - test: 添加或修正测试
               - build: 构建系统或外部依赖变更
               - ci: CI 配置或脚本变更
               - chore: 其他杂项（不修改 src 或 test 文件）

            3. 可选 scope：用括号包裹，表示本次变更影响的范围（如 (ui)、(api)、(auth) 等）。

            4. 破坏性变更：若本次变更包含不向后兼容的改动，需在 type 后加 ! 标记（如 feat!: 或 fix(api)!:），
               并在 footer 中添加 BREAKING CHANGE: 说明。

            5. 描述部分用 $language 书写，紧跟在冒号空格后，概括本次变更的核心目的，不超过 50 个字，结尾不加句号。

            6. 只输出提交信息文本本身，禁止输出任何解释、前后缀、markdown 围栏或引号。
        """.trimIndent()
        val user = buildString {
            append("变更文件清单：\n")
            fileList.forEach { append("- ").append(it).append('\n') }
            append("\n代码变更 diff：\n")
            append(diffText)
        }
        return listOf(
            ChatMessage("system", system),
            ChatMessage("user", user),
        )
    }

    /**
     * 组装「代码解释」功能的对话消息。
     *
     * @param language 代码所属语言展示名（如 Kotlin / Java）
     * @param fileName 文件展示名
     * @param symbolKindName 符号类型展示名（已本地化，如「类」/「方法」/「代码块」）
     * @param symbolName 命中符号名（选中代码块时为 null）
     * @param code 待解释代码文本
     * @param outputLanguage 输出语言（"zh" 中文 / 其他值英文）
     */
    fun buildExplainCode(
        language: String,
        fileName: String,
        symbolKindName: String,
        symbolName: String?,
        code: String,
        outputLanguage: String,
    ): List<ChatMessage> {
        val outLang = if (outputLanguage.equals("en", ignoreCase = true)) "English" else "中文（简体）"
        val headings = if (outputLanguage.equals("en", ignoreCase = true)) EN_HEADINGS else ZH_HEADINGS

        val system = """
            你是一名资深软件工程师，擅长把代码用通俗、准确的语言解释给其他开发者。
            请根据用户提供的代码与上下文，输出一份结构化的解释。

            输出要求：
            1. 使用 Markdown 组织，但不要用 ``` 代码围栏包裹整篇回答。
            2. 只允许使用：二级标题（##）、三级标题（###）、加粗（**）、行内代码（`）、
               代码围栏（```，仅用于引用代码片段）、无序列表（- ）。禁止表格、链接与一级标题（#）。
            3. 严格按以下五个标题顺序输出，不得增删标题：
               ## ${headings[0]}
               ## ${headings[1]}
               ## ${headings[2]}
               ## ${headings[3]}
               ## ${headings[4]}
            4. 解释正文用 $outLang 书写；代码标识符与关键字保持原样。
            5. 「${headings[0]}」用一句话说清这段代码做什么（不超过 50 字）；
               「${headings[2]}」分要点说明关键流程 / 算法 / 数据流 / 控制流；
               「${headings[3]}」按符号类型组织：类/接口列主要成员与方法，方法/函数给入参与返回值，
               代码块给关键输入输出与依赖；
               「${headings[4]}」说明边界条件、异常、性能或潜在陷阱，无内容时写「无明显注意事项」。
            6. 只输出正文，不要寒暄与解释性废话。
        """.trimIndent()

        val user = buildString {
            append("代码语言：").append(language).append('\n')
            append("所在文件：").append(fileName).append('\n')
            append("符号类型：").append(symbolKindName)
            if (symbolName != null) append("（").append(symbolName).append("）")
            append("\n\n待解释代码：\n").append(code)
        }

        return listOf(
            ChatMessage("system", system),
            ChatMessage("user", user),
        )
    }

    /** 解释结果的中文标题序列（与模型输出约定一一对应） */
    private val ZH_HEADINGS = listOf("概述", "作用与用途", "核心逻辑", "关键成分", "注意事项")

    /** 解释结果的英文标题序列（与模型输出约定一一对应） */
    private val EN_HEADINGS = listOf("Overview", "Purpose", "Key Logic", "Key Elements", "Notes")
}