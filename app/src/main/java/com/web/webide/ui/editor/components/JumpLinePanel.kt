/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.web.webide.ui.editor.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpLinePanel(
    onJump: (String) -> Unit,
    onClose: () -> Unit
) {
    var lineText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .height(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = lineText,
                onValueChange = { input ->
                    // 只允许数字
                    val filtered = input.filter { it.isDigit() }
                    lineText = filtered
                    // 🔥 实时预览跳转：如果输入不为空，则直接调用跳转逻辑
                    if (filtered.isNotEmpty()) {
                        onJump(filtered)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入行号...", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done // 键盘右下角显示“完成”
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // 按下完成键时关闭面板
                        onClose()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                )
            )

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "关闭", modifier = Modifier.size(20.dp))
            }
        }
    }
}