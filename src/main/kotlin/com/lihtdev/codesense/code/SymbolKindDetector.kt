package com.lihtdev.codesense.code

/**
 * 符号类型判别（纯函数，可单测）。
 *
 * 依据代码首行关键字做语言无关的启发式分类：仅用于 Prompt 框定与标题展示，
 * 判别失败时降级为 [ExplainSymbolKind.BLOCK]，不影响解释能力。
 */
object SymbolKindDetector {

    /** 接口 / trait / protocol 等契约声明关键字 */
    private val INTERFACE_REGEX = Regex("""\b(interface|trait|protocol)\b""")

    /** 类 / 枚举 / 结构体 / 记录 / 单例 / 类型别名等「类型」声明关键字 */
    private val CLASS_REGEX = Regex("""\b(class|enum|struct|record|object)\b|\btype\s+[A-Za-z_$][\w$]*\s*=""")

    /** 函数式语言声明关键字（Python def / Kotlin fun / Swift func / JS function / Go func / Rust fn / Lua function） */
    private val FUNCTION_KEYWORD_REGEX = Regex("""^\s*(async\s+)?(def|fun|fn|func|function)\b""")

    /** 方法签名（Java / C# / C++ / TypeScript 带修饰符或返回类型 + 名称 + 参数列表） */
    private val METHOD_SIGNATURE_REGEX = Regex(
        """^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|virtual|override|async|const|export|default)\s+)*(?:void|int|long|short|byte|double|float|boolean|bool|char|[A-Z][\w<>,.?&\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)""",
    )

    /**
     * 判别代码文本的符号类型（取首个非空行做启发式判断）。
     *
     * @param code 待解释的代码文本（通常为完整声明或选中代码块）
     * @return 判别结果，未知时返回 [ExplainSymbolKind.BLOCK]
     */
    fun detect(code: String): ExplainSymbolKind {
        val firstLine = code.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return ExplainSymbolKind.BLOCK

        if (INTERFACE_REGEX.containsMatchIn(firstLine)) return ExplainSymbolKind.INTERFACE
        if (CLASS_REGEX.containsMatchIn(firstLine)) return ExplainSymbolKind.CLASS
        if (FUNCTION_KEYWORD_REGEX.containsMatchIn(firstLine)) return ExplainSymbolKind.FUNCTION
        if (METHOD_SIGNATURE_REGEX.containsMatchIn(firstLine)) return ExplainSymbolKind.METHOD
        return ExplainSymbolKind.BLOCK
    }
}