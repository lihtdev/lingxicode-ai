package com.lihtdev.lingxicode.feature

import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.changes.Change
import com.lihtdev.lingxicode.ai.ChatMessage
import com.lihtdev.lingxicode.ai.PromptBuilder
import com.lihtdev.lingxicode.git.DiffTextBuilder
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AppSettingsState

/**
 * 「提交信息生成」功能的上下文。
 */
data class CommitFeatureContext(
    val commitMessage: CommitMessageI,
    val changes: List<Change>,
)

/**
 * 提交信息生成功能：diff → prompt → 回填提交消息框。
 */
class CommitMessageFeature : AiFeature {

    override val id: String = "commit-message"

    override val displayName: String = LingxiCodeBundle.message("feature.commitMessage")

    /** 推理模型的思考 token 也计入输出配额，短文本功能也需留足余量 */
    override val maxOutputTokens: Int = COMMIT_MAX_TOKENS

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

    companion object {
        /** 提交信息输出 token 上限（推理模型的思考过程也计入输出配额，需留足余量） */
        const val COMMIT_MAX_TOKENS = 4096
    }
}
