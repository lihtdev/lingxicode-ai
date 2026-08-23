package com.lihtdev.codesense.settings

/**
 * 用户自定义提供商档案（持久化到 codesense-ai.xml）。
 *
 * 与预设提供商（[ProviderPresets.ALL]）不同，自定义提供商是用户可增删改查的对象：
 * - [id]：稳定标识（`custom-$uuid`），模型条目经 [AiProviderConfig.providerId] 引用
 * - [displayName]：界面上可见的提供商名称
 * - [plans]：支持的类型 → baseUrl（类型语义与预设一致，区别仅 baseUrl）
 */
data class UserProvider(
    var id: String = "",
    var displayName: String = "",
    var plans: MutableList<PlanPreset> = mutableListOf(),
) {
    companion object {

        /**
         * 从既有模型条目回填自定义提供商档案。
         *
         * 把 providerId 不属于任何预设的条目按 providerId 分组生成档案；
         * 与 [existing] 中的已有档案按 id 合并（保留已存档案，只补缺失的档案与 plans），
         * 保证旧的自定义模型条目的提供商能出现在「选择提供商」下拉，且沿用原 providerId
         * 使其后续新增的模型与旧条目保持同组。
         */
        fun backfillFrom(
            entries: List<AiProviderConfig>,
            existing: List<UserProvider> = emptyList(),
        ): List<UserProvider> {
            val merged = LinkedHashMap<String, UserProvider>()
            existing.forEach { merged[it.id] = it.copy(plans = it.plans.toMutableList()) }
            entries
                // 按 base id 分组：带类型后缀的 providerId（自定义-x:TOKEN_PLAN）归并回同一档案
                .groupBy { ProviderIds.baseOf(it.providerId) }
                .toSortedMap()
                .forEach { (providerId, group) ->
                    // 仅回填自定义提供商（providerId 不属于任何预设）
                    if (providerId.isNotBlank() && ProviderPresets.byId(providerId) == null) {
                        val current = merged[providerId]
                        if (current == null) {
                            merged[providerId] = UserProvider(
                                id = providerId,
                                displayName = group.firstOrNull()?.displayName.orEmpty(),
                                plans = group.mapNotNull { planPresetOf(it) }.distinct().toMutableList(),
                            )
                        } else {
                            // 补充缺失的 plans，不覆盖已有档案的显示名
                            val existingTypes = current.plans.map { it.type }.toSet()
                            val missing = group
                                .mapNotNull { planPresetOf(it) }
                                .filter { it.type !in existingTypes }
                            if (missing.isNotEmpty()) {
                                current.plans.addAll(missing)
                            }
                        }
                    }
                }
            return merged.values.toList()
        }

        /** 把模型条目中的 (planType, baseUrl) 转为 PlanPreset；baseUrl 为空时忽略 */
        private fun planPresetOf(config: AiProviderConfig): PlanPreset? =
            config.baseUrl.takeIf { it.isNotBlank() }?.let { PlanPreset(config.planType, it) }
    }
}