package com.lihtdev.lingxicode.code

/**
 * 待解释代码的符号类型（语言无关的粗粒度分类）。
 *
 * 用于 Prompt 框定解释重点与对话框标题展示，具体中文/英文文案经
 * [com.lihtdev.lingxicode.i18n.LingxiCodeBundle] 依 `bundleKey` 解析。
 */
enum class ExplainSymbolKind(val bundleKey: String) {
    /** 类 / 结构体 / 枚举 / 记录 / 类型别名等「类型」声明 */
    CLASS("explain.symbol.class"),

    /** 接口 / trait / protocol 等契约型声明 */
    INTERFACE("explain.symbol.interface"),

    /** 方法（Java / C# / C++ 等带修饰符与返回类型的成员函数） */
    METHOD("explain.symbol.method"),

    /** 函数（Python def / Kotlin fun / JS function / Go func / Rust fn 等） */
    FUNCTION("explain.symbol.function"),

    /** 任意选中或兜底采集的代码块 */
    BLOCK("explain.symbol.block"),

    /** 无法判别的符号 */
    UNKNOWN("explain.symbol.unknown");

    /**
     * 是否为「声明级、值得一键解释」的符号。
     *
     * gutter「解释」图标仅在这些符号名称上展示，变量/字段/参数/代码块等一律不挂，
     * 避免编辑器行号旁图标过密。
     */
    val isExplainableDeclaration: Boolean
        get() = this == CLASS || this == INTERFACE || this == METHOD || this == FUNCTION
}