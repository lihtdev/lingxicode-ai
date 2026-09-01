package com.lihtdev.lingxicode.action

import com.intellij.openapi.project.Project
import com.lihtdev.lingxicode.code.CodeContext
import com.lihtdev.lingxicode.feature.ExplainLineByLineFeature
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService

/**
 * 「逐行解释」统一触发入口：右键、gutter 图标等入口均经此执行，
 * 保证空目标提示与执行管线行为一致。
 */
object ExplainLineByLineStarter {

    private val invocationService = AiInvocationService()

    /**
     * 触发一次逐行解释。
     *
     * @param project 当前项目
     * @param context 采集到的解释上下文；为空时给出警告通知，不发起请求
     */
    fun trigger(project: Project, context: CodeContext?) {
        if (context == null) {
            // 空目标文案与「解释代码」共用（目标采集逻辑一致，措辞完全适用）
            AiInvocationService.notifyWarning(project, LingxiCodeBundle.message("notification.noExplainTarget"))
            return
        }
        invocationService.invoke(
            project,
            ExplainLineByLineFeature(),
            context,
            LingxiCodeBundle.message("task.explainLineByLine"),
        )
    }
}
