package com.lihtdev.codesense.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.vcs.VcsDataKeys
import com.lihtdev.codesense.feature.CommitFeatureContext
import com.lihtdev.codesense.feature.CommitMessageFeature
import com.lihtdev.codesense.git.ChangeCollector
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.service.AiInvocationService

/**
 * 提交对话框消息区 toolbar 的「AI 生成提交信息」按钮。
 * 读取勾选变更 → 后台调用大模型 → 清洗 → 回填提交消息框。
 */
class GenerateCommitMessageAction : AnAction() {

    private val invocationService = AiInvocationService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        val changes = ChangeCollector.collect(e)
        if (changes.isEmpty()) {
            AiInvocationService.notifyWarning(project, CodeSenseBundle.message("notification.noChanges"))
            return
        }
        val context = CommitFeatureContext(commitMessage, changes)
        invocationService.invoke(project, CommitMessageFeature(), context, CodeSenseBundle.message("task.generateCommit"))
    }

    override fun update(e: AnActionEvent) {
        // 仅在提交消息上下文中可用
        e.presentation.isEnabled = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null
    }

    // update 仅读取数据上下文、不触碰 UI，声明在后台线程执行（避免 OLD_EDT 弃用告警）
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
