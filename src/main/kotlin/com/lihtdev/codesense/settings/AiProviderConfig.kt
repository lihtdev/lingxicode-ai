package com.lihtdev.codesense.settings

/**
 * 类型（提供商套餐类型）：区别仅在于 baseUrl，API 协议统一为 OpenAI 兼容格式。
 */
enum class ProviderPlanType {
    TOKEN_PLAN,
    CODING_PLAN,
    PAY_AS_YOU_GO,
}

/**
 * 单个模型条目（持久化到 codesense-ai.xml）。
 *
 * 以「模型」维度存储：同一提供商可存在多条记录（每条对应一个模型）。
 * - [id]：模型条目唯一 id，用于定位当前激活条目
 * - [providerId]：提供商 id，同一提供商的多条记录共享（apiKey 按此存取）
 * - [model]：模型名（API 实际请求用的 model id）
 * - [modelDisplayName]：显示名称（空则回退 [model]）
 * - [tags]：标签列表
 * - [enabled]：是否启用（停用的模型不参与调用与切换）
 *
 * 注意：apiKey 不在持久化文件中，经 PasswordSafe 按 providerId 存取（见 [AppSettings]）。
 */
data class AiProviderConfig(
    var id: String = "",
    var providerId: String = "",
    var displayName: String = "",
    var planType: ProviderPlanType = ProviderPlanType.PAY_AS_YOU_GO,
    var baseUrl: String = "",
    var model: String = "",
    var modelDisplayName: String = "",
    var tags: MutableList<String> = mutableListOf(),
    var enabled: Boolean = true,
)