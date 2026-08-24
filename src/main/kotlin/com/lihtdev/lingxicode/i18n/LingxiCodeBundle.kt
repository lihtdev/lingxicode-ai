package com.lihtdev.lingxicode.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/**
 * LingxiCode AI 国际化资源束。
 *
 * 资源文件位于 src/main/resources/messages/LingxiCodeBundle.properties（中文默认）
 * 与 LingxiCodeBundle_en.properties（英文）。
 *
 * 用法：LingxiCodeBundle.message("key") 或 LingxiCodeBundle.message("key", param1, param2)
 *
 * 在 IntelliJ 平台运行时通过 DynamicBundle 加载；在单测环境（无 ApplicationManager）
 * 则回退到 Java 标准 ResourceBundle 从 classpath 加载。
 */
class LingxiCodeBundle : DynamicBundle(BUNDLE_PATH) {

    companion object {
        @NonNls
        private const val BUNDLE_PATH = "messages.LingxiCodeBundle"

        private var instance: LingxiCodeBundle? = null
        private var fallbackBundle: ResourceBundle? = null

        @JvmStatic
        fun message(
            @PropertyKey(resourceBundle = BUNDLE_PATH) key: String,
            vararg params: Any,
        ): String {
            return try {
                getInstance().getMessage(key, *params)
            } catch (_: Exception) {
                // 单测环境无 ApplicationManager，回退到标准 ResourceBundle
                getFallbackMessage(key, *params)
            }
        }

        private fun getInstance(): LingxiCodeBundle {
            if (instance == null) {
                instance = com.intellij.openapi.application.ApplicationManager.getApplication()
                    .getService(LingxiCodeBundle::class.java)
            }
            return instance!!
        }

        private fun getFallbackMessage(key: String, vararg params: Any): String {
            if (fallbackBundle == null) {
                fallbackBundle = ResourceBundle.getBundle(BUNDLE_PATH, Locale.getDefault())
            }
            val pattern = fallbackBundle!!.getString(key)
            return if (params.isNotEmpty()) {
                MessageFormat.format(pattern, *params)
            } else {
                pattern
            }
        }
    }
}