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
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lihtdev.codesense.i18n.CodeSenseBundle
import com.lihtdev.codesense.settings.AppSettings
import com.lihtdev.codesense.settings.ProviderPlanType
import com.lihtdev.codesense.settings.SettingsConfigurable
import com.lihtdev.codesense.settings.providerPlanTypeLabel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
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
        /** 提供商分组标题（不可选）：名称 + 类型（组内 providerId 相同，类型一致） */
        data class Header(
            val displayName: String,
            val planType: ProviderPlanType,
        ) : ListEntry()

        /** 模型条目：模型名 + 模型标签 */
        data class Model(
            val id: String,
            val providerId: String,
            val model: String,
            val modelTags: List<String>,
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

        // 头部：品牌栏（左侧 logo + 品牌名，右侧设置齿轮）+ 分隔线 + 粗体小节标题
        val headerBox = createHeaderBox(
            brandBar = createBrandBar(
                onGearClick = {
                    popupRef.closeOk(null)
                    SettingsConfigurable.open(project)
                },
            ),
            titleKey = "popup.currentModel.title",
        )
        panel.add(headerBox, BorderLayout.NORTH)

        if (provider != null) {
            // 当前模型信息：第一行模型名 + 模型标签 chip，第二行灰字「提供商 · 类型」
            val infoPanel = JPanel().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            }
            val modelRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(JBLabel(provider.model).apply {
                    font = JBFont.label()
                })
                if (provider.modelTags.isNotEmpty()) {
                    add(ChipComponents.chipsRow(provider.modelTags, LIST_WIDTH - 140))
                }
            }
            modelRow.alignmentX = Component.LEFT_ALIGNMENT
            val providerLabel = JBLabel(
                "${provider.displayName} · ${providerPlanTypeLabel(provider.planType)}",
            ).apply {
                font = JBFont.small()
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyTop(4)
            }
            providerLabel.alignmentX = Component.LEFT_ALIGNMENT
            infoPanel.add(modelRow)
            infoPanel.add(providerLabel)
            panel.add(infoPanel, BorderLayout.CENTER)
        } else {
            val noModelLabel = JBLabel(CodeSenseBundle.message("popup.currentModel.noModel")).apply {
                font = JBFont.small()
                foreground = UIUtil.getContextHelpForeground()
            }
            panel.add(noModelLabel, BorderLayout.CENTER)
        }

        val switchButton = JButton(CodeSenseBundle.message("popup.switchAction")).apply {
            addActionListener {
                popupRef.closeOk(null)
                showSecondLevel(project, owner, onChanged)
            }
        }
        // 按钮行：底部全宽主操作，与信息区保持间距
        val buttonPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 0, 0, 0)
            add(switchButton, BorderLayout.CENTER)
        }
        panel.add(buttonPanel, BorderLayout.SOUTH)

        // 固定宽度与二级列表对齐，保证两级弹框视觉一致
        panel.preferredSize = Dimension(LIST_WIDTH, panel.preferredSize.height)

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

        // 按 providerId 分组构建模型列表（同名不同类型 = 不同 providerId，自然各自成组）
        val entries = mutableListOf<ListEntry>()
        val grouped = providers.groupBy { it.providerId }
        for ((_, group) in grouped) {
            val first = group.first()
            entries.add(ListEntry.Header(first.displayName, first.planType))
            group.forEach { p ->
                entries.add(
                    ListEntry.Model(
                        p.id, p.providerId, p.model, p.modelTags,
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

        // 头部：品牌栏（二级不带齿轮）+ 分隔线 + 粗体小节标题
        val headerBox = createHeaderBox(
            brandBar = createBrandBar(onGearClick = null),
            titleKey = "popup.selectModel.title",
        )

        // 列表滚动面板：固定宽度、高度受限，模型多时内部滚动
        // （头部高度由 NORTH 的 headerBox 占据，此处只计列表本身；
        //   高度按渲染器实际行高求和——分组标题行与模型行高度不同，
        //   固定行高估算会裁掉行——上限仍为 MAX_VISIBLE_ROWS 行）
        val listHeight = minOf(list.preferredSize.height, MAX_VISIBLE_ROWS * ROW_HEIGHT)
        val scrollPane = JScrollPane(list).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(LIST_WIDTH, listHeight)
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

    /**
     * 分组条目渲染器：标题行 = 提供商名（灰字加粗）+ 类型 chip；模型行 = ✓ 标记 + 模型名 + 模型标签 chip。
     * 每次渲染重建复合面板（弹窗行数有限，代价可忽略），选中态配色随列表。
     */
    private class GroupedEntryRenderer(
        private val activeProvider: com.lihtdev.codesense.settings.AiProviderConfig?,
    ) : ListCellRenderer<ListEntry> {
        private val checkIcon = AllIcons.Actions.Checked
        private val checkIconSelected = AllIcons.Actions.Checked_selected
        private val checkPlaceholder = EmptyIcon.create(checkIcon)

        override fun getListCellRendererComponent(
            list: JList<out ListEntry>?, value: ListEntry, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component = when (value) {
            is ListEntry.Header -> headerPanel(value, list, isSelected)
            is ListEntry.Model -> modelPanel(value, list, isSelected)
        }

        private fun headerPanel(header: ListEntry.Header, list: JList<out ListEntry>?, isSelected: Boolean): JComponent {
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 3)).apply {
                background = if (isSelected) list?.selectionBackground ?: JBColor.background()
                else list?.background ?: JBColor.background()
                isOpaque = true
                // 分组上方留白更大、与下方成员紧凑，形成分层
                border = JBUI.Borders.empty(7, 10, 3, 10)
            }
            row.add(JLabel(header.displayName).apply {
                font = JBFont.small().asBold()
                foreground = UIUtil.getContextHelpForeground()
            })
            row.add(ChipComponents.chipLabel(providerPlanTypeLabel(header.planType)))
            return row
        }

        private fun modelPanel(model: ListEntry.Model, list: JList<out ListEntry>?, isSelected: Boolean): JComponent {
            val active = model.id == activeProvider?.id
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 3)).apply {
                background = if (isSelected) list?.selectionBackground ?: JBColor.background()
                else list?.background ?: JBColor.background()
                isOpaque = true
                border = JBUI.Borders.empty(3, 10, 3, 10)
            }
            row.add(JLabel().apply {
                icon = when {
                    active && isSelected -> checkIconSelected
                    active -> checkIcon
                    // 非激活行用同尺寸空白图标占位，保证各行文字左对齐不跳动
                    else -> checkPlaceholder
                }
            })
            row.add(JLabel(model.model).apply {
                font = if (active) JBFont.label().asBold() else JBFont.label()
                foreground = if (isSelected) {
                    list?.selectionForeground ?: JBColor.foreground()
                } else {
                    list?.foreground ?: JBColor.foreground()
                }
            })
            if (model.modelTags.isNotEmpty()) {
                row.add(ChipComponents.chipsRow(model.modelTags, LIST_WIDTH - 110))
            }
            return row
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

    /** 品牌栏：logo + 品牌名靠左、设置齿轮（可选）靠右；GridBag 显式锚定保证同一行垂直居中 */
    private fun createBrandBar(onGearClick: (() -> Unit)?): JComponent {
        val logo = JLabel(IconLoader.getIcon("/icons/codesense.svg", ModelSwitcherPopup::class.java))
        val nameLabel = JBLabel(CodeSenseBundle.message("popup.brand.name")).apply {
            font = JBFont.label().asBold()
        }
        val titleBlock = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(logo)
            add(nameLabel)
        }
        val bar = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 8, 0)
        }

        // 品牌块：weightx 吸收剩余宽度并靠左（anchor=WEST 自带垂直居中）
        bar.add(titleBlock, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            anchor = GridBagConstraints.WEST
        })

        if (onGearClick != null) {
            // 齿轮：锚 EAST 顶到右缘、垂直居中；钉死 16×16 屏蔽 LAF 按钮尺寸差异
            bar.add(JButton(AllIcons.General.Settings).apply {
                isFocusable = false
                isContentAreaFilled = false
                isBorderPainted = false
                margin = JBUI.insets(0)
                preferredSize = Dimension(16, 16)
                minimumSize = Dimension(16, 16)
                maximumSize = Dimension(16, 16)
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                toolTipText = CodeSenseBundle.message("popup.settings.tooltip")
                addActionListener { onGearClick() }
            }, GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                anchor = GridBagConstraints.EAST
                insets = java.awt.Insets(0, 6, 0, 0)
            })
        }
        return bar
    }

    /**
     * 头部容器：品牌栏 / 分隔线 / 小节标题纵向三行。
     * 用 GridBag 单列 + fill=HORIZONTAL 保证每行确定性撑满弹框宽度
     * （BoxLayout Y_AXIS 的横向宽度按扩容能力分配，行为不可靠）。
     */
    private fun createHeaderBox(brandBar: JComponent, titleKey: String): JComponent {
        val box = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
        }
        gbc.gridy = 0
        box.add(brandBar, gbc)
        gbc.gridy = 1
        box.add(createDivider(), gbc)
        gbc.gridy = 2
        box.add(sectionHeader(titleKey), gbc)
        return box
    }

    /** 1px 分隔线（品牌栏下方，分隔品牌区与内容区；宽度由外层单列 GridBag 撑满） */
    private fun createDivider(): JComponent = JPanel().apply {
        background = JBColor.border()
        isOpaque = true
        preferredSize = Dimension(1, 1)
    }

    /** 功能小节标题（品牌栏 + 分隔线下方，粗体主色；上下留白拉开层次） */
    private fun sectionHeader(key: String): JComponent = JBLabel(CodeSenseBundle.message(key)).apply {
        font = JBFont.label().asBold()
        border = JBUI.Borders.empty(10, 0, 10, 0)
    }

    /** 弹框边框估算（content 与窗口尺寸之差） */
    private const val POPUP_BORDER_DELTA = 2

    /** 屏幕边缘安全留白 */
    private const val EDGE_MARGIN = 8

    /** 二级列表固定宽度（容纳类型/模型标签 chip，较原先略宽） */
    private const val LIST_WIDTH = 296

    /** 二级列表行高 */
    private const val ROW_HEIGHT = 28

    /** 二级列表最多可见行数（超出滚动） */
    private const val MAX_VISIBLE_ROWS = 10
}