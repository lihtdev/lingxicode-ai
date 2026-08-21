package com.lihtdev.codesense.service

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ReadTask
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.lihtdev.codesense.ai.AiClient
import com.lihtdev.codesense.ai.AiClientException
import com.lihtdev.codesense.ai.OpenAiCompatClient
import com.lihtdev.codesense.ai.ResponseCleaner
import com.lihtdev.codesense.feature.AiFeature
import com.lihtdev.codesense.settings.AppSettings

/**
 * AI 功能统一执行管线：
 * 前置校验（厂商/密钥）→ 后台组 prompt → 调用模型 → 清洗 → EDT 回调 → 通知。
 *
 * 所有 AI 功能（提交信息生成、后续代码解释等）共用此执行层。
 */
class AiInvocationService(private val client: AiClient = OpenAiCompatClient()) {

    /**
     * 执行一次 AI 功能。
     *
     * @param project 当前项目
     * @param feature AI 功能（含 prompt 组装与结果处理）
     * @param context 功能上下文
     * @param taskTitle 后台任务标题（进度条展示）
     */
    fun invoke(project: Project, feature: AiFeature, context: Any, taskTitle: String) {
        val settings = AppSettings.instance
        val provider = settings.activeProvider()
        if (provider == null || provider.baseUrl.isBlank() || provider.model.isBlank()) {
            notifyWarning(project, "尚未配置可用的 AI 模型，请先在设置中完成配置")
            return
        }
        val apiKey = AppSettings.getApiKey(provider.id)
        if (apiKey.isNullOrBlank()) {
            notifyWarning(project, "「${provider.displayName}」尚未配置 API Key，请先在设置中填写")
            return
        }

        object : Task.Backgroundable(project, taskTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                // 组 prompt（可能读取文件内容，需要读动作；用写动作优先级避免阻塞 UI）
                val messages = ReadTask
                    .computeOnPooledThreadInReadActionWithWriteActionPriority { feature.buildPrompt(context, settings.state) }
                    .get()
                indicator.checkCanceled()
                val raw = client.chat(provider, apiKey, messages)
                indicator.checkCanceled()
                val cleaned = ResponseCleaner.clean(raw)
                if (cleaned.isBlank()) {
                    throw AiClientException("模型返回内容为空，请重试或更换模型")
                }
                ApplicationManager.getApplication().invokeLater {
                    feature.handleResult(cleaned, context)
                }
            }

            override fun onThrowable(error: Throwable) {
                notifyError(project, "${feature.displayName}失败：${error.message ?: error.javaClass.simpleName}")
            }
        }.queue()
    }

    companion object {

        private const val NOTIFICATION_GROUP = "CodeSenseAI"
        private const val NOTIFICATION_TITLE = "CodeSense AI"

        /** 弹出警告通知 */
        @JvmStatic
        fun notifyWarning(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, NOTIFICATION_TITLE, message, NotificationType.WARNING),
                project,
            )
        }

        /** 弹出错误通知 */
        @JvmStatic
        fun notifyError(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, NOTIFICATION_TITLE, message, NotificationType.ERROR),
                project,
            )
        }

        /** 弹出信息通知 */
        @JvmStatic
        fun notifyInfo(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, NOTIFICATION_TITLE, message, NotificationType.INFORMATION),
                project,
            )
        }
    }
}
