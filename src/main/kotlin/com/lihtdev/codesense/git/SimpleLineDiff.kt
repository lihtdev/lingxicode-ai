package com.lihtdev.codesense.git

/**
 * 简易行级 diff（LCS 算法，纯函数，可单测）。
 * 输出 unified diff 风格的变更行：@@ 头 + 带 " "/"-"/"+" 前缀的内容行（上下文 3 行分块）。
 */
object SimpleLineDiff {

    /** 单侧最大参与 LCS 对比的行数（超出降级为整文件替换，避免 O(n²) 内存/耗时爆炸） */
    const val MAX_COMPARE_LINES = 2000

    /** hunk 上下文行数 */
    private const val CONTEXT_LINES = 3

    private sealed class Op {
        data class Keep(val line: String) : Op()
        data class Delete(val line: String) : Op()
        data class Add(val line: String) : Op()
    }

    /**
     * 计算两段文本的 unified diff 行。
     * 内容完全一致时返回空列表。
     */
    fun unifiedDiff(beforeText: String?, afterText: String?): List<String> {
        val before = beforeText?.lines() ?: emptyList()
        val after = afterText?.lines() ?: emptyList()
        if (before == after) return emptyList()
        if (before.size > MAX_COMPARE_LINES || after.size > MAX_COMPARE_LINES) {
            // 过大文件降级：整文件替换（全删全加）
            return buildList {
                before.forEach { add("-$it") }
                after.forEach { add("+$it") }
            }
        }
        return formatHunks(lcsOps(before, after))
    }

    /** LCS 动态规划求编辑操作序列 */
    private fun lcsOps(before: List<String>, after: List<String>): List<Op> {
        val n = before.size
        val m = after.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (before[i] == after[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val ops = mutableListOf<Op>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                before[i] == after[j] -> {
                    ops += Op.Keep(before[i]); i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    ops += Op.Delete(before[i]); i++
                }
                else -> {
                    ops += Op.Add(after[j]); j++
                }
            }
        }
        while (i < n) {
            ops += Op.Delete(before[i]); i++
        }
        while (j < m) {
            ops += Op.Add(after[j]); j++
        }
        return ops
    }

    /** 将操作序列按上下文分块为 unified diff 行（@@ 头 + 内容行） */
    private fun formatHunks(ops: List<Op>): List<String> {
        val changedIndices = ops.indices.filter { ops[it] !is Op.Keep }
        if (changedIndices.isEmpty()) return emptyList()

        // 标记需要展示的操作下标（每个变更处 ±CONTEXT_LINES）
        val show = BooleanArray(ops.size)
        for (idx in changedIndices) {
            for (k in maxOf(0, idx - CONTEXT_LINES)..minOf(ops.lastIndex, idx + CONTEXT_LINES)) {
                show[k] = true
            }
        }

        val out = mutableListOf<String>()
        var beforeLine = 1
        var afterLine = 1
        var idx = 0
        while (idx < ops.size) {
            if (!show[idx]) {
                // 未展示区域：仅推进行号
                when (ops[idx]) {
                    is Op.Keep -> { beforeLine++; afterLine++ }
                    is Op.Delete -> beforeLine++
                    is Op.Add -> afterLine++
                }
                idx++
                continue
            }
            // 开启一个 hunk
            val startB = beforeLine
            val startA = afterLine
            var countB = 0
            var countA = 0
            val body = mutableListOf<String>()
            while (idx < ops.size && show[idx]) {
                when (val op = ops[idx]) {
                    is Op.Keep -> {
                        body += " ${op.line}"; countB++; countA++; beforeLine++; afterLine++
                    }
                    is Op.Delete -> {
                        body += "-${op.line}"; countB++; beforeLine++
                    }
                    is Op.Add -> {
                        body += "+${op.line}"; countA++; afterLine++
                    }
                }
                idx++
            }
            out += "@@ -$startB,$countB +$startA,$countA @@"
            out += body
        }
        return out
    }
}
