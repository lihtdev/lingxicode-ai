package com.lihtdev.lingxicode.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * LingxiCodeBundle 按语言解析的回归测试。
 *
 * 背景：「界面语言」设置（AppSettings.uiLanguage）曾无任何消费方，文案实际跟随
 * IDE 语言导致切换不生效（2026-08 修复）。本测试直接驱动 messageIn(zh/en) 验证
 * 双语言解析、参数格式化与键缺失回退，不依赖平台 API（符合项目测试约定）。
 */
class LingxiCodeBundleLanguageTest {

    @Test
    fun `英文语言返回英文文案`() {
        assertEquals("Explain Code", LingxiCodeBundle.messageIn("en", "action.explainCode.text"))
    }

    @Test
    fun `中文语言返回中文文案`() {
        assertEquals("解释代码", LingxiCodeBundle.messageIn("zh", "action.explainCode.text"))
    }

    @Test
    fun `带占位符键在两种语言下均正确格式化`() {
        assertEquals(
            "连接成功（123ms）：ok",
            LingxiCodeBundle.messageIn("zh", "notification.testSuccess", "123", "ok"),
        )
        assertEquals(
            "Connection successful (123ms): ok",
            LingxiCodeBundle.messageIn("en", "notification.testSuccess", "123", "ok"),
        )
    }

    @Test
    fun `键缺失时回退显示键本身`() {
        assertEquals("no.such.key", LingxiCodeBundle.messageIn("en", "no.such.key"))
        assertEquals("no.such.key", LingxiCodeBundle.messageIn("zh", "no.such.key"))
    }
}
