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
            你是一名资深软件工程师，负责为 Git 提交生成高质量提交信息。
            请根据用户提供的代码变更（diff），生成一条 Conventional Commits 格式的提交信息。
            规则：
            1. 输出格式为 `type: 描述`，type 必须是以下之一：feat、fix、docs、style、refactor、perf、test、build、ci、chore。
            2. 描述用$language 书写，一句话概括本次变更的核心目的，不超过 50 个字，结尾不加句号。
            3. 只输出提交信息这一行文本本身，禁止输出任何解释、前后缀、markdown 围栏或引号。
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
