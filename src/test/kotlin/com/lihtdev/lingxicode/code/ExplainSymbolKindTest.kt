package com.lihtdev.lingxicode.code

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ExplainSymbolKind.isExplainableDeclaration 单元测试（gutter 图标挂载范围的判定） */
class ExplainSymbolKindTest {

    @Test
    fun `声明级符号可解释`() {
        assertTrue(ExplainSymbolKind.CLASS.isExplainableDeclaration)
        assertTrue(ExplainSymbolKind.INTERFACE.isExplainableDeclaration)
        assertTrue(ExplainSymbolKind.METHOD.isExplainableDeclaration)
        assertTrue(ExplainSymbolKind.FUNCTION.isExplainableDeclaration)
    }

    @Test
    fun `非声明级符号不可解释`() {
        assertFalse(ExplainSymbolKind.BLOCK.isExplainableDeclaration)
        assertFalse(ExplainSymbolKind.UNKNOWN.isExplainableDeclaration)
    }
}