package com.lihtdev.lingxicode.settings

/**
 * 表格列宽分配：以各列最小宽为基线，剩余空间（可用宽 − 最小宽之和）按权重比例分配。
 *
 * 设置页模型表格的权重取「理想宽 − 最小宽」，因此：
 * - 可用宽 = 理想宽之和时，精确还原设计列宽；
 * - 可用宽 = 最小宽之和时，各列恰为最小宽（收窄下限）；
 * - 两者之间线性过渡，超出时按同一权重继续增长。
 *
 * @param available 可用总宽度（像素）
 * @param minWidths 各列最小宽（分配下限，原样保留在结果中）
 * @param weights   各列分配权重（必须非负；为零的列不参与剩余分配，如固定宽操作列。
 *                  注意：本函数不做上限钳制，有 maxWidth 约束的列权重必须为 0）
 * @return 各列目标宽度；available ≤ ΣminWidths 时直接返回最小宽
 */
internal fun computeColumnWidths(available: Int, minWidths: IntArray, weights: IntArray): IntArray {
    require(minWidths.size == weights.size) { "minWidths 与 weights 长度必须一致" }
    require(weights.all { it >= 0 }) { "weights 必须非负" }
    val n = minWidths.size
    val result = minWidths.copyOf()
    if (n == 0) return result

    var surplus = available - minWidths.sum()
    if (surplus <= 0) return result

    val weightSum = weights.sum()
    if (weightSum <= 0) {
        // 防御：无任何权重时剩余空间均分
        val share = surplus / n
        for (i in 0 until n) {
            result[i] += share
        }
        surplus -= share * n
        var i = 0
        while (surplus > 0) {
            result[i % n]++
            surplus--
            i++
        }
        return result
    }

    // 按权重分配（向下取整）
    var allocated = 0
    for (i in 0 until n) {
        val share = (surplus.toLong() * weights[i] / weightSum).toInt()
        result[i] += share
        allocated += share
    }
    // 取整余量多轮兜底：按列序补给有权重的列，保证总和贴合可用宽度
    var rest = surplus - allocated
    while (rest > 0) {
        var changed = false
        for (i in 0 until n) {
            if (rest == 0) break
            if (weights[i] > 0) {
                result[i]++
                rest--
                changed = true
            }
        }
        if (!changed) break
    }
    return result
}
