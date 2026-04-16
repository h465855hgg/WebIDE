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


package com.web.webide.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.web.webide.R
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.BoxWithConstraints
// 或者通配符

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorySelector(
    initialPath: String = "/storage/emulated/0",
    onPathSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val rootPath = "/storage/emulated/0"
    var currentPath by remember { mutableStateOf(initialPath.ifEmpty { rootPath }) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    rememberCoroutineScope()
    val selectorTitle = stringResource(R.string.directory_selector_title)
    val newFolderDescription = stringResource(R.string.content_desc_new_folder)
    val closeDescription = stringResource(R.string.action_close)
    val goUpText = stringResource(R.string.directory_go_up)
    val directoryDescription = stringResource(R.string.content_desc_directory)
    val emptyDirectoryText = stringResource(R.string.directory_empty)
    val cancelText = stringResource(R.string.action_cancel)
    val selectThisText = stringResource(R.string.directory_select_this)

    val directoryList by remember(currentPath) {
        derivedStateOf {
            try {
                val currentDir = File(currentPath)
                if (currentDir.exists() && currentDir.isDirectory && currentDir.canRead()) {
                    currentDir.listFiles()
                        ?.filter { it.isDirectory }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                 emptyList()
            }
        }
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            currentPath = currentPath,
            onDismiss = { showCreateFolderDialog = false },
            onFolderCreated = { newFolderName ->
                try {
                    val newFolder = File(currentPath, newFolderName)
                    if (newFolder.exists()) {
                         } else if (newFolder.mkdir()) {
                          // 刷新列表 - 通过切换到新创建的文件夹再返回来触发重组
                        val tempPath = currentPath
                        currentPath = newFolder.absolutePath
                        currentPath = tempPath
                    } else {
                         }
                } catch (_: Exception) {
                     }
                showCreateFolderDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectorTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        // 新建文件夹按钮
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = newFolderDescription)
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = closeDescription)
                        }
                    }
                }

                // 2. 面包屑导航
                PathBreadcrumbs(
                    path = currentPath,
                    rootPath = rootPath,
                    onPathSegmentClicked = { newPath -> currentPath = newPath }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )

                // 3. 目录列表 + 快速滑动条
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 返回上一级
                        if (currentPath != rootPath && File(currentPath).parent != null) {
                            item {
                                Card(
                                    onClick = {
                                        currentPath = File(currentPath).parent ?: rootPath
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = goUpText,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = goUpText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // 目录项
                        items(directoryList, key = { it.absolutePath }) { file ->
                            Card(
                                onClick = { currentPath = file.absolutePath },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = directoryDescription,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 空目录提示
                        if (directoryList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        emptyDirectoryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 快速滑动条
                    FastScrollbar(
                        listState = listState,
                        modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (12).dp)
                        )
                    
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // 4. 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) { Text(cancelText) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPathSelected(currentPath) }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectThisText)
                    }
                }
            }
        }
    }
}


@SuppressLint("UnusedBoxWithConstraintsScope", "FrequentlyChangingValue")
@Composable
private fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    // 计算滑块位置和大小
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    // 如果列表为空或全部可见,不显示滑动条
    if (totalItems <= visibleItems) return

    val thumbHeightDp = 48.dp
    
    // 动态计算滑块位置
    val scrollProgress = remember(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, totalItems, visibleItems, isDragging) {
        if (isDragging) {
            null
        } else run {
            val index = listState.firstVisibleItemIndex.toFloat()
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (index + offset / itemHeight.toFloat()) / (totalItems - visibleItems).coerceAtLeast(1).toFloat()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 8.dp, top = 24.dp, bottom = 24.dp)
            .width(40.dp)
    ) {
        val maxHeight = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val availableHeight = maxHeight - thumbHeightPx
        
        // 计算滑块的Y位置
        val thumbOffsetY = if (isDragging) {
            dragOffset.coerceIn(0f, availableHeight)
        } else {
            (scrollProgress ?: 0f) * availableHeight
        }

        // 滑动轨道 - 透明可点击区域
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(40.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragOffset = (offset.y - thumbHeightPx / 2f).coerceIn(0f, availableHeight)
                            val progress = dragOffset / availableHeight
                            val maxScrollIndex = (totalItems - 1).coerceAtLeast(0)
                            val targetIndex = (progress * maxScrollIndex.toFloat()).toInt().coerceIn(0, maxScrollIndex)
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex, scrollOffset = 0)
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, availableHeight)
                        
                        val progress = dragOffset / availableHeight
                        val maxScrollIndex = (totalItems - 1).coerceAtLeast(0)
                        val targetIndex = (progress * maxScrollIndex.toFloat()).toInt().coerceIn(0, maxScrollIndex)
                        
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex, scrollOffset = 0)
                        }
                    }
                }
        ) {
            // 轨道线 - 细线显示
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
            )
        }

        // 滑块 - MD3 风格圆角矩形
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { thumbOffsetY.toDp() })
                .width(6.dp)
                .height(thumbHeightDp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isDragging)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = thumbOffsetY
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, availableHeight)
                        
                        val progress = dragOffset / availableHeight
                        val maxScrollIndex = (totalItems - 1).coerceAtLeast(0)
                        val targetIndex = (progress * maxScrollIndex.toFloat()).toInt().coerceIn(0, maxScrollIndex)
                        
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex, scrollOffset = 0)
                        }
                    }
                }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFolderDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onFolderCreated: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidCharsMessage = stringResource(R.string.directory_invalid_chars)
    val invalidNameMessage = stringResource(R.string.directory_invalid_name)
    val newFolderTitle = stringResource(R.string.directory_new_folder)
    val currentLocationText = stringResource(R.string.label_location_format, currentPath)
    val folderNameLabel = stringResource(R.string.directory_name_label)
    val createText = stringResource(R.string.action_create)
    val cancelText = stringResource(R.string.action_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(newFolderTitle) },
        text = {
            Column {
                Text(
                    text = currentLocationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { 
                        folderName = it
                        errorMessage = when {
                            it.isEmpty() -> null
                            it.contains('/') || it.contains('\\') -> invalidCharsMessage
                            it == "." || it == ".." -> invalidNameMessage
                            else -> null
                        }
                    },
                    label = { Text(folderNameLabel) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (folderName.isNotEmpty() && errorMessage == null) {
                        onFolderCreated(folderName)
                    }
                },
                enabled = folderName.isNotEmpty() && errorMessage == null
            ) {
                Text(createText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        }
    )
}


@Composable
private fun PathBreadcrumbs(
    path: String,
    rootPath: String,
    onPathSegmentClicked: (String) -> Unit
) {
    data class PathSegment(
        val name: String,
        val fullPath: String,
        val isClickable: Boolean
    )

    val segments by remember(path, rootPath) {
        derivedStateOf {
            val pathParts = path.split('/').filter { it.isNotEmpty() }
            val rootParts = rootPath.split('/').filter { it.isNotEmpty() }

            val result = mutableListOf<PathSegment>()
            var currentPathBuilder = ""

            pathParts.forEachIndexed { index, part ->
                currentPathBuilder += "/$part"
                val clickable = index >= rootParts.size - 1
                result.add(PathSegment(part, currentPathBuilder, clickable))
            }
            result
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(segments.size) { index ->
            val segment = segments[index]
            val isLast = index == segments.size - 1

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = segment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isLast -> MaterialTheme.colorScheme.primary
                        !segment.isClickable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = segment.isClickable && !isLast) {
                            onPathSegmentClicked(segment.fullPath)
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                )

                if (!isLast) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
