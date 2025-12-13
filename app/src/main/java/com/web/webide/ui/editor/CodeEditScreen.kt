package com.web.webide.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject

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

    var snackbarHostState = remember { SnackbarHostState() }
    // 初始加载进度条状态
    var showInitialLoader by remember { mutableStateOf(!viewModel.hasShownInitialLoader) }
    // 构建过程中的进度条状态
    var isBuilding by remember { mutableStateOf(false) }

    // 构建结果状态
    var buildResult by remember { mutableStateOf<BuildResultState?>(null) }

    // 检测 webapp.json 是否存在
    val hasWebAppConfig = remember(projectPath) {
        File(projectPath, "webapp.json").exists()
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
                                    // ✅ 传入 snackbarHostState
                                    viewModel.saveAllModifiedFiles(context, snackbarHostState)
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
                                            // ✅ 传入 snackbarHostState
                                            viewModel.saveAllModifiedFiles(
                                                context,
                                                snackbarHostState
                                            )
                                        }
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
                                                viewModel.saveAllModifiedFiles(
                                                    context,
                                                    snackbarHostState
                                                )
                                                val prefs = context.getSharedPreferences(
                                                    "WebIDE_Project_Settings",
                                                    Context.MODE_PRIVATE
                                                )
                                                val isDebug =
                                                    prefs.getBoolean("debug_$folderName", false)


                                                val configFile = File(projectPath, "webapp.json")
                                                var pkg: String? = null
                                                var verName: String? = null
                                                var verCode: String? = null
                                                var iconPath: String = ""
                                                var permissions: Array<String>? = null
                                                var statusBarConfig: String? = null // 新增：状态栏配置

                                                if (configFile.exists()) {
                                                    try {
                                                        val jsonStr =
                                                            withContext(Dispatchers.IO) { configFile.readText() }
                                                        val json = JSONObject(jsonStr)

                                                        // 解析包名、版本
                                                        pkg = json.optString(
                                                            "package",
                                                            "com.example.webapp"
                                                        )
                                                        verName =
                                                            json.optString("versionName", "1.0")
                                                        verCode = json.optString("versionCode", "1")

                                                        // 解析 Icon 路径
                                                        val iconName = json.optString("icon", "")
                                                        if (iconName.isNotEmpty()) {
                                                            val iconFile =
                                                                File(projectPath, iconName)
                                                            if (iconFile.exists()) {
                                                                iconPath = iconFile.absolutePath
                                                            } else {
                                                                LogCatcher.w(
                                                                    "Build",
                                                                    "未找到图标文件: ${iconFile.absolutePath}"
                                                                )
                                                            }
                                                        }

                                                        // 解析权限列表
                                                        val jsonPerms =
                                                            json.optJSONArray("permissions")
                                                        if (jsonPerms != null && jsonPerms.length() > 0) {
                                                            val list = ArrayList<String>()
                                                            for (i in 0 until jsonPerms.length()) {
                                                                list.add(jsonPerms.getString(i))
                                                            }
                                                            permissions = list.toTypedArray()
                                                        }

                                                        // 新增：解析状态栏配置
                                                        val statusBarJson =
                                                            json.optJSONObject("statusBar")
                                                        if (statusBarJson != null) {
                                                            statusBarConfig =
                                                                statusBarJson.toString()
                                                            LogCatcher.d(
                                                                "Build",
                                                                "状态栏配置: $statusBarConfig"
                                                            )
                                                        }

                                                    } catch (e: Exception) {
                                                        LogCatcher.e(
                                                            "Build",
                                                            "解析 webapp.json 失败",
                                                            e
                                                        )
                                                    }
                                                }


                                                val result = withContext(Dispatchers.IO) {
                                                    // 2. 将解析出来的数据显式传递给 ApkBuilder
                                                    com.web.webide.build.ApkBuilder.bin(
                                                        context,           // Context context
                                                        workspacePath,     // String mRootDir
                                                        projectPath,       // String projectPath
                                                        folderName,        // String aname
                                                        pkg,               // String pkg
                                                        verName,           // String ver
                                                        verCode,           // String code
                                                        iconPath,          // String amph
                                                        permissions,        // String[] ps
                                                        isDebug            // 🔥 boolean isDebug
                                                        // 注意：ApkBuilder.bin 方法的参数列表没有 statusBarConfig
                                                        // 但我们已在 mergeApk 方法中将 webapp.json 打包到 assets
                                                    )
                                                }

                                                isBuilding = false

                                                if (result.startsWith("error:")) {
                                                    LogCatcher.e("Build", "构建失败: $result")
                                                    buildResult =
                                                        BuildResultState.Finished(result, null)
                                                } else {
                                                    LogCatcher.i("Build", "构建成功: $result")
                                                    buildResult = BuildResultState.Finished(
                                                        "构建成功",
                                                        result
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
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
                    AnimatedVisibility(visible = showInitialLoader || isBuilding) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Butt
                        )
                    }
                    EditCode(modifier = Modifier.fillMaxSize(), viewModel = viewModel)
                }
            }
        )

        // 弹窗逻辑
        buildResult?.let { result ->
            if (result is BuildResultState.Finished) {
                // 如果有 APK 路径，就视为成功
                val isSuccess = result.apkPath != null

                AlertDialog(
                    onDismissRequest = { buildResult = null },
                    title = {
                        Text(if (isSuccess) "构建成功" else "构建失败")
                    },
                    text = {
                        Column {
                            if (isSuccess) {
                                Text("APK 已生成，是否立即安装？")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("输出路径:", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${result.apkPath}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("错误信息：", color = MaterialTheme.colorScheme.error)
                                Text(result.message)
                            }
                        }
                    },
                    confirmButton = {
                        if (isSuccess && result.apkPath != null) {
                            TextButton(onClick = {
                                buildResult = null
                            }) {
                                Text("安装")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { buildResult = null }) {
                            Text("关闭")
                        }
                    }
                )
            }
        }
    }
}

// ---------------- 辅助函数 ----------------

// 确保 Keystore 存在
private suspend fun ensureKeystoreExists(context: Context, workspacePath: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val keystoreName = "WebIDE.jks"
            val targetKeystore = File(workspacePath, keystoreName)

            if (!targetKeystore.exists()) {
                try {
                    context.assets.open(keystoreName).use { input ->
                        targetKeystore.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    return@withContext null
                }
            }
            targetKeystore.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

// 安装 APK
private fun installApk(
    context: Context, apkFile: File,
    scope: kotlinx.coroutines.CoroutineScope, // 新增
    snackbarHostState: SnackbarHostState
) {
    if (!apkFile.exists()) {
        return
    }

    // Android 8.0+ 权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            scope.launch { snackbarHostState.showSnackbar("请开启安装权限") }
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
    }

    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

    } catch (e: Exception) {
        LogCatcher.e("Install", "Error", e)
    }
}

// ✅ 修正后的查找逻辑：去 build 目录找
private fun findBuiltApk(projectPath: String, appName: String): File? {
    // 你的 Java 代码写的是 File(projectPath, "build")
    val buildDir = File(projectPath, "build")

    if (buildDir.exists() && buildDir.isDirectory) {
        // 优先匹配标准命名: appName + "_release.apk"
        val specificFile = File(buildDir, "${appName}_release.apk")
        if (specificFile.exists()) return specificFile

        // 如果没找到标准命名的，找任何以 .apk 结尾的最新文件
        val apks = buildDir.listFiles { _, name -> name.lowercase().endsWith(".apk") }
        if (!apks.isNullOrEmpty()) {
            return apks.maxByOrNull { it.lastModified() }
        }
    }
    return null
}

// 以下 UI 组件保持不变
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolBar(viewModel: EditorViewModel) {
    val symbols = listOf(
        "Tab",
        "<",
        ">",
        "/",
        "=",
        "\"",
        "'",
        "!",
        "?",
        ",",
        ";",
        ":",
        "(",
        ")",
        "[",
        "]",
        "{",
        "}",
        "+",
        "-",
        "*",
        "_",
        "&",
        "|"
    )
    BottomAppBar(modifier = Modifier
        .imePadding()
        .height(48.dp)) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { symbol ->
                Box(modifier = Modifier
                    .clickable { viewModel.insertSymbol(symbol) }
                    .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
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
                selectedTabIndex = pagerState.currentPage.coerceIn(
                    0,
                    openFiles.size - 1
                ),
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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