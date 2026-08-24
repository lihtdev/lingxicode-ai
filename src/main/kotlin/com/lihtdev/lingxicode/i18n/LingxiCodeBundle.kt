package com.lihtdev.lingxicode.i18n

import com.intellij.DynamicBundle
import com.intellij.openapi.application.ApplicationManager
import com.lihtdev.lingxicode.settings.AppSettings
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

/**
 * LingxiCode AI 国际化资源束。
 *
 * 资源文件位于 src/main/resources/messages/LingxiCodeBundle.properties（中文默认）
 * 与 LingxiCodeBundle_en.properties（英文）。
 *
 * 与平台 DynamicBundle 默认解析（跟随 IDE 语言）不同，本束按设置页「界面语言」
 * （[AppSettings.uiLanguage]，zh/en）显式选择语言，插件内语言切换无需重启 IDE。
 * 通知、弹窗、状态栏等文案在展示时实时调用 [message]，天然即时生效。
 *
 * 用法：LingxiCodeBundle.message("key") 或 LingxiCodeBundle.message("key", param1, param2)
 *
 * 单测环境（无 ApplicationManager）跟随 JVM 默认语言，可直接调用 [messageIn] 驱动指定语言。
 */
class LingxiCodeBundle : DynamicBundle(BUNDLE_PATH) {

    companion object {
        @NonNls
        private const val BUNDLE_PATH = "messages.LingxiCodeBundle"

        /** 支持的语言：中文（默认，资源束 base 文件）与英文（_en 文件） */
        private const val LANG_ZH = "zh"
        private const val LANG_EN = "en"

        // 按语言缓存的 ResourceBundle（仅 zh/en 两项），语言切换零成本、无需失效钩子
        private val bundles = ConcurrentHashMap<String, ResourceBundle>()

        @JvmStatic
        fun message(
            @PropertyKey(resourceBundle = BUNDLE_PATH) key: String,
            vararg params: Any,
        ): String = messageIn(currentLanguage(), key, *params)

        /** 按指定语言解析文案；键缺失时回退显示 key 本身（便于发现漏翻） */
        internal fun messageIn(lang: String, key: String, vararg params: Any): String {
            val pattern = try {
                bundleFor(lang).getString(key)
            } catch (_: MissingResourceException) {
                key
            }
            return if (params.isEmpty()) pattern else MessageFormat.format(pattern, *params)
        }

        private fun bundleFor(lang: String): ResourceBundle =
            bundles.computeIfAbsent(lang) {
                val locale = if (lang == LANG_EN) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
                ResourceBundle.getBundle(
                    BUNDLE_PATH,
                    locale,
                    LingxiCodeBundle::class.java.classLoader,
                    object : ResourceBundle.Control() {
                        // 禁止回退到 JVM 默认 locale：防止英文 JVM 请求中文时误载 _en（反之亦然）。
                        // base properties 文件仍作为所请求语言链的终极回退：zh→base（中文）、en→_en。
                        override fun getFallbackLocale(baseName: String, locale: Locale): Locale? = null
                    },
                )
            }

        /** 当前生效语言：运行期读设置页「界面语言」；单测无 Application 时跟随 JVM 语言 */
        private fun currentLanguage(): String = try {
            if (ApplicationManager.getApplication() == null) {
                if (Locale.getDefault().language == LANG_EN) LANG_EN else LANG_ZH
            } else {
                AppSettings.instance.state.uiLanguage.ifBlank { LANG_ZH }
            }
        } catch (_: Exception) {
            LANG_ZH
        }
    }
}
