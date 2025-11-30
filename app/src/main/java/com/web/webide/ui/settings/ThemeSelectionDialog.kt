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
import com.web.webide.core.utils.LogCatcher // 导入日志工具

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (Int, Int, Color, Boolean) -> Unit,
    initialModeIndex: Int = 0,
    initialThemeIndex: Int = 0
) {
    var selectedModeIndex by remember { mutableStateOf(initialModeIndex) }
    var selectedThemeIndex by remember { mutableStateOf(initialThemeIndex) }
    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(Color(0xFF6750A4)) }

    Dialog(onDismissRequest = onDismiss) {
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
                            onClick = { selectedModeIndex = index },
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
                            onClick = { selectedThemeIndex = index }
                        )
                    }
                    // 自定义 (入口)
                    com.web.webide.ui.welcome.CustomThemeCard(
                        isSelected = selectedThemeIndex == themeColors.size,
                        onClick = {
                            selectedThemeIndex = themeColors.size
                            showColorPicker = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 底部按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val isCustom = selectedThemeIndex == themeColors.size

                            // [Debug Log] UI层点击确认
                            LogCatcher.i("ThemeDebug_UI", "用户点击应用: 模式=$selectedModeIndex, 主题Index=$selectedThemeIndex, 是否自定义=$isCustom")
                            LogCatcher.i("ThemeDebug_UI", "自定义颜色Hex: #${Integer.toHexString(customColor.value.toInt())}")

                            onThemeSelected(selectedModeIndex, selectedThemeIndex, customColor, isCustom)
                            onDismiss()
                        }
                    ) { Text("应用") }
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
            }
        )
    }
}