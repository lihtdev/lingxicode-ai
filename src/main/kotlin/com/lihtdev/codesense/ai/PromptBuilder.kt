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
}