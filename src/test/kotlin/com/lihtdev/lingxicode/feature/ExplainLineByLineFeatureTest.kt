package com.lihtdev.lingxicode.feature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ExplainLineByLineFeature 单元测试 */
class ExplainLineByLineFeatureTest {

    @Test
    fun `输出 token 上限为 32768（逐行解释输出约为代码两倍量级）`() {
        assertEquals(32768, ExplainLineByLineFeature().maxOutputTokens)
    }

    @Test
    fun `清洗策略为保留围栏的提取清洗（防回归：不得回退 cleanMarkdown）`() {
        // 合法输出本身即单个围栏，cleanMarkdown 会剥掉最外层围栏导致渲染退化为段落文本
        val raw = "```kotlin\nval a = 1\n```"
        assertEquals(raw, ExplainLineByLineFeature().cleanResponse(raw))
    }
}
