package com.lihtdev.lingxicode.settings

/**
 * 模型条目唯一性判定：「供应商 + 类型 + 模型」必须唯一。
 *
 * 「供应商」取界面上可见的提供商名称 [AiProviderConfig.displayName]（而非 providerId）：
 * 自定义提供商每次弹窗生成的 providerId 随机（`custom-$uuid`），无法作为稳定身份；
 * 而表格「供应商」列展示的正是 displayName，与用户可见的三列语义保持一致。
 * 判定基于 trim 后精确匹配，简单可预期。
 */
object ModelUniqueness {

    /** 关键分隔符（各字段 trim 后拼接，避免边界歧义） */
    private const val SEP = "\u0001"

    /** 归一化唯一键：供应商 + 类型 + 模型 */
    fun key(displayName: String, planType: ProviderPlanType, model: String): String =
        "${displayName.trim()}$SEP${planType.name}$SEP${model.trim()}"

    /** 单个配置的唯一键（编辑校验用，需排除自身 id 时由调用方过滤） */
    fun keyOf(config: AiProviderConfig): String = key(config.displayName, config.planType, config.model)

    /** 从现有模型条目构建键集合 */
    fun keysOf(configs: List<AiProviderConfig>): Set<String> =
        configs.mapTo(linkedSetOf()) { keyOf(it) }

    /**
     * 把候选模型名按现有键切分为「(重复列表, 唯一列表)」。
     *
     * - 命中 [existingKeys] 的模型名归入重复；
     * - 同一批内已接受过的模型名（含仅 trim 不同的等价名）只保留首次出现，其余归入重复；
     * - [models] 相对顺序保持稳定。
     */
    fun partition(
        existingKeys: Set<String>,
        displayName: String,
        planType: ProviderPlanType,
        models: List<String>,
    ): Pair<List<String>, List<String>> {
        val duplicates = mutableListOf<String>()
        val uniques = mutableListOf<String>()
        val acceptedKeys = HashSet<String>()
        for (model in models) {
            val k = key(displayName, planType, model)
            if (k in existingKeys || !acceptedKeys.add(k)) {
                duplicates.add(model)
            } else {
                uniques.add(model)
            }
        }
        return duplicates.distinct() to uniques
    }
}