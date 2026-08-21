package com.lihtdev.codesense.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import java.awt.Component
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import com.lihtdev.codesense.ai.AiClientException
import com.lihtdev.codesense.ai.ChatMessage
import com.lihtdev.codesense.ai.OpenAiCompatClient

/**
 * 设置页（Tools → CodeSense AI）。
 * 厂商下拉 + 类型下拉（自动带出 baseUrl）+ baseUrl / model（可编辑下拉）/ API Key，
 * 支持「添加自定义」「删除」「测试连接」，以及输出语言与 diff 上限。
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
    private val providerCombo = JComboBox<AiProviderConfig>()
    private val planCombo = JComboBox<ProviderPlanType>()
    private val baseUrlField = javax.swing.JTextField()
    private val modelCombo = JComboBox<String>()
    private val apiKeyField = JBPasswordField()
    private val languageCombo = JComboBox(arrayOf(LANGUAGE_ZH, LANGUAGE_EN))
    private val maxDiffField = javax.swing.JSpinner(javax.swing.SpinnerNumberModel(60000, 1000, 1000000, 1000))
    private val mainPanel: JComponent

    init {
        providerCombo.renderer = ProviderRenderer()
        planCombo.renderer = PlanRenderer()

        val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT)).apply {
            add(JButton("添加自定义").apply { addActionListener { addCustomProvider() } })
            add(JButton("删除当前").apply { addActionListener { removeCurrentProvider() } })
            add(JButton("测试连接").apply { addActionListener { testConnection() } })
        }

        modelCombo.isEditable = true

        providerCombo.addActionListener { onProviderSelected() }
        planCombo.addActionListener { onPlanChanged() }

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("厂商："), providerCombo)
            .addLabeledComponent(JLabel("类型："), planCombo)
            .addLabeledComponent(JLabel("接口地址（baseUrl）："), baseUrlField)
            .addLabeledComponent(JLabel("模型："), modelCombo)
            .addLabeledComponent(JLabel("API Key："), apiKeyField)
            .addComponent(buttonPanel)
            .addSeparator()
            .addLabeledComponent(JLabel("提交信息语言："), languageCombo)
            .addLabeledComponent(JLabel("Diff 最大字符数："), maxDiffField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
    }

    // ---- Configurable 实现 ----

    override fun getDisplayName(): String = "CodeSense AI"

    override fun createComponent(): JComponent = mainPanel

    override fun isModified(): Boolean {
        syncUiToWorking()
        val current = settings.state
        val appliedLanguage = if (languageCombo.selectedIndex == 1) "en" else "zh"
        return workingProviders != current.providers
            || workingActiveId != current.activeProviderId
            || appliedLanguage != current.outputLanguage
            || (maxDiffField.value as Int) != current.maxDiffChars
            || workingApiKeys.any { (id, key) -> AppSettings.getApiKey(id) != key }
    }

    override fun apply() {
        syncUiToWorking()
        val state = settings.state
        state.providers = workingProviders.map { it.copy() }.toMutableList()
        state.activeProviderId = workingActiveId
        state.outputLanguage = if (languageCombo.selectedIndex == 1) "en" else "zh"
        state.maxDiffChars = maxDiffField.value as Int
        // API Key 写入 PasswordSafe
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
        languageCombo.selectedIndex = if (state.outputLanguage == "en") 1 else 0
        maxDiffField.value = state.maxDiffChars
        refreshProviderCombo()
    }

    // ---- 内部逻辑 ----

    /** 刷新厂商下拉并选中当前生效厂商 */
    private fun refreshProviderCombo() {
        isLoading = true
        try {
            providerCombo.model = DefaultComboBoxModel(workingProviders.toTypedArray())
            val selected = workingProviders.firstOrNull { it.id == workingActiveId } ?: workingProviders.firstOrNull()
            providerCombo.selectedItem = selected
            loadFieldsForSelected()
        } finally {
            isLoading = false
        }
    }

    private fun onProviderSelected() {
        if (isLoading) return
        syncUiToWorking()
        val selected = providerCombo.selectedItem as? AiProviderConfig ?: return
        workingActiveId = selected.id
        loadFieldsForSelected()
    }

    /** 将当前 UI 字段同步回工作副本中选中的厂商 */
    private fun syncUiToWorking() {
        val selected = providerCombo.selectedItem as? AiProviderConfig ?: return
        selected.baseUrl = baseUrlField.text.trim()
        selected.model = (modelCombo.editor.item as? String ?: "").trim()
        selected.planType = planCombo.selectedItem as? ProviderPlanType ?: selected.planType
        workingApiKeys[selected.id] = String(apiKeyField.password)
    }

    /** 依据选中厂商加载类型/baseUrl/model/apiKey 字段 */
    private fun loadFieldsForSelected() {
        val selected = providerCombo.selectedItem as? AiProviderConfig ?: return
        isLoading = true
        try {
            val preset = ProviderPresets.byId(selected.id)
            // 类型下拉：预设厂商显示其可用类型；自定义厂商仅「按量付费」
            val planTypes = preset?.plans?.map { it.type }?.distinct()
                ?: listOf(ProviderPlanType.PAY_AS_YOU_GO)
            planCombo.model = DefaultComboBoxModel(planTypes.toTypedArray())
            planCombo.selectedItem = if (planTypes.contains(selected.planType)) selected.planType else planTypes.first()
            baseUrlField.text = selected.baseUrl
            // model 可编辑下拉：预设模型列表 + 当前值
            val models = (preset?.models ?: emptyList()).toMutableSet()
            if (selected.model.isNotBlank()) models.add(selected.model)
            modelCombo.model = DefaultComboBoxModel(models.toTypedArray())
            modelCombo.selectedItem = selected.model
            apiKeyField.text = workingApiKeys[selected.id] ?: ""
        } finally {
            isLoading = false
        }
    }

    /** 切换类型：预设厂商自动带出该类型对应的 baseUrl（可手动覆盖） */
    private fun onPlanChanged() {
        if (isLoading) return
        val selected = providerCombo.selectedItem as? AiProviderConfig ?: return
        val planType = planCombo.selectedItem as? ProviderPlanType ?: return
        selected.planType = planType
        val preset = ProviderPresets.byId(selected.id) ?: return
        preset.plans.firstOrNull { it.type == planType }?.let { plan ->
            baseUrlField.text = plan.baseUrl
        }
    }

    /** 添加自定义厂商（任意 OpenAI 兼容端点） */
    private fun addCustomProvider() {
        val name = Messages.showInputDialog(
            mainPanel, "请输入自定义厂商名称：", "添加自定义厂商", null,
        )?.trim() ?: return
        if (name.isEmpty()) return
        val config = AiProviderConfig(
            id = "custom-${UUID.randomUUID()}",
            displayName = name,
            planType = ProviderPlanType.PAY_AS_YOU_GO,
            baseUrl = "",
            model = "",
        )
        workingProviders.add(config)
        workingApiKeys[config.id] = null
        refreshProviderCombo()
        providerCombo.selectedItem = config
        loadFieldsForSelected()
    }

    /** 删除当前选中的自定义厂商（至少保留一个） */
    private fun removeCurrentProvider() {
        val selected = providerCombo.selectedItem as? AiProviderConfig ?: return
        if (workingProviders.size <= 1) {
            Messages.showWarningDialog(mainPanel, "至少保留一个厂商配置", "无法删除")
            return
        }
        workingProviders.remove(selected)
        workingApiKeys.remove(selected.id)
        if (workingActiveId == selected.id) {
            workingActiveId = workingProviders.first().id
        }
        refreshProviderCombo()
    }

    /** 测试当前配置的连通性（后台发一条最小请求） */
    private fun testConnection() {
        syncUiToWorking()
        val provider = providerCombo.selectedItem as? AiProviderConfig ?: return
        if (provider.baseUrl.isBlank() || provider.model.isBlank()) {
            Messages.showWarningDialog(mainPanel, "请先填写接口地址与模型名称", "无法测试")
            return
        }
        val apiKey = workingApiKeys[provider.id] ?: ""
        if (apiKey.isBlank()) {
            Messages.showWarningDialog(mainPanel, "请先填写 API Key", "无法测试")
            return
        }
        val project: Project? = ProjectManager.getInstance().openProjects.firstOrNull()
        val startedAt = System.currentTimeMillis()
        object : Task.Backgroundable(project, "正在测试 ${provider.displayName} 连接…", true) {
            override fun run(indicator: ProgressIndicator) {
                val reply = try {
                    OpenAiCompatClient().chat(
                        provider, apiKey,
                        listOf(
                            ChatMessage("user", "你好，请回复「连接成功」四个字。"),
                        ),
                    )
                } catch (e: AiClientException) {
                    notifyTestResult("连接失败：${e.message}")
                    return
                } catch (e: Exception) {
                    notifyTestResult("连接失败：${e.message ?: e.javaClass.simpleName}")
                    return
                }
                val elapsed = System.currentTimeMillis() - startedAt
                notifyTestResult("连接成功（${elapsed}ms）：${reply.take(50)}")
            }
        }.queue()
    }

    private fun notifyTestResult(message: String) {
        com.intellij.notification.Notifications.Bus.notify(
            com.intellij.notification.Notification(
                "CodeSenseAI", "CodeSense AI - 测试连接", message,
                com.intellij.notification.NotificationType.INFORMATION,
            ),
            null,
        )
    }

    // ---- 渲染器 ----

    private class ProviderRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.text = (value as? AiProviderConfig)?.displayName ?: value.toString()
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

    private class PlanRenderer : ListCellRenderer<Any?> {
        private val delegate = JLabel()
        override fun getListCellRendererComponent(
            list: javax.swing.JList<out Any?>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            delegate.text = (value as? ProviderPlanType)?.displayName ?: value.toString()
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
        private const val LANGUAGE_ZH = "中文"
        private const val LANGUAGE_EN = "English"

        /** 打开设置页（供外部跳转） */
        fun open(project: Project?) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
        }
    }
}
