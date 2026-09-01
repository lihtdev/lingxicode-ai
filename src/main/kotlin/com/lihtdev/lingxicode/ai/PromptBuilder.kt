package com.lihtdev.lingxicode.ai

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
            请根据用户提供的代码变更（diff），生成一条 Conventional Commits 格式的提交信息。

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

            4. description 用 $language 书写，紧跟在冒号空格后，概括本次变更的核心目的，不超过 50 个字，结尾不加句号。
            
            5. 可选 body：须在 description 结束后空出一整行，再从下一行开始书写；body 同样使用 $language 书写，采用无序列表（- ）形式逐项列出主要变更内容，每项简洁说明该变更的具体行为、原因或影响，避免与描述部分简单重复。

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
        val flowchartHeading = if (outputLanguage.equals("en", ignoreCase = true)) EN_FLOWCHART_HEADING else ZH_FLOWCHART_HEADING

        val system = """
            你是一名资深软件工程师，擅长把代码用通俗、准确的语言解释给其他开发者。
            请根据用户提供的代码与上下文，输出一份结构化的解释。

            输出要求：
            1. 使用 Markdown 组织，但不要用 ``` 代码围栏包裹整篇回答。
            2. 只允许使用：二级标题（##）、三级标题（###）、加粗（**）、行内代码（`）、
               代码围栏（```，仅用于引用代码片段或绘制流程图）、无序列表（- ）和有序列表（1. ）。禁止表格、链接与一级标题（#）。
            3. 严格按以下五个固定标题顺序输出（条件性第六标题见第 4 条），此外不得增删标题：
               ## ${headings[0]}
               ## ${headings[1]}
               ## ${headings[2]}
               ## ${headings[3]}
               ## ${headings[4]}
            4. 条件性第六标题「## $flowchartHeading」：仅当代码包含多分支、循环、多步骤流程等复杂控制流时，
               在「## ${headings[4]}」之后追加；简单直线型代码（顺序执行、无分支）不要追加，也不要输出空标题。
               流程图绘制要求：
               - 使用 ASCII/Unicode 制表符绘制（┌ ┐ └ ┘ │ ─ ▼ ├ ┤ ┼ 等），自上而下表达主流程；
               - 整个流程图放在一个无语言标注的 ``` 代码围栏中（不要写 ```text 等语言标记）；
               - 每行显示宽度不超过 72 列（中文按 2 列计）；全图不超过 30 行、12 个节点；
               - 节点内用简短的业务语义文字（如「校验参数」「查询库存」），不要直接粘贴代码语句或变量名；
               - 分支连线用「是/否」（英文输出时用 Y/N）标注条件走向；循环用回指箭头表示。
            5. 解释正文用 $outLang 书写；代码标识符与关键字保持原样。
            6. 「${headings[0]}」用一句话说清这段代码做什么（不超过 50 字）；
               「${headings[2]}」分要点说明关键流程 / 算法 / 数据流 / 控制流；
               「${headings[3]}」按符号类型组织：类/接口列主要成员与方法，方法/函数给入参与返回值，
               代码块给关键输入输出与依赖；
               「${headings[4]}」说明边界条件、异常、性能或潜在陷阱，无内容时写「无明显注意事项」。
            7. 只输出正文，不要寒暄与解释性废话。
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

    /**
     * 组装「代码评审」功能的对话消息。
     *
     * 报告按固定标题顺序输出：总体评价 → 十个评审维度（按重要性/优先级降序）→ 总结；
     * 每个维度内先列问题、有问题时紧随其后给出该维度的改进建议，无问题写「无明显问题」。
     *
     * @param language 代码所属语言展示名（如 Kotlin / Java）
     * @param fileName 文件展示名
     * @param symbolKindName 符号类型展示名（已本地化，如「类」/「方法」/「代码块」）
     * @param symbolName 命中符号名（选中代码块时为 null）
     * @param code 待评审代码文本
     * @param outputLanguage 输出语言（"zh" 中文 / 其他值英文）
     */
    fun buildReviewCode(
        language: String,
        fileName: String,
        symbolKindName: String,
        symbolName: String?,
        code: String,
        outputLanguage: String,
    ): List<ChatMessage> {
        val en = outputLanguage.equals("en", ignoreCase = true)
        val outLang = if (en) "English" else "中文（简体）"
        val headings = if (en) EN_REVIEW_HEADINGS else ZH_REVIEW_HEADINGS
        val suggestionLabel = if (en) EN_REVIEW_SUGGESTION_LABEL else ZH_REVIEW_SUGGESTION_LABEL
        val noIssue = if (en) EN_REVIEW_NO_ISSUE else ZH_REVIEW_NO_ISSUE

        val system = """
            你是一名资深代码评审专家，擅长从多个维度评审代码质量并给出可执行的改进建议。
            请根据用户提供的代码与上下文，输出一份结构化的评审报告。

            输出要求：
            1. 使用 Markdown 组织，但不要用 ``` 代码围栏包裹整篇回答。
            2. 只允许使用：二级标题（##）、三级标题（###）、加粗（**）、行内代码（`）、
               代码围栏（```，仅用于引用代码片段或给出改进示例）、无序列表（- ）和有序列表（1. ）。禁止表格、链接与一级标题（#）。
            3. 严格按以下标题顺序输出，不得增删标题（评审维度已按重要性从高到低排列）：
               ${headings.joinToString("\n               ") { "## $it" }}
            4. 「${headings[0]}」用一段话给出整体质量结论与最值得关注的问题（不超过 100 字）。
            5. 每个评审维度内部：
               - 先按严重度从高到低列出发现的问题，每条用行内代码引用相关标识符或片段并说明理由；
               - 有问题时，紧随其后用加粗「**$suggestionLabel**」引出该维度的可执行改进项，
                 必要处附小段 ``` 代码示例；
               - 无问题时只写「$noIssue」，不要为凑数编造问题，也不要输出空的改进建议；
               - 某维度与代码无关时（如纯顺序代码没有并发问题）同样写「$noIssue」。
            6. 「${headings.last()}」汇总关键发现与最高优先级的改进项（不超过 5 条）。
            7. 报告正文用 $outLang 书写；代码标识符与关键字保持原样。
            8. 只输出报告正文，不要寒暄与解释性废话。
        """.trimIndent()

        val user = buildString {
            append("代码语言：").append(language).append('\n')
            append("所在文件：").append(fileName).append('\n')
            append("符号类型：").append(symbolKindName)
            if (symbolName != null) append("（").append(symbolName).append("）")
            append("\n\n待评审代码：\n").append(code)
        }

        return listOf(
            ChatMessage("system", system),
            ChatMessage("user", user),
        )
    }

    /**
     * 组装「逐行解释」功能的对话消息。
     *
     * 与 [buildExplainCode] 的概览式解释互补：输出为单个代码围栏，完整代码原样保留，
     * 每行有实义的代码上方插入一行解释注释（注释正文随输出语言，注释语法随代码语言）。
     *
     * @param language 代码所属语言展示名（如 Kotlin / Java）
     * @param fileName 文件展示名
     * @param symbolKindName 符号类型展示名（已本地化，如「类」/「方法」/「代码块」）
     * @param symbolName 命中符号名（选中代码块时为 null）
     * @param code 待逐行解释代码文本
     * @param outputLanguage 输出语言（"zh" 中文 / 其他值英文）
     */
    fun buildExplainLineByLine(
        language: String,
        fileName: String,
        symbolKindName: String,
        symbolName: String?,
        code: String,
        outputLanguage: String,
    ): List<ChatMessage> {
        val en = outputLanguage.equals("en", ignoreCase = true)
        val outLang = if (en) "English" else "中文（简体）"

        val system = if (en) """
            You are a senior software engineer who excels at explaining code line by line.
            Please explain the user's code line by line: insert one concise comment above every
            meaningful line of code, keeping the code itself intact.

            Output requirements:
            1. Your entire answer must contain exactly one ``` code fence, with the code language
               marked on the opening fence line (e.g. ```kotlin, ```python); output nothing
               outside the fence — no headings, no notes, no greetings, and no second fence.
            2. Keep the code verbatim: copy every line of the user's code into the fence in the
               original order, preserving the original indentation, blank lines and line breaks;
               never rewrite, omit, reorder, split or merge any code line.
            3. Line-by-line comment rules:
               - For every line of code with real meaning (declarations, assignments, calls,
                 control flow, expressions, etc.), insert one explanatory comment on the line
                 immediately above it;
               - Each comment occupies its own line, aligned with the code line it explains
                 (same indentation), kept within one line, stating what the line does or why —
                 do not parrot the code literally;
               - Use the standard line-comment syntax of the code language: // for
                 Kotlin/Java/C/C++/Go/JS/TS and similar, # for Python/Ruby/Shell/YAML,
                 -- for SQL, ' for Visual Basic; for languages without a line-comment syntax
                 (e.g. HTML/XML), wrap each comment in a block comment;
               - Do not comment these lines: blank lines, lines containing only a closing brace
                 or parenthesis, and pre-existing comment lines (original comments are part of
                 the code and must be kept verbatim, without adding another comment above them);
               - Consecutive tightly-coupled lines expressing one idea may be treated as a group,
                 with a single comment above the first line only.
            4. Write comment text in $outLang; keep code, identifiers and keywords as-is.
            5. Apart from inserting comments above code lines, do not modify the code in any
               other way: no line numbers, no separators, no sub-headings, no added or removed
               blank lines.
            6. If the code is incomplete or ends with a truncation marker, explain only what is
               given; do not complete, guess, or continue the code.
            7. Output only the single fence described in rule 1; no greetings or filler.
        """.trimIndent() else """
            你是一名资深软件工程师，擅长逐行讲解代码。请对用户提供的代码做逐行解释：
            在每行有实际含义的代码上方插入一条简短注释，代码本身原样保留。

            输出要求：
            1. 整个回答只包含一个 ``` 代码围栏，起始围栏行标注代码语言（如 ```kotlin、```python）；
               围栏之外不得输出任何标题、说明、寒暄或第二个围栏。
            2. 代码原样保留：把用户提供的代码逐行完整复制进围栏，保持原有缩进、空行与换行，
               不得改写、省略、重排、拆分或合并任何代码行。
            3. 逐行注释规则：
               - 对每行有实际含义的代码（声明、赋值、调用、控制流、表达式等），
                 在紧邻其上方插入一行解释注释；
               - 注释独占一行，与被注释的代码行对齐（相同缩进），内容控制在一行以内，
                 点明该行做什么或为什么这么做，不要逐字复述代码；
               - 注释使用该代码语言标准的行注释语法：Kotlin/Java/C/C++/Go/JS/TS 等用 //，
                 Python/Ruby/Shell/YAML 用 #，SQL 用 --，Visual Basic 用 '；
                 无行注释语法的语言（如 HTML/XML）用块注释包裹单行；
               - 以下行不加注释：空行、只有右括号/右圆括号等单个标点的行、原有注释行
                 （原注释属于代码，必须原样保留，也不要在其上方再加解释）；
               - 连续多行表达同一件事的紧密语句可视作一组，仅在首行上方加一条注释。
            4. 注释正文用 $outLang 书写；代码、标识符与关键字保持原样。
            5. 除在代码行上方插入注释外，不得对代码做任何其他修改：
               不加行号、不加分隔线、不加小标题、不额外增删空行。
            6. 若代码不完整或末尾带截断标记，只解释给出的部分，不要补全、猜测或续写后续代码。
            7. 只输出第 1 条所述的单个围栏，不要寒暄与解释性废话。
        """.trimIndent()

        val user = buildString {
            append("代码语言：").append(language).append('\n')
            append("所在文件：").append(fileName).append('\n')
            append("符号类型：").append(symbolKindName)
            if (symbolName != null) append("（").append(symbolName).append("）")
            append("\n\n待逐行解释代码：\n").append(code)
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

    /** 条件性第六标题（仅复杂控制流时由模型追加）：流程图 */
    private const val ZH_FLOWCHART_HEADING = "流程图"

    /** 条件性第六标题（英文）：Flowchart */
    private const val EN_FLOWCHART_HEADING = "Flowchart"

    /** 评审报告的中文标题序列：总体评价 + 十个维度（按重要性降序）+ 总结 */
    private val ZH_REVIEW_HEADINGS = listOf(
        "总体评价",
        "正确性与潜在 Bug",
        "安全性",
        "并发安全",
        "健壮性与异常处理",
        "性能",
        "资源管理",
        "设计与架构",
        "可维护性",
        "可读性",
        "代码规范",
        "总结",
    )

    /** 评审报告的英文标题序列（与中文序列一一对应） */
    private val EN_REVIEW_HEADINGS = listOf(
        "Overall Assessment",
        "Correctness & Potential Bugs",
        "Security",
        "Concurrency Safety",
        "Robustness & Exception Handling",
        "Performance",
        "Resource Management",
        "Design & Architecture",
        "Maintainability",
        "Readability",
        "Code Style",
        "Summary",
    )

    /** 维度内改进建议的加粗标签（中文） */
    private const val ZH_REVIEW_SUGGESTION_LABEL = "改进建议"

    /** 维度内改进建议的加粗标签（英文） */
    private const val EN_REVIEW_SUGGESTION_LABEL = "Suggestions"

    /** 维度无问题时的占位文案（中文） */
    private const val ZH_REVIEW_NO_ISSUE = "无明显问题"

    /** 维度无问题时的占位文案（英文） */
    private const val EN_REVIEW_NO_ISSUE = "No issues found"
}