package com.lihtdev.lingxicode.action

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.ui.awt.RelativePoint
import com.lihtdev.lingxicode.code.CodeContextBuilder
import com.lihtdev.lingxicode.code.SymbolKindDetector
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import java.awt.event.MouseEvent

/**
 * 编辑器行号旁 gutter「AI 代码功能」图标（解释 / 评审 / 逐行解释共用）：
 * 仅在类/接口/方法/函数等声明级符号的 **名称标识符**上显示（灰暗配色），
 * 单击弹出功能选择菜单（解释该代码 / 评审该代码 / 逐行解释该代码）。
 * 变量/字段/参数等不展示，语言无关，仅依赖平台级 PSI（[PsiNameIdentifierOwner]）。
 */
class AiCodeLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        // 仅在「名称标识符」上挂标记，避免整段声明的每一行都显示图标
        val owner = element.parent as? PsiNameIdentifierOwner ?: return null
        if (owner.nameIdentifier !== element) return null
        // 只对声明级符号（类/接口/方法/函数）提供功能，变量/字段/参数不挂，避免图标过密
        if (!SymbolKindDetector.detect(owner.text).isExplainableDeclaration) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            ICON,
            { _: PsiElement -> LingxiCodeBundle.message("ai.marker.tooltip") },
            { mouseEvent, nameElement ->
                val ownerElement = nameElement.parent as? PsiNameIdentifierOwner
                if (ownerElement != null) showFeatureMenu(mouseEvent, ownerElement)
            },
            GutterIconRenderer.Alignment.LEFT,
            { LingxiCodeBundle.message("ai.marker.tooltip") },
        )
    }

    /** 在点击位置弹出功能选择菜单；菜单文案点击时实时解析，界面语言切换后即时生效 */
    private fun showFeatureMenu(mouseEvent: MouseEvent, owner: PsiNameIdentifierOwner) {
        val project = owner.project
        val items = listOf(
            LingxiCodeBundle.message("explain.marker.tooltip"),
            LingxiCodeBundle.message("review.marker.tooltip"),
            LingxiCodeBundle.message("explainLineByLine.marker.tooltip"),
        )
        val step = object : BaseListPopupStep<String>(null, items) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice && selectedValue != null) {
                    // 上下文在选中后采集一次，三个功能共用（为空时由 Starter 给出警告通知）
                    val context = CodeContextBuilder.fromElement(project, owner)
                    when (items.indexOf(selectedValue)) {
                        0 -> ExplainCodeStarter.trigger(project, context)
                        1 -> ReviewCodeStarter.trigger(project, context)
                        2 -> ExplainLineByLineStarter.trigger(project, context)
                    }
                }
                return FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).show(RelativePoint(mouseEvent))
    }

    companion object {
        private val ICON = IconLoader.getIcon("/icons/lingxicode-gutter.svg", AiCodeLineMarkerProvider::class.java)
    }
}
