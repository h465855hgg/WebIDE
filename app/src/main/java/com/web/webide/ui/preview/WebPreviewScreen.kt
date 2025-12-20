package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.*
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
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

    // --- 0. 自动关闭软键盘 ---
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    // --- 状态管理 ---
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

    // --- 1. 读取配置 ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir, key2 = configRefreshTrigger) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try {
                    val rawJson = configFile.readText()
                    // 简单的去注释处理
                    val cleanJson = rawJson.lines().joinToString("\n") { line ->
                        val index = line.indexOf("//")
                        if (index != -1 && !line.substring(0, index).trim().endsWith(":")) {
                            if(!line.contains("http:") && !line.contains("https:")) line.substring(0, index) else line
                        } else line
                    }
                    JSONObject(cleanJson)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }
    val config = webAppConfig.value

    // --- 2. 动态权限申请 ---
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

    // --- 3. 屏幕方向控制 (优化版) ---
    DisposableEffect(config) {
        if (activity != null && config != null) {
            val orientation = config.optString("orientation", "0")
            val targetOrientation = when (orientation) {
                "1" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "0" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "auto" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            // 只有当前方向与目标不一致时才设置，避免无意义的重建
            if (activity.requestedOrientation != targetOrientation) {
                activity.requestedOrientation = targetOrientation
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // --- 4. 状态栏与全屏控制 ---
    var isFullScreenConfig by remember(config) {
        mutableStateOf(config?.optBoolean("fullscreen", false) == true)
    }
    var isUserFullScreen by remember(isFullScreenConfig) { mutableStateOf(isFullScreenConfig) }

    DisposableEffect(config, isUserFullScreen) {
        if (activity != null) {
            val window = activity.window
            val windowController = WindowCompat.getInsetsController(window, window.decorView)

            if (isUserFullScreen) {
                windowController.hide(WindowInsetsCompat.Type.systemBars())
                windowController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowController.show(WindowInsetsCompat.Type.systemBars())

                val statusBarConfig = config?.optJSONObject("statusBar")
                if (statusBarConfig != null) {
                    try {
                        val colorStr = statusBarConfig.optString("backgroundColor", "#FFFFFF")
                        if (colorStr.isNotEmpty()) window.statusBarColor = AndroidColor.parseColor(colorStr)

                        val style = statusBarConfig.optString("style", "dark")
                        // style="dark" 代表想要深色文字，所以 LightStatusBars = true
                        windowController.isAppearanceLightStatusBars = (style == "dark")
                    } catch (e: Exception) {}
                } else {
                    window.statusBarColor = AndroidColor.WHITE
                    windowController.isAppearanceLightStatusBars = true
                }
            }
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val windowController = WindowCompat.getInsetsController(window, window.decorView)
                windowController.show(WindowInsetsCompat.Type.systemBars())
                windowController.isAppearanceLightStatusBars = true
                window.statusBarColor = AndroidColor.TRANSPARENT
            }
        }
    }

    // --- 5. 路径解析 ---
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

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filePathCallback = null
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val refreshKey = remember(config, isDebugEnabled, currentUAType, configRefreshTrigger) { System.currentTimeMillis() }

    BackHandler(enabled = true) {
        if (isJsHandlingBack) {
            webViewRef?.evaluateJavascript("if(window.onAndroidBack) window.onAndroidBack();", null)
        } else {
            if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isUserFullScreen) {
                TopAppBar(
                    // 🔥 这里修改了：固定标题
                    title = { Text("App 预览") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
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
                        IconButton(onClick = { toggleDebugMode() }) {
                            Icon(Icons.Default.BugReport, "调试", tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            webViewRef?.reload()
                            configRefreshTrigger = System.currentTimeMillis()
                        }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
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

// 辅助函数保持不变，无需修改
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
        return htmlContent
    }
}

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
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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

    if (manualUA != UserAgents.DEFAULT) {
        settings.userAgentString = manualUA
    } else if (finalUA.isNotEmpty()) {
        settings.userAgentString = finalUA
    } else {
        settings.userAgentString = null
    }

    val packageName = config?.optString("package", "com.example.webapp") ?: "com.web.preview"

    webView.addJavascriptInterface(
        FullWebAppInterface(context, webView, packageName, projectDir, onBackStateChange),
        "Android"
    )

    webView.webChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
            return if (filePathCallback != null) onShowFileChooser(filePathCallback, fileChooserParams) else false
        }
        override fun onPermissionRequest(request: PermissionRequest?) {
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
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, request?.url)); return true } catch (e: Exception) {}
            }
            return false
        }
    }
    WebView.setWebContentsDebuggingEnabled(true)
}