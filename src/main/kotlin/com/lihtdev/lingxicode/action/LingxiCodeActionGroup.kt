package com.lihtdev.lingxicode.action

import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * 编辑器右键菜单「LingxiCode AI」功能组。
 *
 * v1 为空组（预留挂载点）：后续「AI 代码解释」等功能经
 * plugin.xml 的 `lingxicode.aiFeature` 扩展点注册后挂载到此组。
 */
class LingxiCodeActionGroup : DefaultActionGroup()
