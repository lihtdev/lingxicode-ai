package com.lihtdev.lingxicode.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SimpleLineDiff 单元测试 */
class SimpleLineDiffTest {

    @Test
    fun `内容一致时返回空列表`() {
        val diff = SimpleLineDiff.unifiedDiff("a\nb\nc", "a\nb\nc")
        assertTrue(diff.isEmpty())
    }

    @Test
    fun `修改一行时生成删除与新增行`() {
        val diff = SimpleLineDiff.unifiedDiff("a\nb\nc", "a\nx\nc")
        assertTrue(diff.contains("-b"))
        assertTrue(diff.contains("+x"))
        // 上下文行保留
        assertTrue(diff.contains(" a"))
        assertTrue(diff.contains(" c"))
        // hunk 头行号正确（1 起，3 行上下文）
        assertTrue(diff.any { it.startsWith("@@ -1,3 +1,3 @@") })
    }

    @Test
    fun `新增行只产生加号行`() {
        val diff = SimpleLineDiff.unifiedDiff("a\nb", "a\nb\nc")
        assertTrue(diff.contains("+c"))
        assertTrue(diff.none { it.startsWith("-") })
    }

    @Test
    fun `删除行只产生减号行`() {
        val diff = SimpleLineDiff.unifiedDiff("a\nb\nc", "a\nb")
        assertTrue(diff.contains("-c"))
        assertTrue(diff.none { it.startsWith("+") })
    }

    @Test
    fun `新文件（before 为 null）全部为加号行`() {
        val diff = SimpleLineDiff.unifiedDiff(null, "line1\nline2")
        assertEquals(listOf("+line1", "+line2"), diff.filter { it.startsWith("+") })
        assertTrue(diff.none { it.startsWith("-") })
    }

    @Test
    fun `删除文件（after 为 null）全部为减号行`() {
        val diff = SimpleLineDiff.unifiedDiff("line1\nline2", null)
        assertEquals(listOf("-line1", "-line2"), diff.filter { it.startsWith("-") })
    }

    @Test
    fun `多处变更生成多个 hunk`() {
        val before = (1..20).joinToString("\n") { "line$it" }
        val after = (1..20).joinToString("\n") { if (it == 2 || it == 18) "changed$it" else "line$it" }
        val diff = SimpleLineDiff.unifiedDiff(before, after)
        // 两处变更相距超过 2×3 行上下文，应拆为两个 hunk
        val hunkCount = diff.count { it.startsWith("@@") }
        assertEquals(2, hunkCount)
    }

    @Test
    fun `超大文件降级为整文件替换`() {
        val before = (1..2500).joinToString("\n") { "b$it" }
        val after = (1..2500).joinToString("\n") { "a$it" }
        val diff = SimpleLineDiff.unifiedDiff(before, after)
        // 降级模式：无 hunk 头，全删全加
        assertTrue(diff.none { it.startsWith("@@") })
        assertEquals(2500, diff.count { it.startsWith("-") })
        assertEquals(2500, diff.count { it.startsWith("+") })
    }
}
