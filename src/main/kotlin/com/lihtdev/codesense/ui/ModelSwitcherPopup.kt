package com.lihtdev.codesense.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import com.lihtdev.codesense.settings.ProviderPresets
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * 模型切换弹出面板：按厂商分组显示所有可用模型，点击即可切换。
 */
object ModelSwitcherPopup {

    /** 弹出列表中的单条模型条目 */
    data class ModelEntry(
        val providerId: String,
        val displayName: String,
        val model: String,
    )

    /**
     * 在指定组件下方弹出模型选择面板。
     *
     * @param project 当前项目
     * @param owner   弹出锚点组件（状态栏标签）
     * @param onChanged 切换成功后回调
     */
    fun show(project: Project, owner: Component, onChanged: () -> Unit) {
        val settings = AppSettings.instance
        val providers = settings.state.providers
        val activeId = settings.state.activeProviderId

        val entries = mutableListOf<ModelEntry>()
        for (provider in providers) {
            val preset = ProviderPresets.byId(provider.id)
            val models = (preset?.models ?: emptyList()).toMutableSet()
            if (provider.model.isNotBlank()) {
                models.add(provider.model)
            }
            for (model in models.sorted()) {
                entries.add(ModelEntry(provider.id, provider.displayName, model))
            }
        }

        if (entries.isEmpty()) {
            return
        }

        val listModel = CollectionListModel<ModelEntry>()
        entries.forEach { listModel.add(it) }

        val list = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = ModelEntryRenderer(activeId)
            // 选中当前激活的条目
            val activeProvider = providers.firstOrNull { it.id == activeId }
            val activeEntry = entries.firstOrNull {
                it.providerId == activeId && activeProvider != null && it.model == activeProvider.model
            }
            if (activeEntry != null) {
                setSelectedValue(activeEntry, true)
            }
        }

        // 标题栏
        val titleLabel = JBLabel(CodeSenseBundle.message("popup.modelSwitcher.title")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            border = JBUI.Borders.empty(8, 10, 4, 10)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            add(list, BorderLayout.CENTER)
        }

        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, list)
            .setTitle(CodeSenseBundle.message("popup.modelSwitcher.title"))
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        list.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val entry = list.selectedValue ?: return@addListSelectionListener
            popup.closeOk(null)
            // 切换厂商 + 模型
            val provider = settings.state.providers.firstOrNull { p -> p.id == entry.providerId }
                ?: return@addListSelectionListener
            provider.model = entry.model
            settings.setActiveProvider(entry.providerId)
            ApplicationManager.getApplication().messageBus
                .syncPublisher(AppSettingsListener.TOPIC)
                .providerChanged()
            onChanged()
        }

        popup.showUnderneathOf(owner)
    }

    /** 模型条目渲染器 */
    private class ModelEntryRenderer(
        private val activeProviderId: String,
    ) : ListCellRenderer<ModelEntry> {
        private val delegate = JBLabel()

        override fun getListCellRendererComponent(
            list: JList<out ModelEntry>?, value: ModelEntry, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.text = "${value.displayName} \u2014 ${value.model}"
            delegate.font = delegate.font.deriveFont(Font.PLAIN, 12f)
            delegate.border = JBUI.Borders.empty(4, 10)
            if (isSelected) {
                delegate.background = list?.selectionBackground
                delegate.foreground = list?.selectionForeground
            } else {
                delegate.background = list?.background
                delegate.foreground = list?.foreground
            }
            delegate.isOpaque = true
            return delegate
        }
    }
}