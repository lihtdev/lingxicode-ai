package com.lihtdev.lingxicode.service

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.lihtdev.lingxicode.ai.AiClient
import com.lihtdev.lingxicode.ai.AiClientException
import com.lihtdev.lingxicode.ai.ChatMessage
import com.lihtdev.lingxicode.ai.ChatStreamListener
import com.lihtdev.lingxicode.ai.OpenAiCompatClient
import com.lihtdev.lingxicode.feature.AiFeature
import com.lihtdev.lingxicode.feature.AiStreamView
import com.lihtdev.lingxicode.i18n.LingxiCodeBundle
import com.lihtdev.lingxicode.settings.AppSettings
import com.lihtdev.lingxicode.settings.AiProviderConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * AI 功能统一执行管线：
 * 前置校验（厂商/密钥）→ 创建流式视图 → 后台组 prompt → 流式调用模型
 * → 增量节流推送 EDT → 清洗 → 完成回调 → 通知。
 *
 * 流式失败（厂商不支持等可重试 4xx 且尚未展示正文）时自动降级非流式重试一次；
 * 功能未提供流式视图（createStreamView 返回 null）时走原非流式完成回调路径。
 * 所有 AI 功能（提交信息生成、代码解释、代码评审）共用此执行层。
 */
class AiInvocationService(private val client: AiClient = OpenAiCompatClient()) {

    /**
     * 执行一次 AI 功能。
     *
     * @param project 当前项目
     * @param feature AI 功能（含 prompt 组装、流式视图创建与结果处理）
     * @param context 功能上下文
     * @param taskTitle 后台任务标题（进度条展示）
     */
    fun invoke(project: Project, feature: AiFeature, context: Any, taskTitle: String) {
        val settings = AppSettings.instance
        val provider = settings.activeProvider()
        if (provider == null || provider.baseUrl.isBlank() || provider.model.isBlank()) {
            notifyWarning(project, LingxiCodeBundle.message("notification.noProvider"))
            return
        }
        val apiKey = AppSettings.getApiKey(provider.providerId)
        if (apiKey.isNullOrBlank()) {
            notifyWarning(project, LingxiCodeBundle.message("notification.noApiKey", provider.displayName))
            return
        }

        // 流式视图在 EDT 上先于请求创建（对话框即弹出，边生成边展示）
        val view: AiStreamView? = feature.createStreamView(project, context)

        object : Task.Backgroundable(project, taskTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                // 视图已先于请求创建（对话框已弹出），组 prompt 阶段的异常/取消
                // 也必须通知视图，否则对话框会永久停留在「生成中…」
                val messages = try {
                    ReadAction.compute<List<ChatMessage>, RuntimeException> {
                        feature.buildPrompt(context, settings.state)
                    }
                } catch (t: Throwable) {
                    ApplicationManager.getApplication().invokeLater {
                        if (t is ProcessCanceledException || t is InterruptedException) {
                            view?.onCancelled()
                        } else {
                            view?.onFailed(t.message ?: t.javaClass.simpleName)
                        }
                    }
                    throw t
                }
                try {
                    indicator.checkCanceled()
                } catch (t: ProcessCanceledException) {
                    ApplicationManager.getApplication().invokeLater { view?.onCancelled() }
                    throw t
                }
                executeStreaming(indicator, project, feature, context, view, provider, apiKey, messages)
            }

            override fun onThrowable(error: Throwable) {
                // 用户取消不算错误，不弹错误通知
                if (error is InterruptedException || error is ProcessCanceledException) return
                val msg = LingxiCodeBundle.message(
                    "error.featureFailed",
                    feature.displayName,
                    error.message ?: error.javaClass.simpleName,
                )
                notifyError(project, msg)
            }
        }.queue()
    }

    /**
     * 流式执行 + 节流推送 + 降级回退（在 Task.Backgroundable 后台线程内执行）。
     *
     * 增量不逐 chunk 切 EDT：后台线程只往 [DeltaBatcher] 累积，
     * 由 Alarm（SWING 线程）按 ≥[FLUSH_INTERVAL_MS] 的时间窗合并刷新。
     */
    private fun executeStreaming(
        indicator: ProgressIndicator,
        project: Project,
        feature: AiFeature,
        context: Any,
        view: AiStreamView?,
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
    ) {
        val application = ApplicationManager.getApplication()
        val batcher = DeltaBatcher()
        val receivedContentDelta = AtomicBoolean(false)
        val flushScheduled = AtomicBoolean(false)
        val terminated = AtomicBoolean(false)
        val lastFlushAt = AtomicLong(System.currentTimeMillis())
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

        /** EDT：取走累积增量推送给视图（终止后不再推送，避免取消/失败后又追加内容） */
        fun flushNow() {
            flushScheduled.set(false)
            lastFlushAt.set(System.currentTimeMillis())
            if (terminated.get() || !batcher.hasPending()) return
            val (content, reasoning) = batcher.drain()
            if (content.isNotEmpty()) view?.onContentDelta(content)
            if (reasoning.isNotEmpty()) view?.onReasoningDelta(reasoning)
        }

        /** 后台线程：保证距上次刷新至少一个时间窗（CAS 去重并发调度） */
        fun scheduleFlush() {
            if (!flushScheduled.compareAndSet(false, true)) return
            val elapsed = System.currentTimeMillis() - lastFlushAt.get()
            val delay = max(0L, FLUSH_INTERVAL_MS - elapsed)
            alarm.addRequest({ flushNow() }, delay)
        }

        val listener = object : ChatStreamListener {
            override fun onContentDelta(delta: String) {
                receivedContentDelta.set(true)
                batcher.offer(delta, null)
                scheduleFlush()
            }

            override fun onReasoningDelta(delta: String) {
                batcher.offer(null, delta)
                scheduleFlush()
            }
        }

        try {
            val result = client.chatStreaming(
                provider, apiKey, messages, feature.maxOutputTokens, listener,
            ) { indicator.isCanceled }
            indicator.checkCanceled()
            val cleaned = cleanOrThrow(feature, result.content)
            application.invokeLater {
                // 完成前强制排空残余增量（含 reasoning 尾巴），再最终回调
                flushNow()
                if (view != null) view.onCompleted(cleaned) else feature.handleResult(cleaned, context)
            }
        } catch (e: ProcessCanceledException) {
            terminated.set(true)
            application.invokeLater { view?.onCancelled() }
            throw e
        } catch (e: InterruptedException) {
            terminated.set(true)
            application.invokeLater { view?.onCancelled() }
            throw e // 用户取消，直接向上传递
        } catch (e: Throwable) {
            if (view != null && StreamFallbackPolicy.shouldFallback(e, receivedContentDelta.get())) {
                terminated.set(true)
                fallbackToNonStreaming(indicator, project, feature, view, provider, apiKey, messages, ::flushNow)
                return
            }
            terminated.set(true)
            application.invokeLater { view?.onFailed(e.message ?: e.javaClass.simpleName) }
            throw e
        }
    }

    /**
     * 自动降级：非流式重试一次（仅当流式收到可重试 4xx 且尚未展示正文时进入）。
     * 本段失败直接上抛，不再二次回退。
     */
    private fun fallbackToNonStreaming(
        indicator: ProgressIndicator,
        project: Project,
        feature: AiFeature,
        view: AiStreamView,
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        flushNow: () -> Unit,
    ) {
        val application = ApplicationManager.getApplication()
        try {
            val raw = client.chat(provider, apiKey, messages, feature.maxOutputTokens)
            indicator.checkCanceled()
            val cleaned = cleanOrThrow(feature, raw)
            application.invokeLater {
                // 先排空流式阶段可能残留的 reasoning（正文必然为空，否则不会进入降级）
                flushNow()
                view.onContentDelta(cleaned)
                view.onCompleted(cleaned)
            }
            notifyInfo(project, LingxiCodeBundle.message("notification.streamFallback"))
        } catch (fallbackError: Throwable) {
            if (fallbackError is ProcessCanceledException || fallbackError is InterruptedException) {
                application.invokeLater { view.onCancelled() }
            } else {
                application.invokeLater {
                    view.onFailed(fallbackError.message ?: fallbackError.javaClass.simpleName)
                }
            }
            throw fallbackError
        }
    }

    /** 清洗模型输出；为空时抛出用户可读异常 */
    private fun cleanOrThrow(feature: AiFeature, raw: String): String {
        val cleaned = feature.cleanResponse(raw)
        if (cleaned.isBlank()) {
            throw AiClientException(LingxiCodeBundle.message("error.emptyCleaned"))
        }
        return cleaned
    }

    companion object {

        private const val NOTIFICATION_GROUP = "LingxiCodeAI"

        /** 流式增量推送到 EDT 的最小时间窗（毫秒） */
        private const val FLUSH_INTERVAL_MS = 150L

        private fun notificationTitle(): String = LingxiCodeBundle.message("notification.groupTitle")

        /** 弹出警告通知 */
        @JvmStatic
        fun notifyWarning(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, notificationTitle(), message, NotificationType.WARNING),
                project,
            )
        }

        /** 弹出错误通知 */
        @JvmStatic
        fun notifyError(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, notificationTitle(), message, NotificationType.ERROR),
                project,
            )
        }

        /** 弹出信息通知 */
        @JvmStatic
        fun notifyInfo(project: Project?, message: String) {
            Notifications.Bus.notify(
                Notification(NOTIFICATION_GROUP, notificationTitle(), message, NotificationType.INFORMATION),
                project,
            )
        }
    }
}
