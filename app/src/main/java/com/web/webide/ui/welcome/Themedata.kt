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


package com.web.webide.ui.welcome

import androidx.compose.ui.graphics.Color


data class ThemeColor(
    val name: String,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val primaryColor: Color,
    val accentColor: Color
)


val themeColors = listOf(
    ThemeColor("Catppuccin", Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFCBA6F7), Color(0xFFF5C2E7)),
    ThemeColor("Apple", Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF32D74B), Color(0xFF007AFF)),
    ThemeColor("Lavender", Color(0xFF1A1626), Color(0xFF49454F), Color(0xFFB8ADFF), Color(0xFFCBC0FF)),
    ThemeColor("Midnight", Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF58A6FF), Color(0xFF79C0FF)),
    ThemeColor("Nord", Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF88C0D0), Color(0xFF81A1C1)),
    ThemeColor("Strawberry", Color(0xFF201418), Color(0xFF524347), Color(0xFFFF8FB4), Color(0xFFFFB1C8)),
    ThemeColor("Tako", Color(0xFF1A1625), Color(0xFF2D2640), Color(0xFF9D7CD8), Color(0xFFB79FE8))
)


enum class WelcomeStep {
    INTRO,          // 介绍/开始
    PERMISSIONS,    // 权限
    THEME_SETUP     // 主题设置
}