package com.lihtdev.codesense.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.ScreenUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import com.lihtdev.codesense.settings.SettingsConfigurable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
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
     * 一级窗口：品牌栏（右侧设置齿轮直达设置页）+ 当前模型信息 + 切换按钮。
     */
    fun showFirstLevel(project: Project, owner: Component, onChanged: () -> Unit) {
        val settings = AppSettings.instance
        val provider = settings.activeProvider()

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
        }

        // 弹框容器（builder 持有 panel 引用，先创建、后填充内容是安全的）
        val popupRef = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setResizable(false)
            .setRequestFocus(true)
            .createPopup()

        // 头部：品牌栏（左侧 logo + 品牌名，右侧设置齿轮）+ 功能小标题
        val headerBox = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(
                createBrandBar(
                    onGearClick = {
                        popupRef.closeOk(null)
                        SettingsConfigurable.open(project)
                    },
                ),
            )
            add(sectionCaption("popup.currentModel.title"))
        }
        panel.add(headerBox, BorderLayout.NORTH)

        if (provider != null) {
            // 当前模型信息：第一行显示名，第二行灰字「提供商 / 模型代号」
            val infoPanel = JPanel().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            }
            infoPanel.add(JBLabel(provider.modelDisplayName.ifBlank { provider.model }).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
            })
            infoPanel.add(JBLabel("${provider.displayName} / ${provider.model}").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = JBColor.GRAY
                border = JBUI.Borders.emptyTop(2)
            })
            panel.add(infoPanel, BorderLayout.CENTER)
        } else {
            val noModelLabel = JBLabel(CodeSenseBundle.message("popup.currentModel.noModel")).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
            }
            panel.add(noModelLabel, BorderLayout.CENTER)
        }

        val switchButton = JButton(CodeSenseBundle.message("popup.switchAction")).apply {
            addActionListener {
                popupRef.closeOk(null)
                showSecondLevel(project, owner, onChanged)
            }
        }
        // 按钮行：右对齐，与信息区保持间距
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            border = JBUI.Borders.empty(14, 0, 0, 0)
            add(switchButton)
        }
        panel.add(buttonPanel, BorderLayout.SOUTH)

        // 显示在状态栏图标上方
        showAbove(popupRef, owner)
    }

    /**
     * 二级窗口：按提供商分组显示模型列表，位于状态栏图标上方。
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

        // 头部：品牌栏（二级不带齿轮）+ 功能小标题
        val headerBox = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(createBrandBar(onGearClick = null))
            add(sectionCaption("popup.selectModel.title"))
        }

        // 列表滚动面板：固定宽度、高度受限，模型多时内部滚动
        val headerHeight = headerBox.preferredSize.height
        val listHeight = minOf(entries.size, MAX_VISIBLE_ROWS) * ROW_HEIGHT
        val scrollPane = JScrollPane(list).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(LIST_WIDTH, headerHeight + listHeight)
            // 长模型名直接裁剪，不出现横向滚动条
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val panel = JPanel(BorderLayout()).apply {
            // 与一级弹框一致的外边距
            border = JBUI.Borders.empty(12)
            add(headerBox, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, list)
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

        // 显示在状态栏图标上方
        showAbove(popup, owner)
    }

    /** 分组条目渲染器（分组标题灰字加粗 + 激活模型 ✓ 标记，行距对称均匀） */
    private class GroupedEntryRenderer(
        private val activeProvider: com.lihtdev.codesense.settings.AiProviderConfig?,
    ) : ListCellRenderer<ListEntry> {
        private val delegate = JBLabel()
        private val checkIcon = AllIcons.Actions.Checked
        private val checkIconSelected = AllIcons.Actions.Checked_selected
        private val checkPlaceholder = EmptyIcon.create(checkIcon)

        override fun getListCellRendererComponent(
            list: JList<out ListEntry>?, value: ListEntry, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.iconTextGap = 6
            when (value) {
                is ListEntry.Header -> {
                    delegate.text = value.displayName
                    delegate.icon = null
                    delegate.font = delegate.font.deriveFont(Font.BOLD, 12f)
                    delegate.foreground = JBColor.GRAY
                    // 分组上方留白更大、与下方成员紧凑，形成分层
                    delegate.border = JBUI.Borders.empty(9, 10, 3, 10)
                    delegate.background = list?.background
                    delegate.isOpaque = true
                }
                is ListEntry.Model -> {
                    val active = value.id == activeProvider?.id
                    delegate.text = value.modelDisplayName
                    delegate.icon = when {
                        active && isSelected -> checkIconSelected
                        active -> checkIcon
                        // 非激活行用同尺寸空白图标占位，保证各行文字左对齐不跳动
                        else -> checkPlaceholder
                    }
                    delegate.font = delegate.font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 12f)
                    delegate.border = JBUI.Borders.empty(5, 10, 4, 10)
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

    /**
     * 把弹框显示在锚组件（状态栏图标）上方：
     * 水平居中于锚、垂直贴锚顶部并留 [gap] 空隙；用锚所在屏幕矩形钳制坐标，
     * 上方空间不足时回落到锚下方；最后 moveToFitScreen() 兜底保证完整在屏内。
     */
    private fun showAbove(popup: JBPopup, anchor: Component, gap: Int = 8) {
        val panel = popup.getContent()
        val w = panel.preferredSize.width + POPUP_BORDER_DELTA
        val h = panel.preferredSize.height + POPUP_BORDER_DELTA
        val anchorLoc = anchor.locationOnScreen
        val screen = ScreenUtil.getScreenRectangle(anchorLoc)
        var x = anchorLoc.x + (anchor.width - w) / 2
        x = x.coerceIn(
            screen.x + EDGE_MARGIN,
            (screen.x + screen.width - w - EDGE_MARGIN).coerceAtLeast(screen.x + EDGE_MARGIN),
        )
        var y = anchorLoc.y - h - gap
        if (y < screen.y + EDGE_MARGIN) {
            // 上方放不下：回落到图标下方
            y = anchorLoc.y + anchor.height + gap
            y = y.coerceAtMost(screen.y + screen.height - h - EDGE_MARGIN)
        }
        popup.showInScreenCoordinates(anchor, Point(x, y))
        popup.moveToFitScreen()
    }

    /** 品牌栏：logo + 品牌名靠左，设置齿轮（可选）靠右，同一行垂直居中 */
    private fun createBrandBar(onGearClick: (() -> Unit)?): JComponent {
        val logo = JLabel(IconLoader.getIcon("/icons/codesense.svg", ModelSwitcherPopup::class.java))
        val nameLabel = JBLabel(CodeSenseBundle.message("popup.brand.name")).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
        val titleBlock = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            alignmentY = 0.5f
            add(logo)
            add(nameLabel)
        }
        return JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(0, 0, 4, 0)
            add(titleBlock)
            // 水平胶水把齿轮推到最右，品牌保持靠左
            add(javax.swing.Box.createHorizontalGlue())
            if (onGearClick != null) {
                add(JButton(AllIcons.General.Settings).apply {
                    isFocusable = false
                    isContentAreaFilled = false
                    isBorderPainted = false
                    margin = JBUI.insets(0)
                    // 显式垂直居中：齿轮按自然尺寸渲染，不随行高拉伸
                    alignmentY = 0.5f
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    toolTipText = CodeSenseBundle.message("popup.settings.tooltip")
                    addActionListener { onGearClick() }
                })
            }
        }
    }

    /** 功能小标题（品牌栏下方，灰色小字；底部留 8px 与内容区隔） */
    private fun sectionCaption(key: String): JComponent = JBLabel(CodeSenseBundle.message(key)).apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(0, 0, 8, 0)
    }

    /** 弹框边框估算（content 与窗口尺寸之差） */
    private const val POPUP_BORDER_DELTA = 2

    /** 屏幕边缘安全留白 */
    private const val EDGE_MARGIN = 8

    /** 二级列表固定宽度 */
    private const val LIST_WIDTH = 280

    /** 二级列表行高 */
    private const val ROW_HEIGHT = 26

    /** 二级列表最多可见行数（超出滚动） */
    private const val MAX_VISIBLE_ROWS = 10
}