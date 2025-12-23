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

package com.web.webide.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.web.webide.ui.welcome.themeColors
import com.web.webide.ui.welcome.ColorPickerDialog
import com.web.webide.core.utils.LogCatcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    // 这个回调会更新全局 ViewModel/DataStore
    onThemeSelected: (Int, Int, Color, Boolean) -> Unit,
    initialModeIndex: Int = 0,
    initialThemeIndex: Int = 0,
    initialCustomColor: Color = Color(0xFF6750A4), // 建议传入初始自定义颜色
    initialIsCustom: Boolean = false // 建议传入初始是否为自定义
) {
    // 1. 记录初始状态，用于"取消"时回滚
    val originMode = remember { initialModeIndex }
    val originTheme = remember { initialThemeIndex }
    val originColor = remember { initialCustomColor }
    val originIsCustom = remember { initialIsCustom }

    // 2. 本地 UI 状态
    var selectedModeIndex by remember { mutableIntStateOf(initialModeIndex) }
    var selectedThemeIndex by remember { mutableIntStateOf(initialThemeIndex) }
    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(initialCustomColor) }

    // 辅助函数：立即应用主题 (实现实时预览的核心)
    fun applyThemeNow(
        mode: Int = selectedModeIndex,
        themeIdx: Int = selectedThemeIndex,
        color: Color = customColor
    ) {
        val isCustom = themeIdx == themeColors.size
        // 立即触发外部回调，界面会瞬间变色
        onThemeSelected(mode, themeIdx, color, isCustom)

        LogCatcher.d("ThemeDebug_Preview", "实时预览: Mode=$mode, Theme=$themeIdx, Color=${color.value}")
    }

    Dialog(onDismissRequest = {
        // 点击外部区域关闭时，视为"取消"，回滚状态
        onThemeSelected(originMode, originTheme, originColor, originIsCustom)
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("选择主题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 模式
                Text("模式", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                val modeOptions = listOf("跟随系统", "浅色", "深色")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedModeIndex == index,
                            onClick = {
                                selectedModeIndex = index
                                // 🔥 关键修改：点击即应用
                                applyThemeNow(mode = index)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
                            icon = {}
                        ) { Text(label) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 颜色列表
                Text("颜色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 预设
                    themeColors.forEachIndexed { index, theme ->
                        com.web.webide.ui.welcome.ThemePreviewCard(
                            theme = theme,
                            isSelected = selectedThemeIndex == index,
                            onClick = {
                                selectedThemeIndex = index
                                // 🔥 关键修改：点击即应用
                                applyThemeNow(themeIdx = index)
                            }
                        )
                    }
                    // 自定义 (入口)
                    com.web.webide.ui.welcome.CustomThemeCard(
                        isSelected = selectedThemeIndex == themeColors.size,
                        onClick = {
                            selectedThemeIndex = themeColors.size
                            // 这里先不应用，等选完颜色再应用，或者这里先应用上次的自定义色
                            applyThemeNow(themeIdx = themeColors.size)
                            showColorPicker = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 底部按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        // 🛑 "取消"逻辑：回滚到最初的状态
                        onThemeSelected(originMode, originTheme, originColor, originIsCustom)
                        onDismiss()
                    }) { Text("取消") }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // ✅ "确定"逻辑：什么都不用做，因为已经是最新状态了，直接关闭即可
                            onDismiss()
                        }
                    ) { Text("完成") }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = customColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                customColor = color
                showColorPicker = false
                // 🔥 关键修改：选完颜色立即刷新
                // 确保选中"自定义"选项
                selectedThemeIndex = themeColors.size
                applyThemeNow(themeIdx = themeColors.size, color = color)
            }
        )
    }
}