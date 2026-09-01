package com.lihtdev.lingxicode.ai

/**
 * 轻量代码语法高亮（纯函数，可单测，零平台/第三方依赖）。
 *
 * 与 [MarkdownToHtml] 同哲学：以「够用的近似」换取零依赖与可预测性——
 * 单遍字符扫描近似分词（不追求精确语法树），将关键字、字符串、数字、注释
 * 四类 token 染色为内联 `<span style="color:#xxxxxx">`，其余文本原样转义。
 *
 * 明确的近似取舍：
 * - 行内字符串一律止于换行（流式渲染的未闭合中间态误染色至多一行）；
 * - 未闭合块注释 / 三引号字符串染色至输入末尾，随流式批次全量重扫自愈；
 * - JS 模板字符串、Rust 生命周期等特异语法不建模（Rust 不把 `'` 当字符串，避免 `'a` 误染）；
 * - 标识符判定基于 [Character.isLetter]，中文标识符整体为一个普通 token，不会误染。
 */
object CodeHighlighter {

    /** 高亮输入超长熔断：超过该长度直接降级纯转义，防止 EDT 上渲染卡顿 */
    private const val MAX_HIGHLIGHT_CHARS = 100_000

    /**
     * 四类 token 的 CSS 颜色值（如 "#cc7832"，由调用方从主题取色并 hex 化）。
     * 某字段为 null 表示该类别不上色：token 原文只转义、不包 span。
     */
    data class HighlightColors(
        val keyword: String?,
        val string: String?,
        val number: String?,
        val comment: String?,
    )

    /**
     * 对代码做语法高亮，输出已转义、含内联颜色 span 的 HTML 片段。
     *
     * @param code 代码文本
     * @param languageId 围栏语言标注（如 kotlin / python，大小写不敏感；未知语言降级纯转义）
     * @param colors token 颜色；某类别为 null 则该类别不上色
     */
    fun highlight(code: String, languageId: String, colors: HighlightColors): String {
        val dialect = DIALECTS[languageId.trim().lowercase()] ?: return escape(code)
        if (code.length > MAX_HIGHLIGHT_CHARS) return escape(code)

        val out = StringBuilder(code.length + 64)
        var plainStart = 0
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                isIdentifierStart(c) -> { // 标识符 → 关键字判定
                    flush(out, code, plainStart, i)
                    val end = identifierEnd(code, i)
                    val word = code.substring(i, end)
                    out.append(if (dialect.isKeyword(word)) span(word, colors.keyword) else escape(word))
                    i = end
                    plainStart = i
                }

                c.isDigit() -> { // 数字 token
                    flush(out, code, plainStart, i)
                    val end = numberEnd(code, i)
                    out.append(span(code.substring(i, end), colors.number))
                    i = end
                    plainStart = i
                }

                dialect.matchLineComment(code, i) -> { // 行注释：至行尾（换行符留给普通段）
                    flush(out, code, plainStart, i)
                    val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                    out.append(span(code.substring(i, end), colors.comment))
                    i = end
                    plainStart = i
                }

                dialect.matchBlockComment(code, i) -> { // 块注释：至闭合标记；未闭合 → 输入末尾
                    flush(out, code, plainStart, i)
                    val end = dialect.blockCommentEnd(code, i)
                    out.append(span(code.substring(i, end), colors.comment))
                    i = end
                    plainStart = i
                }

                dialect.matchMultilineString(code, i) -> { // """ / ''' 原始字符串：跨行；未闭合 → 输入末尾
                    flush(out, code, plainStart, i)
                    val end = dialect.multilineStringEnd(code, i)
                    out.append(span(code.substring(i, end), colors.string))
                    i = end
                    plainStart = i
                }

                c in dialect.stringDelimiterSet -> { // 行内字符串：同行同引号闭合；未闭合 → 行尾截断
                    flush(out, code, plainStart, i)
                    val end = stringEnd(code, i, c, dialect)
                    out.append(span(code.substring(i, end), colors.string))
                    i = end
                    plainStart = i
                }

                else -> i++ // 普通字符：留在普通段，flush 时统一转义
            }
        }
        flush(out, code, plainStart, code.length)
        return out.toString()
    }

    /** 输出普通段 [from, to) 的转义文本 */
    private fun flush(out: StringBuilder, code: String, from: Int, to: Int) {
        if (to > from) out.append(escape(code.substring(from, to)))
    }

    /** 先转义 token 原文，再按颜色包 span；color 为 null 时只输出转义文本 */
    private fun span(text: String, color: String?): String =
        if (color == null) escape(text)
        else "<span style=\"color:$color\">" + escape(text) + "</span>"

    /**
     * 行内字符串结束下标：`\x` 转义跳过下一字符（但不越过换行，杜绝 `\`+换行的跨行蔓延）；
     * 同行同引号闭合；换行视为未闭合（止于行尾，流式中间态降级）。
     */
    private fun stringEnd(code: String, start: Int, quote: Char, dialect: Dialect): Int {
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\n' -> return i
                dialect.escapeChar != null && c == dialect.escapeChar && i + 1 < code.length && code[i + 1] != '\n' -> i += 2
                c == quote -> {
                    if (dialect.doubledQuoteEscape && i + 1 < code.length && code[i + 1] == quote) i += 2 else return i + 1
                }

                else -> i++
            }
        }
        return code.length
    }

    /** 数字结束下标：数字/字母/下划线连吃（0x1F、2L、1e5）；小数点仅在后随数字时吃进（1.toString() 不误吞） */
    private fun numberEnd(code: String, start: Int): Int {
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            val ok = when {
                c.isDigit() || c == '_' || c.isLetter() -> true
                c == '.' -> i + 1 < code.length && code[i + 1].isDigit()
                else -> false
            }
            if (!ok) break
            i++
        }
        return i
    }

    private fun isIdentifierStart(c: Char): Boolean = Character.isLetter(c) || c == '_'

    private fun identifierEnd(code: String, start: Int): Int {
        var i = start
        while (i < code.length && (Character.isLetterOrDigit(code[i]) || code[i] == '_')) i++
        return i
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 语言方言：注释定界、字符串引号、关键字集等近似语法要素 */
    private class Dialect(
        val lineCommentPrefixes: List<String> = emptyList(),
        val blockCommentOpen: String? = null,
        val blockCommentClose: String? = null,
        val stringDelimiters: String = "",
        val multilineStringDelimiters: List<String> = emptyList(),
        val escapeChar: Char? = '\\',
        val doubledQuoteEscape: Boolean = false,
        val keywords: Set<String> = emptySet(),
        val keywordsIgnoreCase: Boolean = false,
    ) {
        val stringDelimiterSet: Set<Char> = stringDelimiters.toSet()

        fun isKeyword(word: String): Boolean =
            if (keywordsIgnoreCase) keywords.contains(word.lowercase()) else keywords.contains(word)

        fun matchLineComment(code: String, i: Int): Boolean =
            lineCommentPrefixes.any { code.startsWith(it, i) }

        fun matchBlockComment(code: String, i: Int): Boolean =
            blockCommentOpen != null && code.startsWith(blockCommentOpen, i)

        fun matchMultilineString(code: String, i: Int): Boolean =
            multilineStringDelimiters.any { code.startsWith(it, i) }

        /** 块注释结束下标（含闭合标记）；未闭合 → 输入末尾 */
        fun blockCommentEnd(code: String, start: Int): Int {
            val close = blockCommentClose ?: return start + 1
            // 搜索起点跳过开启标记本身，避免 /*/ 这类重叠匹配被误判为已闭合
            val idx = code.indexOf(close, start + (blockCommentOpen?.length ?: 1))
            return if (idx == -1) code.length else idx + close.length
        }

        /** 多行字符串结束下标（含闭合定界）；未闭合 → 输入末尾 */
        fun multilineStringEnd(code: String, start: Int): Int {
            val delim = multilineStringDelimiters.firstOrNull { code.startsWith(it, start) } ?: return start + 1
            val idx = code.indexOf(delim, start + delim.length)
            return if (idx == -1) code.length else idx + delim.length
        }
    }

    /**
     * 语言别名 → 方言映射（键全部小写；未知语言查表失败即降级纯转义）。
     * 惰性初始化：声明在关键字表之前，直接初始化会因前向引用未初始化属性而编译失败。
     */
    private val DIALECTS: Map<String, Dialect> by lazy { buildMap {
        val kotlin = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", multilineStringDelimiters = listOf("\"\"\""),
            keywords = KOTLIN_KEYWORDS,
        )
        val java = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = JAVA_KEYWORDS,
        )
        val c = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = C_KEYWORDS,
        )
        val cpp = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = CPP_KEYWORDS,
        )
        val csharp = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = CSHARP_KEYWORDS,
        )
        val go = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = GO_KEYWORDS,
        )
        val rust = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"", // 不含 '：避免生命周期 'a 被误判为未闭合字符串
            keywords = RUST_KEYWORDS,
        )
        val swift = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", multilineStringDelimiters = listOf("\"\"\""),
            keywords = SWIFT_KEYWORDS,
        )
        val php = Dialect(
            lineCommentPrefixes = listOf("//", "#"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", keywords = PHP_KEYWORDS,
        )
        val dart = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", multilineStringDelimiters = listOf("\"\"\"", "'''"),
            keywords = DART_KEYWORDS,
        )
        val scala = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'", multilineStringDelimiters = listOf("\"\"\""),
            keywords = SCALA_KEYWORDS,
        )
        val python = Dialect(
            lineCommentPrefixes = listOf("#"),
            stringDelimiters = "\"'", multilineStringDelimiters = listOf("\"\"\"", "'''"),
            keywords = PYTHON_KEYWORDS,
        )
        val js = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'`", keywords = JS_KEYWORDS,
        )
        val ts = Dialect(
            lineCommentPrefixes = listOf("//"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'`", keywords = TS_KEYWORDS,
        )
        val sql = Dialect(
            lineCommentPrefixes = listOf("--"), blockCommentOpen = "/*", blockCommentClose = "*/",
            stringDelimiters = "\"'",
            escapeChar = null, doubledQuoteEscape = true, // SQL 的 '' 转义
            keywords = SQL_KEYWORDS, keywordsIgnoreCase = true,
        )
        val shell = Dialect(
            lineCommentPrefixes = listOf("#"),
            stringDelimiters = "\"'", keywords = SHELL_KEYWORDS,
        )
        val json = Dialect(stringDelimiters = "\"", keywords = JSON_YAML_KEYWORDS)
        val yaml = Dialect(lineCommentPrefixes = listOf("#"), stringDelimiters = "\"'", keywords = JSON_YAML_KEYWORDS)
        // XML/HTML 无 \ 转义语义：置 null，避免 "a\"b" 吞掉闭引号误染后续内容
        val xml = Dialect(
            blockCommentOpen = "<!--", blockCommentClose = "-->",
            stringDelimiters = "\"'", escapeChar = null,
        )

        fun alias(vararg names: String, dialect: Dialect) = names.forEach { put(it, dialect) }
        alias("kotlin", "kt", "kts", dialect = kotlin)
        alias("java", dialect = java)
        alias("c", "h", dialect = c)
        alias("cpp", "c++", "cxx", "cc", "hpp", dialect = cpp)
        alias("cs", "csharp", "c#", dialect = csharp)
        alias("go", "golang", dialect = go)
        alias("rust", "rs", dialect = rust)
        alias("swift", dialect = swift)
        alias("php", dialect = php)
        alias("dart", dialect = dart)
        alias("scala", dialect = scala)
        alias("python", "py", "python3", dialect = python)
        alias("js", "javascript", "jsx", dialect = js)
        alias("ts", "typescript", "tsx", dialect = ts)
        alias("sql", dialect = sql)
        alias("shell", "sh", "bash", "zsh", dialect = shell)
        alias("json", dialect = json)
        alias("yaml", "yml", dialect = yaml)
        alias("xml", dialect = xml)
        alias("html", "htm", "xhtml", dialect = xml)
    } }

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "val", "var", "when", "while", "by", "catch", "constructor",
        "finally", "import", "init", "where", "abstract", "actual", "annotation", "companion",
        "const", "crossinline", "data", "enum", "expect", "external", "final", "infix", "inline",
        "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg",
    )

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "record", "return", "sealed", "short", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "try", "var", "void",
        "volatile", "while", "yield", "permits",
    )

    private val C_KEYWORDS = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
        "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
    )

    private val CPP_KEYWORDS = C_KEYWORDS + setOf(
        "alignas", "alignof", "and", "asm", "bool", "catch", "char16_t", "char32_t", "char8_t",
        "class", "concept", "const_cast", "consteval", "constexpr", "constinit", "co_await",
        "co_return", "co_yield", "decltype", "delete", "dynamic_cast", "explicit", "export",
        "false", "friend", "mutable", "namespace", "new", "noexcept", "not", "nullptr",
        "operator", "or", "private", "protected", "public", "reinterpret_cast", "requires",
        "static_assert", "static_cast", "template", "this", "thread_local", "throw", "true",
        "try", "typeid", "typename", "using", "virtual", "wchar_t", "xor",
    )

    private val CSHARP_KEYWORDS = setOf(
        "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
        "class", "const", "continue", "decimal", "default", "delegate", "do", "double",
        "dynamic", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed",
        "float", "for", "foreach", "get", "goto", "if", "implicit", "in", "init", "int",
        "interface", "internal", "is", "lock", "long", "namespace", "new", "not", "null",
        "object", "operator", "or", "out", "override", "params", "partial", "private",
        "protected", "public", "readonly", "record", "ref", "return", "sbyte", "sealed",
        "set", "short", "sizeof", "stackalloc", "static", "string", "struct", "switch",
        "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe",
        "ushort", "using", "var", "virtual", "void", "volatile", "when", "where", "while",
        "with", "yield",
    )

    private val GO_KEYWORDS = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else",
        "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
        "package", "range", "return", "select", "struct", "switch", "type", "var",
        // 预声明标识符（Go 官方将类型/内置函数一并高亮）
        "true", "false", "nil", "iota", "append", "cap", "close", "complex", "copy", "delete",
        "imag", "len", "make", "new", "panic", "print", "println", "real", "recover",
        "bool", "byte", "complex64", "complex128", "error", "float32", "float64", "int",
        "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8", "uint16",
        "uint32", "uint64", "uintptr",
    )

    private val RUST_KEYWORDS = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
        "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
        "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
        "trait", "true", "type", "unsafe", "use", "where", "while",
    )

    private val SWIFT_KEYWORDS = setOf(
        "associatedtype", "actor", "as", "any", "async", "await", "break", "case", "catch",
        "class", "continue", "convenience", "default", "defer", "deinit", "didSet", "do",
        "dynamic", "else", "enum", "extension", "fallthrough", "false", "fileprivate",
        "final", "for", "func", "get", "guard", "if", "import", "in", "infix", "init",
        "inout", "internal", "is", "lazy", "let", "nil", "open", "operator", "optional",
        "override", "postfix", "private", "protocol", "public", "repeat", "rethrows",
        "return", "self", "set", "some", "static", "struct", "subscript", "super", "switch",
        "throw", "throws", "true", "try", "typealias", "unowned", "var", "weak", "where",
        "while", "willSet",
    )

    private val PHP_KEYWORDS = setOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class",
        "clone", "const", "continue", "declare", "default", "do", "echo", "else", "elseif",
        "empty", "enum", "extends", "final", "finally", "fn", "for", "foreach", "function",
        "global", "goto", "if", "implements", "include", "include_once", "instanceof",
        "insteadof", "interface", "isset", "list", "match", "namespace", "new", "or", "print",
        "private", "protected", "public", "readonly", "require", "require_once", "return",
        "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor",
        "yield", "true", "false", "null",
    )

    private val DART_KEYWORDS = setOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class",
        "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else",
        "enum", "export", "extends", "extension", "external", "factory", "false", "final",
        "finally", "for", "get", "if", "implements", "import", "in", "interface", "is", "late",
        "library", "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
        "return", "set", "static", "super", "switch", "sync", "this", "throw", "true", "try",
        "typedef", "var", "void", "when", "while", "with", "yield",
    )

    private val SCALA_KEYWORDS = setOf(
        "abstract", "case", "catch", "class", "def", "do", "else", "end", "enum", "extends",
        "export", "false", "final", "finally", "for", "forSome", "given", "if", "implicit",
        "import", "lazy", "match", "new", "null", "object", "override", "package", "private",
        "protected", "return", "sealed", "super", "then", "this", "throw", "trait", "true",
        "try", "type", "using", "val", "var", "while", "with", "yield",
    )

    private val PYTHON_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield",
    )

    private val JS_KEYWORDS = setOf(
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else", "enum", "export",
        "extends", "false", "finally", "for", "from", "function", "if", "implements",
        "import", "in", "instanceof", "interface", "let", "new", "null", "package",
        "private", "protected", "public", "return", "static", "super", "switch", "this",
        "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with",
        "yield",
    )

    private val TS_KEYWORDS = JS_KEYWORDS + setOf(
        "any", "asserts", "boolean", "declare", "infer", "is", "keyof", "module", "namespace",
        "never", "number", "readonly", "satisfies", "string", "symbol", "type", "unknown",
    )

    private val SQL_KEYWORDS = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "add", "column", "index", "view", "join",
        "inner", "left", "right", "outer", "full", "cross", "on", "as", "and", "or", "not",
        "null", "in", "is", "like", "between", "exists", "case", "when", "then", "else",
        "end", "union", "all", "distinct", "group", "by", "having", "order", "limit",
        "offset", "asc", "desc", "count", "sum", "avg", "min", "max", "primary", "key",
        "foreign", "references", "constraint", "unique", "default", "check", "commit",
        "rollback", "begin", "transaction", "with", "over", "partition", "cast", "if",
        "use", "database", "grant", "revoke", "truncate", "explain", "analyze", "returning",
        "conflict", "do", "nothing", "true", "false",
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
        "esac", "in", "function", "select", "return", "break", "continue", "local", "export",
        "readonly", "declare", "typeset", "unset", "shift", "eval", "exec", "trap", "exit",
        "echo", "printf", "read", "cd", "set", "alias", "source", "true", "false",
    )

    private val JSON_YAML_KEYWORDS = setOf("true", "false", "null")
}
