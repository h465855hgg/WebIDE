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


package com.web.webide.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.web.webide.ui.editor.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    viewModel: EditorViewModel,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit
) {
    var replaceText by remember { mutableStateOf("") }
    var isReplaceVisible by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
            // 第一行：搜索栏
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(50.dp) // 固定高度，防止跳动
            ) {
                TextField(
                    value = searchText,
                    onValueChange = {
                        onSearchTextChange(it)
                        viewModel.searchText(it, ignoreCase)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp),
                    placeholder = { Text("搜索...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        // 大小写切换按钮放在左侧，节省空间
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(onClick = {
                                ignoreCase = !ignoreCase
                                viewModel.searchText(searchText, ignoreCase)
                            }) {
                                Text(
                                    "Aa",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (!ignoreCase) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!ignoreCase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onSearchTextChange("")
                                    viewModel.searchText("", ignoreCase)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchNext() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    )
                )

                // 紧凑的控制组
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.searchPrev() }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, "上一个")
                        }
                        IconButton(onClick = { viewModel.searchNext() }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "下一个")
                        }
                        IconButton(onClick = { isReplaceVisible = !isReplaceVisible }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Icon(
                                Icons.Default.FindReplace,
                                contentDescription = "替换",
                                tint = if (isReplaceVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            viewModel.stopSearch()
                            onClose()
                        }, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                }
            }

            // 第二行：替换栏
            AnimatedVisibility(visible = isReplaceVisible) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("替换...", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )

                    // 使用较小的按钮
                    Button(
                        onClick = { viewModel.replaceCurrent(replaceText) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("替换", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.replaceAll(replaceText) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("全部", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}