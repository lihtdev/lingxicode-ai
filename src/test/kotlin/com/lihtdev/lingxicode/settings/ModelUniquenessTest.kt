package com.lihtdev.lingxicode.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ModelUniqueness（「供应商 + 类型 + 模型」唯一键与去重切分）单元测试 */
class ModelUniquenessTest {

    private fun config(
        displayName: String,
        planType: ProviderPlanType,
        model: String,
    ) = AiProviderConfig(
        id = "$displayName:$model",
        providerId = displayName,
        displayName = displayName,
        planType = planType,
        model = model,
    )

    @Test
    fun `key 归一化 trim 并区分三要素`() {
        assertEquals(
            "DeepSeek\u0001PAY_AS_YOU_GO\u0001deepseek-v4-flash",
            ModelUniqueness.key(
                "  DeepSeek  ",
                ProviderPlanType.PAY_AS_YOU_GO,
                "  deepseek-v4-flash  ",
            ),
        )
        // 供应商不同
        assertNotEquals(
            ModelUniqueness.key("a", ProviderPlanType.PAY_AS_YOU_GO, "m"),
            ModelUniqueness.key("b", ProviderPlanType.PAY_AS_YOU_GO, "m"),
        )
        // 类型不同
        assertNotEquals(
            ModelUniqueness.key("a", ProviderPlanType.PAY_AS_YOU_GO, "m"),
            ModelUniqueness.key("a", ProviderPlanType.CODING_PLAN, "m"),
        )
        // 模型不同
        assertNotEquals(
            ModelUniqueness.key("a", ProviderPlanType.PAY_AS_YOU_GO, "m1"),
            ModelUniqueness.key("a", ProviderPlanType.PAY_AS_YOU_GO, "m2"),
        )
    }

    @Test
    fun `keysOf 覆盖全部条目`() {
        val keys = ModelUniqueness.keysOf(
            listOf(
                config("DeepSeek", ProviderPlanType.PAY_AS_YOU_GO, "deepseek-v4-flash"),
                config("DeepSeek", ProviderPlanType.PAY_AS_YOU_GO, "deepseek-v4-pro"),
            ),
        )
        assertEquals(2, keys.size)
        assertTrue(ModelUniqueness.key("DeepSeek", ProviderPlanType.PAY_AS_YOU_GO, "deepseek-v4-flash") in keys)
    }

    @Test
    fun `partition 命中已有键归入重复`() {
        val existing = ModelUniqueness.keysOf(
            listOf(config("OpenAI", ProviderPlanType.PAY_AS_YOU_GO, "gpt-4o-mini")),
        )
        val (duplicates, uniques) = ModelUniqueness.partition(
            existing,
            "OpenAI",
            ProviderPlanType.PAY_AS_YOU_GO,
            listOf("gpt-4o-mini", "gpt-5"),
        )
        assertEquals(listOf("gpt-4o-mini"), duplicates)
        assertEquals(listOf("gpt-5"), uniques)
    }

    @Test
    fun `partition 批量内重复只保留首次`() {
        val (duplicates, uniques) = ModelUniqueness.partition(
            emptySet(),
            "X",
            ProviderPlanType.PAY_AS_YOU_GO,
            listOf("m1", "m2", "m1", " m2 "),
        )
        // 首次出现的 m1/m2 为唯一项；重复出现（含仅 trim 不同的等价名）归入重复列表（原样保留）
        assertEquals(listOf("m1", " m2 "), duplicates)
        assertEquals(listOf("m1", "m2"), uniques)
    }

    @Test
    fun `partition 全部重复时唯一列表为空`() {
        val existing = ModelUniqueness.keysOf(
            listOf(config("X", ProviderPlanType.PAY_AS_YOU_GO, "m1")),
        )
        val (duplicates, uniques) = ModelUniqueness.partition(
            existing,
            "X",
            ProviderPlanType.PAY_AS_YOU_GO,
            listOf("m1"),
        )
        assertEquals(listOf("m1"), duplicates)
        assertTrue(uniques.isEmpty())
    }

    @Test
    fun `分供应商或类型或模型不同均不算重复`() {
        val existing = ModelUniqueness.keysOf(
            listOf(config("X", ProviderPlanType.PAY_AS_YOU_GO, "m1")),
        )
        // 供应商不同
        val (_, uniques1) = ModelUniqueness.partition(existing, "Y", ProviderPlanType.PAY_AS_YOU_GO, listOf("m1"))
        assertEquals(listOf("m1"), uniques1)
        // 类型不同
        val (_, uniques2) = ModelUniqueness.partition(existing, "X", ProviderPlanType.CODING_PLAN, listOf("m1"))
        assertEquals(listOf("m1"), uniques2)
        // 模型不同
        val (_, uniques3) = ModelUniqueness.partition(existing, "X", ProviderPlanType.PAY_AS_YOU_GO, listOf("m2"))
        assertEquals(listOf("m2"), uniques3)
    }
}