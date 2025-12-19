package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// 定义 UA 常量
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

    // --- 0. 状态管理 ---
    val prefs = remember { context.getSharedPreferences("WebIDE_Project_Settings", Context.MODE_PRIVATE) }

    // 调试开关状态
    var isDebugEnabled by remember {
        mutableStateOf(prefs.getBoolean("debug_$folderName", false))
    }

    // 🔥 新增：UA 类型状态
    var currentUAType by remember {
        mutableStateOf(prefs.getString("ua_type_$folderName", UserAgents.DEFAULT) ?: UserAgents.DEFAULT)
    }
    var showUAMenu by remember { mutableStateOf(false) }

    // 刷新配置的触发器
    var configRefreshTrigger by remember { mutableLongStateOf(0L) }

    // 管理网页是否接管返回键的状态
    var isJsHandlingBack by remember { mutableStateOf(false) }

    // 切换调试模式
    fun toggleDebugMode() {
        isDebugEnabled = !isDebugEnabled
        prefs.edit().putBoolean("debug_$folderName", isDebugEnabled).apply()
        scope.launch { snackbarHostState.showSnackbar(if (isDebugEnabled) "调试模式已开启" else "调试模式已关闭") }
    }

    // 🔥 新增：切换 UA 函数
    fun updateUA(type: String) {
        currentUAType = type
        prefs.edit().putString("ua_type_$folderName", type).apply()
        showUAMenu = false
        // 触发刷新
        configRefreshTrigger = System.currentTimeMillis()
        scope.launch { snackbarHostState.showSnackbar("UA 已切换为: ${if(type == UserAgents.DEFAULT) "默认" else "自定义"}") }
    }

    // --- 1. 权限申请 ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        LogCatcher.d("WebPreview", "Permissions granted: $permissions")
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))
    }

    // --- 2. 读取配置 (增强版：支持注释 + 错误提示) ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir, key2 = configRefreshTrigger) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try {
                    val rawJson = configFile.readText()
                    val cleanJson = rawJson.lines().map { line ->
                        val index = line.indexOf("//")
                        if (index != -1) {
                            if (index > 0 && (line[index - 1] == ':' || line.substring(0, index).contains("http"))) line
                            else line.substring(0, index)
                        } else line
                    }.joinToString("\n")
                    JSONObject(cleanJson)
                } catch (e: Exception) {
                    LogCatcher.e("WebPreview", "配置解析失败", e)
                    launch(Dispatchers.Main) {
                        scope.launch { snackbarHostState.showSnackbar("webapp.json 格式错误: ${e.message}") }
                    }
                    null
                }
            } else null
        }
    }
    val config = webAppConfig.value

    // 全屏模式状态
    var isFullScreenMode by remember(config) {
        mutableStateOf(config?.optBoolean("fullscreen", false) == true)
    }

    // --- 3. 应用状态栏配置 ---
    DisposableEffect(config) {
        val statusBarConfig = config?.optJSONObject("statusBar")
        if (statusBarConfig != null && activity != null) {
            val window = activity.window
            val decorView = window.decorView

            if (statusBarConfig.optBoolean("hidden", false)) {
                val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                decorView.systemUiVisibility = flags
            } else {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                try {
                    val colorStr = statusBarConfig.optString("backgroundColor", "")
                    if (colorStr.isNotEmpty() && colorStr.startsWith("#")) {
                        val color = Color(android.graphics.Color.parseColor(colorStr))
                        window.statusBarColor = color.toArgb()
                    }
                } catch (e: Exception) { }

                val style = statusBarConfig.optString("style", "dark")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    when (style) {
                        "light" -> decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        "dark" -> decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
                if (statusBarConfig.optBoolean("translucent", false)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                }
            }
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                }
            }
        }
    }

    // --- 4. 智能路径查找 ---
    val targetUrl = remember(projectDir, config) {
        val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("entry")?.takeIf { it.isNotEmpty() }
            ?: "index.html"

        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            val cleanPath = rawUrl.removePrefix("./").removePrefix("/")
            val rootFile = File(projectDir, cleanPath)
            val assetFile = File(projectDir, "src/main/assets/$cleanPath")
            val defaultAssetIndex = File(projectDir, "src/main/assets/index.html")
            val defaultRootIndex = File(projectDir, "index.html")

            when {
                rootFile.exists() -> "file://${rootFile.absolutePath}"
                assetFile.exists() -> "file://${assetFile.absolutePath}"
                defaultAssetIndex.exists() -> "file://${defaultAssetIndex.absolutePath}"
                defaultRootIndex.exists() -> "file://${defaultRootIndex.absolutePath}"
                else -> "file://${rootFile.absolutePath}"
            }
        }
    }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@rememberLauncherForActivityResult
        val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 注意：key 里加入了 currentUAType，确保 UA 切换时 WebView 能正确重载配置
    val refreshKey = remember(config, isDebugEnabled, currentUAType, configRefreshTrigger) { System.currentTimeMillis() }

    // --- 物理返回键拦截逻辑 ---
    BackHandler(enabled = true) {
        if (isJsHandlingBack) {
            webViewRef?.evaluateJavascript("if(window.onAndroidBack) window.onAndroidBack();", null)
        } else {
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            } else {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullScreenMode) {
                TopAppBar(
                    title = { Text(if (targetUrl.startsWith("http")) "网页预览" else "App 预览") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        // 🔥 新增：UA 切换菜单
                        Box {
                            IconButton(onClick = { showUAMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = "切换 UA",
                                    tint = if (currentUAType != UserAgents.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(expanded = showUAMenu, onDismissRequest = { showUAMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("默认 (移动端)") },
                                    onClick = { updateUA(UserAgents.DEFAULT) },
                                    trailingIcon = { if(currentUAType == UserAgents.DEFAULT) Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text("桌面模式 (PC)") },
                                    onClick = { updateUA(UserAgents.PC) }
                                )
                                DropdownMenuItem(
                                    text = { Text("iPhone (Safari)") },
                                    onClick = { updateUA(UserAgents.IPHONE) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Android (Chrome)") },
                                    onClick = { updateUA(UserAgents.ANDROID) }
                                )
                            }
                        }

                        IconButton(onClick = { toggleDebugMode() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "调试模式",
                                tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            webViewRef?.reload()
                            configRefreshTrigger = System.currentTimeMillis()
                        }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        IconButton(onClick = { isFullScreenMode = true }) {
                            Icon(Icons.Default.Fullscreen, "全屏")
                        }
                    }
                )
            }
        },
        containerColor = if (isFullScreenMode) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val actualPadding = if (isFullScreenMode) PaddingValues(0.dp) else innerPadding

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
                                // 🔥 传入当前选择的 UA
                                manualUA = currentUAType,
                                onShowFileChooser = { callback, params ->
                                    filePathCallback = callback
                                    try {
                                        val intent = params?.createIntent()
                                        if (intent != null) {
                                            fileChooserLauncher.launch(intent)
                                            true
                                        } else false
                                    } catch (e: Exception) {
                                        filePathCallback = null
                                        false
                                    }
                                },
                                onBackStateChange = { shouldIntercept ->
                                    isJsHandlingBack = shouldIntercept
                                }
                            )
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        if (webView.url != null && webView.url == targetUrl) return@AndroidView

                        if (targetUrl.startsWith("file://") && isDebugEnabled) {
                            try {
                                val filePath = targetUrl.replace("file://", "")
                                val file = File(filePath)
                                if (file.exists()) {
                                    val rawHtml = file.readText()
                                    val injectedHtml = injectErudaIntoHtml(context, rawHtml)
                                    webView.loadDataWithBaseURL(targetUrl, injectedHtml, "text/html", "UTF-8", targetUrl)
                                } else {
                                    webView.loadUrl(targetUrl)
                                }
                            } catch (e: Exception) {
                                LogCatcher.e("WebPreview", "注入失败", e)
                                webView.loadUrl(targetUrl)
                            }
                        } else {
                            if (webView.url != targetUrl) webView.loadUrl(targetUrl)
                        }
                    }
                )
            }

            if (isFullScreenMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { isFullScreenMode = false },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.FullscreenExit,
                            "退出全屏",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isDebugEnabled) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            Icons.Default.BugReport,
                            "Debug On",
                            tint = Color.Green.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

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
                console.log("Eruda injected");
            })();
            </script>
        """
        return if (htmlContent.contains("</body>", ignoreCase = true)) {
            htmlContent.replace("</body>", "$script</body>", ignoreCase = true)
        } else {
            htmlContent + script
        }
    } catch (e: Exception) {
        LogCatcher.e("WebPreview", "读取本地 eruda 失败", e)
        return htmlContent
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureFullWebView(
    webView: WebView,
    context: Context,
    config: JSONObject?,
    projectDir: File,
    manualUA: String, // 🔥 新增：手动选择的 UA
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

    // --- UA 设置逻辑 ---
    var finalUA = ""

    // 1. 如果有 webapp.json 配置，先取配置里的 UA
    if (config != null) {
        val wv = config.optJSONObject("webview")
        if (wv != null) {
            settings.setSupportZoom(wv.optBoolean("zoomEnabled", false))
            settings.builtInZoomControls = wv.optBoolean("zoomEnabled", false)
            settings.displayZoomControls = false
            settings.textZoom = wv.optInt("textZoom", 100)
            finalUA = wv.optString("userAgent", "")
        }
    }

    // 2. 如果手动选择了非“默认”模式，则强制覆盖
    if (manualUA != UserAgents.DEFAULT) {
        finalUA = manualUA
    }

    // 3. 应用 UA
    if (finalUA.isNotEmpty()) {
        settings.userAgentString = finalUA
    } else {
        // 如果 finalUA 为空，表示使用系统默认移动端 UA
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