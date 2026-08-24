package com.lihtdev.lingxicode.code

import com.intellij.openapi.project.Project

/**
 * 「代码解释」功能的上下文：在 EDT 采集为纯字符串与平台句柄，
 * 供功能层组 prompt（后台）与结果展示（EDT 弹窗）使用。
 */
data class ExplainCodeContext(
    /** 当前项目（用于设置读取与对话框挂载） */
    val project: Project,
    /** 代码所属语言展示名（如 Kotlin / Java / Python） */
    val language: String,
    /** 文件展示名 */
    val fileName: String,
    /** 符号类型（判别失败为 BLOCK） */
    val symbolKind: ExplainSymbolKind,
    /** 命中的符号名（选中代码块时为 null） */
    val symbolName: String?,
    /** 待解释代码文本（可能已按上限截断） */
    val code: String,
)