package com.lihtdev.lingxicode.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.lihtdev.lingxicode.code.CodeContextBuilder
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle

/**
 * 编辑器右键「逐行解释」入口。
 *
 * 优先逐行解释选中的代码块，否则解释光标所在的类 / 方法 / 函数；
 * 采集失败（无有效目标）时由 [ExplainLineByLineStarter] 给出警告通知，不发起请求。
 *
 * 文案经 [LingxiCodeBundle] 在实例化时设置（平台不解析 plugin.xml 属性中的资源束 key），
 * 保持无参构造器以兼容平台的反射实例化。
 */
class ExplainLineByLineAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ExplainLineByLineStarter.trigger(project, CodeContextBuilder.build(e))
    }

    override fun update(e: AnActionEvent) {
        // 仅在编辑器上下文可用；是否命中目标在 actionPerformed 中进一步校验
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
        // 文案每次展开菜单按当前「界面语言」实时解析，语言切换后无需重启即生效
        e.presentation.setText(LingxiCodeBundle.message("action.explainLineByLine.text"))
        e.presentation.setDescription(LingxiCodeBundle.message("action.explainLineByLine.description"))
    }

    // update 仅读取数据上下文、不触碰 UI，声明在后台线程执行（避免 OLD_EDT 弃用告警）
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
