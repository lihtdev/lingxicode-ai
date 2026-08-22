package com.lihtdev.codesense.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * 模型切换弹出面板：两级窗口。
 * 一级窗口：显示当前模型 + 「切换模型」按钮。
 * 二级窗口：按提供商分组显示模型列表。
 */
object ModelSwitcherPopup {

    /** 列表条目：分组标题或模型条目 */
    sealed class ListEntry {
        /** 提供商分组标题（不可选） */
        data class Header(val displayName: String) : ListEntry()
        /** 模型条目 */
        data class Model(
            val id: String,
            val providerId: String,
            val displayName: String,
            val model: String,
            val modelDisplayName: String,
        ) : ListEntry()
    }

    /**
     * 一级窗口：当前模型信息 + 切换按钮。
     */
    fun showFirstLevel(project: Project, owner: Component, onChanged: () -> Unit) {
        val settings = AppSettings.instance
        val provider = settings.activeProvider()

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
        }

        val titleLabel = JBLabel(CodeSenseBundle.message("popup.currentModel.title")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        panel.add(titleLabel, BorderLayout.NORTH)

        if (provider != null) {
            val modelLabel = JBLabel("${provider.displayName} / ${provider.modelDisplayName.ifBlank { provider.model }}").apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                border = JBUI.Borders.empty(8, 0)
            }
            panel.add(modelLabel, BorderLayout.CENTER)
        } else {
            val noModelLabel = JBLabel(CodeSenseBundle.message("popup.currentModel.noModel")).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                border = JBUI.Borders.empty(8, 0)
            }
            panel.add(noModelLabel, BorderLayout.CENTER)
        }

        val popupRef = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setTitle(CodeSenseBundle.message("popup.currentModel.title"))
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        val switchButton = JButton(CodeSenseBundle.message("popup.switchAction")).apply {
            addActionListener {
                popupRef.closeOk(null)
                showSecondLevel(project, owner, onChanged)
            }
        }
        val buttonPanel = JPanel().apply {
            add(switchButton)
        }
        panel.add(buttonPanel, BorderLayout.SOUTH)

        popupRef.showUnderneathOf(owner)
    }

    /**
     * 二级窗口：按提供商分组显示模型列表，位置居中。
     */
    private fun showSecondLevel(project: Project, owner: Component, onChanged: () -> Unit) {
        val settings = AppSettings.instance
        val providers = settings.state.providers
        val activeId = settings.state.activeProviderId

        if (providers.isEmpty()) {
            return
        }

        // 按 providerId 分组构建列表（仅启用条目）
        val entries = mutableListOf<ListEntry>()
        val grouped = providers.filter { it.enabled }.groupBy { it.providerId }
        for ((_, group) in grouped) {
            val first = group.first()
            entries.add(ListEntry.Header(first.displayName))
            group.forEach { p ->
                entries.add(
                    ListEntry.Model(
                        p.id, p.providerId, p.displayName, p.model,
                        p.modelDisplayName.ifBlank { p.model },
                    ),
                )
            }
        }

        val listModel = CollectionListModel<ListEntry>()
        entries.forEach { listModel.add(it) }

        val activeProvider = providers.firstOrNull { it.id == activeId }

        val list = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = GroupedEntryRenderer(activeProvider)
            // 选中当前激活的模型条目
            for (i in 0 until entries.size) {
                val entry = entries[i]
                if (entry is ListEntry.Model && entry.id == activeId) {
                    setSelectedIndex(i)
                    break
                }
            }
        }

        val titleLabel = JBLabel(CodeSenseBundle.message("popup.selectModel.title")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            border = JBUI.Borders.empty(8, 10, 4, 10)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.NORTH)
            add(list, BorderLayout.CENTER)
        }

        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, list)
            .setTitle(CodeSenseBundle.message("popup.selectModel.title"))
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        list.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val entry = list.selectedValue
            if (entry is ListEntry.Header) {
                // 分组标题不可选，跳回
                return@addListSelectionListener
            }
            val modelEntry = entry as? ListEntry.Model ?: return@addListSelectionListener
            popup.closeOk(null)
            settings.setActiveProvider(modelEntry.id)
            ApplicationManager.getApplication().messageBus
                .syncPublisher(AppSettingsListener.TOPIC)
                .providerChanged()
            onChanged()
        }

        // 居中显示
        popup.showInFocusCenter()
    }

    /** 分组条目渲染器 */
    private class GroupedEntryRenderer(
        private val activeProvider: com.lihtdev.codesense.settings.AiProviderConfig?,
    ) : ListCellRenderer<ListEntry> {
        private val delegate = JBLabel()

        override fun getListCellRendererComponent(
            list: JList<out ListEntry>?, value: ListEntry, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            when (value) {
                is ListEntry.Header -> {
                    delegate.text = value.displayName
                    delegate.font = delegate.font.deriveFont(Font.BOLD, 12f)
                    delegate.foreground = JBColor.GRAY
                    delegate.border = JBUI.Borders.empty(6, 10, 2, 10)
                    delegate.background = list?.background
                    delegate.isOpaque = true
                }
                is ListEntry.Model -> {
                    delegate.text = "    ${value.modelDisplayName}"
                    delegate.font = delegate.font.deriveFont(Font.PLAIN, 12f)
                    delegate.border = JBUI.Borders.empty(2, 10, 4, 10)
                    if (isSelected) {
                        delegate.background = list?.selectionBackground
                        delegate.foreground = list?.selectionForeground
                    } else {
                        delegate.background = list?.background
                        delegate.foreground = list?.foreground
                    }
                    delegate.isOpaque = true
                }
            }
            return delegate
        }
    }
}