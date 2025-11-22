package com.web.webide.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 编辑器主题测试界面
 * 用于测试和演示编辑器主题同步功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorThemeTestScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    
    // 预设主题色
    val themeColors = remember {
        listOf(
            "Material Purple" to Color(0xFF6750A4),
            "Blue" to Color(0xFF2196F3),
            "Green" to Color(0xFF4CAF50),
            "Red" to Color(0xFFF44336),
            "Orange" to Color(0xFFFF9800),
            "Pink" to Color(0xFFE91E63),
            "Teal" to Color(0xFF009688),
            "Indigo" to Color(0xFF3F51B5),
            "Cyan" to Color(0xFF00BCD4),
            "Lime" to Color(0xFFCDDC39)
        )
    }
    
    var selectedColorIndex by remember { mutableStateOf(0) }
    val currentColor = themeColors[selectedColorIndex].second
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑器主题测试") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前主题信息
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前主题", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("模式: ${if (isDark) "深色" else "浅色"}")
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(currentColor, MaterialTheme.shapes.small)
                        )
                    }
                }
            }
            
            // 预设主题选择
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("预设主题", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    themeColors.forEachIndexed { index, (name, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(color, MaterialTheme.shapes.small)
                                )
                                Text(name)
                            }
                            
                            RadioButton(
                                selected = selectedColorIndex == index,
                                onClick = {
                                    selectedColorIndex = index
                                    viewModel.updateEditorTheme(color, isDark)
                                }
                            )
                        }
                    }
                }
            }
            
            // 应用按钮
            Button(
                onClick = { viewModel.updateEditorTheme(currentColor, isDark) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("应用当前主题到编辑器")
            }
        }
    }
}