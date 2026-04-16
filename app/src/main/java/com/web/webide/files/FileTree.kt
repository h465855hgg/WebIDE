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

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.web.webide.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class FileNode(
    val file: File,
    val isDirectory: Boolean,
)

// Config for FileTree
data class FileTreeConfig(
    val sortBy: SortBy = SortBy.NAME,
    val foldersAlwaysOnTop: Boolean = true,
    val showDetails: Boolean = false,
    val compactMiddlePackages: Boolean = false,
    val compactMiddlePackageCount: Int = 3, // Default max depth for compaction
    val alwaysSelectOpenedFile: Boolean = false,
    val showIndentGuides: Boolean = false
)

enum class SortBy {
    NAME,
    TYPE,
    DATE_NEWEST,
    DATE_OLDEST
}

// Helper for file size
@SuppressLint("DefaultLocale")
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTree(
    rootPaths: List<String>,
    activeFile: File? = null,
    config: FileTreeConfig = FileTreeConfig(),
    locateTrigger: Long = 0,
    collapseTrigger: Long = 0,
    expandTrigger: Long = 0,
    refreshTrigger: Long = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onFileClick: (File) -> Unit,
    onFileRenamed: (oldFile: File, newFile: File) -> Unit = { _, _ -> }
) {
    // 兼容单个rootPath调用
    FileTreeImpl(
        rootPaths = rootPaths,
        activeFile = activeFile,
        config = config,
        locateTrigger = locateTrigger,
        collapseTrigger = collapseTrigger,
        expandTrigger = expandTrigger,
        refreshTrigger = refreshTrigger,
        modifier = modifier,
        onFileClick = onFileClick,
        onFileRenamed = onFileRenamed
    )
}

@Composable
fun FileTree(
    rootPath: String,
    activeFile: File? = null,
    config: FileTreeConfig = FileTreeConfig(),
    locateTrigger: Long = 0,
    collapseTrigger: Long = 0,
    expandTrigger: Long = 0,
    refreshTrigger: Long = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onFileClick: (File) -> Unit,
    onFileRenamed: (oldFile: File, newFile: File) -> Unit = { _, _ -> }
) {
    FileTree(
        rootPaths = listOf(rootPath),
        activeFile = activeFile,
        config = config,
        locateTrigger = locateTrigger,
        collapseTrigger = collapseTrigger,
        expandTrigger = expandTrigger,
        refreshTrigger = refreshTrigger,
        modifier = modifier,
        onFileClick = onFileClick,
        onFileRenamed = onFileRenamed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTreeImpl(
    rootPaths: List<String>,
    activeFile: File? = null,
    config: FileTreeConfig = FileTreeConfig(),
    locateTrigger: Long = 0,
    collapseTrigger: Long = 0,
    expandTrigger: Long = 0,
    refreshTrigger: Long = 0,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onFileClick: (File) -> Unit,
    onFileRenamed: (oldFile: File, newFile: File) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var showInstallApkDialog by remember { mutableStateOf<File?>(null) }
    var rootFiles by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Sort logic
    fun sortFiles(files: List<File>): List<File> {
        return files.sortedWith(Comparator { f1, f2 ->
            // 1. Folders on top check
            if (config.foldersAlwaysOnTop) {
                if (f1.isDirectory && !f2.isDirectory) return@Comparator -1
                if (!f1.isDirectory && f2.isDirectory) return@Comparator 1
            }
            
            // 2. Main sort
            when (config.sortBy) {
                SortBy.NAME -> f1.name.compareTo(f2.name, ignoreCase = true)
                SortBy.TYPE -> {
                    val ext1 = f1.extension
                    val ext2 = f2.extension
                    val res = ext1.compareTo(ext2, ignoreCase = true)
                    if (res != 0) res else f1.name.compareTo(f2.name, ignoreCase = true)
                }
                SortBy.DATE_NEWEST -> f2.lastModified().compareTo(f1.lastModified())
                SortBy.DATE_OLDEST -> f1.lastModified().compareTo(f2.lastModified())
            }
        })
    }

    var containerWidth by remember { mutableIntStateOf(0) }
    val sideMargin = 12.dp
    val density = LocalDensity.current

    val minItemWidth = remember(containerWidth, sideMargin) {
        if (containerWidth == 0) 0.dp else
            with(density) { (containerWidth.toDp() - (sideMargin * 2)).coerceAtLeast(0.dp) }
    }

    var itemWidths by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val maxContentWidth = itemWidths.values.maxOrNull() ?: 0
    val viewportWidthPx =
        if (containerWidth > 0) containerWidth - with(density) { (sideMargin * 2).toPx() } else 0f
    val isHorizontalScrollEnabled = maxContentWidth > viewportWidthPx && containerWidth > 0
    val horizontalScrollState = rememberScrollState()

    var expandedNodes by remember(rootPaths) { 
        mutableStateOf(rootPaths.map { File(it).path }.toSet()) 
    }
    var treeSelection by remember { mutableStateOf<String?>(null) }

    // Helper to expand to active file
    fun expandToActiveFile() {
        if (activeFile != null) {
            val matchedRoot = rootPaths.find { activeFile.absolutePath.startsWith(it) }
            if (matchedRoot != null) {
                val toExpand = mutableSetOf<String>()
                var parent = activeFile.parentFile
                while (parent != null && parent.absolutePath.startsWith(matchedRoot)) {
                    toExpand.add(parent.absolutePath)
                    parent = parent.parentFile
                }
                if (toExpand.isNotEmpty()) {
                    expandedNodes += toExpand
                }
            }
        }
    }

    // Auto-expand to active file
    LaunchedEffect(activeFile) {
        if (config.alwaysSelectOpenedFile && activeFile != null) {
            treeSelection = activeFile.absolutePath
            expandToActiveFile()
        }
    }

    // Handle config toggle
    LaunchedEffect(config.alwaysSelectOpenedFile) {
        if (config.alwaysSelectOpenedFile) {
            if (activeFile != null) {
                treeSelection = activeFile.absolutePath
                expandToActiveFile()
            }
        } else {
            // Only clear if the user JUST toggled it off
            if (treeSelection == activeFile?.absolutePath) {
                treeSelection = null
            }
        }
    }

    // Manual triggers
    LaunchedEffect(locateTrigger) {
        if (locateTrigger > 0) {
            if (activeFile != null) {
                treeSelection = activeFile.absolutePath
                expandToActiveFile()
                
                // If "Always Select" is OFF, show temporarily then clear
                if (!config.alwaysSelectOpenedFile) {
                    delay(1000)
                    if (treeSelection == activeFile.absolutePath) {
                        treeSelection = null
                    }
                }
            }
        }
    }

    LaunchedEffect(collapseTrigger) {
        if (collapseTrigger > 0) {
            // Collapse All: Clear everything
            expandedNodes = emptySet()
        }
    }

    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0) {
             withContext(Dispatchers.IO) {
                 val allPaths = mutableSetOf<String>()
                 // Use a queue for BFS traversal
                 val queue = java.util.ArrayDeque<File>()
                 rootPaths.forEach { queue.add(File(it)) }
                 
                 var count = 0
                 val maxNodes = 200 // Safety limit to prevent freezing
                 
                 while (!queue.isEmpty() && count < maxNodes) {
                     val current = queue.removeFirst()
                     if (current.isDirectory) {
                         allPaths.add(current.path)
                         current.listFiles()?.let { children ->
                             // Add directories to queue
                             children.filter { it.isDirectory }.forEach { queue.add(it) }
                         }
                         count++
                     }
                 }
                 withContext(Dispatchers.Main) {
                     expandedNodes = allPaths
                 }
             }
        }
    }

    val onSmartToggle: (FileNode) -> Unit = smartToggle@{ node ->
        // Logic moved to FileNodeItem for visual compaction
        val path = node.file.path
        if (expandedNodes.contains(path)) {
            expandedNodes -= path
        } else {
            expandedNodes += path
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
    val confirmDeleteTitleText = stringResource(R.string.file_tree_confirm_delete_title)
    val confirmDeleteMessageText = stringResource(
        R.string.file_tree_confirm_delete_message,
        selectedFileNode?.file?.name ?: ""
    )
    val createFileTitleText = stringResource(R.string.file_tree_create_file_title)
    val fileNameText = stringResource(R.string.file_tree_file_name)
    val createFolderTitleText = stringResource(R.string.file_tree_create_folder_title)
    val folderNameText = stringResource(R.string.file_tree_folder_name)
    val renameTitleText = stringResource(R.string.file_tree_rename_title)
    val newNameText = stringResource(R.string.file_tree_new_name)
    val installAppTitleText = stringResource(R.string.file_tree_install_app_title)
    val actionDeleteText = stringResource(R.string.action_delete)
    val actionConfirmText = stringResource(R.string.action_confirm)
    val actionCancelText = stringResource(R.string.action_cancel)
    val actionInstallText = stringResource(R.string.action_install)

    LaunchedEffect(isHorizontalScrollEnabled) {
        if (!isHorizontalScrollEnabled) {
            horizontalScrollState.animateScrollTo(0)
        }
    }

    // Initial load & Refresh
    LaunchedEffect(rootPaths, refreshTrigger) {
        withContext(Dispatchers.IO) {
            val nodes = rootPaths.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) FileNode(file, file.isDirectory) else null
            }
            withContext(Dispatchers.Main) {
                rootFiles = nodes
            }
        }
    }

    val fileSorter = remember(config.sortBy, config.foldersAlwaysOnTop) { { files: List<File> -> sortFiles(files) } }

    if (rootFiles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = modifier
                .onSizeChanged { containerWidth = it.width }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    selectedPath = treeSelection,
                    config = config,
                    fileSorter = fileSorter,
                    refreshTrigger = refreshTrigger,
                    depth = 0,
                    expandedNodes = expandedNodes,
                    minWidth = minItemWidth,
                    onToggle = onSmartToggle,
                        onFileClick = {
                            if (it.extension.equals("apk", ignoreCase = true)) {
                                showInstallApkDialog = it
                            } else {
                                treeSelection = it.absolutePath
                                onFileClick(it)
                                if (!config.alwaysSelectOpenedFile) {
                                    scope.launch {
                                        delay(1000)
                                        if (treeSelection == it.absolutePath) {
                                            treeSelection = null
                                        }
                                    }
                                }
                            }
                        },
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
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { if (!sheetState.isVisible) showBottomSheet = false }
                },
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
            title = { Text(confirmDeleteTitleText) },
            text = { Text(confirmDeleteMessageText) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        showBottomSheet = false
                        selectedFileNode?.let { node ->
                            scope.launch {
                                val parent = node.file.parentFile
                                val success =
                                    withContext(Dispatchers.IO) { if (node.isDirectory) node.file.deleteRecursively() else node.file.delete() }
                                if (success && parent != null) refreshDirectory(parent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(actionDeleteText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text(actionCancelText)
                }
            }
        )
    }
    if (showCreateFileDialog) {
        var name by remember { mutableStateOf("") }
        val extensions = remember { listOf(".html", ".css", ".js", ".php", ".json", ".xml", ".kt", ".java", ".txt", ".md") }
        
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(createFileTitleText) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(fileNameText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(extensions) { ext ->
                            SuggestionChip(
                                onClick = {
                                    if (name.contains(".")) {
                                        name = name.substringBeforeLast(".") + ext
                                    } else {
                                        name += ext
                                    }
                                },
                                label = { Text(ext) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            showCreateFileDialog = false
                            showBottomSheet = false
                            selectedFileNode?.let { node ->
                                val parent = if (node.isDirectory) node.file else node.file.parentFile
                                parent?.let {
                                    scope.launch {
                                        val newFile = File(it, name)
                                        withContext(Dispatchers.IO) {
                                            newFile.createNewFile()
                                        }
                                        refreshDirectory(it)
                                        onFileClick(newFile)
                                    }
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text(actionConfirmText) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text(actionCancelText) }
            }
        )
    }
    if (showCreateFolderDialog) {
        InputDialog(
            title = createFolderTitleText,
            label = folderNameText,
            onDismiss = { showCreateFolderDialog = false }) { name ->
            showCreateFolderDialog = false; showBottomSheet = false
            selectedFileNode?.let { node ->
                val parent = if (node.isDirectory) node.file else node.file.parentFile
                parent?.let {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            File(
                                it,
                                name
                            ).mkdirs()
                        }; refreshDirectory(it)
                    }
                }
            }
        }
    }
    if (showRenameDialog) {
        InputDialog(
            title = renameTitleText,
            label = newNameText,
            initialValue = selectedFileNode?.file?.name ?: "",
            onDismiss = { showRenameDialog = false }
        ) { name ->
            // 1. 关闭弹窗
            showRenameDialog = false
            showBottomSheet = false

            selectedFileNode?.let { node ->
                val parent = node.file.parentFile
                // 给这里的 it 起个名字叫 parentDir，避免混淆
                parent?.let { parentDir ->
                    scope.launch {
                        val oldFile = node.file
                        val newFile = File(parentDir, name)

                        // 2. 在 IO 线程执行重命名
                        val success = withContext(Dispatchers.IO) {
                            oldFile.renameTo(newFile)
                        }

                        // 3. 根据结果刷新 UI
                        if (success) {
                            // 刷新当前文件夹视图
                            refreshDirectory(parentDir)
                            // 【关键】通知外部更新 Tabs
                            onFileRenamed(oldFile, newFile)
                        }
                    }
                }
            }
        }
    }
    if (showInstallApkDialog != null) {
        val file = showInstallApkDialog!!
        val installAppMessageText = stringResource(R.string.file_tree_install_app_message, file.name)
        AlertDialog(
            onDismissRequest = { showInstallApkDialog = null },
            title = { Text(installAppTitleText) },
            text = { Text(installAppMessageText) },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallApkDialog = null
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.file_tree_install_failed, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) { Text(actionInstallText) }
            },
            dismissButton = {
                TextButton(onClick = { showInstallApkDialog = null }) {
                    Text(actionCancelText)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileNodeItem(
    node: FileNode,
    selectedPath: String?,
    config: FileTreeConfig,
    fileSorter: (List<File>) -> List<File>,
    refreshTrigger: Long,
    depth: Int,
    expandedNodes: Set<String>,
    minWidth: Dp,
    onToggle: (FileNode) -> Unit,
    onFileClick: (File) -> Unit,
    onLongClick: (FileNode) -> Unit,
    onWidthMeasured: (String, Int) -> Unit,
    onDisposed: (String) -> Unit
) {
    val (effectiveNode, effectiveName) = remember(node, config.compactMiddlePackages, config.compactMiddlePackageCount, refreshTrigger) {
        if (!config.compactMiddlePackages || !node.isDirectory) {
            node to node.file.name
        } else {
            var curr = node.file
            var nameBuilder = StringBuilder(curr.name)
            var count = 0
            while (count < config.compactMiddlePackageCount) {
                val kids = curr.listFiles()
                if (kids != null && kids.size == 1 && kids[0].isDirectory) {
                    curr = kids[0]
                    nameBuilder.append(".").append(curr.name)
                    count++
                } else {
                    break
                }
            }
            FileNode(curr, true) to nameBuilder.toString()
        }
    }

    val isExpanded = expandedNodes.contains(effectiveNode.file.path)
    val isSelected = selectedPath == effectiveNode.file.absolutePath

    val animationSpec = tween<Float>(durationMillis = 150)
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "arrowAnimation",
        animationSpec = animationSpec
    )

    val children by remember(isExpanded, effectiveNode, fileSorter, refreshTrigger) {
        derivedStateOf {
            if (isExpanded && effectiveNode.isDirectory) {
                val rawChildren = effectiveNode.file.listFiles()?.toList() ?: emptyList()
                fileSorter(rawChildren).map { FileNode(file = it, isDirectory = it.isDirectory) }
            } else {
                emptyList()
            }
        }
    }

    val indentGuideVisible = config.showIndentGuides

    DisposableEffect(effectiveNode.file.path) {
        onDispose { onDisposed(effectiveNode.file.path) }
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
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (effectiveNode.isDirectory) {
                        onToggle(effectiveNode)
                    } else {
                        onFileClick(effectiveNode.file)
                    }
                },
                onLongClick = { onLongClick(effectiveNode) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth() // Row 填满外层 Column
                .onSizeChanged { onWidthMeasured(effectiveNode.file.path, it.width) }
                .padding(vertical = 10.dp, horizontal = 4.dp), // 内部上下边距，稍微减小一点显得更紧凑
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (depth > 0) {
                Spacer(modifier = Modifier.width((depth * 20).dp))
            }

            val (icon, baseTint) = FileIcons.getFileIcon(effectiveNode.file.name, effectiveNode.isDirectory, isExpanded)
            
            val tint = if (effectiveNode.isDirectory) {
                 MaterialTheme.colorScheme.primary
            } else {
                 if (baseTint == Color.Unspecified) LocalContentColor.current.copy(alpha = 0.7f) else baseTint
            }

            if (effectiveNode.isDirectory) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,

                    contentDescription = stringResource(R.string.content_desc_expand),
                    modifier = Modifier.size(24.dp).rotate(arrowRotation),
                    tint = LocalContentColor.current.copy(alpha = 0.6f)
                )
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = effectiveName,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color.Unspecified
            )

            // File Details (if enabled)
            if (config.showDetails && !effectiveNode.isDirectory) {
                Spacer(modifier = Modifier.width(8.dp))
                val size = formatFileSize(effectiveNode.file.length())
                Text(
                    text = size,
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(150))
        ) {
            Column(modifier = Modifier.fillMaxWidth().drawBehind {
                if (indentGuideVisible) {
                    val indentUnit = 20.dp.toPx()
                    val guideX = (depth * indentUnit) + 4.dp.toPx() + 10.dp.toPx()

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(guideX, 0f),
                        end = Offset(guideX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }) {
                children.forEach { child ->
                    FileNodeItem(
                        node = child,
                        selectedPath = selectedPath,
                        config = config,
                        fileSorter = fileSorter,
                        refreshTrigger = refreshTrigger,
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
    val createFileText = stringResource(R.string.file_tree_action_create_file)
    val createFolderText = stringResource(R.string.file_tree_action_create_folder)
    val renameText = stringResource(R.string.file_tree_action_rename)
    val copyPathText = stringResource(R.string.file_tree_action_copy_path)
    val deleteText = stringResource(R.string.action_delete)
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // File Info Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(node.file.name, style = MaterialTheme.typography.titleMedium)
                val size = if (node.isDirectory) stringResource(R.string.file_tree_folder_type) else formatFileSize(node.file.length())
                val date = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                    Locale.getDefault()
                ).format(Date(node.file.lastModified()))
                Text(
                    stringResource(R.string.file_tree_details_format, size, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        BottomSheetActionItem(Icons.Default.Description, createFileText, { onCreateFileRequest(); onDismiss() })
        BottomSheetActionItem(Icons.Default.CreateNewFolder, createFolderText, { onCreateFolderRequest(); onDismiss() })
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
        
        BottomSheetActionItem(Icons.Default.DriveFileRenameOutline, renameText, { onRenameRequest(); onDismiss() })
        BottomSheetActionItem(Icons.Default.ContentCopy, copyPathText, {
            clipboardManager.setText(AnnotatedString(node.file.absolutePath))
            Toast.makeText(context, context.getString(R.string.file_tree_path_copied), Toast.LENGTH_SHORT).show()
            onDismiss()
        })
        BottomSheetActionItem(Icons.Default.Delete, deleteText, { onDeleteRequest(); onDismiss() }, MaterialTheme.colorScheme.error)
    }
}

@Composable
fun InputDialog(title: String, label: String, initialValue: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    val actionConfirmText = stringResource(R.string.action_confirm)
    val actionCancelText = stringResource(R.string.action_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text(actionConfirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(actionCancelText) } }
    )
}
