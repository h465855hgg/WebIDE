package com.web.webide.ui.editor

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
import androidx.compose.material3.HorizontalDivider
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
import com.web.webide.build.ApkBuilder
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.files.FileTree
import com.web.webide.ui.editor.components.CodeEditorView
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Sealed class 用于管理构建UI的状态
sealed class BuildState {
    object Idle : BuildState() // 空闲状态
    object InProgress : BuildState() // 构建进行中
    data class Finished(val message: String) : BuildState() // 构建完成（包含结果信息）
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
    var showProgressBar by remember { mutableStateOf(!viewModel.hasShownInitialLoader) }
    var buildState by remember { mutableStateOf<BuildState>(BuildState.Idle) } // 使用新的状态管理

    LaunchedEffect(projectPath) {
        viewModel.loadInitialFile(projectPath)
    }

    LaunchedEffect(Unit) {
        if (showProgressBar) {
            delay(500L)
            showProgressBar = false
            viewModel.onInitialLoaderShown()
        }
    }

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false, // 禁用手势打开
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
            topBar = {
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
                                    if (drawerState.isClosed) {
                                        drawerState.open()
                                    } else {
                                        drawerState.close()
                                    }
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
                                viewModel.saveAllModifiedFiles(context)
                                navController.navigate("preview/$folderName")
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
                                        scope.launch { viewModel.saveAllModifiedFiles(context) }
                                        isMoreMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("build") },
                                    onClick = {
                                        isMoreMenuExpanded = false // 关闭菜单

                                        // 启动协程
                                        scope.launch {
                                            // 1. 【新增】先保存所有已修改的文件到磁盘
                                            // 这一步是必须的，因为 ApkBuilder 读取的是磁盘文件
                                            viewModel.saveAllModifiedFiles(context)

                                            // 2. 设置 UI 状态为“构建中”
                                            buildState = BuildState.InProgress

                                            // 3. 切换到 IO 线程执行耗时的打包操作
                                            withContext(Dispatchers.IO) {
                                                val result = ApkBuilder.bin(
                                                    context,
                                                    workspacePath, // mRootDir
                                                    projectPath,   // mDir
                                                    folderName,    // aname
                                                    // 包名生成逻辑
                                                    "com.example.${folderName.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase(Locale.ROOT)}",
                                                    "1.0", // ver
                                                    "1",   // code
                                                    "",    // amph
                                                    arrayOf() // ps
                                                )

                                                // 4. 切回主线程更新结果
                                                withContext(Dispatchers.Main) {
                                                    buildState = BuildState.Finished(result)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SymbolBar(viewModel = viewModel)
                }
            },
            content = { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
                    AnimatedVisibility(visible = showProgressBar) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Butt
                        )
                    }
                    EditCode(modifier = Modifier.fillMaxSize(), viewModel = viewModel)
                }
            }
        )
        
        // --- 根据构建状态显示不同的对话框 ---
        when (val currentBuildState = buildState) {
            is BuildState.InProgress -> {
                AlertDialog(
                    onDismissRequest = { /* 构建时不允许关闭 */ },
                    title = { Text("构建中") },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("正在打包 APK...")
                        }
                    },
                    confirmButton = {}
                )
            }
            is BuildState.Finished -> {
                val isSuccess = !currentBuildState.message.startsWith("error:")
                AlertDialog(
                    onDismissRequest = { buildState = BuildState.Idle },
                    title = { Text(if (isSuccess) "构建成功" else "构建失败") },
                    text = { Text(currentBuildState.message) },
                    confirmButton = {
                        Button(onClick = { buildState = BuildState.Idle }) {
                            Text("确定")
                        }
                    }
                )
            }
            is BuildState.Idle -> {
                // 空闲状态，不显示任何对话框
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolBar(viewModel: EditorViewModel) {
    val symbols = listOf(
        "Tab", "<", ">", "/", "=", "\"", "'", "!", "?", ",", ";", ":",
        "(", ")", "[", "]", "{", "}", "+", "-", "*", "_", "&", "|"
    )
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
                        .padding(horizontal = 16.dp, vertical = 0.dp),
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
fun EditCode(modifier: Modifier = Modifier, viewModel: EditorViewModel) {
    val openFiles = viewModel.openFiles
    val activeFileIndex = viewModel.activeFileIndex
    val scope = rememberCoroutineScope()
    var expandedTabIndex by remember { mutableStateOf<Int?>(null) }
    val currentFiles by rememberUpdatedState(openFiles)
    val currentIndex by rememberUpdatedState(activeFileIndex)

    val pagerState = rememberPagerState(
        initialPage = activeFileIndex.coerceIn(0, maxOf(0, openFiles.size - 1)),
        pageCount = { currentFiles.size }
    )

    LaunchedEffect(currentIndex, currentFiles.size) {
        if (currentFiles.isNotEmpty() &&
            currentIndex >= 0 &&
            currentIndex < currentFiles.size &&
            pagerState.currentPage != currentIndex
        ) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (currentFiles.isNotEmpty() &&
                    page in currentFiles.indices &&
                    page != currentIndex
                ) {
                    viewModel.changeActiveFileIndex(page)
                }
            }
    }

    Column(modifier = modifier) {
        if (openFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未打开任何文件")
            }
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
                }
            ) {
                openFiles.forEachIndexed { index, editorState ->
                    Box {
                        val displayName = if (editorState.isModified) {
                            "*${editorState.file.name}"
                        } else {
                            editorState.file.name
                        }
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                if (pagerState.currentPage == index) {
                                    expandedTabIndex = index
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            },
                            text = { Text(text = displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )

                        DropdownMenu(
                            expanded = expandedTabIndex == index,
                            onDismissRequest = { expandedTabIndex = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("关闭") },
                                onClick = {
                                    expandedTabIndex = null
                                    viewModel.closeFile(index)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭其他") },
                                onClick = {
                                    expandedTabIndex = null
                                    viewModel.closeOtherFiles(index)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭全部") },
                                onClick = {
                                    expandedTabIndex = null
                                    viewModel.closeAllFiles()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                    userScrollEnabled = false,
                key = { index ->
                    if (index < openFiles.size) {
                        openFiles[index].file.absolutePath
                    } else {
                        "empty_$index"
                    }
                }
            ) { page ->
                if (page in openFiles.indices) {
                    val editorState = openFiles[page]
                    CodeEditorView(
                        modifier = Modifier.fillMaxSize(),
                        state = editorState,
                        viewModel = viewModel
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
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

fun lerp(start: Offset, stop: Offset, fraction: Float): Offset {
    return Offset(
        x = lerp(start.x, stop.x, fraction),
        y = lerp(start.y, stop.y, fraction)
    )
}

@Composable
fun AnimatedDrawerToggle(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
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
                rotate(degrees = lerp(0f, 180f, progress), pivot = Offset(x = width / 2, y = height / 2))
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