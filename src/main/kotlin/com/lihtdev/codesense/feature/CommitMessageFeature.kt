package com.lihtdev.codesense.feature

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.commit.message.CommitMessage
import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.ai.PromptBuilder
import com.lihtdev.codesense.git.DiffTextBuilder
import com.lihtdev.codesense.settings.AppSettingsState

/**
 * 「提交信息生成」功能的上下文。
 */
data class CommitFeatureContext(
    val commitMessage: CommitMessage,
    val changes: List<Change>,
)

/**
 * 提交信息生成功能：diff → prompt → 回填提交消息框。
 */
class CommitMessageFeature : AiFeature {

    override val id: String = "commit-message"

    override val displayName: String = "提交信息生成"

    override fun buildPrompt(context: Any, settings: AppSettingsState): List<ChatMessage> {
        val ctx = context as CommitFeatureContext
        val diffText = DiffTextBuilder.build(ctx.changes, settings.maxDiffChars)
        val fileList = ctx.changes.map { DiffTextBuilder.filePathOf(it) }
        return PromptBuilder.buildCommitMessages(fileList, diffText, settings.outputLanguage)
    }

    override fun handleResult(result: String, context: Any) {
        val ctx = context as CommitFeatureContext
        // 回填提交消息框（用户可继续编辑后再提交）
        ctx.commitMessage.setCommitMessage(result)
    }
}
