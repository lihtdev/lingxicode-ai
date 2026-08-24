package com.lihtdev.codesense.code

/**
 * 符号类型判别（纯函数，可单测）。
 *
 * 依据代码声明头做语言无关的启发式分类。用于两类场景：
 * 1. 编辑器 gutter「解释」图标是否挂载（[ExplainSymbolKind.isExplainableDeclaration] 门控）；
 * 2. Prompt 框定与标题展示。
 *
 * 说明：仅依赖 `com.intellij.modules.platform` 时拿不到 `PsiClass`/`PsiMethod` 等结构型 PSI，
 * 因此这里以「声明白文本」的关键字启发式近似结构判定。为减少误判，会：
 * - 跳过纯注释行；
 * - 剥掉前导注解 / 属性（Java/Kotlin 的 `@Annotation`、C# 的 `[Attribute]`、Python 装饰器）；
 * - 允许函数关键字前的可见性 / 修饰符（如 `suspend fun`、`private fun`、`pub fn`、`export function`）。
 * 仍可能有少量边界情况漏判，识别失败时按 [ExplainSymbolKind.BLOCK] 处理，不影响解释能力。
 */
object SymbolKindDetector {

    /** 接口 / trait / protocol 等契约声明关键字（含 Java `@interface` 注解类型） */
    private val INTERFACE_REGEX = Regex("""\b(interface|trait|protocol)\b""")

    /** 类 / 枚举 / 结构体 / 记录 / 单例 / 类型别名等「类型」声明关键字 */
    private val CLASS_REGEX = Regex("""\b(class|enum|struct|record|object)\b|\btype\s+[A-Za-z_$][\w$]*\s*=""")

    /**
     * 函数式语言声明关键字（Python def / Kotlin fun / Swift func / JS function / Go func / Rust fn / Lua function）。
     * 锚定行首、允许前导可见性 / 修饰符，避免把 `let f = function(){}` 之类的变量初始化误判成函数。
     */
    private val FUNCTION_KEYWORD_REGEX = Regex(
        """^\s*(?:(?:async|suspend|inline|operator|infix|tailrec|external|actual|expect|public|private|protected|internal|export|static|pub|pure|unsafe|extern|virtual|override|final|abstract|default|readonly|open)\s+)*(?:def|fun|fn|func|function)\b""",
    )

    /** 方法签名（Java / C# / C++ 等：修饰符 + 返回类型 + 名称 + 参数列表） */
    private val METHOD_SIGNATURE_REGEX = Regex(
        """^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|virtual|override|async|const|export|default|readonly)\s+)*(?:void|int|long|short|byte|double|float|boolean|bool|char|string|object|decimal|[A-Z][\w<>,.?&\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)""",
    )

    /**
     * 无关键字的「名称 + 参数列表」方法声明（TS/JS 类方法 `foo(): void`、构造器等）。
     * 锚定行首、只允许前导修饰符/可选返回类型，并排除 `if/for/while/...` 等控制关键字，
     * 避免把普通语句或 `x = foo()` 之类的初始化误判。
     */
    private val CALLABLE_REGEX = Regex(
        """^\s*(?:(?:public|private|protected|static|final|abstract|virtual|override|async|export|default|internal|open|suspend|operator|inline|infix|extern|unsafe|pub|pure)\s+)*(?:[A-Za-z_$][\w$]*[\w$.<>,?&\[\]]*\s+)?(?!if\b|for\b|while\b|switch\b|catch\b|with\b|return\b|throw\b|new\b|delete\b|typeof\b|instanceof\b|in\b|of\b|case\b|do\b|else\b)[A-Za-z_$][\w$]*\s*\([^)]*\)""",
    )

    /** 前导注解 / 属性（`@Annotation(...)`、`@a.b.c(...)`、C# `[Attribute]`） */
    private val LEADING_DECORATOR = Regex(
        """^(?:@[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:\s*\([^)]*\))?|\[[^\]\n]*\])\s*""",
    )

    /**
     * 判别代码文本的符号类型。
     *
     * @param code 待解释的代码文本（通常为完整声明或选中代码块）
     * @return 判别结果，未知时返回 [ExplainSymbolKind.BLOCK]
     */
    fun detect(code: String): ExplainSymbolKind {
        for (rawLine in code.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || isCommentLine(line)) continue

            // 契约关键字（含 `@interface`）按原始行优先判定
            if (INTERFACE_REGEX.containsMatchIn(line)) return ExplainSymbolKind.INTERFACE

            // 剥掉前导注解 / 属性（@Override、[HttpGet]、@app.route(...) 等）后再看真正的声明头
            val head = stripLeadingDecorators(line).trim()
            if (head.isEmpty() || isCommentLine(head)) continue

            return classify(head)
        }
        return ExplainSymbolKind.BLOCK
    }

    /** 对已剥离注解的声明头做关键字分类 */
    private fun classify(head: String): ExplainSymbolKind {
        if (CLASS_REGEX.containsMatchIn(head)) return ExplainSymbolKind.CLASS
        if (FUNCTION_KEYWORD_REGEX.containsMatchIn(head)) return ExplainSymbolKind.FUNCTION
        if (METHOD_SIGNATURE_REGEX.containsMatchIn(head)) return ExplainSymbolKind.METHOD
        if (CALLABLE_REGEX.containsMatchIn(head)) return ExplainSymbolKind.METHOD
        return ExplainSymbolKind.BLOCK
    }

    /** 是否为纯注释行（不含可判别的声明头） */
    private fun isCommentLine(line: String): Boolean =
        line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") ||
            line.startsWith("#") || line.startsWith("<!--")

    /** 反复剥掉行首的注解 / 属性装饰，返回剩余声明头 */
    private fun stripLeadingDecorators(line: String): String {
        var s = line.trim()
        while (true) {
            val m = LEADING_DECORATOR.find(s) ?: return s
            if (m.range.first != 0) return s
            s = s.substring(m.range.last + 1).trim()
            if (s.isEmpty()) return s
        }
    }
}