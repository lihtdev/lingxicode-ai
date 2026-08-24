package com.lihtdev.lingxicode.settings

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [computeColumnWidths]（表格列宽分配：最小宽为基线，剩余空间按「理想宽 − 最小宽」权重分配）单元测试。
 *
 * 列参数取自设置页模型表格真实常量：
 * 最小宽 = [模型 150, 标签 135, 供应商 95, 类型 75, 操作 68]（合计 523）
 * 理想宽 = [模型 200, 标签 200, 供应商 118, 类型 92, 操作 68]（合计 678）
 * 权重   = 理想宽 − 最小宽 = [50, 65, 23, 17, 0]
 */
class TableColumnWidthAllocatorTest {

    private val minWidths = intArrayOf(150, 135, 95, 75, 68)
    private val weights = intArrayOf(50, 65, 23, 17, 0)

    @Test
    fun `默认宽度精确还原理想宽`() {
        // 可用宽度 = 理想宽之和（678）时，剩余空间 = 权重之和，每列恰好分得自身权重
        val result = computeColumnWidths(678, minWidths, weights)

        assertArrayEquals(intArrayOf(200, 200, 118, 92, 68), result)
    }

    @Test
    fun `可用宽度等于最小宽之和时各列恰为最小宽`() {
        val result = computeColumnWidths(523, minWidths, weights)

        assertArrayEquals(minWidths, result)
    }

    @Test
    fun `收窄时各列不小于最小宽且总和贴合可用宽度`() {
        // 设置窗口允许的最小表格宽度（scrollPane minimumSize = 560）
        val result = computeColumnWidths(560, minWidths, weights)

        assertEquals(560, result.sum())
        result.forEachIndexed { i, w -> assertTrue(w >= minWidths[i], "第 $i 列 $w 小于最小宽 ${minWidths[i]}") }
        // 权重为零的操作列不增长
        assertEquals(68, result[4])
    }

    @Test
    fun `拉宽时按权重增长且总和贴合可用宽度`() {
        val result = computeColumnWidths(1000, minWidths, weights)

        assertEquals(1000, result.sum())
        result.forEachIndexed { i, w -> assertTrue(w >= minWidths[i], "第 $i 列 $w 小于最小宽 ${minWidths[i]}") }
        // 标签列权重最大（65），增长量应高于供应商列（23）
        assertTrue(result[1] - minWidths[1] > result[2] - minWidths[2])
        // 操作列恒为最小宽
        assertEquals(68, result[4])
    }

    @Test
    fun `任意可用宽度下总和均贴合且不低于最小宽`() {
        listOf(523, 524, 560, 678, 679, 700, 1000, 1500).forEach { available ->
            val result = computeColumnWidths(available, minWidths, weights)

            assertEquals(available, result.sum(), "available=$available 时总和应贴合")
            result.forEachIndexed { i, w ->
                assertTrue(w >= minWidths[i], "available=$available 时第 $i 列 $w 小于最小宽 ${minWidths[i]}")
            }
        }
    }

    @Test
    fun `可用宽度小于最小宽之和时兜底为最小宽`() {
        // 防御分支：现实窗口不会这么窄（minimumSize 兜底），但函数不应返回小于最小宽的结果
        val result = computeColumnWidths(400, minWidths, weights)

        assertArrayEquals(minWidths, result)
    }

    @Test
    fun `权重全零时剩余空间均分`() {
        val result = computeColumnWidths(110, intArrayOf(10, 10, 10), intArrayOf(0, 0, 0))

        assertEquals(110, result.sum())
        result.forEach { assertTrue(it >= 36, "均分时每列至少 10 + 80/3 = 36，实际 $it") }
        // 均分差额不超过 1
        assertTrue(result.max() - result.min() <= 1)
    }

    @Test
    fun `空列返回空数组`() {
        assertArrayEquals(IntArray(0), computeColumnWidths(678, IntArray(0), IntArray(0)))
    }

    @Test
    fun `单列场景剩余空间全部分配给该列`() {
        val result = computeColumnWidths(500, intArrayOf(100), intArrayOf(1))

        assertArrayEquals(intArrayOf(500), result)
    }

    @Test
    fun `minWidths 与 weights 长度不一致时抛异常`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            computeColumnWidths(678, intArrayOf(150, 135), intArrayOf(50))
        }
    }

    @Test
    fun `负权重时抛异常`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            computeColumnWidths(678, minWidths, intArrayOf(50, -1, 23, 17, 0))
        }
    }
}
