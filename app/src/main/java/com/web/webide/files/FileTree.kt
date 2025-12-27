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


package com.web.webide.files

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FileNode(
    val file: File,
    val isDirectory: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTree(
    rootPath: String,
    modifier: Modifier = Modifier,
    onFileClick: (File) -> Unit
) {
    var rootFiles by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    val scope = rememberCoroutineScope()

    var containerWidth by remember { mutableIntStateOf(0) }
    val sideMargin = 12.dp
    val density = LocalDensity.current

    val minItemWidth = remember(containerWidth, sideMargin) {
        if (containerWidth == 0) 0.dp else
            with(density) { (containerWidth.toDp() - (sideMargin * 2)).coerceAtLeast(0.dp) }
    }

    var itemWidths by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val maxContentWidth = itemWidths.values.maxOrNull() ?: 0
    val viewportWidthPx = if (containerWidth > 0) containerWidth - with(density) { (sideMargin * 2).toPx() } else 0f
    val isHorizontalScrollEnabled = maxContentWidth > viewportWidthPx && containerWidth > 0
    val horizontalScrollState = rememberScrollState()

    var expandedNodes by remember(rootPath) { mutableStateOf(setOf(File(rootPath).path)) }

    val onSmartToggle: (FileNode) -> Unit = smartToggle@{ node ->
        val path = node.file.path
        if (expandedNodes.contains(path)) {
            expandedNodes -= path
            return@smartToggle
        }
        scope.launch(Dispatchers.IO) {
            val children = node.file.listFiles()
            val childCount = children?.size ?: 0
            if (childCount != 1 || children?.first()?.isFile == true) {
                withContext(Dispatchers.Main) {
                    expandedNodes += path
                }
            } else {
                val pathsToExpend = mutableListOf<String>()
                var currentFile = node.file
                var currentChildren = children
                while (currentChildren?.size == 1 && currentChildren.first().isDirectory) {
                    pathsToExpend.add(currentFile.path)
                    val singleChild = currentChildren.first()
                    currentFile = singleChild
                    currentChildren = currentFile.listFiles()
                }
                pathsToExpend.add(currentFile.path)
                withContext(Dispatchers.Main) {
                    expandedNodes += pathsToExpend
                }
            }
        }
    }

    fun refreshDirectory(directory: File) {
        scope.launch {
            val path = directory.absolutePath
            if (expandedNodes.contains(path)) {
                expandedNodes -= path
                delay(20)
                expandedNodes += path
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedFileNode by remember { mutableStateOf<FileNode?>(null) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isHorizontalScrollEnabled) {
        if (!isHorizontalScrollEnabled) {
            horizontalScrollState.animateScrollTo(0)
        }
    }

    LaunchedEffect(rootPath) {
        val rootFile = File(rootPath)
        rootFiles = if (rootFile.exists()) {
            listOf(FileNode(file = rootFile, isDirectory = rootFile.isDirectory))
        } else {
            emptyList()
        }
    }

    if (rootFiles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier = modifier
                .onSizeChanged { containerWidth = it.width }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        state = horizontalScrollState,
                        enabled = isHorizontalScrollEnabled
                    ),
                contentPadding = PaddingValues(
                    horizontal = sideMargin,
                    vertical = 4.dp
                )
            ) {
                items(rootFiles, key = { it.file.path }) { node ->
                    FileNodeItem(
                        node = node,
                        depth = 0,
                        expandedNodes = expandedNodes,
                        minWidth = minItemWidth,
                        onToggle = onSmartToggle,
                        onFileClick = onFileClick,
                        onLongClick = {
                            selectedFileNode = it
                            showBottomSheet = true
                        },
                        onWidthMeasured = { path, width ->
                            if (itemWidths[path] != width) itemWidths = itemWidths + (path to width)
                        },
                        onDisposed = { path ->
                            itemWidths = itemWidths - path
                        }
                    )
                }
            }
        }
    }
    if (showBottomSheet && selectedFileNode != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            FileActionBottomSheet(
                node = selectedFileNode!!,
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) showBottomSheet = false } },
                onDeleteRequest = { showDeleteConfirmationDialog = true },
                onCreateFileRequest = { showCreateFileDialog = true },
                onCreateFolderRequest = { showCreateFolderDialog = true },
                onRenameRequest = { showRenameDialog = true }
            )
        }
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("确认删除") },
            text = { Text("你确定要删除 “${selectedFileNode?.file?.name}” 吗？") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmationDialog = false
                    showBottomSheet = false
                    selectedFileNode?.let { node ->
                        scope.launch {
                            val parent = node.file.parentFile ?: File(rootPath)
                            val success = withContext(Dispatchers.IO) { if (node.isDirectory) node.file.deleteRecursively() else node.file.delete() }
                            if (success) refreshDirectory(parent)
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmationDialog = false }) { Text("取消") } }
        )
    }
    if (showCreateFileDialog) {
        InputDialog(title = "新建文件", label = "文件名", onDismiss = { showCreateFileDialog = false }) { name ->
            showCreateFileDialog = false; showBottomSheet = false
            selectedFileNode?.let { node ->
                val parent = if (node.isDirectory) node.file else node.file.parentFile
                parent?.let { scope.launch { withContext(Dispatchers.IO) { File(it, name).createNewFile() }; refreshDirectory(it) } }
            }
        }
    }
    if (showCreateFolderDialog) {
        InputDialog(title = "新建文件夹", label = "文件夹名", onDismiss = { showCreateFolderDialog = false }) { name ->
            showCreateFolderDialog = false; showBottomSheet = false
            selectedFileNode?.let { node ->
                val parent = if (node.isDirectory) node.file else node.file.parentFile
                parent?.let { scope.launch { withContext(Dispatchers.IO) { File(it, name).mkdirs() }; refreshDirectory(it) } }
            }
        }
    }
    if (showRenameDialog) {
        InputDialog(title = "重命名", label = "新名称", initialValue = selectedFileNode?.file?.name ?: "", onDismiss = { showRenameDialog = false }) { name ->
            showRenameDialog = false; showBottomSheet = false
            selectedFileNode?.let { node ->
                val parent = node.file.parentFile
                parent?.let { scope.launch { withContext(Dispatchers.IO) { node.file.renameTo(File(it, name)) }; refreshDirectory(it) } }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileNodeItem(
    node: FileNode,
    depth: Int,
    expandedNodes: Set<String>,
    minWidth: Dp,
    onToggle: (FileNode) -> Unit,
    onFileClick: (File) -> Unit,
    onLongClick: (FileNode) -> Unit,
    onWidthMeasured: (String, Int) -> Unit,
    onDisposed: (String) -> Unit
) {
    val isExpanded = expandedNodes.contains(node.file.path)

    val animationSpec = tween<Float>(durationMillis = 150)
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "arrowAnimation",
        animationSpec = animationSpec
    )

    val children by remember(isExpanded, node) {
        derivedStateOf {
            if (isExpanded && node.isDirectory) {
                node.file.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.map { FileNode(file = it, isDirectory = it.isDirectory) }
                    ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    DisposableEffect(node.file.path) {
        onDispose { onDisposed(node.file.path) }
    }

    val widthModifier = if (minWidth > 0.dp) {
        Modifier.widthIn(min = minWidth)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = Modifier
            .then(widthModifier)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (node.isDirectory) {
                        onToggle(node)
                    } else {
                        onFileClick(node.file)
                    }
                },
                onLongClick = { onLongClick(node) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth() // Row 填满外层 Column
                .onSizeChanged { onWidthMeasured(node.file.path, it.width) }
                .padding(vertical = 10.dp, horizontal = 4.dp), // 内部上下边距
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (depth > 0) {
                Spacer(modifier = Modifier.width((depth * 20).dp))
            }

            val icon = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description
            val tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.7f)

            if (node.isDirectory) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Expand",
                    modifier = Modifier.size(24.dp).rotate(arrowRotation)
                )
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.file.name,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.width(24.dp))
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(150))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                children.forEach { child ->
                    FileNodeItem(
                        node = child,
                        depth = depth + 1,
                        expandedNodes = expandedNodes,
                        minWidth = minWidth,
                        onToggle = onToggle,
                        onFileClick = onFileClick,
                        onLongClick = onLongClick,
                        onWidthMeasured = onWidthMeasured,
                        onDisposed = onDisposed
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetActionItem(icon: ImageVector, text: String, onClick: () -> Unit, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (color != Color.Unspecified) color else LocalContentColor.current
        Icon(imageVector = icon, contentDescription = text, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = tint, fontSize = 16.sp)
    }
}

@Suppress("DEPRECATION")
@Composable
fun FileActionBottomSheet(
    node: FileNode,
    onDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onCreateFileRequest: () -> Unit,
    onCreateFolderRequest: () -> Unit,
    onRenameRequest: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        BottomSheetActionItem(Icons.Default.Description, "新建文件", { onCreateFileRequest(); onDismiss() })
        BottomSheetActionItem(Icons.Default.CreateNewFolder, "新建文件夹", { onCreateFolderRequest(); onDismiss() })
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
        BottomSheetActionItem(Icons.Default.DriveFileRenameOutline, "重命名", { onRenameRequest(); onDismiss() })
        BottomSheetActionItem(Icons.Default.ContentCopy, "复制绝对路径", {
            clipboardManager.setText(AnnotatedString(node.file.absolutePath))
            Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
            onDismiss()
        })
        BottomSheetActionItem(Icons.Default.Delete, "删除", { onDeleteRequest(); onDismiss() }, MaterialTheme.colorScheme.error)
    }
}

@Composable
fun InputDialog(title: String, label: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}