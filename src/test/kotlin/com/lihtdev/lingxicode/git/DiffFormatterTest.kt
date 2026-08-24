package com.lihtdev.lingxicode.git

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** DiffFormatter 单元测试 */
class DiffFormatterTest {

    @Test
    fun `新文件 section 含路径与加号行`() {
        val section = DiffFormatter.formatFileSection("src/App.kt", ChangeKind.NEW, null, "fun main() {}")
        assertTrue(section.contains("src/App.kt（新增）"))
        assertTrue(section.contains("+fun main() {}"))
    }

    @Test
    fun `修改文件 section 含删除与新增行`() {
        val section = DiffFormatter.formatFileSection("src/A.kt", ChangeKind.MODIFIED, "old", "new")
        assertTrue(section.contains("src/A.kt（修改）"))
        assertTrue(section.contains("-old"))
        assertTrue(section.contains("+new"))
    }

    @Test
    fun `删除文件 section 不含内容 diff`() {
        val section = DiffFormatter.formatFileSection("src/A.kt", ChangeKind.DELETED, "content", null)
        assertTrue(section.contains("src/A.kt（删除）"))
        assertTrue(section.contains("整文件删除"))
        assertFalse(section.contains("-content"))
    }

    @Test
    fun `超过 400 行的文件被截断并注明`() {
        val longText = (1..500).joinToString("\n") { "line$it" }
        val section = DiffFormatter.formatFileSection("big.txt", ChangeKind.NEW, null, longText)
        assertTrue(section.contains("已截断"))
        // 只保留前 400 行（第 401 行起不再出现）
        assertFalse(section.contains("+line401"))
        assertTrue(section.contains("+line400"))
    }

    @Test
    fun `内容无差异时注明`() {
        val section = DiffFormatter.formatFileSection("same.txt", ChangeKind.MODIFIED, "x\ny", "x\ny")
        assertTrue(section.contains("内容无行级差异"))
    }
}
