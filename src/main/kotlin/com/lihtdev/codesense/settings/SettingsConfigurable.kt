package com.lihtdev.codesense.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.lihtdev.codesense.ai.AiClientException
import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.ai.OpenAiCompatClient
import com.lihtdev.codesense.i18n.CodeSenseBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * 设置页（Tools → CodeSense AI）。
 *
 * 布局：顶部品牌区 → 中部主从列表（用户厂商列表 + 详情编辑）→ 底部全局设置。
 * 预设厂商仅作为「添加厂商」弹窗中的模板，不直接出现在列表中。
 */
class SettingsConfigurable : Configurable {

    private val settings = AppSettings.instance

    /** 工作副本（应用前不落盘） */
    private lateinit var workingProviders: MutableList<AiProviderConfig>
    private var workingActiveId: String = ""
    private val workingApiKeys = mutableMapOf<String, String?>()

    /** 程序化加载期间为 true，防止监听器把未就绪的 UI 字段反写进工作副本 */
    private var isLoading = false

    // ---- UI 组件 ----
    // 厂商列表
    private val providerListModel = CollectionListModel<AiProviderConfig>()
    private val providerList = JBList(providerListModel)

    // 详情编辑区
    private val planCombo = JComboBox<ProviderPlanType>()
    private val baseUrlField = JTextField()
    private val modelCombo = JComboBox<String>()
    private val apiKeyField = JBPasswordField()
    private val detailPanel: JPanel

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
        // 厂商列表外观
        providerList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        providerList.cellRenderer = ProviderListRenderer()
        providerList.addListSelectionListener {
            if (!isLoading && !it.valueIsAdjusting) {
                onProviderSelected()
            }
        }

        // 类型下拉
        planCombo.renderer = PlanRenderer()
        planCombo.addActionListener { onPlanChanged() }

        // 模型可编辑下拉
        modelCombo.isEditable = true

        // 详情编辑区面板
        detailPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.planType")), planCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.baseUrl")), baseUrlField)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.model")), modelCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.apiKey")), apiKeyField)
            .addComponent(createDetailButtonPanel())
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 组装主面板
        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(createHeaderPanel())
            .addSeparator()
            .addComponent(createMasterDetailPanel())
            .addSeparator()
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.outputLanguage")), outputLanguageCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.uiLanguage")), uiLanguageCombo)
            .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.maxDiff")), maxDiffField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
    }

    // ---- 子面板构建 ----

    /** 顶部品牌区：Logo + 英文名 + 中文名 + 版本号 + 作者 */
    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 12, 0)

        // 图标（使用 IconLoader 加载 SVG，设置较大尺寸）
        val rawIcon = IconLoader.getIcon("/icons/codesense.svg", SettingsConfigurable::class.java)
        val iconLabel = JLabel(rawIcon).apply {
            border = JBUI.Borders.emptyRight(16)
            // 通过设置 preferredSize 放大图标显示
            val size = 56
            minimumSize = java.awt.Dimension(size, size)
            preferredSize = java.awt.Dimension(size, size)
            maximumSize = java.awt.Dimension(size, size)
        }

        // 文字区域
        val textPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        }

        val englishLabel = JLabel(CodeSenseBundle.message("settings.header.englishName")).apply {
            font = font.deriveFont(Font.BOLD, 20f)
        }
        val chineseLabel = JLabel(CodeSenseBundle.message("settings.header.chineseName")).apply {
            font = font.deriveFont(Font.PLAIN, 14f)
            foreground = JBColor.GRAY
        }

        // 版本号
        val version = try {
            PluginManagerCore.getPlugin(PluginId.getId("com.lihtdev.codesense"))?.version ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
        val versionLabel = JLabel(CodeSenseBundle.message("settings.header.version", version)).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        // 作者
        val authorLabel = JLabel(CodeSenseBundle.message("settings.header.author")).apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = JBColor.GRAY
        }

        textPanel.add(englishLabel)
        textPanel.add(chineseLabel)
        textPanel.add(javax.swing.Box.createVerticalStrut(4))
        textPanel.add(versionLabel)
        textPanel.add(authorLabel)

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(iconLabel)
            add(textPanel)
        }

        panel.add(leftPanel, BorderLayout.WEST)
        return panel
    }

    /** 主从布局：左侧用户厂商列表 + 右侧详情编辑 */
    private fun createMasterDetailPanel(): JPanel {
        val listTitle = JLabel(CodeSenseBundle.message("settings.providerList.title")).apply {
            font = Font(font.name, Font.BOLD, font.size)
        }

        val addButton = JButton(CodeSenseBundle.message("settings.addProvider")).apply {
            addActionListener { showAddProviderDialog() }
        }
        val deleteButton = JButton(CodeSenseBundle.message("settings.deleteProvider")).apply {
            addActionListener { removeCurrentProvider() }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(addButton)
            add(deleteButton)
        }

        val listScrollPane = javax.swing.JScrollPane(providerList).apply {
            preferredSize = java.awt.Dimension(180, 220)
        }

        val leftPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyRight(12)
            add(listTitle, BorderLayout.NORTH)
            add(listScrollPane, BorderLayout.CENTER)
            add(buttonRow, BorderLayout.SOUTH)
        }

        val rightPanel = JPanel(BorderLayout()).apply {
            add(detailPanel, BorderLayout.CENTER)
        }

        val splitPanel = JPanel(BorderLayout()).apply {
            add(leftPanel, BorderLayout.WEST)
            add(rightPanel, BorderLayout.CENTER)
        }
        return splitPanel
    }

    /** 详情编辑区按钮行 */
    private fun createDetailButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton(CodeSenseBundle.message("settings.testConnection")).apply {
                addActionListener { testConnection() }
            })
        }
    }

    // ---- Configurable 实现 ----

    override fun getDisplayName(): String = CodeSenseBundle.message("settings.displayName")

    override fun createComponent(): JComponent = mainPanel

    override fun isModified(): Boolean {
        syncUiToWorking()
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
        syncUiToWorking()
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
        workingProviders.forEach { workingApiKeys[it.id] = AppSettings.getApiKey(it.id) }
        outputLanguageCombo.selectedIndex = if (state.outputLanguage == "en") 1 else 0
        uiLanguageCombo.selectedIndex = if (state.uiLanguage == "en") 1 else 0
        maxDiffField.value = state.maxDiffChars
        refreshProviderList()
    }

    // ---- 内部逻辑 ----

    /** 刷新厂商列表并选中当前生效厂商 */
    private fun refreshProviderList() {
        isLoading = true
        try {
            providerListModel.removeAll()
            workingProviders.forEach { providerListModel.add(it) }
            val selected = workingProviders.firstOrNull { it.id == workingActiveId } ?: workingProviders.firstOrNull()
            if (selected != null) {
                providerList.setSelectedValue(selected, true)
            }
            loadFieldsForSelected()
        } finally {
            isLoading = false
        }
    }

    private fun onProviderSelected() {
        if (isLoading) return
        syncUiToWorking()
        val selected = providerList.selectedValue ?: return
        workingActiveId = selected.id
        loadFieldsForSelected()
    }

    /** 将当前 UI 字段同步回工作副本中选中的厂商 */
    private fun syncUiToWorking() {
        val selected = providerList.selectedValue ?: return
        selected.baseUrl = baseUrlField.text.trim()
        selected.model = (modelCombo.editor.item as? String ?: "").trim()
        selected.planType = planCombo.selectedItem as? ProviderPlanType ?: selected.planType
        workingApiKeys[selected.id] = String(apiKeyField.password)
    }

    /** 依据选中厂商加载类型/baseUrl/model/apiKey 字段 */
    private fun loadFieldsForSelected() {
        val selected = providerList.selectedValue ?: return
        isLoading = true
        try {
            val preset = ProviderPresets.byId(selected.id)
            val planTypes = preset?.plans?.map { it.type }?.distinct()
                ?: listOf(ProviderPlanType.PAY_AS_YOU_GO)
            planCombo.model = DefaultComboBoxModel(planTypes.toTypedArray())
            planCombo.selectedItem = if (planTypes.contains(selected.planType)) selected.planType else planTypes.first()
            baseUrlField.text = selected.baseUrl
            val models = (preset?.models ?: emptyList()).toMutableSet()
            if (selected.model.isNotBlank()) models.add(selected.model)
            modelCombo.model = DefaultComboBoxModel(models.toTypedArray())
            modelCombo.selectedItem = selected.model
            apiKeyField.text = workingApiKeys[selected.id] ?: ""
        } finally {
            isLoading = false
        }
    }

    /** 切换类型：预设厂商自动带出该类型对应的 baseUrl */
    private fun onPlanChanged() {
        if (isLoading) return
        val selected = providerList.selectedValue ?: return
        val planType = planCombo.selectedItem as? ProviderPlanType ?: return
        selected.planType = planType
        val preset = ProviderPresets.byId(selected.id) ?: return
        preset.plans.firstOrNull { it.type == planType }?.let { plan ->
            baseUrlField.text = plan.baseUrl
        }
    }

    /** 弹出添加厂商对话框（预设厂商作为模板选择） */
    private fun showAddProviderDialog() {
        val dialog = AddProviderDialog(mainPanel)
        if (dialog.showAndGet()) {
            val config = dialog.resultConfig ?: return
            workingProviders.add(config)
            workingApiKeys[config.id] = null
            refreshProviderList()
            providerList.setSelectedValue(config, true)
            loadFieldsForSelected()
        }
    }

    /** 删除当前选中的厂商 */
    private fun removeCurrentProvider() {
        val selected = providerList.selectedValue ?: return
        if (workingProviders.isEmpty()) return
        workingProviders.remove(selected)
        workingApiKeys.remove(selected.id)
        if (workingActiveId == selected.id) {
            workingActiveId = workingProviders.firstOrNull()?.id ?: ""
        }
        refreshProviderList()
    }

    /** 测试当前配置的连通性（后台发一条最小请求） */
    private fun testConnection() {
        syncUiToWorking()
        val provider = providerList.selectedValue ?: return
        if (provider.baseUrl.isBlank() || provider.model.isBlank()) {
            Messages.showWarningDialog(
                mainPanel,
                CodeSenseBundle.message("settings.test.warn.config"),
                CodeSenseBundle.message("settings.test.warn.title"),
            )
            return
        }
        val apiKey = workingApiKeys[provider.id] ?: ""
        if (apiKey.isBlank()) {
            Messages.showWarningDialog(
                mainPanel,
                CodeSenseBundle.message("settings.test.warn.key"),
                CodeSenseBundle.message("settings.test.warn.title"),
            )
            return
        }
        val project: Project? = ProjectManager.getInstance().openProjects.firstOrNull()
        val startedAt = System.currentTimeMillis()
        val taskTitle = CodeSenseBundle.message("settings.test.progress", provider.displayName)
        object : Task.Backgroundable(project, taskTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                val reply = try {
                    OpenAiCompatClient().chat(
                        provider, apiKey,
                        listOf(
                            ChatMessage("user", "Reply with just the word 'OK'."),
                        ),
                    )
                } catch (e: AiClientException) {
                    notifyTestResult(CodeSenseBundle.message("notification.testFail", e.message ?: ""))
                    return
                } catch (e: Exception) {
                    notifyTestResult(
                        CodeSenseBundle.message("notification.testFail", e.message ?: e.javaClass.simpleName),
                    )
                    return
                }
                val elapsed = System.currentTimeMillis() - startedAt
                notifyTestResult(
                    CodeSenseBundle.message("notification.testSuccess", elapsed.toString(), reply.take(50)),
                )
            }
        }.queue()
    }

    private fun notifyTestResult(message: String) {
        com.intellij.notification.Notifications.Bus.notify(
            com.intellij.notification.Notification(
                "CodeSenseAI",
                CodeSenseBundle.message("notification.groupTitle"),
                message,
                com.intellij.notification.NotificationType.INFORMATION,
            ),
            null,
        )
    }

    // ---- 添加厂商对话框 ----

    /**
     * 添加厂商对话框：从预设模板中选择，或手动填写自定义厂商信息。
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
        private val modelCombo = JComboBox<String>().apply { isEditable = true }

        var resultConfig: AiProviderConfig? = null
            private set

        init {
            title = CodeSenseBundle.message("settings.addProvider.title")
            planTypeCombo.renderer = PlanRenderer()
            onPresetSelected()
            init()
        }

        override fun createCenterPanel(): JComponent {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.addProvider.template")), presetCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.provider")), nameField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.planType")), planTypeCombo)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.baseUrl")), baseUrlField)
                .addLabeledComponent(JLabel(CodeSenseBundle.message("settings.model")), modelCombo)
                .panel
        }

        private fun onPresetSelected() {
            val preset = presetCombo.selectedItem as? ProviderPreset ?: return
            if (preset.id == "__custom__") {
                nameField.text = ""
                val types = arrayOf(ProviderPlanType.PAY_AS_YOU_GO)
                planTypeCombo.model = DefaultComboBoxModel(types)
                baseUrlField.text = ""
                modelCombo.model = DefaultComboBoxModel(emptyArray<String>())
                modelCombo.selectedItem = ""
            } else {
                nameField.text = preset.displayName
                val planTypes = preset.plans.map { it.type }.distinct().toTypedArray()
                planTypeCombo.model = DefaultComboBoxModel(planTypes)
                planTypeCombo.selectedItem = planTypes.first()
                baseUrlField.text = preset.plans.first().baseUrl
                modelCombo.model = DefaultComboBoxModel(preset.models.toTypedArray())
                modelCombo.selectedItem = preset.models.first()
            }
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
            val preset = presetCombo.selectedItem as? ProviderPreset
            val providerId = if (preset != null && preset.id != "__custom__") preset.id else "custom-${UUID.randomUUID()}"
            resultConfig = AiProviderConfig(
                id = providerId,
                displayName = name,
                planType = planTypeCombo.selectedItem as? ProviderPlanType ?: ProviderPlanType.PAY_AS_YOU_GO,
                baseUrl = baseUrlField.text.trim(),
                model = (modelCombo.editor.item as? String ?: "").trim(),
            )
            super.doOKAction()
        }
    }

    // ---- 渲染器 ----

    /** 厂商列表渲染器 */
    private class ProviderListRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.text = (value as? AiProviderConfig)?.displayName ?: value.toString()
            delegate.font = delegate.font.deriveFont(Font.PLAIN)
            if (isSelected) {
                delegate.background = list.selectionBackground
                delegate.foreground = list.selectionForeground
            } else {
                delegate.background = list.background
                delegate.foreground = list.foreground
            }
            delegate.isOpaque = true
            delegate.border = JBUI.Borders.empty(4, 8)
            return delegate
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

    /** 预设厂商渲染器 */
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
        /** 打开设置页（供外部跳转） */
        fun open(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
        }
    }
}