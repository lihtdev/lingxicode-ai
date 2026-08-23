package com.lihtdev.codesense.settings

import com.intellij.util.xmlb.annotations.XCollection

/**
 * 类型（提供商套餐类型）：区别仅在于 baseUrl，API 协议统一为 OpenAI 兼容格式。
 */
enum class ProviderPlanType {
    TOKEN_PLAN,
    CODING_PLAN,
    PAY_AS_YOU_GO,
    ;

    companion object {
        /** 界面上类型下拉的展示顺序：按量付费 → Token Plan → Coding Plan */
        val DISPLAY_ORDER = listOf(PAY_AS_YOU_GO, TOKEN_PLAN, CODING_PLAN)
    }
}

/**
 * 单个模型条目（持久化到 codesense-ai.xml）。
 *
 * 以「模型」维度存储：同一提供商可存在多条记录（每条对应一个模型）。
 * - [id]：模型条目唯一 id，用于定位当前激活条目
 * - [providerId]：提供商 id（「提供商 × 类型」唯一，apiKey 按此独立存取）
 * - [model]：模型名（API 实际请求用的 model id）
 * - [modelTags]：模型标签列表（区别于提供商本体的标签概念）
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
    // 保持 XML 元素名 <tags>，兼容历史版本持久化的标签数据（字段改名不丢数据）
    @XCollection(propertyElementName = "tags")
    var modelTags: MutableList<String> = mutableListOf(),
)