package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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

    // 切换调试模式
    fun toggleDebugMode() {
        isDebugEnabled = !isDebugEnabled
        prefs.edit().putBoolean("debug_$folderName", isDebugEnabled).apply()
        scope.launch { snackbarHostState.showSnackbar(if (isDebugEnabled) "调试模式已开启" else "调试模式已关闭") }
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

    // --- 2. 读取配置 ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try { JSONObject(configFile.readText()) } catch (e: Exception) { null }
            } else null
        }
    }
    val config = webAppConfig.value

    // 🔥🔥🔥 新增：全屏模式状态（控制标题栏显示/隐藏） 🔥🔥🔥
    // 默认值参考 config 中的设置，如果没有设置则为 false
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

    // --- 4. URL 计算 ---
    val targetUrl = remember(projectDir, config) {
        val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("entry")?.takeIf { it.isNotEmpty() }

        when {
            rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) -> rawUrl
            rawUrl != null -> {
                val f = File(projectDir, rawUrl)
                val af = File(projectDir, "src/main/assets/$rawUrl")
                if (f.exists()) "file://${f.absolutePath}" else "file://${af.absolutePath}"
            }
            else -> {
                val af = File(projectDir, "src/main/assets/index.html")
                if (af.exists()) "file://${af.absolutePath}" else "file://${File(projectDir, "index.html").absolutePath}"
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
    val refreshKey = remember(config, isDebugEnabled) { System.currentTimeMillis() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // 🔥🔥🔥 核心：如果不是全屏模式，才显示标题栏 🔥🔥🔥
            if (!isFullScreenMode) {
                TopAppBar(
                    title = { Text(if (targetUrl.startsWith("http")) "网页预览" else "App 预览") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        // 1. 调试开关
                        IconButton(onClick = { toggleDebugMode() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "调试模式",
                                tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 2. 刷新按钮
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        // 3. 🔥新增：进入全屏按钮🔥
                        IconButton(onClick = { isFullScreenMode = true }) {
                            Icon(Icons.Default.Fullscreen, "全屏")
                        }
                    }
                )
            }
        },
        // 如果全屏，背景设为黑色（防止闪烁），否则用默认色
        containerColor = if (isFullScreenMode) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // 如果全屏，Padding 设为 0，否则使用 Scaffold 给的 padding
        val actualPadding = if (isFullScreenMode) PaddingValues(0.dp) else innerPadding

        Box(modifier = Modifier.padding(actualPadding).fillMaxSize()) {
            key(refreshKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(-1, -1)
                            configureFullWebView(this, ctx, config,
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
                                    // 注入 Eruda (传入 Context 读取 assets)
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

            // 🔥🔥🔥 核心：全屏模式下的悬浮控件 🔥🔥🔥
            // 只要进入全屏模式，就显示这些浮动按钮，方便用户操作
            if (isFullScreenMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 半透明返回按钮
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

                    // 2. 🔥新增：退出全屏按钮🔥
                    IconButton(
                        onClick = { isFullScreenMode = false }, // 点击恢复标题栏
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

                    // 3. 调试开启指示器
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

// 离线 Eruda 注入 (请确保 assets/eruda.min.js 存在)
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
                    // 位置：居中靠右
                    entryBtn.position({
                        x: window.innerWidth - 50,
                        y: window.innerHeight / 2
                    });
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

// ... configureFullWebView 和 FullWebAppInterface 保持不变 (引用之前的代码) ...
@SuppressLint("SetJavaScriptEnabled")
private fun configureFullWebView(
    webView: WebView,
    context: Context,
    config: JSONObject?,
    onShowFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean
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

    if (config != null) {
        val wv = config.optJSONObject("webview")
        if (wv != null) {
            settings.setSupportZoom(wv.optBoolean("zoomEnabled", false))
            settings.builtInZoomControls = wv.optBoolean("zoomEnabled", false)
            settings.displayZoomControls = false
            settings.textZoom = wv.optInt("textZoom", 100)
            val ua = wv.optString("userAgent", "")
            if (ua.isNotEmpty()) settings.userAgentString = ua
        }
    }

    val packageName = config?.optString("package", "com.example.webapp") ?: "com.web.preview"
    webView.addJavascriptInterface(FullWebAppInterface(context, webView, packageName), "Android")

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

class FullWebAppInterface(private val context: Context, private val webView: WebView, private val packageName: String) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("WebIDE_Preview_${packageName}", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun httpRequest(method: String, urlStr: String, headersJson: String, body: String, callbackId: String) {
        // 开启新线程避免阻塞 UI
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method.uppercase()
                conn.connectTimeout = 15000 // 15秒超时
                conn.readTimeout = 15000

                // 1. 设置 Headers
                if (headersJson.isNotEmpty()) {
                    try {
                        val headers = JSONObject(headersJson)
                        val keys = headers.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            conn.setRequestProperty(key, headers.getString(key))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 2. 发送 Body (如果是 POST/PUT)
                if (body.isNotEmpty() && (method.equals("POST", true) || method.equals("PUT", true))) {
                    conn.doOutput = true
                    conn.outputStream.use { os ->
                        os.write(body.toByteArray(StandardCharsets.UTF_8))
                    }
                }

                // 3. 获取响应状态
                val code = conn.responseCode

                // 4. 读取响应内容 (成功读 inputStream，失败读 errorStream)
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                // 5. 构造返回给 JS 的数据
                val resultJson = JSONObject()
                resultJson.put("status", code)
                resultJson.put("body", responseText)

                // 可选：返回响应头
                val responseHeaders = JSONObject()
                for ((k, v) in conn.headerFields) {
                    if (k != null) responseHeaders.put(k, v.joinToString(","))
                }
                resultJson.put("headers", responseHeaders)

                // 成功回调
                sendResultToJs(callbackId, true, resultJson.toString())

            } catch (e: Exception) {
                // 失败回调
                e.printStackTrace()
                val errorJson = JSONObject()
                errorJson.put("status", 0)
                errorJson.put("error", e.message)
                sendResultToJs(callbackId, false, errorJson.toString())
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
    @JavascriptInterface fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    @JavascriptInterface fun vibrate(ms: Long) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (e: Exception) {}
    }
    @JavascriptInterface fun keepScreenOn(enable: Boolean) {
        mainHandler.post {
            val win = (context as? Activity)?.window
            if (enable) win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    @JavascriptInterface fun copyToClipboard(text: String) {
        mainHandler.post {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("WebIDE", text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
    @JavascriptInterface fun getFromClipboard(callbackId: String) {
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = if (cm.hasPrimaryClip()) cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "" else ""
                sendResultToJs(callbackId, true, text)
            } catch (e: Exception) { sendResultToJs(callbackId, false, "") }
        }
    }
    @JavascriptInterface fun openBrowser(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
    }
    @JavascriptInterface fun shareText(text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
            context.startActivity(Intent.createChooser(intent, "分享"))
        } catch (e: Exception) {}
    }
    @JavascriptInterface fun getDeviceInfo(): String {
        val json = JSONObject()
        json.put("model", Build.MODEL); json.put("android", Build.VERSION.RELEASE); json.put("package", packageName)
        return json.toString()
    }
    @JavascriptInterface fun saveStorage(k: String, v: String) = prefs.edit().putString(k, v).apply()
    @JavascriptInterface fun getStorage(k: String): String = prefs.getString(k, "") ?: ""

    private fun sendResultToJs(callbackId: String, success: Boolean, data: String) {
        val json = JSONObject().put("success", success).put("data", data).toString()
        val base64 = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        mainHandler.post { webView.evaluateJavascript("if(window.onAndroidResponse) window.onAndroidResponse('$callbackId', '$base64')", null) }
    }
}