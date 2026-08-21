package com.lihtdev.codesense.action

import com.intellij.openapi.action.AnAction
import com.intellij.openapi.action.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys
import com.lihtdev.codesense.feature.CommitFeatureContext
import com.lihtdev.codesense.feature.CommitMessageFeature
import com.lihtdev.codesense.git.ChangeCollector
import com.lihtdev.codesense.service.AiInvocationService

/**
 * 提交对话框消息区 toolbar 的「AI 生成提交信息」按钮。
 * 读取勾选变更 → 后台调用大模型 → 清洗 → 回填提交消息框。
 */
class GenerateCommitMessageAction : AnAction() {

    private val invocationService = AiInvocationService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE) ?: return
        val changes = ChangeCollector.collect(e)
        if (changes.isEmpty()) {
            AiInvocationService.notifyWarning(project, "没有可提交的变更，请先勾选要提交的文件")
            return
        }
        val context = CommitFeatureContext(commitMessage, changes)
        invocationService.invoke(project, CommitMessageFeature(), context, "正在生成提交信息…")
    }

    override fun update(e: AnActionEvent) {
        // 仅在提交消息上下文中可用
        e.presentation.isEnabled = e.getData(VcsDataKeys.COMMIT_MESSAGE) != null
    }
}
