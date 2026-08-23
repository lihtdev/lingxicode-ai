package com.lihtdev.codesense.settings

import com.lihtdev.codesense.i18n.CodeSenseBundle

/**
 * 提供商 id 工具：按「提供商 × 类型」唯一化。
 *
 * 规则：同名（同 id 基准）不同类的提供商是不同提供商，其 providerId 必须不同
 * （不同类型各自存储独立的 API Key，Key 槽 = providerId）。
 * 唯一 providerId = `"${baseId}:${type.name}"`（如 `qwen:TOKEN_PLAN`）。
 * 旧数据（迁移前）保存的是无类型后缀的 baseId，[baseOf] 可兼容归一。
 */
object ProviderIds {

    /** 生成「提供商 × 类型」唯一 providerId */
    fun of(baseId: String, type: ProviderPlanType): String = "$baseId:${type.name}"

    /** 取 baseId（无 ':' 时原样返回，兼容旧数据） */
    fun baseOf(providerId: String): String = providerId.substringBefore(':')

    /** 是否为旧形态（未带类型后缀）的 providerId */
    fun isLegacy(providerId: String): Boolean = ':' !in providerId
}

/**
 * 选择提供商下拉条目：一个「提供商 × 类型」组合。
 *
 * 规则：一条提供商记录只能有一个类型；同一提供商若支持多种类型（各类型对应
 * 各自的 baseUrl，即使 baseUrl 相同），则在下拉中作为多条记录展示——
 * 名称相同、名称右侧以标签样式标注类型。预设提供商与自定义提供商共用该规则。
 *
 * - [providerId]：本条目的唯一提供商 id（`ProviderIds.of(baseId, type)`），
 *   API Key 按此独立存取、模型条目按此归属；同名不同类型互不相同
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
 * 每个有效的 [PlanPreset] 展开为一条选项（一条记录一个类型，providerId 带类型后缀）；
 * 空 baseUrl 的 plan 忽略；选项按 [ProviderPlanType.DISPLAY_ORDER]
 * （按量付费 → Token Plan → Coding Plan）排序，顺序稳定可预期。
 */
object ProviderComboOptions {

    fun of(
        baseId: String,
        displayName: String,
        plans: List<PlanPreset>,
        isCustom: Boolean,
    ): List<ProviderComboOption> =
        plans
            .filter { it.baseUrl.isNotBlank() }
            .map {
                ProviderComboOption(
                    providerId = ProviderIds.of(baseId, it.type),
                    displayName = displayName,
                    type = it.type,
                    baseUrl = it.baseUrl,
                    isCustom = isCustom,
                )
            }
            .distinct()
            .sortedBy { ProviderPlanType.DISPLAY_ORDER.indexOf(it.type) }
}

/**
 * 套餐类型展示文案（共享：设置页表格/下拉、模型切换弹窗等）。
 */
fun providerPlanTypeLabel(type: ProviderPlanType): String = when (type) {
    ProviderPlanType.TOKEN_PLAN -> CodeSenseBundle.message("provider.type.tokenPlan")
    ProviderPlanType.CODING_PLAN -> CodeSenseBundle.message("provider.type.codingPlan")
    ProviderPlanType.PAY_AS_YOU_GO -> CodeSenseBundle.message("provider.type.payAsYouGo")
}