package com.lihtdev.codesense.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import java.awt.event.MouseEvent
import javax.swing.Icon

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
 * 状态栏模型切换器：仅显示插件图标，点击弹出两级切换窗口。
 */
class ModelSwitcherWidget(private val project: Project) : StatusBarWidget {

    private var statusBar: StatusBar? = null

    private val icon: Icon by lazy {
        IconLoader.getIcon("/icons/codesense.svg", ModelSwitcherWidget::class.java)
    }

    /** 当前 tooltip */
    private fun currentTooltip(): String {
        val provider = AppSettings.instance.activeProvider()
        return if (provider != null) {
            "${provider.displayName} / ${provider.model}"
        } else {
            CodeSenseBundle.message("statusbar.modelSwitcher.noProvider")
        }
    }

    override fun ID(): String = "codesense.modelSwitcher"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
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
        return object : StatusBarWidget.IconPresentation {
            override fun getIcon(): Icon = icon

            override fun getTooltipText(): String = currentTooltip()

            override fun getClickConsumer(): Consumer<MouseEvent>? {
                return Consumer {
                    ModelSwitcherPopup.showFirstLevel(project, it.component) {
                        statusBar?.updateWidget(ID())
                    }
                }
            }
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