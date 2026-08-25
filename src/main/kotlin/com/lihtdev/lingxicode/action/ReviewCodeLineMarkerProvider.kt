package com.lihtdev.lingxicode.action

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.lihtdev.lingxicode.code.CodeContextBuilder
import com.lihtdev.lingxicode.code.SymbolKindDetector
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle

/**
 * 编辑器行号旁 gutter「评审代码」图标：仅在类/接口/方法/函数等声明级符号的
 * **名称标识符**上显示，单击即评审该符号。
 *
 * 与 [ExplainCodeLineMarkerProvider] 使用同一图标：同一行上平台会把相同图标的
 * 多个 marker 自动合并为单图标，点击弹出「解释该代码 / 评审该代码」选择列表。
 * 变量/字段/参数等不展示，语言无关，仅依赖平台级 PSI（[PsiNameIdentifierOwner]）。
 */
class ReviewCodeLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        // 仅在「名称标识符」上挂标记，避免整段声明的每一行都显示图标
        val owner = element.parent as? PsiNameIdentifierOwner ?: return null
        if (owner.nameIdentifier !== element) return null
        // 只评审声明级符号（类/接口/方法/函数），变量/字段/参数不挂，避免图标过密
        if (!SymbolKindDetector.detect(owner.text).isExplainableDeclaration) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            ICON,
            { _: PsiElement -> LingxiCodeBundle.message("review.marker.tooltip") },
            GutterIconNavigationHandler<PsiElement> { _, nameElement ->
                val ownerElement = nameElement.parent as? PsiNameIdentifierOwner
                if (ownerElement != null) {
                    val project = nameElement.project
                    ReviewCodeStarter.trigger(project, CodeContextBuilder.fromElement(project, ownerElement))
                }
            },
            GutterIconRenderer.Alignment.LEFT,
            { LingxiCodeBundle.message("review.marker.tooltip") },
        )
    }

    companion object {
        private val ICON = IconLoader.getIcon("/icons/lingxicode-gutter.svg", ReviewCodeLineMarkerProvider::class.java)
    }
}
