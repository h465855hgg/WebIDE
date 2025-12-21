package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// UA 常量定义
object UserAgents {
    const val DEFAULT = "Default"
    const val PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    const val ANDROID = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(folderName: String, navController: NavController, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(workspacePath, folderName)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- 0. 自动收起软键盘 ---
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    // --- 1. 核心修复：环境状态保存与恢复 (Snapshot & Restore) ---
    // 使用 DisposableEffect(Unit) 确保只在进入和退出时各执行一次，不受重组影响
    DisposableEffect(Unit) {
        val window = activity?.window
        // 1. 保存进入前的屏幕方向
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // 2. 保存进入前的状态栏颜色和图标样式
        var originalStatusBarColor = AndroidColor.TRANSPARENT
        var originalIsLightStatusBars = true
        var originalSystemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            originalStatusBarColor = window.statusBarColor
            originalIsLightStatusBars = controller.isAppearanceLightStatusBars
            originalSystemBarsBehavior = controller.systemBarsBehavior
        }

        onDispose {
            // --- 退出时精准还原 ---
            if (activity != null && window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)

                // 还原方向
                activity.requestedOrientation = originalOrientation

                // 还原系统栏显示状态 (防止全屏退出后状态栏消失)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = originalSystemBarsBehavior

                // 还原颜色和图标颜色
                window.statusBarColor = originalStatusBarColor
                controller.isAppearanceLightStatusBars = originalIsLightStatusBars
            }
        }
    }

    // --- 2. 状态管理 ---
    val prefs = remember { context.getSharedPreferences("WebIDE_Project_Settings", Context.MODE_PRIVATE) }
    var isDebugEnabled by remember { mutableStateOf(prefs.getBoolean("debug_$folderName", false)) }
    var currentUAType by remember { mutableStateOf(prefs.getString("ua_type_$folderName", UserAgents.DEFAULT) ?: UserAgents.DEFAULT) }
    var showUAMenu by remember { mutableStateOf(false) }
    var configRefreshTrigger by remember { mutableLongStateOf(0L) }
    var isJsHandlingBack by remember { mutableStateOf(false) }

    fun toggleDebugMode() {
        isDebugEnabled = !isDebugEnabled
        prefs.edit().putBoolean("debug_$folderName", isDebugEnabled).apply()
        scope.launch { snackbarHostState.showSnackbar(if (isDebugEnabled) "调试模式已开启" else "调试模式已关闭") }
    }

    fun updateUA(type: String) {
        currentUAType = type
        prefs.edit().putString("ua_type_$folderName", type).apply()
        showUAMenu = false
        configRefreshTrigger = System.currentTimeMillis()
        scope.launch { snackbarHostState.showSnackbar("UA 已切换") }
    }

    // --- 3. 读取配置 (webapp.json) ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir, key2 = configRefreshTrigger) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try {
                    val rawJson = configFile.readText()
                    // 简单去注释逻辑
                    val cleanJson = rawJson.lines().joinToString("\n") { line ->
                        val index = line.indexOf("//")
                        if (index != -1 && !line.substring(0, index).trim().endsWith(":")) {
                            if(!line.contains("http:") && !line.contains("https:")) line.substring(0, index) else line
                        } else line
                    }
                    JSONObject(cleanJson)
                } catch (e: Exception) {
                    LogCatcher.e("WebPreview", "Config parse error", e)
                    null
                }
            } else null
        }
    }
    val config = webAppConfig.value

    // --- 4. 动态权限申请 ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(config) {
        config?.let { json ->
            val permsJson = json.optJSONArray("permissions")
            if (permsJson != null) {
                val permsList = mutableListOf<String>()
                for (i in 0 until permsJson.length()) permsList.add(permsJson.getString(i))
                val neededPerms = permsList.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
                if (neededPerms.isNotEmpty()) permissionLauncher.launch(neededPerms)
            }
        }
    }

    // --- 5. 应用窗口配置 (方向、状态栏、全屏) ---
    // 这里只负责“应用”配置，不负责“还原”
    var isFullScreenConfig by remember(config) {
        mutableStateOf(config?.optBoolean("fullscreen", false) == true)
    }
    var isUserFullScreen by remember(isFullScreenConfig) { mutableStateOf(isFullScreenConfig) }

    LaunchedEffect(config, isUserFullScreen) {
        if (activity != null) {
            val window = activity.window
            val windowController = WindowCompat.getInsetsController(window, window.decorView)

            // A. 设置方向
            if (config != null) {
                val orientation = config.optString("orientation", "portrait")
                val targetOrientation = when (orientation) {
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "auto" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                // 只有方向不同时才设置，避免重复调用导致 Activity 重绘
                if (activity.requestedOrientation != targetOrientation) {
                    activity.requestedOrientation = targetOrientation
                }
            }

            // B. 设置全屏与状态栏样式
            if (isUserFullScreen) {
                windowController.hide(WindowInsetsCompat.Type.systemBars())
                windowController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowController.show(WindowInsetsCompat.Type.systemBars())

                val statusBarConfig = config?.optJSONObject("statusBar")
                if (statusBarConfig != null) {
                    try {
                        // 背景色
                        val colorStr = statusBarConfig.optString("backgroundColor", "#FFFFFF")
                        if (colorStr.isNotEmpty()) window.statusBarColor = AndroidColor.parseColor(colorStr)

                        // 文字颜色: style="dark" -> 深色文字 -> LightStatusBars=true
                        val style = statusBarConfig.optString("style", "dark")
                        windowController.isAppearanceLightStatusBars = (style == "dark")
                    } catch (e: Exception) {
                        LogCatcher.e("WebPreview", "Status bar config error", e)
                    }
                } else {
                    // 默认预览样式：白底黑字
                    window.statusBarColor = AndroidColor.WHITE
                    windowController.isAppearanceLightStatusBars = true
                }
            }
        }
    }

    // --- 6. 路径解析 ---
    val targetUrl = remember(projectDir, config) {
        val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
            ?: "index.html"

        if (rawUrl.startsWith("http")) {
            rawUrl
        } else {
            val cleanPath = rawUrl.removePrefix("./").removePrefix("/")
            val rootFile = File(projectDir, cleanPath)
            val assetFile = File(projectDir, "src/main/assets/$cleanPath")
            when {
                rootFile.exists() -> "file://${rootFile.absolutePath}"
                assetFile.exists() -> "file://${assetFile.absolutePath}"
                else -> "file://${File(projectDir, "index.html").absolutePath}"
            }
        }
    }

    // --- 7. WebView 交互组件 ---
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filePathCallback = null
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 刷新 Key：当配置、调试模式或 UA 改变时，重建 WebView
    val refreshKey = remember(config, isDebugEnabled, currentUAType, configRefreshTrigger) { System.currentTimeMillis() }

    // 处理物理返回键
    BackHandler(enabled = true) {
        if (isJsHandlingBack) {
            webViewRef?.evaluateJavascript("if(window.onAndroidBack) window.onAndroidBack();", null)
        } else {
            if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else navController.popBackStack()
        }
    }

    // --- 8. UI 布局 ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isUserFullScreen) {
                TopAppBar(
                    title = { Text("App 预览") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        // UA 切换
                        Box {
                            IconButton(onClick = { showUAMenu = true }) {
                                Icon(Icons.Default.Devices, "UA", tint = if (currentUAType != UserAgents.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = showUAMenu, onDismissRequest = { showUAMenu = false }) {
                                listOf(
                                    UserAgents.DEFAULT to "默认",
                                    UserAgents.PC to "PC",
                                    UserAgents.IPHONE to "iOS",
                                    UserAgents.ANDROID to "Android"
                                ).forEach { (ua, name) ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = { updateUA(ua) })
                                }
                            }
                        }
                        // 调试开关
                        IconButton(onClick = { toggleDebugMode() }) {
                            Icon(Icons.Default.BugReport, "调试", tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 刷新
                        IconButton(onClick = {
                            webViewRef?.reload()
                            configRefreshTrigger = System.currentTimeMillis()
                        }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        // 全屏开关
                        IconButton(onClick = { isUserFullScreen = true }) {
                            Icon(Icons.Default.Fullscreen, "全屏")
                        }
                    }
                )
            }
        },
        containerColor = if (isUserFullScreen) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val actualPadding = if (isUserFullScreen) PaddingValues(0.dp) else innerPadding

        Box(modifier = Modifier.padding(actualPadding).fillMaxSize()) {
            key(refreshKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(-1, -1)
                            configureFullWebView(
                                webView = this,
                                context = ctx,
                                config = config,
                                projectDir = projectDir,
                                manualUA = currentUAType,
                                onShowFileChooser = { callback, params ->
                                    filePathCallback = callback
                                    try {
                                        params?.createIntent()?.let { fileChooserLauncher.launch(it); true } ?: false
                                    } catch (e: Exception) {
                                        filePathCallback = null; false
                                    }
                                },
                                onBackStateChange = { isJsHandlingBack = it }
                            )
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        if (webView.url != null && webView.url == targetUrl) return@AndroidView

                        // 调试模式下注入 Eruda
                        if (targetUrl.startsWith("file://") && isDebugEnabled) {
                            try {
                                val file = File(targetUrl.replace("file://", ""))
                                if (file.exists()) {
                                    val html = injectErudaIntoHtml(context, file.readText())
                                    webView.loadDataWithBaseURL(targetUrl, html, "text/html", "UTF-8", targetUrl)
                                } else webView.loadUrl(targetUrl)
                            } catch (e: Exception) { webView.loadUrl(targetUrl) }
                        } else {
                            if (webView.url != targetUrl) webView.loadUrl(targetUrl)
                        }
                    }
                )
            }

            // 全屏时的退出按钮
            if (isUserFullScreen) {
                Row(modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)) {
                    IconButton(
                        onClick = { isUserFullScreen = false },
                        modifier = Modifier.background(Color.Black.copy(0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.FullscreenExit, "退出", tint = Color.White)
                    }
                }
            }
        }
    }
}

// --- 辅助函数：注入 Eruda 调试工具 ---
private fun injectErudaIntoHtml(context: Context, htmlContent: String): String {
    try {
        val erudaCode = context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
        val script = """
            <script>
            $erudaCode
            (function() {
                localStorage.removeItem('eruda-entry-btn');
                eruda.init();
                var entryBtn = eruda.get('entry');
                if (entryBtn) {
                    entryBtn.position({ x: window.innerWidth - 50, y: window.innerHeight / 2 });
                }
            })();
            </script>
        """
        return if (htmlContent.contains("</body>", ignoreCase = true)) {
            htmlContent.replace("</body>", "$script</body>", ignoreCase = true)
        } else {
            htmlContent + script
        }
    } catch (e: Exception) {
        LogCatcher.e("WebPreview", "Eruda inject failed", e)
        return htmlContent
    }
}

// --- 辅助函数：WebView 配置 ---
@SuppressLint("SetJavaScriptEnabled")
private fun configureFullWebView(
    webView: WebView,
    context: Context,
    config: JSONObject?,
    projectDir: File,
    manualUA: String,
    onShowFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean,
    onBackStateChange: (Boolean) -> Unit
) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    settings.allowFileAccessFromFileURLs = true
    settings.allowUniversalAccessFromFileURLs = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }

    // --- 读取 WebView 相关配置 ---
    var finalUA = ""
    var textZoom = 100
    var zoomEnabled = false

    if (config != null) {
        val wv = config.optJSONObject("webview")
        if (wv != null) {
            zoomEnabled = wv.optBoolean("zoomEnabled", false)
            textZoom = wv.optInt("textZoom", 100)
            finalUA = wv.optString("userAgent", "")
        }
    }

    settings.setSupportZoom(zoomEnabled)
    settings.builtInZoomControls = zoomEnabled
    settings.displayZoomControls = false
    settings.textZoom = textZoom

    // UA 优先级：手动选择 > 配置文件 > 系统默认
    if (manualUA != UserAgents.DEFAULT) {
        settings.userAgentString = manualUA
    } else if (finalUA.isNotEmpty()) {
        settings.userAgentString = finalUA
    } else {
        settings.userAgentString = null
    }

    val packageName = config?.optString("package", "com.example.webapp") ?: "com.web.preview"

    // 绑定 JS 接口 (注意：FullWebAppInterface 需与之前的类名一致)
    webView.addJavascriptInterface(
        FullWebAppInterface(context, webView, packageName, projectDir, onBackStateChange),
        "Android"
    )

    webView.webChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
            return if (filePathCallback != null) onShowFileChooser(filePathCallback, fileChooserParams) else false
        }
        override fun onPermissionRequest(request: PermissionRequest?) {
            // 自动授权
            request?.grant(request.resources)
        }
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            LogCatcher.d("WebPreview_JS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
            return true
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            // 拦截特殊协议
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, request?.url)); return true } catch (e: Exception) {}
            }
            return false
        }
    }
    WebView.setWebContentsDebuggingEnabled(true)
}