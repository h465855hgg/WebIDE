




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
// 文件: java/com/example/sorarunrun/terminal/TerminalConfig.kt
package com.web.webide.ui.terminal

import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle

object TerminalConfig {

    // === 动态颜色配置 ===
    const val VIRTUAL_KEYS_JSON = """
[
  [
    "ESC",
    {
      "key": "/",
      "popup": "\\"
    },
    {
      "key": "-",
      "popup": "|"
    },
    "HOME",
    "UP",
    "END",
    "PGUP"
  ],
  [
    "TAB",
    "CTRL",
    "ALT",
    "LEFT",
    "DOWN",
    "RIGHT",
    "PGDN"
  ]
]
"""
    // 获取背景色
    fun getBackgroundColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFF000000.toInt() // 深色模式：纯黑
        } else {
            0xFFFFFFFF.toInt() // 浅色模式：纯白
        }
    }

    // 获取前景色（文字颜色）
    fun getForegroundColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFFFFFFFF.toInt() // 深色模式：白色文字
        } else {
            0xFF1C1B1F.toInt() // 浅色模式：深灰文字
        }
    }

    // 获取光标颜色
    fun getCursorColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFFFFFFFF.toInt() // 深色模式：白色光标
        } else {
            0xFF1C1B1F.toInt() // 浅色模式：深灰光标
        }
    }

    /**
     * 将颜色方案应用到终端全局静态颜色方案（TerminalColors.COLOR_SCHEME）
     * 这样新建的会话和 OSC 重置都会使用正确的颜色
     */
    fun applyColorScheme(isDark: Boolean) {
        val scheme = TerminalColors.COLOR_SCHEME
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = getForegroundColor(isDark)
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = getBackgroundColor(isDark)
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = getCursorColor(isDark)
    }

    // === 底部虚拟按键栏颜色 ===

    // 获取按钮文字颜色
    fun getButtonTextColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFFFFFFFF.toInt() // 深色模式：白色文字
        } else {
            0xFF1C1B1F.toInt() // 浅色模式：深灰文字
        }
    }

    // 获取底部栏背景色（与终端背景协调）
    fun getButtonBarBgColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFF1A1B1E.toInt() // 深色模式：深灰，略亮于纯黑终端背景以做视觉区分
        } else {
            0xFFEEEEEE.toInt() // 浅色模式：浅灰，略暗于纯白终端背景以做视觉区分
        }
    }

    // 获取按钮激活态文字颜色（CTRL/ALT 按下时）
    fun getButtonActiveTextColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFFEF5350.toInt() // 深色模式：亮红色，在深色背景上清晰
        } else {
            0xFFD32F2F.toInt() // 浅色模式：深红色，在浅色背景上清晰
        }
    }

    // 获取按钮按下时的背景色
    fun getButtonActiveBgColor(isDark: Boolean): Int {
        return if (isDark) {
            0xFF3C3C3C.toInt() // 深色模式：中灰，在深色背景上有反馈
        } else {
            0xFFBDBDBD.toInt() // 浅色模式：中灰，在浅色背景上有反馈
        }
    }

}
