package com.lihtdev.codesense.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 共享 chip（圆角标签）组件：模型切换弹窗的类型/模型标签、设置页下拉的类型标签等复用。
 * 视觉参数与设置页 `TagChipStyle`（settings/SettingsConfigurable.kt）保持一致：
 * 填充 H_PAD 7 / V_PAD 3、圆角 ARC 10、灰底圆角边框。
 */
object ChipComponents {

    /** chip 圆角半径 */
    private const val ARC = 10

    /** chip 水平内边距（文字两侧） */
    private const val H_PAD = 7

    /** chip 垂直内边距（文字上下） */
    private const val V_PAD = 3

    /** chip 填充色（亮/暗主题） */
    private val background = JBColor(0xF5F5F5, 0x454545)

    /**
     * 单个只读 chip：文字 + 圆角边框（无交互按钮）。
     * 字号用 JBFont.small()（约 11px，随 IDE 字体缩放），与设置页标签一致。
     */
    fun chipLabel(text: String): JPanel {
        val label = JLabel(text).apply {
            font = JBFont.small()
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            border = ChipBorder()
            add(label)
        }
    }

    /**
     * 一行 chip（FlowLayout 左对齐），每个 chip tooltip 显示完整文本；
     * 总宽超出 [maxWidth]（>0）时按顺序截断，避免把父容器撑爆。
     */
    fun chipsRow(tags: List<String>, maxWidth: Int = 0): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 1))
        row.isOpaque = false
        var w = 0
        for (tag in tags) {
            val chip = chipLabel(tag)
            chip.toolTipText = tag
            val chipW = chip.preferredSize.width
            if (maxWidth > 0 && w + chipW > maxWidth) break
            row.add(chip)
            w += chipW + 6
        }
        return row
    }

    /** Chip 圆角边框（灰底填充 + 边框线，与设置页风格一致） */
    private class ChipBorder : javax.swing.border.AbstractBorder() {
        override fun paintBorder(
            c: Component?, g: Graphics?,
            x: Int, y: Int, width: Int, height: Int,
        ) {
            val g2 = g as java.awt.Graphics2D
            g2.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
            )
            g2.color = background
            g2.fillRoundRect(x, y, width - 1, height - 1, ARC, ARC)
            g2.color = JBColor.border()
            g2.drawRoundRect(x, y, width - 1, height - 1, ARC, ARC)
        }

        override fun getBorderInsets(c: Component?) =
            JBUI.insets(V_PAD, H_PAD, V_PAD, H_PAD)
    }
}