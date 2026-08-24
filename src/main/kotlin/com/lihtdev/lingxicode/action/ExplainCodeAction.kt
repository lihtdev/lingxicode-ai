package com.lihtdev.lingxicode.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.lihtdev.lingxicode.code.CodeContextBuilder

/**
 * 编辑器右键「解释代码」入口。
 *
 * 优先解释选中的代码块，否则解释光标所在的类 / 方法 / 函数；
 * 采集失败（无有效目标）时由 [ExplainCodeStarter] 给出警告通知，不发起请求。
 */
class ExplainCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ExplainCodeStarter.trigger(project, CodeContextBuilder.build(e))
    }

    override fun update(e: AnActionEvent) {
        // 仅在编辑器上下文可用；是否命中目标在 actionPerformed 中进一步校验
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    // update 仅读取数据上下文、不触碰 UI，声明在后台线程执行（避免 OLD_EDT 弃用告警）
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}