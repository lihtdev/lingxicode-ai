package com.lihtdev.lingxicode.feature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ReviewCodeFeature 输出 token 上限测试 */
class ReviewCodeFeatureTest {

    @Test
    fun `输出 token 上限为 16384（推理模型思考计入输出配额）`() {
        assertEquals(16384, ReviewCodeFeature().maxOutputTokens)
    }
}
