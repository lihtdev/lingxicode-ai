package com.lihtdev.codesense.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.table.JBTable
import com.intellij.util.IconUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.ai.OpenAiCompatClient
import com.lihtdev.codesense.i18n.CodeSenseBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * chip 标签共用样式：设置页表格标签列 与 编辑弹窗标签面板 保持一致（填充/边框/圆角/内边距）。
 */
private object TagChipStyle {
    /** chip 填充色（亮/暗主题） */
    val background = JBColor(0xF5F5F5, 0x454545)

    /** chip 圆角半径 */
    const val ARC = 10

    /** chip 水平内边距（文字两侧） */
    const val H_PAD = 7

    /** chip 垂直内边距（文字上下） */
    const val V_PAD = 3
}

/**
 * 套餐类型展示文案（表格「类型」列 / 类型下拉渲染器 / 重复提示共用）。
 * 文件级私有函数，供本文件内各嵌套类直接调用。
 */
private fun planTypeLabel(type: ProviderPlanType): String = when (type) {
    ProviderPlanType.TOKEN_PLAN -> CodeSenseBundle.message("provider.type.tokenPlan")
    ProviderPlanType.CODING_PLAN -> CodeSenseBundle.message("provider.type.codingPlan")
    ProviderPlanType.PAY_AS_YOU_GO -> CodeSenseBundle.message("provider.type.payAsYouGo")
}

/**
 * 设置页（Tools → CodeSense AI）。
 *
 * 布局：顶部品牌区 → 模型表格（模型/标签/供应商/类型/操作）→ 底部全局设置。
 * 编辑、删除均在表格操作列完成；添加经「添加模型」弹窗批量选择。
 */
class SettingsConfigurable : Configurable {

    private val settings = AppSettings.instance

    /** 工作副本（应用前不落盘） */
    private lateinit var workingProviders: MutableList<AiProviderConfig>
    private lateinit var workingUserProviders: MutableList<UserProvider>
    private var workingActiveId: String = ""
    private val workingApiKeys = mutableMapOf<String, String?>()

    // ---- UI 组件 ----
    private val tableModel = ModelTableModel()
    private val actionRenderer = ActionCellRenderer()
    private val tagsRenderer = TagsCellRenderer()
    private val table = ModelTable(tableModel, actionRenderer)

    // 全局设置
    private val outputLanguageCombo = JComboBox(
        arrayOf(
            CodeSenseBundle.message("settings.language.zh"),
            CodeSenseBundle.message("settings.language.en"),
        ),
    )
    private val uiLanguageCombo = JComboBox(
        arrayOf(
            CodeSenseBundle.message("settings.language.zh"),
            CodeSenseBundle.message("settings.language.en"),
        ),
    )
    private val maxDiffField = javax.swing.JSpinner(javax.swing.SpinnerNumberModel(60000, 1000, 1000000, 1000))
    private val mainPanel: JComponent

    init {
        // 表格配置（行不可选，仅 hover 高亮，见 ModelTable）
        table.rowHeight = 32
        table.tableHeader.reorderingAllowed = false
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.fillsViewportHeight = true
        setFixedColumnWidth(0, COL_MODEL_W)
        setFixedColumnWidth(TAGS_COLUMN, COL_TAGS_W)
        setFixedColumnWidth(2, COL_PROVIDER_W)
        setFixedColumnWidth(TYPE_COLUMN, COL_TYPE_W)
        setFixedColumnWidth(ACTION_COLUMN, COL_ACTION_W)
        // 数据列：hover 高亮（标签列改用 chip 渲染器）
        val dataRenderer = GrayRowRenderer()
        table.columnModel.getColumn(0).cellRenderer = dataRenderer
        table.columnModel.getColumn(TAGS_COLUMN).cellRenderer = tagsRenderer
        table.columnModel.getColumn(2).cellRenderer = dataRenderer
        table.columnModel.getColumn(TYPE_COLUMN).cellRenderer = dataRenderer
        // 操作列：图标按钮
        table.columnModel.getColumn(ACTION_COLUMN).cellRenderer = actionRenderer
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                handleTableClick(e)
            }
        })

        // 组装主面板
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(createHeaderPanel())
            .addSeparator()
            .addComponent(createTablePanel())
            .addSeparator()
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.outputLanguage")), outputLanguageCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.uiLanguage")), uiLanguageCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.maxDiff")), maxDiffField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
    }

    // ---- 子面板构建 ----

    /** 顶部品牌区：Logo + 中英文名同行 + 版本/作者 */
    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 12, 0)

        // Logo：用 IconUtil 缩放放大（原始 SVG 为 16×16，放大到 80×80）
        val rawIcon = IconLoader.getIcon("/icons/codesense.svg", SettingsConfigurable::class.java)
        val scaledIcon = IconUtil.scale(rawIcon, panel, 5.0f)
        val iconLabel = JLabel(scaledIcon).apply {
            border = JBUI.Borders.emptyRight(16)
        }

        // 文字区域
        val textPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        }

        // 第一行：英文名 + 中文名 + 版本号（同一行，字号一致）
        val englishLabel = JLabel(CodeSenseBundle.message("settings.header.englishName")).apply {
            font = font.deriveFont(Font.BOLD, 20f)
        }
        val chineseLabel = JLabel(CodeSenseBundle.message("settings.header.chineseName")).apply {
            font = font.deriveFont(Font.PLAIN, 20f)
        }
        val version = try {
            PluginManagerCore.getPlugin(PluginId.getId("com.lihtdev.codesense"))?.version ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
        val versionLabel = JLabel(" v$version ").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = JBColor.GRAY
            border = object : javax.swing.border.AbstractBorder() {
                override fun paintBorder(c: Component?, g: java.awt.Graphics?, x: Int, y: Int, width: Int, height: Int) {
                    val g2 = g as java.awt.Graphics2D
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = JBColor.GRAY
                    g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8)
                }
                override fun getBorderInsets(c: Component?) = java.awt.Insets(2, 5, 2, 5)
            }
        }
        val nameRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(englishLabel)
            add(javax.swing.Box.createHorizontalStrut(10))
            add(chineseLabel)
            add(javax.swing.Box.createHorizontalStrut(10))
            add(versionLabel)
        }
        // BoxLayout(Y_AXIS) 下默认居中，需显式左对齐（第二行同理）
        nameRow.alignmentX = Component.LEFT_ALIGNMENT

        // 第二行：用户图标 + 作者 + 分隔符 + GitHub 图标 + 链接
        val authorIcon = JLabel(IconLoader.getIcon("/icons/user.svg", SettingsConfigurable::class.java)).apply {
            border = JBUI.Borders.emptyRight(3)
        }
        val authorLabel = JLabel(CodeSenseBundle.message("settings.header.author")).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = JBColor.GRAY
        }
        val separator = JLabel(" | ").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(0, 6, 0, 6)
        }
        val githubIcon = JLabel(IconLoader.getIcon("/icons/github.svg", SettingsConfigurable::class.java)).apply {
            border = JBUI.Borders.emptyRight(3)
        }
        val githubLink = JLabel("<html><a href=\"\">https://github.com/lihtdev</a></html>").apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    com.intellij.ide.BrowserUtil.browse("https://github.com/lihtdev")
                }
            })
        }
        val metaRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(authorIcon)
            add(authorLabel)
            add(separator)
            add(githubIcon)
            add(githubLink)
        }
        metaRow.alignmentX = Component.LEFT_ALIGNMENT

        textPanel.add(nameRow)
        textPanel.add(javax.swing.Box.createVerticalStrut(6))
        textPanel.add(metaRow)

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(iconLabel)
            add(textPanel)
        }

        panel.add(leftPanel, BorderLayout.WEST)
        return panel
    }

    /** 模型表格区：标题 + 添加按钮 + 表格 */
    private fun createTablePanel(): JPanel {
        val titleLabel = JLabel(CodeSenseBundle.message("settings.providerList.title")).apply {
            font = Font(font.name, Font.BOLD, font.size)
        }
        val addButton = JButton(CodeSenseBundle.message("settings.addProvider")).apply {
            addActionListener { showAddProviderDialog() }
        }

        val topPanel = JPanel(BorderLayout()).apply {
            add(titleLabel, BorderLayout.WEST)
            add(addButton, BorderLayout.EAST)
        }

        val scrollPane = JScrollPane(table).apply {
            preferredSize = Dimension(TABLE_WIDTH, TABLE_HEIGHT)
            minimumSize = Dimension(TABLE_WIDTH, TABLE_HEIGHT)
            maximumSize = Dimension(TABLE_WIDTH, TABLE_HEIGHT)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 0, 8, 0)
            add(topPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
        return panel
    }

    // ---- Configurable 实现 ----

    override fun getDisplayName(): String = CodeSenseBundle.message("settings.displayName")

    override fun createComponent(): JComponent = mainPanel

    override fun isModified(): Boolean {
        val current = settings.state
        val appliedOutputLanguage = if (outputLanguageCombo.selectedIndex == 1) "en" else "zh"
        val appliedUiLanguage = if (uiLanguageCombo.selectedIndex == 1) "en" else "zh"
        return workingProviders != current.providers
            || workingUserProviders != current.userProviders
            || workingActiveId != current.activeProviderId
            || appliedOutputLanguage != current.outputLanguage
            || appliedUiLanguage != current.uiLanguage
            || (maxDiffField.value as Int) != current.maxDiffChars
            || workingApiKeys.any { (id, key) -> AppSettings.getApiKey(id) != key }
    }

    override fun apply() {
        val state = settings.state
        state.providers = workingProviders.map { it.copy() }.toMutableList()
        state.userProviders = workingUserProviders.map { it.copy(plans = it.plans.toMutableList()) }.toMutableList()
        state.activeProviderId = workingActiveId
        state.outputLanguage = if (outputLanguageCombo.selectedIndex == 1) "en" else "zh"
        state.uiLanguage = if (uiLanguageCombo.selectedIndex == 1) "en" else "zh"
        state.maxDiffChars = maxDiffField.value as Int
        workingApiKeys.forEach { (id, key) ->
            AppSettings.setApiKey(id, key)
        }
    }

    override fun reset() {
        val state = settings.state
        workingProviders = state.providers.map { it.copy() }.toMutableList()
        workingUserProviders = state.userProviders
            .map { it.copy(plans = it.plans.toMutableList()) }
            .toMutableList()
        workingActiveId = state.activeProviderId
        workingApiKeys.clear()
        workingProviders.forEach { workingApiKeys[it.providerId] = AppSettings.getApiKey(it.providerId) }
        outputLanguageCombo.selectedIndex = if (state.outputLanguage == "en") 1 else 0
        uiLanguageCombo.selectedIndex = if (state.uiLanguage == "en") 1 else 0
        maxDiffField.value = state.maxDiffChars
        refreshTable()
    }

    // ---- 内部逻辑 ----

    /** 刷新表格数据 */
    private fun refreshTable() {
        tableModel.refresh(workingProviders)
    }

    /** 固定某列宽度（min=max=preferred） */
    private fun setFixedColumnWidth(column: Int, width: Int) {
        val col = table.columnModel.getColumn(column)
        col.minWidth = width
        col.maxWidth = width
        col.preferredWidth = width
    }

    /** 弹出添加模型对话框（先在提供商信息区选择/维护提供商，再批量勾选要添加的模型） */
    private fun showAddProviderDialog() {
        val dialog = AddProviderDialog(
            mainPanel,
            ModelUniqueness.keysOf(workingProviders),
            workingProviders,
            workingUserProviders,
        )
        if (dialog.showAndGet()) {
            // 1) 删除的提供商：级联移除其全部模型条目
            dialog.resultRemovedProviderIds.forEach { removedId ->
                workingProviders.removeAll { it.providerId == removedId }
            }
            // 2) 编辑的提供商：级联更新其模型条目的显示名与 baseUrl
            dialog.resultEditedProviders.forEach { (providerId, updated) ->
                val updatedBaseUrl = updated.plans.firstOrNull()?.baseUrl
                workingProviders.filter { it.providerId == providerId }.forEach {
                    it.displayName = updated.displayName
                    if (updatedBaseUrl != null) {
                        it.baseUrl = updatedBaseUrl
                    }
                }
            }
            // 3) 提供商档案整体替换
            workingUserProviders = dialog.resultProviders
                .map { it.copy(plans = it.plans.toMutableList()) }
                .toMutableList()
            // 4) 新增模型条目
            val configs = dialog.resultConfigs
            if (configs.isNotEmpty()) {
                configs.forEach { config ->
                    workingProviders.add(config)
                    if (dialog.apiKey.isNotBlank()) {
                        workingApiKeys[config.providerId] = dialog.apiKey
                    }
                }
                if (workingActiveId.isBlank()) {
                    workingActiveId = configs.first().id
                }
            }
            // 5) 激活条目被级联删除后回退到第一个条目
            if (workingActiveId.isNotBlank() && workingProviders.none { it.id == workingActiveId }) {
                workingActiveId = workingProviders.firstOrNull()?.id ?: ""
            }
            refreshTable()
        }
    }

    /** 编辑选中模型条目 */
    private fun editProvider(config: AiProviderConfig) {
        val dialog = EditProviderDialog(mainPanel, config, workingApiKeys[config.providerId], workingProviders)
        if (dialog.showAndGet()) {
            val updated = dialog.resultConfig ?: return
            val idx = workingProviders.indexOfFirst { it.id == config.id }
            if (idx >= 0) {
                workingProviders[idx] = updated
            }
            if (dialog.resultApiKey?.isNotBlank() == true) {
                workingApiKeys[updated.providerId] = dialog.resultApiKey
            }
            refreshTable()
        }
    }

    /** 删除模型条目 */
    private fun removeProvider(config: AiProviderConfig) {
        workingProviders.remove(config)
        // 不删除 apiKey：同一提供商的其他模型条目可能共享该 key
        if (workingActiveId == config.id) {
            workingActiveId = workingProviders.firstOrNull()?.id ?: ""
        }
        refreshTable()
    }

    /**
     * 删除前的确认弹框（正文带模型名），用户点击「删除」返回 true，「取消」返回 false。
     * 平台 2024.2 的 Messages 无 Component 父窗口重载，传 null Project 会以当前窗口为父。
     */
    private fun confirmRemoveProvider(config: AiProviderConfig): Boolean {
        val modelName = config.model
        val result = Messages.showYesNoDialog(
            null as Project?,
            CodeSenseBundle.message("settings.deleteProvider.confirm", modelName),
            CodeSenseBundle.message("settings.deleteProvider.confirm.title"),
            CodeSenseBundle.message("settings.deleteProvider.confirm.ok"),
            CodeSenseBundle.message("settings.deleteProvider.confirm.cancel"),
            Messages.getQuestionIcon(),
        )
        return result == Messages.YES
    }

    /** 处理表格操作列点击：仅在精确命中按钮 bounds 时才触发 */
    private fun handleTableClick(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        if (row < 0 || col != ACTION_COLUMN) return
        val config = workingProviders.getOrNull(row) ?: return
        val cellRect = table.getCellRect(row, col, true)
        val p = java.awt.Point(e.x - cellRect.x, e.y - cellRect.y)
        when {
            actionRenderer.editBounds.contains(p) -> editProvider(config)
            actionRenderer.deleteBounds.contains(p) -> {
                // 删除前先弹确认框，避免误删
                if (confirmRemoveProvider(config)) {
                    removeProvider(config)
                }
            }
            // 点击空白/按钮间隙：不触发任何操作
        }
    }

    // ---- 添加模型对话框 ----

    /**
     * 添加模型对话框：先在「提供商信息」区选择/维护提供商，再在「选择模型」区勾选要添加的模型。
     */
    private class AddProviderDialog(
        parent: JComponent,
        private val existingKeys: Set<String>,
        private val existingProviders: List<AiProviderConfig>,
        initialUserProviders: List<UserProvider>,
    ) : DialogWrapper(parent, true) {

        // ---- 提供商信息区 ----
        private val providerCombo = JComboBox<ProviderOption>().apply {
            renderer = ProviderOptionRenderer()
            minimumSize = Dimension(200, 30)
            addActionListener { onProviderSelected() }
        }
        private val providerValueLabel = JBLabel("").apply {
            minimumSize = Dimension(200, preferredSize.height)
        }
        private val planTypeCombo = JComboBox<ProviderPlanType>().apply {
            renderer = PlanRenderer()
            minimumSize = Dimension(140, 30)
            addActionListener { onPlanTypeChanged() }
        }
        private val baseUrlValueLabel = JBLabel("").apply {
            minimumSize = Dimension(200, preferredSize.height)
        }
        private val apiKeyField = JBPasswordField().apply {
            minimumSize = Dimension(200, preferredSize.height)
        }
        private val testButton = JButton(CodeSenseBundle.message("settings.testConnection")).apply {
            addActionListener { testConnection() }
        }
        private val addProviderButton = iconButton(AllIcons.General.Add, "settings.addProvider.addProvider") {
            showProviderEditor(null)
        }
        private val editProviderButton = iconButton(AllIcons.Actions.Edit, "settings.addProvider.editProvider") {
            showProviderEditor(selectedCustomProvider())
        }
        private val deleteProviderButton = iconButton(AllIcons.General.Delete, null) {
            confirmDeleteProvider()
        }

        // 模型复选框列表
        private val modelListModel = DefaultListModel<CheckableModel>()
        private val modelList = JBList(modelListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = CheckboxModelRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val idx = locationToIndex(e.point)
                    if (idx >= 0) {
                        val item = modelListModel.getElementAt(idx)
                        item.selected = !item.selected
                        repaint()
                    }
                }
            })
        }
        private val manualModelField = JTextField(18).apply {
            minimumSize = Dimension(120, preferredSize.height)
        }
        private val selectAllButton = JButton(CodeSenseBundle.message("settings.addProvider.selectAll")).apply {
            // 初始模型列表为空：先置灰，待列表非空后由 setModelItems / manualAddModel 恢复
            isEnabled = false
            addActionListener { toggleSelectAll() }
        }

        /** 程序化加载期间为 true，防止监听器误触发 */
        private var isLoading = false

        /** 对话框内提供商档案工作副本（基于设置页工作副本初始化；取消对话框不生效） */
        private val workingUserProviders: MutableList<UserProvider> =
            initialUserProviders.map { it.copy(plans = it.plans.toMutableList()) }.toMutableList()

        /** 本次对话框内被删除的提供商 id（其模型条目由设置页在 OK 后级联移除） */
        private val removedProviderIds = linkedSetOf<String>()

        /** 本次对话框内被编辑的提供商（providerId → 新档案，其模型条目由设置页在 OK 后级联更新） */
        private val editedProviders = linkedMapOf<String, UserProvider>()

        var resultConfigs: List<AiProviderConfig> = emptyList()
            private set
        var apiKey: String = ""
            private set
        var resultProviders: List<UserProvider> = emptyList()
            private set
        var resultRemovedProviderIds: Set<String> = emptySet()
            private set
        var resultEditedProviders: Map<String, UserProvider> = emptyMap()
            private set

        init {
            title = CodeSenseBundle.message("settings.addProvider.title")
            setOKButtonText(CodeSenseBundle.message("settings.addProvider.confirm"))
            setCancelButtonText(CodeSenseBundle.message("settings.addProvider.cancel"))
            rebuildProviderCombo()
            init()
        }

        /** 小图标按钮：16×16、无边框填充、手型光标、带功能提示（参照弹窗齿轮/表格操作列按钮样式）；禁用态显式置灰图标 */
        private fun iconButton(icon: Icon, tipKey: String?, action: () -> Unit): JButton =
            JButton(icon).apply {
                isFocusable = false
                isContentAreaFilled = false
                isBorderPainted = false
                margin = JBUI.insets(0)
                preferredSize = Dimension(16, 16)
                minimumSize = Dimension(16, 16)
                maximumSize = Dimension(16, 16)
                // 显式设置禁用图标：不支持操作的场景按钮置灰（与平台 LAF 内置禁用渲染不冲突）
                disabledIcon = IconLoader.getDisabledIcon(icon)
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                tipKey?.let { toolTipText = CodeSenseBundle.message(it) }
                addActionListener { action() }
            }

        override fun createCenterPanel(): JComponent {
            val hintLabel = JBLabel(CodeSenseBundle.message("settings.addProvider.hint")).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(Font.PLAIN, 12f)
                border = JBUI.Borders.empty(0, 0, 8, 0)
            }

            // 提供商操作按钮行：新增 / 编辑 / 删除（删除仅对自定义提供商开放）；间距 10px 避免图标按钮过于紧凑
            val providerActions = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                add(addProviderButton)
                add(editProviderButton)
                add(deleteProviderButton)
            }
            val providerRow = JPanel(BorderLayout(4, 0)).apply {
                add(providerCombo, BorderLayout.CENTER)
                add(providerActions, BorderLayout.EAST)
            }

            // 模型列表操作行 + 手动添加行
            val fetchButton = JButton(CodeSenseBundle.message("settings.addProvider.fetchModels")).apply {
                addActionListener { fetchModels() }
            }
            val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(fetchButton)
                add(selectAllButton)
            }

            val manualRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(manualModelField)
                add(JButton(CodeSenseBundle.message("settings.addProvider.manualAdd")).apply {
                    addActionListener { manualAddModel() }
                })
            }

            val listScroll = javax.swing.JScrollPane(modelList).apply {
                preferredSize = Dimension(320, 140)
            }

            // 表单标签（设置最小宽度，避免窗口缩小时文字被挤压）
            val templateLabel = JLabel(SettingsConfigurable.requiredLabel("settings.addProvider.template")).apply {
                minimumSize = preferredSize
            }
            val providerLabel = JLabel(CodeSenseBundle.message("settings.provider")).apply {
                minimumSize = preferredSize
            }
            val planTypeLabel = JLabel(CodeSenseBundle.message("settings.planType")).apply {
                minimumSize = preferredSize
            }
            val baseUrlLabel = JLabel(CodeSenseBundle.message("settings.baseUrl")).apply {
                minimumSize = preferredSize
            }
            val apiKeyLabel = JLabel(CodeSenseBundle.message("settings.apiKey")).apply {
                minimumSize = preferredSize
            }

            // 区块标题：加粗灰色小字，把对话框分成「提供商信息 / 选择模型」两区
            val providerSectionCaption = JBLabel(CodeSenseBundle.message("settings.addProvider.section.provider")).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
            }
            val modelsSectionCaption = JBLabel(CodeSenseBundle.message("settings.addProvider.section.models")).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
            }

            // 提供商信息（选择/维护提供商 + 只读展示 + API Key）→ 选择模型（勾选/获取/手动添加）
            return FormBuilder.createFormBuilder()
                .addComponent(hintLabel)
                .addVerticalGap(2)
                .addComponent(providerSectionCaption)
                .addVerticalGap(4)
                .addLabeledComponent(templateLabel, providerRow)
                .addVerticalGap(6)
                .addLabeledComponent(providerLabel, providerValueLabel)
                .addVerticalGap(6)
                .addLabeledComponent(planTypeLabel, planTypeCombo)
                .addVerticalGap(6)
                .addLabeledComponent(baseUrlLabel, baseUrlValueLabel)
                .addVerticalGap(6)
                .addLabeledComponent(apiKeyLabel, apiKeyField)
                .addVerticalGap(10)
                .addSeparator()
                .addVerticalGap(10)
                .addComponent(modelsSectionCaption)
                .addVerticalGap(4)
                .addComponent(buttonRow)
                .addComponent(listScroll)
                .addComponent(manualRow)
                .panel
        }

        /** 在底部按钮行「确定」左侧插入测试连接按钮，保持按钮间距与其他按钮一致 */
        override fun createSouthPanel(): JComponent {
            val south = super.createSouthPanel()
            val okButton = getButton(getOKAction()) ?: return south
            val container = okButton.parent ?: return south
            if (container.components.none { it === testButton }) {
                container.add(testButton, 0)
                // 与平台 BASE_BUTTON_GAP=12 的间距算法保持一致
                val insets = testButton.insets ?: JBUI.insets(0)
                val gap = JBUI.scale(12) - insets.left - insets.right
                if (gap > 0) {
                    container.add(javax.swing.Box.createRigidArea(Dimension(gap, 0)), 1)
                }
                container.revalidate()
            }
            return south
        }

        /** 选中提供商变化：刷新只读展示、类型可选范围与按钮可用态，并清空模型列表 */
        private fun onProviderSelected() {
            if (isLoading) return
            val option = selectedOption() ?: return
            isLoading = true
            try {
                providerValueLabel.text = option.displayName
                // 类型下拉按统一顺序展示：按量付费 → Token Plan → Coding Plan
                val planTypes = option.plans.map { it.type }.distinct()
                    .sortedBy { ProviderPlanType.DISPLAY_ORDER.indexOf(it) }
                val current = planTypeCombo.selectedItem as? ProviderPlanType
                planTypeCombo.model = DefaultComboBoxModel(planTypes.toTypedArray())
                planTypeCombo.selectedItem = if (planTypes.contains(current)) current else planTypes.firstOrNull()
                refreshBaseUrlLabel()
                editProviderButton.isEnabled = option.isCustom
                deleteProviderButton.isEnabled = option.isCustom
                // 删除按钮提示随选中项变化：自定义可删 / 预设仅提示限制
                deleteProviderButton.toolTipText = CodeSenseBundle.message(
                    if (option.isCustom) {
                        "settings.addProvider.deleteProvider"
                    } else {
                        "settings.addProvider.deleteProvider.tooltip"
                    },
                )
                setModelItems(emptyList(), selected = false)
            } finally {
                isLoading = false
            }
        }

        /** 切换类型：接口地址只读展示跟随（提供商档案内的 plans 决定） */
        private fun onPlanTypeChanged() {
            if (isLoading) return
            refreshBaseUrlLabel()
        }

        /** 依据当前选中的类型刷新接口地址只读展示 */
        private fun refreshBaseUrlLabel() {
            val option = selectedOption() ?: return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: return
            baseUrlValueLabel.text = option.baseUrlOf(planType)
        }

        /** 重建提供商下拉（预设 + 自定义档案），可指定选中项 id */
        private fun rebuildProviderCombo(selectId: String? = null) {
            isLoading = true
            try {
                providerCombo.removeAllItems()
                ProviderPresets.ALL.forEach { p ->
                    providerCombo.addItem(ProviderOption(p.id, p.displayName, p.plans, isCustom = false))
                }
                workingUserProviders.forEach { p ->
                    providerCombo.addItem(ProviderOption(p.id, p.displayName, p.plans, isCustom = true))
                }
                val index = selectId?.let { id ->
                    (0 until providerCombo.itemCount).firstOrNull { providerCombo.getItemAt(it).id == id }
                }
                providerCombo.selectedIndex = index ?: 0
            } finally {
                isLoading = false
            }
            onProviderSelected()
        }

        private fun selectedOption(): ProviderOption? = providerCombo.selectedItem as? ProviderOption

        /** 选中的自定义提供商档案；选中预设时返回 null */
        private fun selectedCustomProvider(): UserProvider? {
            val option = selectedOption() ?: return null
            if (!option.isCustom) return null
            return workingUserProviders.firstOrNull { it.id == option.id }
        }

        /** 打开提供商编辑器：existing=null 新建，否则编辑该自定义提供商 */
        private fun showProviderEditor(existing: UserProvider?) {
            val takenNames = ProviderPresets.ALL.map { it.displayName } +
                workingUserProviders.filter { it.id != existing?.id }.map { it.displayName }
            val dialog = ProviderEditorDialog(contentPanel, existing, takenNames.toSet())
            if (dialog.showAndGet()) {
                val result = dialog.resultProvider ?: return
                if (existing == null) {
                    workingUserProviders.add(result)
                } else {
                    val idx = workingUserProviders.indexOfFirst { it.id == existing.id }
                    if (idx >= 0) {
                        workingUserProviders[idx] = result
                    }
                    editedProviders[result.id] = result
                }
                rebuildProviderCombo(selectId = result.id)
            }
        }

        /** 删除选中的自定义提供商：二次确认（含其下模型数量），确认后从档案与下拉移除 */
        private fun confirmDeleteProvider() {
            val provider = selectedCustomProvider() ?: return
            val modelCount = existingProviders.count { it.providerId == provider.id }
            val result = Messages.showYesNoDialog(
                null as Project?,
                CodeSenseBundle.message(
                    "settings.addProvider.deleteProvider.confirm",
                    provider.displayName,
                    modelCount.toString(),
                ),
                CodeSenseBundle.message("settings.deleteProvider.confirm.title"),
                CodeSenseBundle.message("settings.deleteProvider.confirm.ok"),
                CodeSenseBundle.message("settings.deleteProvider.confirm.cancel"),
                Messages.getQuestionIcon(),
            )
            if (result != Messages.YES) return
            workingUserProviders.removeAll { it.id == provider.id }
            editedProviders.remove(provider.id)
            removedProviderIds.add(provider.id)
            rebuildProviderCombo()
        }

        /** 填充模型复选框列表；列表为空时「全选/全不选」无操作对象，置灰 */
        private fun setModelItems(models: List<String>, selected: Boolean) {
            modelListModel.removeAllElements()
            models.forEach { modelListModel.addElement(CheckableModel(it, selected)) }
            selectAllButton.isEnabled = models.isNotEmpty()
        }

        /** 全选 / 全不选 */
        private fun toggleSelectAll() {
            if (modelListModel.size() == 0) return
            val allSelected = (0 until modelListModel.size()).all { modelListModel.getElementAt(it).selected }
            for (i in 0 until modelListModel.size()) {
                modelListModel.getElementAt(i).selected = !allSelected
            }
            modelList.repaint()
        }

        /** 手动添加一个模型名到列表 */
        private fun manualAddModel() {
            val name = manualModelField.text.trim()
            if (name.isEmpty()) return
            modelListModel.addElement(CheckableModel(name, selected = true))
            // 列表非空后恢复「全选/全不选」可用
            selectAllButton.isEnabled = true
            manualModelField.text = ""
        }

        /** 测试当前配置的连通性（后台发一条最小请求，模型取第一个已勾选项，未勾选时取列表第一项） */
        private fun testConnection() {
            val option = selectedOption() ?: return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: return
            val baseUrl = option.baseUrlOf(planType)
            if (baseUrl.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.config"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val model = firstCheckedModelName() ?: firstModelName()
            if (model == null) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.config"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val key = String(apiKeyField.password)
            if (key.isBlank()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.key"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val provider = AiProviderConfig(
                id = "test",
                providerId = "test",
                displayName = option.displayName,
                baseUrl = baseUrl,
                model = model,
            )
            val startedAt = System.currentTimeMillis()
            val taskTitle = CodeSenseBundle.message("settings.test.progress", provider.displayName)
            object : Task.Backgroundable(null, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    val reply = try {
                        OpenAiCompatClient().chat(
                            provider, key,
                            listOf(ChatMessage("user", "Reply with just the word 'OK'.")),
                        )
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                contentPanel,
                                CodeSenseBundle.message("notification.testFail", e.message ?: ""),
                                CodeSenseBundle.message("settings.addProvider.title"),
                            )
                        }
                        return
                    }
                    val elapsed = System.currentTimeMillis() - startedAt
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            contentPanel,
                            CodeSenseBundle.message("notification.testSuccess", elapsed.toString(), reply.take(50)),
                            CodeSenseBundle.message("settings.addProvider.title"),
                        )
                    }
                }
            }.queue()
        }

        /** 第一个已勾选的模型名；无勾选返回 null */
        private fun firstCheckedModelName(): String? =
            (0 until modelListModel.size())
                .map { modelListModel.getElementAt(it) }
                .firstOrNull { it.selected }
                ?.name

        /** 模型列表第一项的名字；列表为空返回 null */
        private fun firstModelName(): String? =
            if (modelListModel.size() > 0) modelListModel.getElementAt(0).name else null

        /** 通过 API（GET {baseUrl}/models）获取模型列表 */
        private fun fetchModels() {
            val option = selectedOption() ?: return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: return
            val baseUrl = option.baseUrlOf(planType)
            if (baseUrl.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.config"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val key = String(apiKeyField.password)
            if (key.isBlank()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.key"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val provider = AiProviderConfig(
                id = "fetch",
                providerId = "fetch",
                displayName = option.displayName,
                baseUrl = baseUrl,
                model = "",
            )
            val taskTitle = CodeSenseBundle.message("settings.addProvider.fetchingModels")
            object : Task.Backgroundable(null, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    val models = try {
                        OpenAiCompatClient().listModels(provider, key)
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog(
                                contentPanel,
                                CodeSenseBundle.message("settings.addProvider.fetchFail", e.message ?: ""),
                                CodeSenseBundle.message("settings.addProvider.title"),
                            )
                        }
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        setModelItems(models, selected = true)
                        Messages.showInfoMessage(
                            contentPanel,
                            CodeSenseBundle.message("settings.addProvider.fetchSuccess", models.size.toString()),
                            CodeSenseBundle.message("settings.addProvider.title"),
                        )
                    }
                }
            }.queue()
        }

        override fun doOKAction() {
            val option = selectedOption() ?: return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: ProviderPlanType.PAY_AS_YOU_GO
            val baseUrl = option.baseUrlOf(planType)
            if (baseUrl.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.baseUrl.empty"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val selectedModels = (0 until modelListModel.size())
                .map { modelListModel.getElementAt(it) }
                .filter { it.selected }
            // 允许不选模型直接确定：此时仅提交提供商档案的增/删/改
            val (duplicates, uniques) = ModelUniqueness.partition(
                existingKeys,
                option.displayName,
                planType,
                selectedModels.map { it.name },
            )
            if (duplicates.isNotEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message(
                        "settings.addProvider.duplicate",
                        option.displayName,
                        planTypeLabel(planType),
                        duplicates.joinToString("、") { it.trim() },
                    ),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
            }
            if (uniques.isEmpty()) {
                resultConfigs = emptyList()
            } else {
                val seen = mutableSetOf<String>()
                resultConfigs = uniques.map { m ->
                    var id = "${option.id}:$m"
                    if (!seen.add(id)) id = "${option.id}:$m:${UUID.randomUUID()}"
                    AiProviderConfig(
                        id = id,
                        providerId = option.id,
                        displayName = option.displayName,
                        planType = planType,
                        baseUrl = baseUrl,
                        model = m,
                    )
                }
            }
            apiKey = String(apiKeyField.password)
            resultProviders = workingUserProviders.map { it.copy(plans = it.plans.toMutableList()) }
            resultRemovedProviderIds = removedProviderIds.toSet()
            resultEditedProviders = editedProviders.toMap()
            super.doOKAction()
        }
    }

    /** 选择提供商下拉条目：预设或用户自定义（统一建模） */
    private data class ProviderOption(
        val id: String,
        val displayName: String,
        val plans: List<PlanPreset>,
        val isCustom: Boolean,
    ) {
        /** 某类型对应的 baseUrl；未声明时返回空串 */
        fun baseUrlOf(type: ProviderPlanType): String = plans.firstOrNull { it.type == type }?.baseUrl ?: ""
    }

    /**
     * 提供商编辑器：新增/编辑用户自定义提供商（名称 + 类型 + 接口地址）。
     * 预设提供商不可编辑（不可进入本对话框）。
     */
    private class ProviderEditorDialog(
        parent: JComponent,
        private val existing: UserProvider?,
        private val takenNames: Set<String>,
    ) : DialogWrapper(parent, true) {

        private val dialogTitle = CodeSenseBundle.message(
            if (existing == null) {
                "settings.addProvider.providerEditor.create"
            } else {
                "settings.addProvider.providerEditor.edit"
            },
        )

        private val nameField = JTextField(30).apply {
            minimumSize = Dimension(200, 30)
        }
        private val planTypeCombo = JComboBox<ProviderPlanType>().apply {
            renderer = PlanRenderer()
            minimumSize = Dimension(140, 30)
        }
        private val baseUrlField = JTextField(30).apply {
            minimumSize = Dimension(220, 30)
        }

        var resultProvider: UserProvider? = null
            private set

        init {
            title = dialogTitle
            planTypeCombo.model = DefaultComboBoxModel(ProviderPlanType.DISPLAY_ORDER.toTypedArray())
            setOKButtonText(CodeSenseBundle.message("settings.addProvider.confirm"))
            setCancelButtonText(CodeSenseBundle.message("settings.addProvider.cancel"))
            loadInitial()
            init()
        }

        private fun loadInitial() {
            if (existing != null) {
                nameField.text = existing.displayName
                planTypeCombo.selectedItem = existing.plans.firstOrNull()?.type ?: ProviderPlanType.PAY_AS_YOU_GO
                baseUrlField.text = existing.plans.firstOrNull()?.baseUrl ?: ""
            }
        }

        override fun createCenterPanel(): JComponent {
            val baseUrlWithNote = JPanel(BorderLayout()).apply {
                add(baseUrlField, BorderLayout.NORTH)
                add(JBLabel(CodeSenseBundle.message("settings.addProvider.protocolNote")).apply {
                    font = font.deriveFont(Font.PLAIN, 11f)
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.empty(2, 0, 0, 0)
                }, BorderLayout.SOUTH)
            }
            val providerLabel = JLabel(SettingsConfigurable.requiredLabel("settings.provider")).apply {
                minimumSize = preferredSize
            }
            val planTypeLabel = JLabel(CodeSenseBundle.message("settings.planType")).apply {
                minimumSize = preferredSize
            }
            val baseUrlLabel = JLabel(SettingsConfigurable.requiredLabel("settings.baseUrl")).apply {
                minimumSize = preferredSize
            }
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(providerLabel, nameField)
                .addVerticalGap(6)
                .addLabeledComponent(planTypeLabel, planTypeCombo)
                .addVerticalGap(6)
                .addLabeledComponent(baseUrlLabel, baseUrlWithNote)
                .panel
        }

        override fun doOKAction() {
            val name = nameField.text.trim()
            if (name.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.addProvider.name.empty"),
                    dialogTitle,
                )
                return
            }
            val baseUrl = baseUrlField.text.trim()
            if (baseUrl.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.baseUrl.empty"),
                    dialogTitle,
                )
                return
            }
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: ProviderPlanType.PAY_AS_YOU_GO
            if (name in takenNames) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.addProvider.providerName.duplicate"),
                    dialogTitle,
                )
                return
            }
            resultProvider = UserProvider(
                id = existing?.id ?: "custom-${UUID.randomUUID()}",
                displayName = name,
                plans = mutableListOf(PlanPreset(planType, baseUrl)),
            )
            super.doOKAction()
        }
    }

    // ---- 标签面板组件（chip 风格，最多 3 个） ----

    /**
     * 标签编辑面板：以 chip（圆角边框标签）形式展示标签，右侧"+"按钮添加，最多 [MAX_TAGS] 个。
     * 每个 chip 带"×"删除按钮，点击即可移除。
     */
    private class TagsPanel : JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)) {

        companion object {
            const val MAX_TAGS = 3
        }

        private val tags = mutableListOf<String>()

        /** 内联输入行（输入框 + 确认 + 取消），默认隐藏 */
        private val inlineInput = JTextField(10).apply {
            toolTipText = CodeSenseBundle.message("settings.editProvider.tags.placeholder")
        }
        private val confirmButton = JButton(AllIcons.Actions.Commit).apply {
            toolTipText = CodeSenseBundle.message("settings.addProvider.confirm")
            isFocusable = false
            margin = JBUI.insets(2)
            addActionListener { commitInlineInput() }
        }
        private val cancelButton = JButton(AllIcons.Actions.Close).apply {
            toolTipText = CodeSenseBundle.message("settings.addProvider.cancel")
            isFocusable = false
            margin = JBUI.insets(2)
            addActionListener { hideInlineInput() }
        }
        private val inlinePanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isVisible = false
            add(inlineInput)
            add(confirmButton)
            add(cancelButton)
        }

        private val addButton = JButton(AllIcons.General.Add).apply {
            toolTipText = CodeSenseBundle.message("settings.editProvider.tags.add")
            isFocusable = false
            margin = JBUI.insets(2)
            addActionListener { showInlineInput() }
        }

        init {
            // Enter 确认、Escape 取消
            inlineInput.addActionListener { commitInlineInput() }
            inlineInput.addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                        hideInlineInput()
                    }
                }
            })
            rebuildChips()
        }

        fun getTags(): List<String> = tags.toList()

        fun setTags(list: List<String>) {
            tags.clear()
            tags.addAll(list.take(MAX_TAGS))
            rebuildChips()
        }

        /** 重建所有 chip + 内联面板 + 添加按钮 */
        private fun rebuildChips() {
            removeAll()
            // 添加已有标签 chip
            tags.forEachIndexed { index, tag ->
                add(createChip(tag, index))
            }
            // 添加按钮（未满时显示）
            if (tags.size < MAX_TAGS) {
                add(addButton)
            }
            // 内联输入行
            add(inlinePanel)
            revalidate()
            repaint()
        }

        /** 创建单个 chip 组件：文字 + "×" 按钮 */
        private fun createChip(text: String, index: Int): JComponent {
            val chip = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0)).apply {
                isOpaque = false
                border = ChipBorder()
            }
            val label = JBLabel(text).apply {
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            val removeButton = JButton(AllIcons.Actions.Close).apply {
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                margin = JBUI.insets(0)
                preferredSize = Dimension(14, 14)
                toolTipText = CodeSenseBundle.message("settings.action.delete")
                addActionListener {
                    if (index < tags.size) {
                        tags.removeAt(index)
                        rebuildChips()
                    }
                }
            }
            chip.add(label)
            chip.add(removeButton)
            return chip
        }

        private fun showInlineInput() {
            inlineInput.text = ""
            inlinePanel.isVisible = true
            addButton.isVisible = false
            revalidate()
            repaint()
            inlineInput.requestFocusInWindow()
        }

        private fun hideInlineInput() {
            inlinePanel.isVisible = false
            addButton.isVisible = tags.size < MAX_TAGS
            revalidate()
            repaint()
        }

        private fun commitInlineInput() {
            val value = inlineInput.text.trim()
            if (value.isNotEmpty() && value !in tags && tags.size < MAX_TAGS) {
                tags.add(value)
            }
            hideInlineInput()
            rebuildChips()
        }

        /** Chip 圆角边框（样式见 [TagChipStyle]） */
        private class ChipBorder : javax.swing.border.AbstractBorder() {
            override fun paintBorder(
                c: Component?, g: java.awt.Graphics?,
                x: Int, y: Int, width: Int, height: Int,
            ) {
                val g2 = g as java.awt.Graphics2D
                g2.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
                )
                // 灰色填充背景
                g2.color = TagChipStyle.background
                g2.fillRoundRect(x, y, width - 1, height - 1, TagChipStyle.ARC, TagChipStyle.ARC)
                // 边框线
                g2.color = JBColor.border()
                g2.drawRoundRect(x, y, width - 1, height - 1, TagChipStyle.ARC, TagChipStyle.ARC)
            }

            override fun getBorderInsets(c: Component?) =
                JBUI.insets(TagChipStyle.V_PAD, TagChipStyle.H_PAD, TagChipStyle.V_PAD, TagChipStyle.H_PAD)
        }
    }

    // ---- 编辑模型对话框 ----

    /**
     * 编辑模型对话框：修改模型/标签/API Key；提供商、类型、接口地址只读展示。
     */
    private class EditProviderDialog(
        parent: JComponent,
        private val initialConfig: AiProviderConfig,
        private val initialApiKey: String?,
        private val existingProviders: List<AiProviderConfig>,
    ) : DialogWrapper(parent, true) {

        private val modelCombo = JComboBox<String>().apply {
            isEditable = true
            minimumSize = Dimension(200, 30)
        }
        private val fetchModelButton = JButton(CodeSenseBundle.message("settings.editProvider.fetchModels")).apply {
            addActionListener { fetchModels() }
            minimumSize = Dimension(75, 30)
        }
        private val tagsPanel = TagsPanel()
        private val apiKeyField = JBPasswordField().apply {
            minimumSize = Dimension(200, 30)
        }
        private val testButton = JButton(CodeSenseBundle.message("settings.testConnection")).apply {
            addActionListener { testConnection() }
        }

        var resultConfig: AiProviderConfig? = null
            private set
        var resultApiKey: String? = null
            private set

        init {
            title = CodeSenseBundle.message("settings.editProvider.title")
            setOKButtonText(CodeSenseBundle.message("settings.addProvider.confirm"))
            setCancelButtonText(CodeSenseBundle.message("settings.addProvider.cancel"))
            loadInitial()
            init()
        }

        override fun createCenterPanel(): JComponent {
            // 提供商信息区只读展示（仅 API Key 可编辑）
            val providerValueLabel = JBLabel(initialConfig.displayName).apply {
                minimumSize = Dimension(200, preferredSize.height)
            }
            val planTypeValueLabel = JBLabel(planTypeLabel(initialConfig.planType)).apply {
                minimumSize = Dimension(200, preferredSize.height)
            }
            val baseUrlValueLabel = JBLabel(initialConfig.baseUrl).apply {
                minimumSize = Dimension(200, preferredSize.height)
            }

            // 模型下拉 + 「获取模型」按钮
            val modelRow = JPanel(BorderLayout(4, 0)).apply {
                add(modelCombo, BorderLayout.CENTER)
                add(fetchModelButton, BorderLayout.EAST)
            }

            // 标签提示（最多 N 个）
            val tagsHintLabel = JBLabel(CodeSenseBundle.message("settings.editProvider.tags.max", TagsPanel.MAX_TAGS.toString())).apply {
                font = font.deriveFont(Font.PLAIN, 10f)
                foreground = JBColor.GRAY
                border = JBUI.Borders.empty(2, 0, 0, 0)
            }

            val tagsWithHint = JPanel(BorderLayout()).apply {
                add(tagsPanel, BorderLayout.NORTH)
                add(tagsHintLabel, BorderLayout.SOUTH)
            }

            // 表单标签（设置最小宽度，避免窗口缩小时文字被挤压）
            val modelLabel = JLabel(SettingsConfigurable.requiredLabel("settings.editProvider.model")).apply {
                minimumSize = preferredSize
            }
            val tagsLabel = JLabel(CodeSenseBundle.message("settings.editProvider.tags")).apply {
                minimumSize = preferredSize
            }
            val providerLabel = JLabel(CodeSenseBundle.message("settings.provider")).apply {
                minimumSize = preferredSize
            }
            val planTypeLabel = JLabel(CodeSenseBundle.message("settings.planType")).apply {
                minimumSize = preferredSize
            }
            val baseUrlLabel = JLabel(CodeSenseBundle.message("settings.baseUrl")).apply {
                minimumSize = preferredSize
            }
            val apiKeyLabel = JLabel(CodeSenseBundle.message("settings.apiKey")).apply {
                minimumSize = preferredSize
            }

            // 区块标题：加粗灰色小字，视觉上把「提供商信息」与「模型信息」分成两区
            val providerSectionCaption = JBLabel(CodeSenseBundle.message("settings.editProvider.section.provider")).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
            }
            val modelSectionCaption = JBLabel(CodeSenseBundle.message("settings.editProvider.section.model")).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
            }

            // 表单分两区：提供商信息（提供商/类型/接口地址只读、API Key 可编辑）→ 模型信息（模型/标签）
            return FormBuilder.createFormBuilder()
                .addComponent(providerSectionCaption)
                .addVerticalGap(4)
                .addLabeledComponent(providerLabel, providerValueLabel)
                .addVerticalGap(6)
                .addLabeledComponent(planTypeLabel, planTypeValueLabel)
                .addVerticalGap(6)
                .addLabeledComponent(baseUrlLabel, baseUrlValueLabel)
                .addVerticalGap(6)
                .addLabeledComponent(apiKeyLabel, apiKeyField)
                .addVerticalGap(10)
                .addSeparator()
                .addVerticalGap(10)
                .addComponent(modelSectionCaption)
                .addVerticalGap(4)
                .addLabeledComponent(modelLabel, modelRow)
                .addVerticalGap(6)
                .addLabeledComponent(tagsLabel, tagsWithHint)
                .panel
        }

        /** 在底部按钮行「确定」左侧插入测试连接按钮，保持按钮间距与其他按钮一致 */
        override fun createSouthPanel(): JComponent {
            val south = super.createSouthPanel()
            val okButton = getButton(getOKAction()) ?: return south
            val container = okButton.parent ?: return south
            if (container.components.none { it === testButton }) {
                container.add(testButton, 0)
                // 与平台 BASE_BUTTON_GAP=12 的间距算法保持一致
                val insets = testButton.insets ?: JBUI.insets(0)
                val gap = JBUI.scale(12) - insets.left - insets.right
                if (gap > 0) {
                    container.add(javax.swing.Box.createRigidArea(Dimension(gap, 0)), 1)
                }
                container.revalidate()
            }
            return south
        }

        private fun loadInitial() {
            // 模型下拉初始仅含当前模型：不预置默认模型列表，用户可手动输入或点「获取模型」加载
            val currentModel = initialConfig.model.takeIf { it.isNotBlank() }
            modelCombo.model = DefaultComboBoxModel(
                if (currentModel != null) arrayOf(currentModel) else emptyArray(),
            )
            modelCombo.selectedItem = currentModel
            tagsPanel.setTags(initialConfig.tags)
            apiKeyField.text = initialApiKey ?: ""
        }

        /** 测试当前配置的连通性（后台发一条最小请求） */
        private fun testConnection() {
            val baseUrl = initialConfig.baseUrl
            val model = (modelCombo.editor.item as? String ?: "").trim()
            if (baseUrl.isEmpty() || model.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.config"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val key = String(apiKeyField.password)
            if (key.isBlank()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.key"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val provider = AiProviderConfig(
                id = "test",
                providerId = "test",
                displayName = initialConfig.displayName,
                baseUrl = baseUrl,
                model = model,
            )
            val startedAt = System.currentTimeMillis()
            val taskTitle = CodeSenseBundle.message("settings.test.progress", provider.displayName)
            object : Task.Backgroundable(null, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    val reply = try {
                        OpenAiCompatClient().chat(
                            provider, key,
                            listOf(ChatMessage("user", "Reply with just the word 'OK'.")),
                        )
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                contentPanel,
                                CodeSenseBundle.message("notification.testFail", e.message ?: ""),
                                CodeSenseBundle.message("settings.editProvider.title"),
                            )
                        }
                        return
                    }
                    val elapsed = System.currentTimeMillis() - startedAt
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            contentPanel,
                            CodeSenseBundle.message("notification.testSuccess", elapsed.toString(), reply.take(50)),
                            CodeSenseBundle.message("settings.editProvider.title"),
                        )
                    }
                }
            }.queue()
        }

        /** 通过 API（GET {baseUrl}/models）获取模型列表 */
        private fun fetchModels() {
            val baseUrl = initialConfig.baseUrl
            if (baseUrl.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.config"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val key = String(apiKeyField.password)
            if (key.isBlank()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.test.warn.key"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val provider = AiProviderConfig(
                id = "fetch",
                providerId = "fetch",
                displayName = initialConfig.displayName,
                baseUrl = baseUrl,
                model = "",
            )
            val taskTitle = CodeSenseBundle.message("settings.editProvider.fetchingModels")
            object : Task.Backgroundable(null, taskTitle, true) {
                override fun run(indicator: ProgressIndicator) {
                    val models = try {
                        OpenAiCompatClient().listModels(provider, key)
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showWarningDialog(
                                contentPanel,
                                CodeSenseBundle.message("settings.editProvider.fetchFail", e.message ?: ""),
                                CodeSenseBundle.message("settings.editProvider.title"),
                            )
                        }
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        // 加载新列表后保留用户已输入/选中的模型名：不在新列表中时编辑器仍显示该文本
                        val previous = (modelCombo.editor.item as? String ?: "").trim()
                        modelCombo.model = DefaultComboBoxModel(models.toTypedArray())
                        if (previous.isNotEmpty()) {
                            modelCombo.selectedItem = previous
                            modelCombo.editor.item = previous
                        }
                        Messages.showInfoMessage(
                            contentPanel,
                            CodeSenseBundle.message("settings.editProvider.fetchSuccess", models.size.toString()),
                            CodeSenseBundle.message("settings.editProvider.title"),
                        )
                    }
                }
            }.queue()
        }

        override fun doOKAction() {
            val model = (modelCombo.editor.item as? String ?: "").trim()
            if (model.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.editProvider.modelEmpty"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            // 「供应商 + 类型 + 模型」唯一性校验：与其它条目重复时阻止保存（供应商/类型源自只读的既有配置）
            val duplicate = existingProviders.any {
                it.id != initialConfig.id &&
                    ModelUniqueness.key(it.displayName, it.planType, it.model) ==
                    ModelUniqueness.key(initialConfig.displayName, initialConfig.planType, model)
            }
            if (duplicate) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message(
                        "settings.editProvider.duplicate",
                        initialConfig.displayName,
                        planTypeLabel(initialConfig.planType),
                        model,
                    ),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val tags = tagsPanel.getTags().toMutableList()
            resultConfig = initialConfig.copy(
                model = model,
                tags = tags,
            )
            resultApiKey = String(apiKeyField.password)
            super.doOKAction()
        }
    }

    // ---- 表格模型 ----

    private class ModelTableModel : AbstractTableModel() {

        private var providers: MutableList<AiProviderConfig> = mutableListOf()

        fun refresh(list: List<AiProviderConfig>) {
            providers = list.toMutableList()
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = providers.size

        override fun getColumnCount(): Int = 5

        override fun getColumnName(column: Int): String = when (column) {
            0 -> CodeSenseBundle.message("settings.table.model")
            1 -> CodeSenseBundle.message("settings.table.tags")
            2 -> CodeSenseBundle.message("settings.table.provider")
            3 -> CodeSenseBundle.message("settings.table.type")
            4 -> CodeSenseBundle.message("settings.table.actions")
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val p = providers.getOrNull(rowIndex) ?: return null
            return when (columnIndex) {
                0 -> p.model
                1 -> p
                2 -> p.displayName
                3 -> planTypeLabel(p.planType)
                4 -> p
                else -> null
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        fun getConfigAt(row: Int): AiProviderConfig? = providers.getOrNull(row)
    }

    // ---- 自定义表格：行不可选 + hover 高亮 + 操作列 tooltip ----

    private class ModelTable(
        model: ModelTableModel,
        private val actionRenderer: ActionCellRenderer,
    ) : JBTable(model) {

        var hoverRow: Int = -1
            private set

        init {
            rowSelectionAllowed = false
            cellSelectionEnabled = false
            columnSelectionAllowed = false
            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    if (row != hoverRow) {
                        hoverRow = row
                        repaint()
                    }
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseExited(e: MouseEvent) {
                    if (hoverRow != -1) {
                        hoverRow = -1
                        repaint()
                    }
                }
            })
        }

        override fun getToolTipText(e: MouseEvent): String? {
            val col = columnAtPoint(e.point)
            if (col == ACTION_COLUMN) {
                val row = rowAtPoint(e.point)
                val cellRect = getCellRect(row, col, true)
                val p = java.awt.Point(e.x - cellRect.x, e.y - cellRect.y)
                return when {
                    actionRenderer.editBounds.contains(p) -> CodeSenseBundle.message("settings.action.edit")
                    actionRenderer.deleteBounds.contains(p) -> CodeSenseBundle.message("settings.action.delete")
                    else -> null
                }
            }
            return super.getToolTipText(e)
        }
    }

    // ---- 数据列渲染器（hover 高亮） ----

    private class GrayRowRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            // 去掉单元格聚焦/选中时的边框（用户看到的选择框）
            (c as JComponent).border = null
            val mt = table as? ModelTable
            val hover = mt?.hoverRow == row
            // hover 高亮背景（行不可选，无选中背景）
            c.background = if (hover) table.selectionBackground else table.background
            c.foreground = table.foreground
            return c
        }
    }

    // ---- 标签列渲染器（chip 圆角边框样式 + 超宽截断省略号） ----

    /**
     * 标签列渲染器：把标签逐个绘制为带圆角边框的 chip。
     * - 无标签：单元格留空；
     * - 放不下：截断并绘制「…」，悬停时 tooltip 展示完整标签；
     * - hover 行：单元格背景高亮（与其它列一致）。
     */
    private class TagsCellRenderer : JComponent(), TableCellRenderer {

        private var tags: List<String> = emptyList()
        private var hover = false
        private var cellBackground: java.awt.Color = JBColor.background()
        private var cellForeground: java.awt.Color = JBColor.foreground()

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int,
        ): Component {
            val config = value as? AiProviderConfig
            tags = config?.tags ?: emptyList()
            val mt = table as? ModelTable
            hover = mt?.hoverRow == row
            cellBackground = if (hover) table.selectionBackground else table.background
            cellForeground = table.foreground
            // 与编辑弹窗 chip 字号保持一致。
            // 渲染器组件不在组件层级树中，getFont() 首帧可能为 null，需安全回退到表格字体。
            val baseFont = font ?: table.font ?: java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            font = baseFont.deriveFont(11f)
            // 截断时 tooltip 展示完整标签
            val cellWidth = table.getCellRect(row, column, false).width
            toolTipText = if (tags.isNotEmpty() && wouldTruncate(cellWidth)) {
                tags.joinToString(", ")
            } else {
                null
            }
            return this
        }

        override fun paintComponent(g: java.awt.Graphics) {
            val g2 = g as java.awt.Graphics2D
            g2.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
            )
            // 单元格背景：hover 高亮（行不可选，无选中背景）
            g2.color = cellBackground
            g2.fillRect(0, 0, width, height)
            if (tags.isEmpty()) return

            val fm = g2.fontMetrics
            val chipH = fm.maxAscent + fm.maxDescent + TagChipStyle.V_PAD * 2
            val y = (height - chipH) / 2
            var x = LEFT_INSET
            tags.forEachIndexed { index, tag ->
                val chipW = fm.stringWidth(tag) + TagChipStyle.H_PAD * 2
                if (x + chipW > width) {
                    // 放不下剩余 chip：空间足够时补一个省略号
                    if (index > 0 && x + fm.stringWidth("\u2026") + ELLIPSIS_GAP <= width) {
                        g2.color = cellForeground
                        g2.drawString("\u2026", x + ELLIPSIS_GAP, textBaseline(y, chipH, fm))
                    }
                    return
                }
                // chip 背景 + 圆角边框
                g2.color = TagChipStyle.background
                g2.fillRoundRect(x, y, chipW - 1, chipH - 1, TagChipStyle.ARC, TagChipStyle.ARC)
                g2.color = JBColor.border()
                g2.drawRoundRect(x, y, chipW - 1, chipH - 1, TagChipStyle.ARC, TagChipStyle.ARC)
                // 文本垂直居中
                g2.color = cellForeground
                g2.drawString(tag, x + TagChipStyle.H_PAD, textBaseline(y, chipH, fm))
                x += chipW + CHIP_GAP
            }
        }

        /** 文本基线：在 chip 内垂直居中 */
        private fun textBaseline(chipTop: Int, chipHeight: Int, fm: java.awt.FontMetrics): Int =
            chipTop + (chipHeight - (fm.maxAscent + fm.maxDescent)) / 2 + fm.maxAscent

        /** 按单元格宽度估算是否需要截断（与 paintComponent 用同一套度量） */
        private fun wouldTruncate(cellWidth: Int): Boolean {
            val fm = getFontMetrics(font)
            var x = LEFT_INSET
            tags.forEach { tag ->
                val chipW = fm.stringWidth(tag) + TagChipStyle.H_PAD * 2
                if (x + chipW > cellWidth) return true
                x += chipW + CHIP_GAP
            }
            return false
        }

        companion object {
            /** 首枚 chip 距单元格左缘的间距 */
            private const val LEFT_INSET = 4

            /** chip 之间的横向间距 */
            private const val CHIP_GAP = 4

            /** 省略号与前一 chip 的间距 */
            private const val ELLIPSIS_GAP = 6
        }
    }

    // ---- 操作列渲染器（图标按钮 + hover 高亮） ----

    private class ActionCellRenderer : TableCellRenderer {
        private val panel = JPanel(FlowLayout(FlowLayout.LEFT, ACTION_GAP, 4))
        private val editButton = JButton()
        private val deleteButton = JButton()

        private val editIcon = AllIcons.Actions.Edit
        private val deleteIcon = AllIcons.General.Delete

        // 最近一次渲染时各按钮在单元格内的实际 bounds（用于精确命中判断）
        var editBounds: Rectangle = Rectangle()
        var deleteBounds: Rectangle = Rectangle()

        init {
            editButton.preferredSize = Dimension(ACTION_ICON_W, 22)
            deleteButton.preferredSize = Dimension(ACTION_ICON_W, 22)
            editButton.isFocusable = false
            deleteButton.isFocusable = false
            editButton.isContentAreaFilled = false
            deleteButton.isContentAreaFilled = false
            editButton.isBorderPainted = false
            deleteButton.isBorderPainted = false
            panel.add(editButton)
            panel.add(deleteButton)
        }

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int,
        ): Component {
            editButton.icon = editIcon
            deleteButton.icon = deleteIcon
            // hover 高亮背景
            val mt = table as? ModelTable
            val hover = mt?.hoverRow == row
            panel.background = if (hover) table.selectionBackground else table.background
            panel.isOpaque = true
            // 触发布局并记录按钮实际 bounds
            panel.size = table.getCellRect(row, column, true).size
            panel.doLayout()
            editBounds = editButton.bounds
            deleteBounds = deleteButton.bounds
            return panel
        }
    }

    // ---- 渲染器 ----

    /** 添加模型弹窗中的可勾选模型条目 */
    private data class CheckableModel(
        val name: String,
        var selected: Boolean,
    )

    /** 复选框模型条目渲染器 */
    private class CheckboxModelRenderer : ListCellRenderer<CheckableModel> {
        private val checkbox = JCheckBox()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out CheckableModel>?, value: CheckableModel, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            checkbox.text = value.name
            checkbox.isSelected = value.selected
            checkbox.background = if (isSelected) list?.selectionBackground else list?.background
            checkbox.isOpaque = true
            checkbox.border = JBUI.Borders.empty(2, 8)
            return checkbox
        }
    }

    private class PlanRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            val text = when (val type = value as? ProviderPlanType) {
                null -> value.toString()
                else -> planTypeLabel(type)
            }
            delegate.text = text
            if (isSelected) {
                delegate.background = list.selectionBackground
                delegate.foreground = list.selectionForeground
            } else {
                delegate.background = list.background
                delegate.foreground = list.foreground
            }
            delegate.isOpaque = true
            return delegate
        }
    }

    /** 选择提供商下拉渲染器（自定义提供商带「（自定义）」标记） */
    private class ProviderOptionRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            val option = value as? ProviderOption
            delegate.text = when {
                option == null -> value.toString()
                option.isCustom -> "${option.displayName}（自定义）"
                else -> option.displayName
            }
            if (isSelected) {
                delegate.background = list.selectionBackground
                delegate.foreground = list.selectionForeground
            } else {
                delegate.background = list.background
                delegate.foreground = list.foreground
            }
            delegate.isOpaque = true
            return delegate
        }
    }

    companion object {
        private const val TAGS_COLUMN = 1
        private const val TYPE_COLUMN = 3
        private const val ACTION_COLUMN = 4
        private const val ACTION_GAP = 8
        private const val ACTION_ICON_W = 26

        // 列固定宽度
        private const val COL_MODEL_W = 200
        private const val COL_TYPE_W = 100
        private const val COL_TAGS_W = 140
        private const val COL_PROVIDER_W = 130
        private const val COL_ACTION_W = 68

        // 表格固定尺寸
        private const val TABLE_WIDTH = 638
        private const val TABLE_HEIGHT = 200

        /** 打开设置页（供外部跳转） */
        fun open(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
        }

        /** 必填项 label 的 HTML 文本：文本末尾追加红色星号 */
        fun requiredLabel(key: String): String =
            "<html>${CodeSenseBundle.message(key)}<font color='red'>*</font></html>"
    }
}