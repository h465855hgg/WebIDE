package com.web.webide.ui.settings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.web.webide.ui.welcome.ThemeColor
import com.web.webide.ui.welcome.themeColors
import com.web.webide.ui.welcome.ColorPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (Int, Int, Color, Boolean) -> Unit,
    initialModeIndex: Int = 0,
    initialThemeIndex: Int = 0 // ✅ 改动: 默认索引改为0
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
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "主题设置",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "选择主题",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 模式选择
                Text(
                    text = "模式选择",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))
                // ✅ 改动: 独立定义 modeOptions
                val modeOptions = listOf("跟随系统", "浅色模式", "深色模式")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedModeIndex == index,
                            onClick = { selectedModeIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
                            icon = {}
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 颜色主题选择
                Text(
                    text = "颜色主题",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))
                // ✅ 改动: colorOptions 直接使用 themeColors
                val colorOptions = themeColors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.width(0.dp))
                    colorOptions.forEachIndexed { index, theme ->
                        com.web.webide.ui.welcome.ThemePreviewCard(
                            theme = theme,
                            isSelected = selectedThemeIndex == index,
                            onClick = { selectedThemeIndex = index }
                        )
                    }
                    com.web.webide.ui.welcome.CustomThemeCard(
                        isSelected = selectedThemeIndex == themeColors.size,
                        onClick = {
                            selectedThemeIndex = themeColors.size
                            showColorPicker = true
                        }
                    )
                    Spacer(modifier = Modifier.width(0.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val isCustom = selectedThemeIndex == themeColors.size
                            onThemeSelected(selectedModeIndex, selectedThemeIndex, customColor, isCustom)
                            onDismiss()
                        }
                    ) {
                        Text("应用")
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = customColor,
            onColorSelected = { color ->
                customColor = color
                showColorPicker = false
            },
            onDismiss = {
                showColorPicker = false
                // 如果用户取消选择自定义颜色，则将主题选择重置为第一个颜色主题
                if (selectedThemeIndex == themeColors.size) {
                    selectedThemeIndex = 0
                }
            }
        )
    }
}