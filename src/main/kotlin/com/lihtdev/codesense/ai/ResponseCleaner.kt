package com.lihtdev.codesense.ai

/**
 * 模型输出清洗（纯函数，可单测）。
 */
object ResponseCleaner {

    /** Conventional Commits 首行格式校验 */
    private val CONVENTIONAL_REGEX =
        Regex("""^(feat|fix|docs|style|refactor|perf|test|build|ci|chore)(\([^)]+\))?(!)?:\s*.+""")

    /**
     * 清洗模型输出：
     * 1. 去除 markdown 围栏行（``` 及语言标注）；
     * 2. 去除首尾包裹引号；
     * 3. 取第一行非空文本作为提交信息。
     */
    fun clean(raw: String): String {
        val noFences = raw.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
            .trim('"', '“', '”')
        return noFences.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: ""
    }

    /** 是否符合 Conventional Commits 格式（type 可带 scope 与 !） */
    fun isConventional(message: String): Boolean = CONVENTIONAL_REGEX.matches(message)
}
