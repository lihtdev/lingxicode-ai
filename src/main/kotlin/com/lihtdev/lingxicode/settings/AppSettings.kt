package com.lihtdev.lingxicode.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 应用级设置持久化状态。
 * 字段经 IntelliJ 持久化机制序列化到 lingxicode-ai.xml。
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
@State(name = "LingxiCodeSettings", storages = [Storage("lingxicode-ai.xml")])
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
        private const val SERVICE_NAME = "LingxiCodeAI"

        /**
         * 从 PasswordSafe 读取某提供商的 API Key。
         * 优先读精确槽（`qwen:TOKEN_PLAN`）；为空时回退读 base 槽（`qwen`），
         * 兼容迁移前的旧 Key 存储（迁移见 [migrateLegacyApiKeys]）。
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
         * 写入时写到精确槽并清理历史 base 槽（防陈旧回退）；清空时只清本类型精确槽，
         * 不影响同名其它类型（迁移后各类型槽位已独立）。
         */
        fun setApiKey(providerId: String, apiKey: String?) {
            val credentials = apiKey?.takeIf { it.isNotBlank() }?.let { Credentials(providerId, it) }
            PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, providerId), credentials)
            if (credentials != null) {
                val base = ProviderIds.baseOf(providerId)
                if (base != providerId) {
                    PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, base), null)
                }
            }
        }

        /**
         * 迁移历史 base 槽 API Key 到各类型的精确槽，随后删除 base 槽，
         * 使同名不同类型彻底独立（各自可清空、互不影响）。幂等，可重复调用。
         * 由设置页 reset() 在预填 Key 前调用；只处理 [providerIds]（在用条目）涉及的 base。
         */
        fun migrateLegacyApiKeys(providerIds: Collection<String>) {
            val bases = LinkedHashSet<String>()
            providerIds.forEach { pid ->
                val base = ProviderIds.baseOf(pid)
                if (base != pid) bases.add(base)
            }
            if (bases.isEmpty()) return
            providerIds.forEach { pid ->
                val base = ProviderIds.baseOf(pid)
                if (base != pid) {
                    val exact = PasswordSafe.instance
                        .get(CredentialAttributes(SERVICE_NAME, pid))
                        ?.getPasswordAsString()
                    if (exact == null) {
                        val legacy = PasswordSafe.instance
                            .get(CredentialAttributes(SERVICE_NAME, base))
                            ?.getPasswordAsString()
                        if (legacy != null) {
                            PasswordSafe.instance.set(
                                CredentialAttributes(SERVICE_NAME, pid),
                                Credentials(pid, legacy),
                            )
                        }
                    }
                }
            }
            bases.forEach {
                PasswordSafe.instance.set(CredentialAttributes(SERVICE_NAME, it), null)
            }
        }
    }
}
