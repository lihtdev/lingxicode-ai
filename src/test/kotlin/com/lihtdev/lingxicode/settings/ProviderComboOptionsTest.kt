package com.lihtdev.lingxicode.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 提供商档案 → 选择下拉选项（ProviderComboOption）与 ProviderIds 单元测试。
 *
 * 新规则：一条提供商记录只能有一个类型，多种类型拆为多条记录，
 * 不因 baseUrl 相同而合并；同名不同类的 providerId 不同（带类型后缀），
 * API Key 各自独立存储。
 */
class ProviderComboOptionsTest {

    @Test
    fun `多类型各成一条记录 - 不同 baseUrl`() {
        val result = ProviderComboOptions.of(
            "qwen", "Qwen (Alibaba)",
            listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://a/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://b/v1"),
            ),
            isCustom = false,
        )
        assertEquals(2, result.size)
        assertEquals(listOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.TOKEN_PLAN), result.map { it.type })
        assertEquals(listOf("https://a/v1", "https://b/v1"), result.map { it.baseUrl })
        // providerId 带类型后缀互不相同 → API Key 槽独立
        assertEquals(listOf("qwen:PAY_AS_YOU_GO", "qwen:TOKEN_PLAN"), result.map { it.providerId })
        assertTrue(result.all { !it.isCustom })
    }

    @Test
    fun `多类型各成一条记录 - baseUrl 相同也不合并`() {
        // MiniMax 型数据：两类型同端点，仍应展示为两条记录
        val result = ProviderComboOptions.of(
            "minimax", "MiniMax",
            listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.minimaxi.com/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://api.minimaxi.com/v1"),
            ),
            isCustom = false,
        )
        assertEquals(2, result.size)
        assertEquals(
            listOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.TOKEN_PLAN),
            result.map { it.type },
        )
        assertEquals(listOf("https://api.minimaxi.com/v1", "https://api.minimaxi.com/v1"), result.map { it.baseUrl })
        assertEquals(listOf("minimax:PAY_AS_YOU_GO", "minimax:TOKEN_PLAN"), result.map { it.providerId })
    }

    @Test
    fun `选项按类型展示顺序排序`() {
        // 乱序输入：Coding Plan → 按量付费 → Token Plan，应按展示顺序输出
        val result = ProviderComboOptions.of(
            "p", "P",
            listOf(
                PlanPreset(ProviderPlanType.CODING_PLAN, "https://c/v1"),
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://a/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://b/v1"),
            ),
            isCustom = true,
        )
        assertEquals(
            listOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.TOKEN_PLAN, ProviderPlanType.CODING_PLAN),
            result.map { it.type },
        )
        assertEquals(
            listOf("p:PAY_AS_YOU_GO", "p:TOKEN_PLAN", "p:CODING_PLAN"),
            result.map { it.providerId },
        )
        assertTrue(result.all { it.isCustom })
    }

    @Test
    fun `空 baseUrl 的 plan 忽略`() {
        val result = ProviderComboOptions.of(
            "p", "P",
            listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, ""),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://b/v1"),
            ),
            isCustom = false,
        )
        assertEquals(1, result.size)
        assertEquals(ProviderPlanType.TOKEN_PLAN, result.first().type)
        assertEquals("p:TOKEN_PLAN", result.first().providerId)
    }

    @Test
    fun `重复 plan 去重`() {
        val result = ProviderComboOptions.of(
            "p", "P",
            listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://a/v1"),
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://a/v1"),
            ),
            isCustom = false,
        )
        assertEquals(1, result.size)
        assertEquals(ProviderPlanType.PAY_AS_YOU_GO, result.first().type)
    }

    @Test
    fun `空 plans 返回空列表`() {
        assertTrue(ProviderComboOptions.of("p", "P", emptyList(), isCustom = true).isEmpty())
    }

    // ---- ProviderIds ----

    @Test
    fun `ProviderIds of 生成带类型后缀的唯一 id`() {
        assertEquals("qwen:TOKEN_PLAN", ProviderIds.of("qwen", ProviderPlanType.TOKEN_PLAN))
        assertEquals("custom-1:PAY_AS_YOU_GO", ProviderIds.of("custom-1", ProviderPlanType.PAY_AS_YOU_GO))
    }

    @Test
    fun `ProviderIds baseOf 归一后缀并兼容旧数据`() {
        assertEquals("qwen", ProviderIds.baseOf("qwen:TOKEN_PLAN"))
        assertEquals("custom-1", ProviderIds.baseOf("custom-1:PAY_AS_YOU_GO"))
        // 无 ':' 的旧形态原样返回
        assertEquals("qwen", ProviderIds.baseOf("qwen"))
        assertEquals("custom-1", ProviderIds.baseOf("custom-1"))
    }

    @Test
    fun `ProviderIds isLegacy 判定旧形态`() {
        assertTrue(ProviderIds.isLegacy("qwen"))
        assertTrue(ProviderIds.isLegacy("custom-1"))
        assertTrue(!ProviderIds.isLegacy("qwen:TOKEN_PLAN"))
    }
}