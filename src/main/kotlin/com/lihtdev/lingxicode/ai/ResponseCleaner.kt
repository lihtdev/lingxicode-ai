package com.lihtdev.lingxicode.ai

/**
 * 模型输出清洗（纯函数，可单测）。
 *
 * 按 Conventional Commits 1.0.0 规范清洗模型输出：
 * 去除 markdown 围栏、引号包裹，保留完整提交信息（含可选的 body 与 footer）。
 */
object ResponseCleaner {

    /** Conventional Commits 首行格式校验（type(scope)?!?: description） */
    private val CONVENTIONAL_REGEX =
        Regex("""^(feat|fix|docs|style|refactor|perf|test|build|ci|chore)(\([^)]+\))?(!)?:\s*.+""")

    /**
     * 清洗模型输出：
     * 1. 去除 markdown 围栏行（``` 及语言标注）；
     * 2. 去除首尾包裹引号；
     * 3. 保留所有非空行（支持 Conventional Commits 的 body 与 footer）。
     */
    fun clean(raw: String): String {
        val noFences = raw.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
            .trim('"', '“', '”')
        val lines = noFences.lineSequence()
            .map { it.trim() }
            .dropWhile { it.isBlank() }
            .toList()
        return lines.joinToString("\n").trim()
    }

    /**
     * 清洗后仅取首行（用于只需要标题的场景）。
     */
    fun cleanFirstLine(raw: String): String {
        return clean(raw).lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?: ""
    }

    /** 是否符合 Conventional Commits 格式（type 可带 scope 与 !） */
    fun isConventional(message: String): Boolean = CONVENTIONAL_REGEX.matches(message)

    /**
     * 清洗 Markdown 结构化输出（用于代码解释等长文本功能）。
     *
     * 与 [clean] 的区别：不逐行 trim、不删除内部 ``` 代码围栏，
     * 仅在整篇回答被首尾 ``` 围栏包裹时去除该对最外层围栏，保留内部结构与缩进。
     */
    fun cleanMarkdown(raw: String): String {
        val lines = raw.lineSequence().toList()
        var start = 0
        var end = lines.size - 1
        while (start <= end && lines[start].isBlank()) start++
        while (end >= start && lines[end].isBlank()) end--
        if (start <= end && lines[start].trim().startsWith("```") && lines[end].trim().startsWith("```")) {
            start++
            end--
        }
        return lines.subList(start, end + 1).joinToString("\n").trim()
    }
}