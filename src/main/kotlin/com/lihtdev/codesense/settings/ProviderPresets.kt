package com.lihtdev.codesense.settings

/**
 * 套餐预设：类型 + baseUrl。
 */
data class PlanPreset(
    val type: ProviderPlanType,
    val baseUrl: String,
)

/**
 * 提供商预设：可用类型（各类型对应 baseUrl）与预设模型列表。
 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val plans: List<PlanPreset>,
)

/**
 * 预设提供商常量。
 * 数据来源：用户提供的官方配置（2026-08），API 调用统一使用 OpenAI 兼容协议。
 */
object ProviderPresets {

    val ALL: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "openai",
            displayName = "OpenAI",
            models = listOf("gpt-4o-mini"),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.openai.com/v1"),
            ),
        ),
        ProviderPreset(
            id = "deepseek",
            displayName = "DeepSeek",
            models = listOf("deepseek-v4-flash", "deepseek-v4-pro"),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.deepseek.com/v1"),
            ),
        ),
        ProviderPreset(
            id = "glm",
            displayName = "GLM (Zhipu)",
            models = listOf("GLM-5.3", "GLM-5.2", "GLM-5.1", "GLM-5", "GLM-4.7"),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://open.bigmodel.cn/api/paas/v4"),
                PlanPreset(ProviderPlanType.CODING_PLAN, "https://open.bigmodel.cn/api/coding/paas/v4"),
            ),
        ),
        ProviderPreset(
            id = "kimi",
            displayName = "Kimi (Moonshot)",
            models = listOf(
                "kimi-k3", "kimi-k2.7-code", "kimi-k2.7-code-highspeed", "kimi-k2.6", "kimi-k2.5",
            ),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.moonshot.cn/v1"),
                PlanPreset(ProviderPlanType.CODING_PLAN, "https://api.kimi.com/coding/v1"),
            ),
        ),
        ProviderPreset(
            id = "minimax",
            displayName = "MiniMax",
            models = listOf(
                "MiniMax-M3",
                "MiniMax-M2.7", "MiniMax-M2.7-highspeed",
                "MiniMax-M2.5", "MiniMax-M2.5-highspeed",
                "MiniMax-M2.1", "MiniMax-M2.1-highspeed",
                "MiniMax-M2",
            ),
            plans = listOf(
                // 两种类型 baseUrl 相同，类型仅用于区分计费套餐
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.minimaxi.com/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://api.minimaxi.com/v1"),
            ),
        ),
        ProviderPreset(
            id = "qwen",
            displayName = "Qwen (Alibaba)",
            models = listOf("qwen3.8-max", "qwen3.7-max", "qwen3.7-plus", "qwen3.7-flash"),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"),
            ),
        ),
        ProviderPreset(
            id = "mimo",
            displayName = "Xiaomi MIMO",
            models = listOf("mimo-v2.5-pro", "mimo-v2.5"),
            plans = listOf(
                PlanPreset(ProviderPlanType.PAY_AS_YOU_GO, "https://api.xiaomimimo.com/v1"),
                PlanPreset(ProviderPlanType.TOKEN_PLAN, "https://token-plan-cn.xiaomimimo.com/v1"),
            ),
        ),
    )

    /** 按 id 查找预设 */
    fun byId(id: String): ProviderPreset? = ALL.firstOrNull { it.id == id }
}
