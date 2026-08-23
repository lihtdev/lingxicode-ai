package com.lihtdev.codesense.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 应用级设置持久化状态。
 * 字段经 IntelliJ 持久化机制序列化到 codesense-ai.xml。
 */
data class AppSettingsState(
    var providers: MutableList<AiProviderConfig> = mutableListOf(),
    var userProviders: MutableList<UserProvider> = mutableListOf(),
    var activeProviderId: String = "",
    var outputLanguage: String = "zh",
    var uiLanguage: String = "zh",
    var maxDiffChars: Int = DEFAULT_MAX_DIFF_CHARS,
) {
    companion object {
        const val DEFAULT_MAX_DIFF_CHARS = 60000
    }
}

/**
 * 应用级设置服务：模型条目列表、当前生效模型、输出语言、界面语言、diff 上限。
 * API Key 经 PasswordSafe 按 providerId 安全存储，不落盘。
 */
@State(name = "CodeSenseSettings", storages = [Storage("codesense-ai.xml")])
class AppSettings : PersistentStateComponent<AppSettingsState> {

    private var myState = AppSettingsState()

    override fun getState(): AppSettingsState = myState

    override fun loadState(state: AppSettingsState) {
        myState = state
        // 兼容旧数据：旧格式每条记录一个提供商，providerId 为空时回填为 id
        myState.providers.forEach { if (it.providerId.isBlank()) it.providerId = it.id }
        // 兼容旧数据：providerId 未带类型后缀时按条目类型补齐（同名不同类型 = 不同提供商、独立 API Key 槽）
        myState.providers.forEach {
            if (ProviderIds.isLegacy(it.providerId)) {
                it.providerId = ProviderIds.of(it.providerId, it.planType)
            }
        }
        // 兼容旧数据：把既有模型条目中的自定义提供商回填为用户档案（保留已保存档案，只补缺失）
        myState.userProviders = UserProvider.backfillFrom(myState.providers, myState.userProviders).toMutableList()
    }

    /** 当前生效的模型条目（找不到 activeId 时，回退到第一个条目） */
    fun activeProvider(): AiProviderConfig? =
        myState.providers.firstOrNull { it.id == myState.activeProviderId }
            ?: myState.providers.firstOrNull()

    fun setActiveProvider(id: String) {
        myState.activeProviderId = id
    }

    companion object {
        @JvmStatic
        val instance: AppSettings
            get() = ApplicationManager.getApplication().getService(AppSettings::class.java)

        /** PasswordSafe 服务名（与 providerId 组合定位凭据） */
        private const val SERVICE_NAME = "CodeSenseAI"

        /**
         * 从 PasswordSafe 读取某提供商的 API Key。
         * 优先读精确槽（`qwen:TOKEN_PLAN`）；为空时回退读 base 槽（`qwen`），
         * 兼容迁移前的旧 Key 存储。
         */
        fun getApiKey(providerId: String): String? {
            val exact = PasswordSafe.instance
                .get(CredentialAttributes(SERVICE_NAME, providerId))
                ?.getPasswordAsString()
            if (exact != null) return exact
            val base = ProviderIds.baseOf(providerId)
            return if (base != providerId) {
                PasswordSafe.instance
                    .get(CredentialAttributes(SERVICE_NAME, base))
                    ?.getPasswordAsString()
            } else {
                null
            }
        }

        /**
         * 保存（或清除，传 null/空）某提供商的 API Key。
         * 写入精确槽（按「提供商 × 类型」独立）；同时清除 base 槽，
         * 避免回退读到陈旧 Key（含「清空 Key」场景）。
         */
        fun setApiKey(providerId: String, apiKey: String?) {
            val attributes = CredentialAttributes(SERVICE_NAME, providerId)
            val credentials = apiKey?.takeIf { it.isNotBlank() }?.let { Credentials(providerId, it) }
            PasswordSafe.instance.set(attributes, credentials)
            val base = ProviderIds.baseOf(providerId)
            if (base != providerId) {
                PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, base), null)
            }
        }
    }
}
