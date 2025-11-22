package com.web.webide.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 主题演示界面，用于测试主题切换功能
 * 类似切换主题色项目中的实现
 */
@Composable
fun ThemeDemoScreen(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 演示文本
            Text(
                text = "主题切换演示",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 演示按钮
            Button(
                onClick = { /* 点击事件 */ },
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(text = "演示按钮")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 主题预览
            Text(
                text = "当前主题颜色:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 颜色预览
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("主色", "辅色", "背景色")) { colorName ->
                    val color = when (colorName) {
                        "主色" -> MaterialTheme.colorScheme.primary
                        "辅色" -> MaterialTheme.colorScheme.secondary
                        "背景色" -> MaterialTheme.colorScheme.background
                        else -> MaterialTheme.colorScheme.surface
                    }
                    
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = color,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = colorName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 主题信息
            Text(
                text = "主题信息:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "主色: ${MaterialTheme.colorScheme.primary.toHex()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "辅色: ${MaterialTheme.colorScheme.secondary.toHex()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "背景色: ${MaterialTheme.colorScheme.background.toHex()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 将颜色转换为十六进制字符串
 */
fun androidx.compose.ui.graphics.Color.toHex(): String {
    return String.format("#%08X", this.value.toInt())
}