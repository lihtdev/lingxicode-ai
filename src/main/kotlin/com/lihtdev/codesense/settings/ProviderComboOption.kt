package com.lihtdev.codesense.settings

/**
 * 选择提供商下拉条目：一个「提供商 × 类型」组合。
 *
 * 规则：一条提供商记录只能有一个类型；同一提供商若支持多种类型（各类型对应
 * 各自的 baseUrl，即使 baseUrl 相同），则在下拉中作为多条记录展示——
 * 名称相同、名称右侧以标签样式标注类型。预设提供商与自定义提供商共用该规则。
 *
 * - [providerId]：归属提供商 id（API Key 按此存取、模型条目按此归属）；
 *   同一提供商的多条类型记录共享同一 [providerId]
 * - [type]：本记录的类型（创建模型条目时写入 [AiProviderConfig.planType]）
 * - [baseUrl]：本记录的类型对应的接口地址
 * - [isCustom]：是否为自定义提供商（决定编辑/删除按钮可用态与「（自定义）」标记）
 */
data class ProviderComboOption(
    val providerId: String,
    val displayName: String,
    val type: ProviderPlanType,
    val baseUrl: String,
    val isCustom: Boolean,
)

/**
 * 提供商档案 → 选择下拉选项列表。
 *
 * 每个有效的 [PlanPreset] 展开为一条选项（一条记录一个类型）；空 baseUrl 的 plan
 * 忽略；选项按 [ProviderPlanType.DISPLAY_ORDER]（按量付费 → Token Plan → Coding Plan）
 * 排序，顺序稳定可预期。
 */
object ProviderComboOptions {

    fun of(
        providerId: String,
        displayName: String,
        plans: List<PlanPreset>,
        isCustom: Boolean,
    ): List<ProviderComboOption> =
        plans
            .filter { it.baseUrl.isNotBlank() }
            .map { ProviderComboOption(providerId, displayName, it.type, it.baseUrl, isCustom) }
            .distinct()
            .sortedBy { ProviderPlanType.DISPLAY_ORDER.indexOf(it.type) }
}