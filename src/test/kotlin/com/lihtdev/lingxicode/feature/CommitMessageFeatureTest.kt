package com.lihtdev.lingxicode.feature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** CommitMessageFeature 输出 token 上限测试 */
class CommitMessageFeatureTest {

    @Test
    fun `输出 token 上限为 4096（推理模型思考计入输出配额）`() {
        assertEquals(4096, CommitMessageFeature().maxOutputTokens)
    }
}
