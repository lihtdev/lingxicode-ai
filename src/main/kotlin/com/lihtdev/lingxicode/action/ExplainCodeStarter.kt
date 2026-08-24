package com.lihtdev.lingxicode.action

import com.intellij.openapi.project.Project
import com.lihtdev.lingxicode.code.ExplainCodeContext
import com.lihtdev.lingxicode.feature.ExplainCodeFeature
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService

/**
 * 「解释代码」统一触发入口：右键、gutter 图标等入口均经此执行，
 * 保证空目标提示与执行管线行为一致。
 */
object ExplainCodeStarter {

    private val invocationService = AiInvocationService()

    /**
     * 触发一次代码解释。
     *
     * @param project 当前项目
     * @param context 采集到的解释上下文；为空时给出警告通知，不发起请求
     */
    fun trigger(project: Project, context: ExplainCodeContext?) {
        if (context == null) {
            AiInvocationService.notifyWarning(project, LingxiCodeBundle.message("notification.noExplainTarget"))
            return
        }
        invocationService.invoke(
            project,
            ExplainCodeFeature(),
            context,
            LingxiCodeBundle.message("task.explainCode"),
        )
    }
}