package com.web.webide.ui.welcome

import androidx.compose.ui.graphics.Color

/**
 * 主题颜色数据类
 */
data class ThemeColor(
    val name: String,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val primaryColor: Color,
    val accentColor: Color
)

/**
 * ✅ 核心修复: 预定义主题颜色列表，已移除 "莫奈"
 */
val themeColors = listOf(
    ThemeColor("Catppuccin", Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFFCBA6F7), Color(0xFFF5C2E7)),
    ThemeColor("青苹果", Color(0xFFF0F9F4), Color(0xFFE1F5E8), Color(0xFF34C759), Color(0xFF30D158)),
    ThemeColor("薰衣草", Color(0xFFF5F3FF), Color(0xFFE8E4FF), Color(0xFF8B7EFF), Color(0xFFB8ADFF)),
    ThemeColor("午夜", Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF58A6FF), Color(0xFF79C0FF)),
    ThemeColor("Nord", Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF88C0D0), Color(0xFF81A1C1)),
    ThemeColor("草莓", Color(0xFFFFF0F3), Color(0xFFFFE1E8), Color(0xFFFF6B9D), Color(0xFFFF8FB4)),
    ThemeColor("Tako", Color(0xFF1A1625), Color(0xFF2D2640), Color(0xFF9D7CD8), Color(0xFFB79FE8))
)

/**
 * 欢迎流程步骤枚举
 */
enum class WelcomeStep {
    INTRO,
    PERMISSIONS,
    THEME_SETUP
}