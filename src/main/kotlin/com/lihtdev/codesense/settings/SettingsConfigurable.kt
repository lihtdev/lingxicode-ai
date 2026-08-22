package com.lihtdev.codesense.settings

import com.intellij.ide.plugins.PluginManagerCore
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

/**
 * 设置页（Tools → CodeSense AI）。
 *
 * 布局：顶部品牌区 → 模型表格（显示名称/模型/标签/提供商/操作）→ 底部全局设置。
 * 编辑、删除、启用停用均在表格操作列完成；添加经「添加模型」弹窗批量选择。
 */
class SettingsConfigurable : Configurable {

    private val settings = AppSettings.instance

    /** 工作副本（应用前不落盘） */
    private lateinit var workingProviders: MutableList<AiProviderConfig>
    private var workingActiveId: String = ""
    private val workingApiKeys = mutableMapOf<String, String?>()

    // ---- UI 组件 ----
    private val tableModel = ModelTableModel()
    private val table = JBTable(tableModel)
    private val actionRenderer = ActionCellRenderer()

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
        // 表格配置
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.rowHeight = 30
        table.tableHeader.reorderingAllowed = false
        table.columnModel.getColumn(0).preferredWidth = 150
        table.columnModel.getColumn(1).preferredWidth = 160
        table.columnModel.getColumn(2).preferredWidth = 140
        table.columnModel.getColumn(3).preferredWidth = 120
        table.columnModel.getColumn(4).preferredWidth = 190
        table.columnModel.getColumn(4).cellRenderer = actionRenderer
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

    /** 顶部品牌区：Logo + 英文名 + 中文名 + 版本/作者横向排列 */
    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 12, 0)

        val rawIcon = IconLoader.getIcon("/icons/codesense.svg", SettingsConfigurable::class.java)
        val iconSize = 72
        val iconLabel = JLabel(rawIcon).apply {
            border = JBUI.Borders.emptyRight(16)
            minimumSize = Dimension(iconSize, iconSize)
            preferredSize = Dimension(iconSize, iconSize)
            maximumSize = Dimension(iconSize, iconSize)
        }

        val textPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        }

        val englishLabel = JLabel(CodeSenseBundle.message("settings.header.englishName")).apply {
            font = font.deriveFont(Font.BOLD, 20f)
        }
        val chineseLabel = JLabel(CodeSenseBundle.message("settings.header.chineseName")).apply {
            font = font.deriveFont(Font.PLAIN, 18f)
            foreground = JBColor.GRAY
        }

        val version = try {
            PluginManagerCore.getPlugin(PluginId.getId("com.lihtdev.codesense"))?.version ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
        val metaLabel = JLabel(
            "${CodeSenseBundle.message("settings.header.version", version)}  |  ${CodeSenseBundle.message("settings.header.author")}",
        ).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = JBColor.GRAY
        }

        textPanel.add(englishLabel)
        textPanel.add(chineseLabel)
        textPanel.add(javax.swing.Box.createVerticalStrut(2))
        textPanel.add(metaLabel)

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

        val scrollPane = javax.swing.JScrollPane(table).apply {
            preferredSize = Dimension(760, 260)
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
            || workingActiveId != current.activeProviderId
            || appliedOutputLanguage != current.outputLanguage
            || appliedUiLanguage != current.uiLanguage
            || (maxDiffField.value as Int) != current.maxDiffChars
            || workingApiKeys.any { (id, key) -> AppSettings.getApiKey(id) != key }
    }

    override fun apply() {
        val state = settings.state
        state.providers = workingProviders.map { it.copy() }.toMutableList()
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

    /** 弹出添加模型对话框（批量勾选预设/API 获取的模型） */
    private fun showAddProviderDialog() {
        val dialog = AddProviderDialog(mainPanel)
        if (dialog.showAndGet()) {
            val configs = dialog.resultConfigs
            if (configs.isEmpty()) return
            configs.forEach { config ->
                workingProviders.add(config)
                if (dialog.apiKey.isNotBlank()) {
                    workingApiKeys[config.providerId] = dialog.apiKey
                }
            }
            if (workingActiveId.isBlank() && configs.firstOrNull()?.enabled == true) {
                workingActiveId = configs.first().id
            }
            refreshTable()
        }
    }

    /** 编辑选中模型条目 */
    private fun editProvider(config: AiProviderConfig) {
        val dialog = EditProviderDialog(mainPanel, config, workingApiKeys[config.providerId])
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
            workingActiveId = workingProviders.firstOrNull { it.enabled }?.id ?: ""
        }
        refreshTable()
    }

    /** 启用 / 停用 */
    private fun toggleEnabled(config: AiProviderConfig) {
        config.enabled = !config.enabled
        if (!config.enabled && workingActiveId == config.id) {
            workingActiveId = workingProviders.firstOrNull { it.enabled }?.id ?: ""
        }
        refreshTable()
    }

    /** 处理表格操作列点击 */
    private fun handleTableClick(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        if (row < 0 || col != ACTION_COLUMN) return
        val config = workingProviders.getOrNull(row) ?: return
        val cellRect = table.getCellRect(row, col, true)
        val x = e.x - cellRect.x
        when {
            x < ACTION_EDIT_W -> editProvider(config)
            x < ACTION_EDIT_W + ACTION_GAP + ACTION_DELETE_W -> removeProvider(config)
            else -> toggleEnabled(config)
        }
    }

    // ---- 添加模型对话框 ----

    /**
     * 添加模型对话框：批量勾选预设模型或通过 API 获取的模型列表。
     */
    private class AddProviderDialog(parent: JComponent) : DialogWrapper(parent, true) {

        private val presetCombo = JComboBox<ProviderPreset>().apply {
            renderer = PresetRenderer()
            // 第一项为「自定义」
            addItem(
                ProviderPreset(
                    id = "__custom__",
                    displayName = "\u2014 \u81EA\u5B9A\u4E49 \u2014",
                    models = emptyList(),
                    plans = listOf(PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "")),
                ),
            )
            ProviderPresets.ALL.forEach { addItem(it) }
            addActionListener { onPresetSelected() }
        }

        private val nameField = JTextField(30)
        private val planTypeCombo = JComboBox<ProviderPlanType>()
        private val baseUrlField = JTextField(30)
        private val apiKeyField = JBPasswordField()

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
        private val manualModelField = JTextField(18)

        /** 程序化加载期间为 true，防止监听器误触发 */
        private var isLoading = false

        var resultConfigs: List<AiProviderConfig> = emptyList()
            private set
        var apiKey: String = ""
            private set

        init {
            title = CodeSenseBundle.message("settings.addProvider.title")
            planTypeCombo.renderer = PlanRenderer()
            planTypeCombo.addActionListener { onPlanTypeChanged() }
            setOKButtonText(CodeSenseBundle.message("settings.addProvider.confirm"))
            setCancelButtonText(CodeSenseBundle.message("settings.addProvider.cancel"))
            onPresetSelected()
            init()
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

            val hintLabel = JBLabel(CodeSenseBundle.message("settings.addProvider.hint")).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(Font.PLAIN, 12f)
                border = JBUI.Borders.empty(0, 0, 8, 0)
            }

            val fetchButton = JButton(CodeSenseBundle.message("settings.addProvider.fetchModels")).apply {
                addActionListener { fetchModels() }
            }
            val selectAllButton = JButton(CodeSenseBundle.message("settings.addProvider.selectAll")).apply {
                addActionListener { toggleSelectAll() }
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

            return FormBuilder.createFormBuilder()
                .addComponent(hintLabel)
                .addSeparator()
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.addProvider.template")), presetCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.provider")), nameField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.planType")), planTypeCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.baseUrl")), baseUrlWithNote)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.apiKey")), apiKeyField)
                .addSeparator()
                .addComponent(JLabel(CodeSenseBundle.message("settings.addProvider.selectModels")).apply {
                    font = font.deriveFont(Font.BOLD, 12f)
                })
                .addComponent(buttonRow)
                .addComponent(listScroll)
                .addComponent(manualRow)
                .panel
        }

        private fun onPresetSelected() {
            val preset = presetCombo.selectedItem as? ProviderPreset ?: return
            isLoading = true
            try {
                if (preset.id == "__custom__") {
                    nameField.text = ""
                    val types = arrayOf(ProviderPlanType.PAY_AS_YOU_GO)
                    planTypeCombo.model = DefaultComboBoxModel(types)
                    baseUrlField.text = ""
                    setModelItems(emptyList(), selected = false)
                } else {
                    nameField.text = preset.displayName
                    val planTypes = preset.plans.map { it.type }.distinct().toTypedArray()
                    planTypeCombo.model = DefaultComboBoxModel(planTypes)
                    planTypeCombo.selectedItem = planTypes.first()
                    baseUrlField.text = preset.plans.first().baseUrl
                    setModelItems(preset.models, selected = true)
                }
            } finally {
                isLoading = false
            }
        }

        /** 切换类型：自动带出该类型对应的 baseUrl */
        private fun onPlanTypeChanged() {
            if (isLoading) return
            val preset = presetCombo.selectedItem as? ProviderPreset ?: return
            if (preset.id == "__custom__") return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: return
            preset.plans.firstOrNull { it.type == planType }?.let { plan ->
                baseUrlField.text = plan.baseUrl
            }
        }

        /** 填充模型复选框列表 */
        private fun setModelItems(models: List<String>, selected: Boolean) {
            modelListModel.removeAllElements()
            models.forEach { modelListModel.addElement(CheckableModel(it, selected)) }
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
            manualModelField.text = ""
        }

        /** 通过 API（GET {baseUrl}/models）获取模型列表 */
        private fun fetchModels() {
            val baseUrl = baseUrlField.text.trim()
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
                displayName = nameField.text.trim(),
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
            val name = nameField.text.trim()
            if (name.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.addProvider.name.empty"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val selectedModels = (0 until modelListModel.size())
                .map { modelListModel.getElementAt(it) }
                .filter { it.selected }
            if (selectedModels.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.addProvider.noModels"),
                    CodeSenseBundle.message("settings.addProvider.title"),
                )
                return
            }
            val preset = presetCombo.selectedItem as? ProviderPreset
            val providerId = if (preset != null && preset.id != "__custom__") preset.id else "custom-${UUID.randomUUID()}"
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: ProviderPlanType.PAY_AS_YOU_GO
            val baseUrl = baseUrlField.text.trim()
            val seen = mutableSetOf<String>()
            resultConfigs = selectedModels.map { m ->
                var id = "$providerId:${m.name}"
                if (!seen.add(id)) id = "$providerId:${m.name}:${UUID.randomUUID()}"
                AiProviderConfig(
                    id = id,
                    providerId = providerId,
                    displayName = name,
                    planType = planType,
                    baseUrl = baseUrl,
                    model = m.name,
                )
            }
            apiKey = String(apiKeyField.password)
            super.doOKAction()
        }
    }

    // ---- 编辑模型对话框 ----

    /**
     * 编辑模型对话框：针对单个模型条目修改显示名称/模型/标签/提供商/套餐/baseUrl/apiKey/启用态。
     */
    private class EditProviderDialog(
        parent: JComponent,
        private val initialConfig: AiProviderConfig,
        private val initialApiKey: String?,
    ) : DialogWrapper(parent, true) {

        private val modelDisplayNameField = JTextField(30)
        private val modelCombo = JComboBox<String>().apply { isEditable = true }
        private val tagsField = JTextField(30)
        private val providerNameField = JTextField(30)
        private val planTypeCombo = JComboBox<ProviderPlanType>()
        private val baseUrlField = JTextField(30)
        private val apiKeyField = JBPasswordField()
        private val enabledCheckbox = JCheckBox(CodeSenseBundle.message("settings.editProvider.enabled"))

        var resultConfig: AiProviderConfig? = null
            private set
        var resultApiKey: String? = null
            private set

        init {
            title = CodeSenseBundle.message("settings.editProvider.title")
            planTypeCombo.renderer = PlanRenderer()
            planTypeCombo.addActionListener { onPlanTypeChanged() }
            setOKButtonText(CodeSenseBundle.message("settings.addProvider.confirm"))
            setCancelButtonText(CodeSenseBundle.message("settings.addProvider.cancel"))
            loadInitial()
            init()
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

            val testButton = JButton(CodeSenseBundle.message("settings.testConnection")).apply {
                addActionListener { testConnection() }
            }

            return FormBuilder.createFormBuilder()
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.editProvider.displayName")), modelDisplayNameField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.editProvider.model")), modelCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.editProvider.tags")), tagsField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.provider")), providerNameField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.planType")), planTypeCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.baseUrl")), baseUrlWithNote)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.apiKey")), apiKeyField)
                .addComponent(enabledCheckbox)
                .addComponent(testButton)
                .panel
        }

        private fun loadInitial() {
            modelDisplayNameField.text = initialConfig.modelDisplayName
            val preset = ProviderPresets.byId(initialConfig.providerId)
            val planTypes = preset?.plans?.map { it.type }?.distinct()
                ?: listOf(ProviderPlanType.PAY_AS_YOU_GO)
            planTypeCombo.model = DefaultComboBoxModel(planTypes.toTypedArray())
            planTypeCombo.selectedItem = if (planTypes.contains(initialConfig.planType)) initialConfig.planType else planTypes.first()
            baseUrlField.text = initialConfig.baseUrl
            val models = (preset?.models ?: emptyList()).toMutableSet()
            if (initialConfig.model.isNotBlank()) models.add(initialConfig.model)
            modelCombo.model = DefaultComboBoxModel(models.toTypedArray())
            modelCombo.selectedItem = initialConfig.model
            tagsField.text = initialConfig.tags.joinToString(", ")
            providerNameField.text = initialConfig.displayName
            apiKeyField.text = initialApiKey ?: ""
            enabledCheckbox.isSelected = initialConfig.enabled
        }

        /** 切换类型：自动带出该类型对应的 baseUrl */
        private fun onPlanTypeChanged() {
            val preset = ProviderPresets.byId(initialConfig.providerId) ?: return
            val planType = planTypeCombo.selectedItem as? ProviderPlanType ?: return
            preset.plans.firstOrNull { it.type == planType }?.let { plan ->
                baseUrlField.text = plan.baseUrl
            }
        }

        /** 测试当前配置的连通性（后台发一条最小请求） */
        private fun testConnection() {
            val baseUrl = baseUrlField.text.trim()
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
                displayName = providerNameField.text.trim(),
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

        override fun doOKAction() {
            val model = (modelCombo.editor.item as? String ?: "").trim()
            val providerName = providerNameField.text.trim()
            if (providerName.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.addProvider.name.empty"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            if (model.isEmpty()) {
                Messages.showWarningDialog(
                    contentPanel,
                    CodeSenseBundle.message("settings.editProvider.modelEmpty"),
                    CodeSenseBundle.message("settings.editProvider.title"),
                )
                return
            }
            val tags = tagsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            resultConfig = initialConfig.copy(
                modelDisplayName = modelDisplayNameField.text.trim(),
                model = model,
                tags = tags,
                displayName = providerName,
                planType = planTypeCombo.selectedItem as? ProviderPlanType ?: ProviderPlanType.PAY_AS_YOU_GO,
                baseUrl = baseUrlField.text.trim(),
                enabled = enabledCheckbox.isSelected,
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
            0 -> CodeSenseBundle.message("settings.table.displayName")
            1 -> CodeSenseBundle.message("settings.table.model")
            2 -> CodeSenseBundle.message("settings.table.tags")
            3 -> CodeSenseBundle.message("settings.table.provider")
            4 -> CodeSenseBundle.message("settings.table.actions")
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val p = providers.getOrNull(rowIndex) ?: return null
            return when (columnIndex) {
                0 -> p.modelDisplayName.ifBlank { p.model }
                1 -> p.model
                2 -> p.tags.joinToString(", ")
                3 -> p.displayName
                4 -> p
                else -> null
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    // ---- 操作列渲染器 ----

    private class ActionCellRenderer : TableCellRenderer {
        private val panel = JPanel(FlowLayout(FlowLayout.LEFT, ACTION_GAP, 3))
        private val editButton = JButton()
        private val deleteButton = JButton()
        private val toggleButton = JButton()

        init {
            panel.add(editButton)
            panel.add(deleteButton)
            panel.add(toggleButton)
        }

        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean,
            row: Int, column: Int,
        ): Component {
            val config = value as? AiProviderConfig
            editButton.text = CodeSenseBundle.message("settings.action.edit")
            deleteButton.text = CodeSenseBundle.message("settings.action.delete")
            toggleButton.text = if (config?.enabled == true) {
                CodeSenseBundle.message("settings.action.disable")
            } else {
                CodeSenseBundle.message("settings.action.enable")
            }
            editButton.preferredSize = Dimension(ACTION_EDIT_W, 22)
            deleteButton.preferredSize = Dimension(ACTION_DELETE_W, 22)
            toggleButton.preferredSize = Dimension(ACTION_TOGGLE_W, 22)
            panel.background = if (isSelected) table?.selectionBackground else table?.background
            panel.isOpaque = true
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
            val text = when (value as? ProviderPlanType) {
                ProviderPlanType.TOKEN_PLAN -> CodeSenseBundle.message("provider.type.tokenPlan")
                ProviderPlanType.CODING_PLAN -> CodeSenseBundle.message("provider.type.codingPlan")
                ProviderPlanType.PAY_AS_YOU_GO -> CodeSenseBundle.message("provider.type.payAsYouGo")
                else -> value.toString()
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

    /** 预设提供商渲染器 */
    private class PresetRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.text = (value as? ProviderPreset)?.displayName ?: value.toString()
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
        private const val ACTION_COLUMN = 4
        private const val ACTION_GAP = 6
        private const val ACTION_EDIT_W = 48
        private const val ACTION_DELETE_W = 48
        private const val ACTION_TOGGLE_W = 64

        /** 打开设置页（供外部跳转） */
        fun open(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
        }
    }
}