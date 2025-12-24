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

package com.web.webide.ui.editor

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.files.FileTree
import com.web.webide.ui.editor.components.CodeEditorView
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.lerp
import com.web.webide.build.ApkInstaller
import com.web.webide.ui.editor.components.EditorToolbar
import com.web.webide.ui.editor.components.JumpLinePanel
import com.web.webide.ui.editor.components.SearchPanel
import com.web.webide.ui.welcome.ColorPickerDialog
import com.web.webide.ui.welcome.colorToHex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 构建结果状态
sealed class BuildResultState {
    data class Finished(val message: String, val apkPath: String? = null) : BuildResultState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditScreen(folderName: String, navController: NavController, viewModel: EditorViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectPath = File(workspacePath, folderName).absolutePath

    val snackbarHostState = remember { SnackbarHostState() }

    // 加载配置
    LaunchedEffect(Unit) {
        viewModel.reloadEditorConfig(context)
    }
    val editorConfig = viewModel.editorConfig

    // 初始加载进度条状态
    var showInitialLoader by remember { mutableStateOf(!viewModel.hasShownInitialLoader) }
    // 构建过程中的进度条状态
    var isBuilding by remember { mutableStateOf(false) }

    // 构建结果状态
    var buildResult by remember { mutableStateOf<BuildResultState?>(null) }

    // 检测 webapp.json 和 src/main/assets/ 是否同时存在
    val hasWebAppConfig = remember(projectPath) {
        val configFile = File(projectPath, "webapp.json")
        val assetsDir = File(projectPath, "src/main/assets")
        configFile.exists() && assetsDir.exists() && assetsDir.isDirectory
    }

    var isOpenSearch by remember { mutableStateOf(false) }
    var currentSearchText by remember { mutableStateOf("") }
    LaunchedEffect(viewModel.activeFileIndex) {
        if (isOpenSearch && currentSearchText.isNotEmpty()) {
            delay(200)
            viewModel.searchText(currentSearchText)
        }
    }
    var ignoreCaseState by remember { mutableStateOf(true) }
    LaunchedEffect(viewModel.activeFileIndex) {
        if (isOpenSearch && currentSearchText.isNotEmpty()) {
            viewModel.searchText(currentSearchText, ignoreCaseState)
        }
    }

    var isOpenJump by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    // 进入项目时清理不属于当前项目的文件
    LaunchedEffect(folderName) {
        if (viewModel.openFiles.isNotEmpty()) {
            val firstFile = viewModel.openFiles.first().file
            if (!firstFile.absolutePath.startsWith(projectPath)) {
                viewModel.closeAllFiles()
            }
        }
    }

    LaunchedEffect(projectPath) {
        viewModel.loadInitialFile(projectPath)
    }

    LaunchedEffect(Unit) {
        if (showInitialLoader) {
            delay(500L)
            showInitialLoader = false
            viewModel.onInitialLoaderShown()
        }
    }

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                FileManagerDrawer(
                    projectPath = projectPath,
                    onFileClick = { file ->
                        viewModel.openFile(file)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text("WebIDE", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            AnimatedDrawerToggle(
                                isOpen = drawerState.isOpen,
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            )
                        },
                        actions = {
                            IconButton(onClick = { viewModel.undo() }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, "撤销")
                            }
                            IconButton(onClick = { viewModel.redo() }) {
                                Icon(Icons.AutoMirrored.Filled.Redo, "重做")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    scope.launch {
                                        viewModel.saveAllModifiedFiles(snackbarHostState)
                                        navController.navigate("preview/$folderName")
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.PlayArrow, "运行")
                            }
                            Box {
                                IconButton(onClick = { isMoreMenuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, "更多选项")
                                }
                                DropdownMenu(
                                    expanded = isMoreMenuExpanded,
                                    onDismissRequest = { isMoreMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("全部保存") },
                                        onClick = {
                                            scope.launch {
                                                viewModel.saveAllModifiedFiles(snackbarHostState)
                                            }
                                            isMoreMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isOpenSearch) "关闭搜索" else "搜索") },
                                        leadingIcon = {
                                            Icon(
                                                if (isOpenSearch) Icons.Default.SearchOff else Icons.Default.Search,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            if (isOpenSearch) viewModel.stopSearch()
                                            isOpenSearch = !isOpenSearch
                                            isMoreMenuExpanded = false
                                        }
                                    )
                                    if (hasWebAppConfig) {
                                        DropdownMenuItem(
                                            text = { Text("构建 APK") },
                                            enabled = !isBuilding,
                                            onClick = {
                                                isMoreMenuExpanded = false
                                                scope.launch {
                                                    isBuilding = true
                                                    performBuild(
                                                        context = context,
                                                        projectPath = projectPath,
                                                        folderName = folderName,
                                                        viewModel = viewModel,
                                                        snackbarHostState = snackbarHostState,
                                                        onResult = { resultState ->
                                                            buildResult = resultState
                                                            isBuilding = false
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    // 工具栏 (根据配置显示)
                    if (editorConfig.showToolbar) {
                        EditorToolbar(
                            onSave = { scope.launch { viewModel.saveAllModifiedFiles(
                                snackbarHostState) } },
                            onSearch = {
                                isOpenSearch = !isOpenSearch
                                isOpenJump = false
                            },
                            onJump = {
                                isOpenJump = !isOpenJump
                                isOpenSearch = false
                            },
                            onFormat = { viewModel.formatCode() },
                            onCreate = { showCreateDialog = true },
                            onPalette = { showColorPicker = true },
                            isBuilding = isBuilding,
                            hasWebAppConfig = hasWebAppConfig,
                            onBuild = {
                                scope.launch {
                                    isBuilding = true
                                    performBuild(
                                        context = context,
                                        projectPath = projectPath,
                                        folderName = folderName,
                                        viewModel = viewModel,
                                        snackbarHostState = snackbarHostState,
                                        onResult = { resultState ->
                                            buildResult = resultState
                                            isBuilding = false
                                        }
                                    )
                                }
                            },
                        )
                    }

                    // 搜索面板
                    AnimatedVisibility(visible = isOpenSearch) {
                        Column {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            SearchPanel(
                                viewModel = viewModel,
                                searchText = currentSearchText,
                                onSearchTextChange = { currentSearchText = it },
                                onClose = {
                                    viewModel.stopSearch()
                                    isOpenSearch = false
                                }
                            )
                        }
                    }
                    AnimatedVisibility(visible = isOpenJump) {
                        Column {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            JumpLinePanel(
                                onJump = { line -> viewModel.jumpToLine(line) },
                                onClose = {
                                    isOpenJump = false
                                    viewModel.getActiveEditor()?.requestFocus()
                                }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    // 使用配置中的自定义符号
                    SymbolBar(
                        viewModel = viewModel,
                        symbols = editorConfig.getSymbolList()
                    )
                }
            },
            content = { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    AnimatedVisibility(visible = showInitialLoader || isBuilding) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Butt
                        )
                    }
                    EditCode(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel
                    )
                }
            }
        )

        // 1. 新建对话框
        if (showCreateDialog) {
            var nameInput by remember { mutableStateOf("") }
            var isFileType by remember { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("新建内容") },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isFileType, onClick = { isFileType = true })
                            Text("文件")
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = !isFileType, onClick = { isFileType = false })
                            Text("文件夹")
                        }
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("例test.txt") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.createNewItem(projectPath, nameInput, isFileType) { newItem ->
                                if (isFileType) viewModel.openFile(newItem)
                            }
                        }
                        showCreateDialog = false
                    }) { Text("创建") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
                }
            )
        }

        // 2. 调色板
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = MaterialTheme.colorScheme.primary,
                onDismiss = { showColorPicker = false },
                onColorSelected = { color ->
                    val hex = colorToHex(color, color.alpha < 1f)
                    viewModel.insertText(hex)
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = android.content.ClipData.newPlainText("Hex Color", hex)
                    clipboardManager.setPrimaryClip(clipData)
                    showColorPicker = false
                }
            )
        }

        // 3. 构建结果弹窗
        buildResult?.let { result ->
            if (result is BuildResultState.Finished) {
                val isSuccess = result.apkPath != null
                AlertDialog(
                    onDismissRequest = { buildResult = null },
                    title = { Text(if (isSuccess) "构建成功" else "构建失败") },
                    text = {
                        Column {
                            if (isSuccess) {
                                Text("APK 已生成，是否立即安装？")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("输出路径:", style = MaterialTheme.typography.titleSmall)
                                Text(result.apkPath, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("错误信息：", color = MaterialTheme.colorScheme.error)
                                Text(result.message)
                            }
                        }
                    },
                    confirmButton = {
                        if (isSuccess) {
                            TextButton(onClick = {
                                val apkFile = File(result.apkPath)
                                ApkInstaller.install(context, apkFile)
                                buildResult = null
                            }) { Text("安装") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { buildResult = null }) { Text("关闭") }
                    }
                )
            }
        }
    }
}

// ---------------- 辅助函数 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolBar(viewModel: EditorViewModel, symbols: List<String>) {
    BottomAppBar(
        modifier = Modifier
            .imePadding()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { symbol ->
                Box(
                    modifier = Modifier
                        .clickable { viewModel.insertSymbol(symbol) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = symbol, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCode(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel,
) {
    val openFiles = viewModel.openFiles
    val activeFileIndex = viewModel.activeFileIndex
    val scope = rememberCoroutineScope()
    var expandedTabIndex by remember { mutableStateOf<Int?>(null) }
    val currentFiles by rememberUpdatedState(openFiles)
    val currentIndex by rememberUpdatedState(activeFileIndex)
    val pagerState = rememberPagerState(
        initialPage = activeFileIndex.coerceIn(0, maxOf(0, openFiles.size - 1)),
        pageCount = { currentFiles.size })

    LaunchedEffect(currentIndex, currentFiles.size) {
        if (currentFiles.isNotEmpty() && currentIndex >= 0 && currentIndex < currentFiles.size && pagerState.currentPage != currentIndex) {
            pagerState.scrollToPage(currentIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (currentFiles.isNotEmpty() && page in currentFiles.indices && page != currentIndex) {
                viewModel.changeActiveFileIndex(page)
            }
        }
    }

    Column(modifier = modifier) {
        if (openFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("未打开任何文件") }
        } else {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, openFiles.size - 1),
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty() && pagerState.currentPage < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .height(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(percent = 50)
                                )
                        )
                    }
                }) {
                openFiles.forEachIndexed { index, editorState ->
                    Box {
                        val displayName =
                            if (editorState.isModified) "*${editorState.file.name}" else editorState.file.name
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                if (pagerState.currentPage == index) expandedTabIndex =
                                    index else scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            })
                        DropdownMenu(
                            expanded = expandedTabIndex == index,
                            onDismissRequest = { expandedTabIndex = null }) {
                            DropdownMenuItem(
                                text = { Text("关闭") },
                                onClick = { expandedTabIndex = null; viewModel.closeFile(index) })
                            DropdownMenuItem(
                                text = { Text("关闭其他") },
                                onClick = {
                                    expandedTabIndex = null; viewModel.closeOtherFiles(index)
                                })
                            DropdownMenuItem(
                                text = { Text("关闭全部") },
                                onClick = { expandedTabIndex = null; viewModel.closeAllFiles() })
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = false,
                key = { index -> if (index < openFiles.size) openFiles[index].file.absolutePath else "empty_$index" }) { page ->
                if (page in openFiles.indices) {
                    CodeEditorView(
                        modifier = Modifier.fillMaxSize(),
                        state = openFiles[page],
                        viewModel = viewModel
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
fun FileManagerDrawer(projectPath: String, onFileClick: (File) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "文件树",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        FileTree(
            rootPath = projectPath,
            modifier = Modifier.fillMaxSize(),
            onFileClick = onFileClick
        )
    }
}

@Composable
fun AnimatedDrawerToggle(isOpen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "DrawerToggleProgress"
    )

    IconButton(onClick = onClick, modifier = modifier) {
        val color = LocalContentColor.current
        Canvas(
            modifier = Modifier
                .size(24.dp)
                .padding(2.dp)
        ) {
            val strokeWidth = 2.dp.toPx()
            val cap = StrokeCap.Round
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val yOffset = 5.dp.toPx()
            val arrowheadSize = width * 0.3f

            withTransform({
                rotate(
                    degrees = lerp(0f, 180f, progress),
                    pivot = Offset(x = width / 2, y = height / 2)
                )
            }) {
                drawLine(
                    color = color,
                    strokeWidth = strokeWidth,
                    cap = cap,
                    start = Offset(x = 0f, y = centerY),
                    end = Offset(x = width, y = centerY)
                )
            }
            val bottomInitialStart = Offset(x = 0f, y = centerY + yOffset)
            val bottomInitialEnd = Offset(x = width, y = centerY + yOffset)
            val bottomFinalStart = Offset(x = arrowheadSize, y = centerY - arrowheadSize)
            val bottomFinalEnd = Offset(x = 0f, y = centerY)

            drawLine(
                color = color,
                strokeWidth = strokeWidth,
                cap = cap,
                start = lerp(bottomInitialStart, bottomFinalStart, progress),
                end = lerp(bottomInitialEnd, bottomFinalEnd, progress)
            )

            val topInitialStart = Offset(x = 0f, y = centerY - yOffset)
            val topInitialEnd = Offset(x = width, y = centerY - yOffset)
            val finalTopStart = Offset(x = arrowheadSize, y = centerY + arrowheadSize)
            val finalTopEnd = Offset(x = 0f, y = centerY)

            drawLine(
                color = color,
                strokeWidth = strokeWidth,
                cap = cap,
                start = lerp(topInitialStart, finalTopStart, progress),
                end = lerp(topInitialEnd, finalTopEnd, progress)
            )
        }
    }
}

/**
 * 封装的构建逻辑
 */
private suspend fun performBuild(
    context: Context,
    projectPath: String,
    folderName: String,
    viewModel: EditorViewModel,
    snackbarHostState: SnackbarHostState,
    onResult: (BuildResultState) -> Unit
) {
    viewModel.saveAllModifiedFiles(snackbarHostState)
    val prefs = context.getSharedPreferences("WebIDE_Project_Settings", android.content.Context.MODE_PRIVATE)
    val isDebug = prefs.getBoolean("debug_$folderName", false)

    val configFile = java.io.File(projectPath, "webapp.json")
    var pkg = "com.example.webapp"
    var verName = "1.0"
    var verCode = "1"
    var iconPath = ""
    var permissions: Array<String>? = null

    if (configFile.exists()) {
        try {
            var jsonStr = withContext(Dispatchers.IO) {
                configFile.readText()
            }
            jsonStr = jsonStr.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")

            val json = org.json.JSONObject(jsonStr)
            pkg = json.optString("package", pkg)
            verName = json.optString("versionName", verName)
            verCode = json.optString("versionCode", verCode)

            val iconName = json.optString("icon", "")
            if (iconName.isNotEmpty()) {
                val iconFile = java.io.File(projectPath, iconName)
                if (iconFile.exists()) iconPath = iconFile.absolutePath
            }

            val jsonPerms = json.optJSONArray("permissions")
            if (jsonPerms != null && jsonPerms.length() > 0) {
                val list = ArrayList<String>()
                for (i in 0 until jsonPerms.length()) {
                    list.add(jsonPerms.getString(i))
                }
                permissions = list.toTypedArray()
            }
        } catch (e: Exception) {
            LogCatcher.e("Build", "JSON Error", e)
        }
    }

    val result = withContext(Dispatchers.IO) {
        com.web.webide.build.ApkBuilder.bin(
            context,
            WorkspaceManager.getWorkspacePath(context),
            projectPath,
            folderName,
            pkg,
            verName,
            verCode,
            iconPath,
            permissions,
            isDebug
        )
    }

    if (result.startsWith("error:")) {
        onResult(BuildResultState.Finished(result, null))
    } else {
        onResult(BuildResultState.Finished("构建成功", result))
    }
}