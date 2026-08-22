package com.lihtdev.codesense.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import com.intellij.util.Consumer
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import java.awt.event.MouseEvent

/**
 * IDE 底部状态栏模型切换器工厂。
 * 在 plugin.xml 中通过 statusBarWidgetFactory 扩展点注册。
 */
class ModelSwitcherWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "codesense.modelSwitcher"

    override fun getDisplayName(): String = CodeSenseBundle.message("statusbar.modelSwitcher.tooltip")

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        ModelSwitcherWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        // no-op
    }

    override fun isConfigurable(): Boolean = false
}

/**
 * 状态栏模型切换器：显示当前模型名称，点击弹出切换面板。
 */
class ModelSwitcherWidget(private val project: Project) : StatusBarWidget {

    private var statusBar: StatusBar? = null

    /** 当前显示文本 */
    private fun currentText(): String {
        val provider = AppSettings.instance.activeProvider()
        return if (provider != null) {
            CodeSenseBundle.message("statusbar.modelSwitcher.text", provider.displayName, provider.model)
        } else {
            CodeSenseBundle.message("statusbar.modelSwitcher.noProvider")
        }
    }

    override fun ID(): String = "codesense.modelSwitcher"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // 监听设置变更
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AppSettingsListener.TOPIC, object : AppSettingsListener {
                override fun providerChanged() {
                    statusBar.updateWidget(ID())
                }
            })
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String = currentText()

            override fun getTooltipText(): String =
                CodeSenseBundle.message("statusbar.modelSwitcher.tooltip")

            override fun getClickConsumer(): Consumer<MouseEvent>? {
                return Consumer {
                    ModelSwitcherPopup.show(project, it.component) {
                        statusBar?.updateWidget(ID())
                    }
                }
            }

            override fun getAlignment(): Float = 0f
        }
    }
}

/**
 * 设置变更监听器，用于通知状态栏等 UI 刷新。
 */
interface AppSettingsListener {
    fun providerChanged()

    companion object {
        @JvmField
        val TOPIC = com.intellij.util.messages.Topic.create(
            "CodeSense AI Settings Changes",
            AppSettingsListener::class.java,
        )
    }
}