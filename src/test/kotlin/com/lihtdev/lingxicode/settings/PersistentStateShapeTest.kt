package com.lihtdev.lingxicode.settings

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * 持久化状态类的可序列化形状约束测试（纯反射，不依赖平台 API）。
 *
 * 背景：PlanPreset 曾是 val + 无默认值的 data class，IntelliJ XmlSerializer
 * 无法反序列化，导致整个 LingxiCodeSettings 状态加载失败、设置回退默认值（2026-08 排查修复）。
 * XmlSerializer 构造状态类的两个前提：主构造参数全部有默认值（可无参构造）、
 * 属性全部可写（构造后逐字段回填）。本测试用反射锁住该形状，防止回归。
 * 完整的 XML 往返行为按项目约定经 runIde 沙箱手动验证。
 */
class PersistentStateShapeTest {

    @Test
    fun `PlanPreset 主构造参数全部有默认值`() {
        val params = PlanPreset::class.primaryConstructor!!.parameters
        assertTrue(params.isNotEmpty())
        assertTrue(params.all { it.isOptional }, "PlanPreset 构造参数必须全部可选，实际：$params")
    }

    @Test
    fun `PlanPreset 属性全部可写`() {
        val props = PlanPreset::class.memberProperties
        assertTrue(props.isNotEmpty())
        assertTrue(
            props.all { it is KMutableProperty<*> },
            "PlanPreset 属性必须全部为 var，实际：${props.map { it.name }}",
        )
    }

    @Test
    fun `AppSettingsState 及嵌套状态类构造参数全部有默认值`() {
        listOf(AppSettingsState::class, UserProvider::class, AiProviderConfig::class).forEach { clazz ->
            val params = clazz.primaryConstructor!!.parameters
            assertTrue(
                params.all { it.isOptional },
                "${clazz.simpleName} 构造参数必须全部可选，实际：$params",
            )
        }
    }
}
