package com.lihtdev.lingxicode.code

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

    @Test
    fun `带注解的方法判定为 METHOD`() {
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("@Override\npublic void foo() {"))
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("@Test\nvoid testIt() {"))
    }

    @Test
    fun `带修饰符的函数判定为 FUNCTION`() {
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("suspend fun fetch(): String {"))
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("private fun bar() {"))
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("pub fn main() {"))
        assertEquals(ExplainSymbolKind.FUNCTION, SymbolKindDetector.detect("export function go() {"))
    }

    @Test
    fun `C# string 返回类型判定为 METHOD`() {
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("public string GetName() {"))
    }

    @Test
    fun `无关键字的名称加参数列表判定为 METHOD`() {
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("foo(): string {"))
        assertEquals(ExplainSymbolKind.METHOD, SymbolKindDetector.detect("async foo(x: number): void {"))
    }

    @Test
    fun `变量持有函数不误判为 FUNCTION`() {
        assertEquals(ExplainSymbolKind.BLOCK, SymbolKindDetector.detect("let handler = function() {"))
    }

    @Test
    fun `Python 装饰器函数判定为 FUNCTION`() {
        assertEquals(
            ExplainSymbolKind.FUNCTION,
            SymbolKindDetector.detect("@app.route(\"/hello\")\ndef hello():"),
        )
    }

    @Test
    fun `控制语句不误判为 METHOD`() {
        assertEquals(ExplainSymbolKind.BLOCK, SymbolKindDetector.detect("for (int i = 0; i < 10; i++) {"))
    }
}