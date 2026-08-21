package com.lihtdev.codesense.settings

/**
 * 类型（厂商套餐类型）：区别仅在于 baseUrl，API 协议统一为 OpenAI 兼容格式。
 */
enum class ProviderPlanType(val displayName: String) {
    TOKEN_PLAN("Token Plan"),
    CODING_PLAN("Coding Plan"),
    PAY_AS_YOU_GO("按量付费"),
}

/**
 * 单个模型配置（持久化到 codesense-ai.xml）。
 * 注意：apiKey 不在持久化文件中，经 PasswordSafe 存取（见 [AppSettings]）。
 */
data class AiProviderConfig(
    var id: String = "",
    var displayName: String = "",
    var planType: ProviderPlanType = ProviderPlanType.PAY_AS_YOU_GO,
    var baseUrl: String = "",
    var model: String = "",
)
