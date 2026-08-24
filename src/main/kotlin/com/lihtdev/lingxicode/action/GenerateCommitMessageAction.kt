package com.lihtdev.lingxicode.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.vcs.VcsDataKeys
import com.lihtdev.lingxicode.feature.CommitFeatureContext
import com.lihtdev.lingxicode.feature.CommitMessageFeature
import com.lihtdev.lingxicode.git.ChangeCollector
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.service.AiInvocationService

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
            AiInvocationService.notifyWarning(project, LingxiCodeBundle.message("notification.noChanges"))
            return
        }
        val context = CommitFeatureContext(commitMessage, changes)
        invocationService.invoke(project, CommitMessageFeature(), context, LingxiCodeBundle.message("task.generateCommit"))
    }

    override fun update(e: AnActionEvent) {
        // 仅在提交消息上下文中可用
        e.presentation.isEnabled = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null
        // 文案每次展示按当前「界面语言」实时解析，语言切换后无需重启即生效
        e.presentation.setText(LingxiCodeBundle.message("action.generateCommitMessage.text"))
        e.presentation.setDescription(LingxiCodeBundle.message("action.generateCommitMessage.description"))
    }

    // update 仅读取数据上下文、不触碰 UI，声明在后台线程执行（避免 OLD_EDT 弃用告警）
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
