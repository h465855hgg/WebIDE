/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.web.webide.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

object EditorColorSchemeManager {

    /**
     * 将 Material 主题色应用到现有的 EditorColorScheme
     * 直接使用 Compose 的 ColorScheme，无需重复计算 HCT
     */
    fun applyThemeColors(scheme: EditorColorScheme, colorScheme: ColorScheme) {
        val primary = colorScheme.primary.toArgb()
        val surface = colorScheme.surface.toArgb()
        val surfaceVariant = colorScheme.surfaceVariant.toArgb()
        val background = colorScheme.background.toArgb()
        val onSurfaceVariant = colorScheme.onSurfaceVariant.toArgb()

        // 明暗判断必须基于 Material 主题色（colorScheme.background），它总是可靠地反映
        // 应用当前的实际明暗模式。不能用 editor scheme 的 WHOLE_BACKGROUND：当编辑器使用
        // TextMateColorScheme 时，getColor(WHOLE_BACKGROUND) 会被覆盖为 TextMate 主题计算值，
        // 而 TextMate 主题是异步应用的，此时可能仍是浅色，导致误判为亮色、补全文字仍为黑色。
        val isDark = isColorDark(background)

        scheme.apply {
            // 只更新基础背景和文本颜色
            setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surface)
            setColor(EditorColorScheme.LINE_DIVIDER, surfaceVariant)
            setColor(EditorColorScheme.LINE_NUMBER, onSurfaceVariant)
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, primary)

            // 当前行高亮
            setColor(EditorColorScheme.CURRENT_LINE, adjustAlpha(surfaceVariant, 0.3f))

            // 选择相关
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, adjustAlpha(primary, 0.25f))
            setColor(EditorColorScheme.SELECTION_INSERT, primary)
            setColor(EditorColorScheme.SELECTION_HANDLE, primary)

            // 滚动条
            setColor(EditorColorScheme.SCROLL_BAR_THUMB, adjustAlpha(onSurfaceVariant, 0.3f))
            setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, adjustAlpha(primary, 0.5f))

            // 自动完成窗口
            setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surface)
            setColor(EditorColorScheme.COMPLETION_WND_CORNER, surfaceVariant)
            setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, adjustAlpha(primary, 0.2f))
            // 补全窗口文字颜色：sora 默认依赖 isDark()（恒为 false）导致暗色下显示黑字看不清。
            // 用 Material 主题的明暗判断显式覆盖：暗色模式用纯白，亮色模式跟随主题前景色。
            if (isDark) {
                setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, AndroidColor.WHITE)
                setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, 0xFFB0B0B0.toInt())
            } else {
                setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, colorScheme.onSurface.toArgb())
                setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, onSurfaceVariant)
            }

            // 文本操作弹窗 (双击/长按弹出的菜单)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, surface)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, primary)

            // 括号匹配
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, primary)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, AndroidColor.TRANSPARENT)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, AndroidColor.TRANSPARENT)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, primary)

            // 下划线
            setColor(EditorColorScheme.UNDERLINE, primary)

            // 代码块线条
            setColor(EditorColorScheme.BLOCK_LINE, surfaceVariant)
            setColor(EditorColorScheme.BLOCK_LINE_CURRENT, primary)
            setColor(EditorColorScheme.SIDE_BLOCK_LINE, surfaceVariant)

            // 确保文本颜色适配深色/浅色模式 (针对 TreeSitter 或默认编辑器)
            val onBackground = colorScheme.onBackground.toArgb()
            setColor(EditorColorScheme.TEXT_NORMAL, onBackground)

            // 如果是浅色模式，优化 TreeSitter 的默认高亮颜色 (简单的覆盖，防止看不清)
            if (!isDark) {
                 setColor(EditorColorScheme.KEYWORD, 0xFF0000FF.toInt()) // 蓝色关键字
                 setColor(EditorColorScheme.COMMENT, 0xFF008000.toInt()) // 绿色注释
                 setColor(EditorColorScheme.LITERAL, 0xFF098658.toInt()) // 深绿数字/常量
                 setColor(EditorColorScheme.OPERATOR, 0xFF333333.toInt()) // 深灰操作符
                 setColor(EditorColorScheme.IDENTIFIER_NAME, 0xFF001080.toInt()) // 深蓝标识符
                 setColor(EditorColorScheme.IDENTIFIER_VAR, 0xFF001080.toInt()) // 深蓝变量
                 setColor(EditorColorScheme.FUNCTION_NAME, 0xFF795E26.toInt()) // 金色函数名
                 setColor(EditorColorScheme.ATTRIBUTE_NAME, 0xFF001080.toInt()) // 属性名
                 setColor(EditorColorScheme.ATTRIBUTE_VALUE, 0xFFA31515.toInt()) // 属性值
                 setColor(EditorColorScheme.HTML_TAG, 0xFF800000.toInt()) // HTML标签
            }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        return (r * 0.299 + g * 0.587 + b * 0.114) < 128
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        return AndroidColor.argb(a, r, g, b)
    }

    /**
     * 获取 Diff 视图的新增行背景色
     */
    fun getDiffAddColor(scheme: EditorColorScheme): Int {
        val isDark = isDarkScheme(scheme)
        // 深色模式下用深绿，浅色模式下用浅绿，或者统一用半透明绿
        return if (isDark) 0x401B5E20 else 0x40A5D6A7
    }

    /**
     * 获取 Diff 视图的删除行背景色
     */
    fun getDiffDeleteColor(scheme: EditorColorScheme): Int {
        val isDark = isDarkScheme(scheme)
        return if (isDark) 0x40B71C1C else 0x40EF9A9A
    }

    /**
     * 获取 Diff 视图的新增行背景色 (Word Level)
     */
    fun getDiffAddWordColor(scheme: EditorColorScheme): Int {
        val isDark = isDarkScheme(scheme)
        return if (isDark) 0x802E7D32.toInt() else 0x8066BB6A.toInt()
    }

    /**
     * 获取 Diff 视图的删除行背景色 (Word Level)
     */
    fun getDiffDeleteWordColor(scheme: EditorColorScheme): Int {
        val isDark = isDarkScheme(scheme)
        return if (isDark) 0x80C62828.toInt() else 0x80EF5350.toInt()
    }

    private fun isDarkScheme(scheme: EditorColorScheme): Boolean {
        val bg = scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
        // 简单计算亮度，如果 R/G/B 平均值小于 128 认为是深色
        val r = AndroidColor.red(bg)
        val g = AndroidColor.green(bg)
        val b = AndroidColor.blue(bg)
        return (r * 0.299 + g * 0.587 + b * 0.114) < 128
    }
}