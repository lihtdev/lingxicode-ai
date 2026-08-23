package com.lihtdev.codesense.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** SymbolKindDetector 单元测试（语言无关的符号类型判别） */
class SymbolKindDetectorTest {

    @Test
    fun `Java 类判定为 CLASS`() {
        assertEquals(ExplainSymbolKind.CLASS, SymbolKindDetector.detect("public class Foo {"))
    }

    @Test
    fun `接口与 trait 判定为 INTERFACE`() {
        assertEquals(ExplainSymbolKind.INTERFACE, SymbolKindDetector.detect("interface Shape {"))
        assertEquals(ExplainSymbolKind.INTERFACE, SymbolKindDetector.detect("trait Logging {"))
    }

    @Test
    fun `Kotlin 函数判定为 FUNCTION`() {
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("fun greet(name: String): String {"))
    }

    @Test
    fun `Python 函数判定为 FUNCTION`() {
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("def add(a, b):"))
    }

    @Test
    fun `JS 函数判定为 FUNCTION`() {
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("function run() {"))
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("async function run() {"))
    }

    @Test
    fun `Java 方法签名判定为 METHOD`() {
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("public static String getName() {"))
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("int add(int a, int b) {"))
    }

    @Test
    fun `普通语句判定为 BLOCK`() {
        assertEquals(ExplainSymbolKind.BLOCK, SymbolKindDetector.detect("if (x > 0) {"))
        assertEquals(ExplainSymbolKind.BLOCK, SymbolKindDetector.detect("val total = a + b"))
    }

    @Test
    fun `空输入判定为 BLOCK`() {
        assertEquals(ExplainSymbolKind.BLOCK, SymbolKindDetector.detect("   \n  \n"))
    }
}