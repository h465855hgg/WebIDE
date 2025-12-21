package com.web.webide.ui.preview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
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
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import android.webkit.DownloadListener // 必需
import android.webkit.URLUtil
import android.widget.Toast

// --- 1. 智能微型 HTTP 服务器 (修复 404 问题) ---
class TinyWebServer(private val rootDir: File) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    var port: Int = 0
        private set

    // 定义常见的前端源码目录，服务器会自动去这些目录里找文件
    private val fallbackDirectories = listOf(
        "",                 // 根目录
        "src",              // 常见源码目录
        "public",           // 常见静态资源目录
        "assets",           // 资源目录
        "src/main/assets",  // Android 结构
        "dist",             // 构建输出目录
        "build",            // 构建目录
        "www"               // Cordova/Ionic 目录
    )

    fun start(): Int {
        if (isRunning.get()) return port
        try {
            serverSocket = ServerSocket(0)
            port = serverSocket?.localPort ?: 8080
            isRunning.set(true)
            thread(start = true) {
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        thread { handleClient(client) }
                    } catch (e: Exception) { break }
                }
            }
            LogCatcher.i("WebServer", "Started: http://127.0.0.1:$port")
        } catch (e: Exception) {
            LogCatcher.e("WebServer", "Start failed", e)
        }
        return port
    }

    fun stop() {
        isRunning.set(false)
        try { serverSocket?.close() } catch (e: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = DataOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: run { socket.close(); return }
            val parts = requestLine.split(" ")
            if (parts.size < 2) { socket.close(); return }

            // 1. 路径解码
            var rawPath = parts[1]
            if (rawPath.contains("?")) rawPath = rawPath.substring(0, rawPath.indexOf("?"))
            val decodedPath = URLDecoder.decode(rawPath, "UTF-8")

            // 2. 智能文件查找逻辑
            var targetFile: File? = null
            var finalPath = decodedPath

            // 移除开头的斜杠
            val relativePath = decodedPath.removePrefix("/")

            // A. 遍历所有可能的目录寻找文件
            for (dir in fallbackDirectories) {
                val base = if (dir.isEmpty()) rootDir else File(rootDir, dir)
                val candidate = File(base, relativePath)

                // 情况1: 直接找到了文件
                if (candidate.exists() && candidate.isFile) {
                    targetFile = candidate
                    break
                }

                // 情况2: 是目录，找 index.html
                if (candidate.exists() && candidate.isDirectory) {
                    val indexFile = File(candidate, "index.html")
                    if (indexFile.exists()) {
                        targetFile = indexFile
                        break
                    }
                }

                // 情况3: 可能是省略了 .html 后缀
                val htmlCandidate = File(base, "$relativePath.html")
                if (htmlCandidate.exists()) {
                    targetFile = htmlCandidate
                    break
                }
            }

            // 3. 响应结果
            if (targetFile != null && targetFile.exists()) {
                val contentType = getMimeType(targetFile.name)
                val fileBytes = targetFile.readBytes()

                output.writeBytes("HTTP/1.1 200 OK\r\n")
                output.writeBytes("Content-Type: $contentType\r\n")
                output.writeBytes("Content-Length: ${fileBytes.size}\r\n")
                output.writeBytes("Access-Control-Allow-Origin: *\r\n")
                output.writeBytes("\r\n")
                output.write(fileBytes)
            } else {
                val msg = "404 Not Found: $decodedPath\nSearched in: $fallbackDirectories"
                output.writeBytes("HTTP/1.1 404 Not Found\r\n")
                output.writeBytes("Content-Type: text/plain; charset=utf-8\r\n") // 支持中文报错
                output.writeBytes("\r\n")
                output.write(msg.toByteArray())
            }
            output.flush()
            socket.close()
        } catch (e: Exception) {
            try { socket.close() } catch (e2: Exception) {}
        }
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "txt" -> "text/plain"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}

// UA 常量
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

    // --- 0. 启动服务器 ---
    var serverPort by remember { mutableIntStateOf(0) }
    DisposableEffect(projectDir) {
        val server = TinyWebServer(projectDir)
        val port = server.start()
        serverPort = port
        onDispose { server.stop() }
    }

    // --- 1. UI 环境控制 ---
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboardController?.hide() }

    DisposableEffect(Unit) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            if (activity != null && window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                activity.requestedOrientation = originalOrientation
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = originalSystemBarsBehavior
                window.statusBarColor = originalStatusBarColor
                controller.isAppearanceLightStatusBars = originalIsLightStatusBars
            }
        }
    }

    // --- 2. 状态与配置 ---
    val prefs = remember { context.getSharedPreferences("WebIDE_Project_Settings", Context.MODE_PRIVATE) }
    var isDebugEnabled by remember { mutableStateOf(prefs.getBoolean("debug_$folderName", false)) }
    var currentUAType by remember { mutableStateOf(prefs.getString("ua_type_$folderName", UserAgents.DEFAULT) ?: UserAgents.DEFAULT) }
    var showUAMenu by remember { mutableStateOf(false) }
    var configRefreshTrigger by remember { mutableLongStateOf(0L) }
    var isJsHandlingBack by remember { mutableStateOf(false) }

    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir, key2 = configRefreshTrigger) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try {
                    val rawJson = configFile.readText()
                    val cleanJson = rawJson.lines().joinToString("\n") { line ->
                        val index = line.indexOf("//")
                        if (index != -1 && !line.substring(0, index).trim().endsWith(":") && !line.contains("http")) line.substring(0, index) else line
                    }
                    JSONObject(cleanJson)
                } catch (e: Exception) { null }
            } else null
        }
    }
    val config = webAppConfig.value

    // 权限
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(config) {
        config?.optJSONArray("permissions")?.let { arr ->
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            val needed = list.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // 屏幕与全屏
    var isFullScreenConfig by remember(config) { mutableStateOf(config?.optBoolean("fullscreen", false) == true) }
    var isUserFullScreen by remember(isFullScreenConfig) { mutableStateOf(isFullScreenConfig) }

    LaunchedEffect(config, isUserFullScreen) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            config?.optString("orientation")?.let { ori ->
                val target = when (ori) {
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (activity.requestedOrientation != target) activity.requestedOrientation = target
            }

            if (isUserFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                val sbConfig = config?.optJSONObject("statusBar")
                if (sbConfig != null) {
                    val color = sbConfig.optString("backgroundColor", "#FFFFFF")
                    window.statusBarColor = try { AndroidColor.parseColor(color) } catch(e: Exception) { AndroidColor.WHITE }
                    controller.isAppearanceLightStatusBars = (sbConfig.optString("style", "dark") == "dark")
                } else {
                    window.statusBarColor = AndroidColor.WHITE
                    controller.isAppearanceLightStatusBars = true
                }
            }
        }
    }

    // --- 3. URL 构造 ---
    val targetUrl = remember(projectDir, config, serverPort) {
        if (serverPort == 0) "about:blank"
        else {
            val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
                ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
                ?: "index.html" // 默认找 index.html，由服务器决定在哪
            if (rawUrl.startsWith("http")) rawUrl
            else "http://127.0.0.1:$serverPort/${rawUrl.removePrefix("./").removePrefix("/")}"
        }
    }

    // --- 4. WebView 交互 ---
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filePathCallback = null
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val refreshKey = remember(config, isDebugEnabled, currentUAType, serverPort) { System.nanoTime() }

    BackHandler {
        if (isJsHandlingBack) webViewRef?.evaluateJavascript("if(window.onAndroidBack) window.onAndroidBack();", null)
        else if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
        else navController.popBackStack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isUserFullScreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text("App 预览")
                            if (serverPort != 0) Text(":$serverPort", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showUAMenu = true }) {
                                Icon(Icons.Default.Devices, "UA", tint = if (currentUAType != UserAgents.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = showUAMenu, onDismissRequest = { showUAMenu = false }) {
                                listOf(
                                    UserAgents.DEFAULT to "默认 (Mobile)",
                                    UserAgents.PC to "PC / Desktop",
                                    UserAgents.IPHONE to "iOS",
                                    UserAgents.ANDROID to "Android"
                                ).forEach { (ua, name) ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = {
                                        currentUAType = ua
                                        prefs.edit().putString("ua_type_$folderName", ua).apply()
                                        showUAMenu = false
                                        configRefreshTrigger = System.currentTimeMillis()
                                        scope.launch { snackbarHostState.showSnackbar("UA: $name") }
                                    })
                                }
                            }
                        }
                        IconButton(onClick = {
                            isDebugEnabled = !isDebugEnabled
                            prefs.edit().putBoolean("debug_$folderName", isDebugEnabled).apply()
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.BugReport, "调试", tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, "刷新") }
                        IconButton(onClick = { isUserFullScreen = true }) { Icon(Icons.Default.Fullscreen, "全屏") }
                    }
                )
            }
        },
        containerColor = if (isUserFullScreen) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(if (isUserFullScreen) PaddingValues(0.dp) else innerPadding).fillMaxSize()) {
            if (serverPort == 0) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
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
                                    onShowFileChooser = { cb, p ->
                                        filePathCallback = cb
                                        try { p?.createIntent()?.let { fileChooserLauncher.launch(it); true } ?: false } catch(e: Exception) { false }
                                    },
                                    onBackStateChange = { isJsHandlingBack = it }
                                )
                                webViewRef = this
                                loadUrl(targetUrl)
                            }
                        }
                    )
                }
            }

            if (isUserFullScreen) {
                IconButton(
                    onClick = { isUserFullScreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).background(Color.Black.copy(0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.FullscreenExit, "退出", tint = Color.White)
                }
            }
        }
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
    settings.mediaPlaybackRequiresUserGesture = false

    var finalUA = ""
    var baseTextZoom = 100

    if (config != null) {
        val wv = config.optJSONObject("webview")
        if (wv != null) {
            baseTextZoom = wv.optInt("textZoom", 100)
            finalUA = wv.optString("userAgent", "")
        }
    }

    if (manualUA != UserAgents.DEFAULT) {
        settings.userAgentString = manualUA
    } else if (finalUA.isNotEmpty()) {
        settings.userAgentString = finalUA
    } else {
        settings.userAgentString = null
    }

    // --- 核心逻辑：PC 模式适配 ---
    if (manualUA == UserAgents.PC) {
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.textZoom = if (baseTextZoom == 100) 50 else baseTextZoom / 2
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
    } else {
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.textZoom = baseTextZoom
        settings.setSupportZoom(false)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }

    val packageName = config?.optString("package", "com.example.webapp") ?: "com.web.preview"
    webView.addJavascriptInterface(
        FullWebAppInterface(context, webView, packageName, projectDir, onBackStateChange),
        "Android"
    )

    webView.webChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            // 如果上一级传进来的回调是有效的，就执行
            if (filePathCallback != null) {
                return onShowFileChooser(filePathCallback, fileChooserParams)
            }
            return false
        }
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            LogCatcher.d("WebPreview_JS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
            return true
        }
    }

    // --- 修复 1: 绑定下载监听器 ---
    webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        // 复用 SharedWebInterface 里的下载逻辑，或者直接在这里写
        // 这里我们简单起见，直接启动系统下载
        try {
            val request = android.app.DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("Cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(filename)
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "正在下载: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // 如果 DownloadManager 失败，尝试用浏览器打开
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                Toast.makeText(context, "无法下载文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val prefs = context.getSharedPreferences("WebIDE_Project_Settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("debug_${projectDir.name}", false)) {
                injectEruda(context, view)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url.toString()
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true } catch (e: Exception) {}
            }
            return false
        }
    }
    WebView.setWebContentsDebuggingEnabled(true)
}

// --- 注入 Eruda ---
private fun injectEruda(context: Context, webView: WebView?) {
    if (webView == null) return
    try {
        val erudaCode = context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
        webView.evaluateJavascript(erudaCode + "; if(window.eruda){ eruda.init(); }", null)
    } catch (e: Exception) {
        val cdnJs = """
            (function () { 
                if (window.eruda) return;
                var script = document.createElement('script'); 
                script.src = "https://cdn.jsdelivr.net/npm/eruda"; 
                document.body.appendChild(script); 
                script.onload = function () { eruda.init(); } 
            })();
        """
        webView.evaluateJavascript(cdnJs, null)
    }
}
