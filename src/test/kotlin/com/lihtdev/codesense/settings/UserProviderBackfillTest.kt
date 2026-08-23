package com.lihtdev.codesense.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UserProvider（自定义提供商档案回填）单元测试 */
class UserProviderBackfillTest {

    private fun entry(
        providerId: String,
        name: String,
        type: ProviderPlanType,
        baseUrl: String = "https://default/",
    ) = AiProviderConfig(
        id = "$providerId:model",
        providerId = providerId,
        displayName = name,
        planType = type,
        baseUrl = baseUrl,
        model = "m",
    )

    @Test
    fun `空列表返回空档案列表`() {
        assertEquals(emptyList<UserProvider>(), UserProvider.backfillFrom(emptyList()))
    }

    @Test
    fun `预设提供商不参与回填`() {
        val result = UserProvider.backfillFrom(
            listOf(entry("openai", "OpenAI", ProviderPlanType.PAY_AS_YOU_GO)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `同 providerId 的条目归并为一个档案并保留多类型 plans`() {
        val result = UserProvider.backfillFrom(
            listOf(
                entry("custom-a", "我的厂商", ProviderPlanType.PAY_AS_YOU_GO),
                entry("custom-a", "我的厂商", ProviderPlanType.CODING_PLAN, "https://coding/"),
            ),
        )
        assertEquals(1, result.size)
        val p = result.first()
        assertEquals("custom-a", p.id)
        assertEquals("我的厂商", p.displayName)
        assertEquals(listOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.CODING_PLAN), p.plans.map { it.type })
        assertEquals(listOf("https://default/", "https://coding/"), p.plans.map { it.baseUrl })
    }

    @Test
    fun `重复 plans 去重且空 baseUrl 忽略`() {
        val result = UserProvider.backfillFrom(
            listOf(
                entry("custom-b", "B", ProviderPlanType.PAY_AS_YOU_GO),
                entry("custom-b", "B", ProviderPlanType.PAY_AS_YOU_GO),
                entry("custom-b", "B", ProviderPlanType.PAY_AS_YOU_GO, ""),
                entry("custom-b", "B", ProviderPlanType.TOKEN_PLAN),
            ),
        )
        val p = result.first()
        assertEquals(setOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.TOKEN_PLAN), p.plans.map { it.type }.toSet())
        assertEquals(1, p.plans.count { it.type == ProviderPlanType.PAY_AS_YOU_GO })
        assertTrue(p.plans.none { it.baseUrl.isBlank() })
    }

    @Test
    fun `与已有档案按 id 合并：保留显示名并补充缺失 plans`() {
        val existing = listOf(
            UserProvider(
                id = "custom-c",
                displayName = "旧名",
                plans = mutableListOf(PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://t/")),
            ),
        )
        val result = UserProvider.backfillFrom(
            listOf(
                entry("custom-c", "新名", ProviderPlanType.PAY_AS_YOU_GO),
                entry("custom-d", "D", ProviderPlanType.PAY_AS_YOU_GO),
            ),
            existing,
        )
        assertEquals(setOf("custom-c", "custom-d"), result.map { it.id }.toSet())
        val c = result.first { it.id == "custom-c" }
        assertEquals("旧名", c.displayName)
        assertEquals(setOf(ProviderPlanType.TOKEN_PLAN, ProviderPlanType.PAY_AS_YOU_GO), c.plans.map { it.type }.toSet())
    }

    @Test
    fun `providerId 为空的条目跳过`() {
        val result = UserProvider.backfillFrom(
            listOf(entry("", "无名", ProviderPlanType.PAY_AS_YOU_GO)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `带类型后缀的自定义 providerId 归并回 base 档案`() {
        val result = UserProvider.backfillFrom(
            listOf(
                entry("custom-a:PAY_AS_YOU_GO", "我的厂商", ProviderPlanType.PAY_AS_YOU_GO),
                entry("custom-a:TOKEN_PLAN", "我的厂商", ProviderPlanType.TOKEN_PLAN, "https://token/"),
            ),
        )
        assertEquals(1, result.size)
        val p = result.first()
        assertEquals("custom-a", p.id)
        assertEquals(setOf(ProviderPlanType.PAY_AS_YOU_GO, ProviderPlanType.TOKEN_PLAN), p.plans.map { it.type }.toSet())
    }

    @Test
    fun `带类型后缀的预设 providerId 不参与回填`() {
        val result = UserProvider.backfillFrom(
            listOf(
                entry("qwen:PAY_AS_YOU_GO", "Qwen (Alibaba)", ProviderPlanType.PAY_AS_YOU_GO),
                entry("minimax:TOKEN_PLAN", "MiniMax", ProviderPlanType.TOKEN_PLAN),
            ),
        )
        assertTrue(result.isEmpty())
    }
}