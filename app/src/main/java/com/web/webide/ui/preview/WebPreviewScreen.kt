package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // 刷新配置的触发器
    var configRefreshTrigger by remember { mutableLongStateOf(0L) }

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

    // --- 2. 读取配置 (增强版：支持注释 + 错误提示) ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir, key2 = configRefreshTrigger) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try {
                    val rawJson = configFile.readText()
                    // 去除注释逻辑
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
                    // 🔥 重点：解析失败弹窗提示 🔥
                    launch(Dispatchers.Main) {
                        scope.launch { snackbarHostState.showSnackbar("webapp.json 格式错误: ${e.message}") }
                    }
                    null
                }
            } else null
        }
    }
    val config = webAppConfig.value


    // 全屏模式状态（控制标题栏显示/隐藏）
    // 使用 remember(config) 确保配置加载后能自动应用默认值
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
                    window.statusBarColor = android.graphics.Color.TRANSPARENT // 或者恢复为你应用的主题色
                }
            }
        }
    }

    // --- 4. 智能路径查找 (核心修改) ---
    val targetUrl = remember(projectDir, config) {
        // 1. 获取配置的入口
        val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("entry")?.takeIf { it.isNotEmpty() }
            ?: "index.html" // 默认值

        // 2. 如果是网络链接，直接返回
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            // 3. 本地文件查找逻辑
            // 清理路径前缀
            val cleanPath = rawUrl.removePrefix("./").removePrefix("/")

            // 可能性 A: 在根目录
            val rootFile = File(projectDir, cleanPath)
            // 可能性 B: 在 src/main/assets 目录 (标准 WebApp 结构)
            val assetFile = File(projectDir, "src/main/assets/$cleanPath")
            // 可能性 C: 默认 fallback (如果是配置解析失败的情况)
            val defaultAssetIndex = File(projectDir, "src/main/assets/index.html")
            val defaultRootIndex = File(projectDir, "index.html")

            when {
                rootFile.exists() -> "file://${rootFile.absolutePath}"
                assetFile.exists() -> "file://${assetFile.absolutePath}"
                // 如果指定的文件找不到，尝试找默认的 index.html
                defaultAssetIndex.exists() -> "file://${defaultAssetIndex.absolutePath}"
                defaultRootIndex.exists() -> "file://${defaultRootIndex.absolutePath}"
                else -> "file://${rootFile.absolutePath}" // 实在找不到，就硬着头皮加载配置的那个路径
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
    // 当 config 改变或 debug 改变时，重新创建/加载 WebView
    val refreshKey = remember(config, isDebugEnabled) { System.currentTimeMillis() }

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
                        // 1. 调试开关
                        IconButton(onClick = { toggleDebugMode() }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "调试模式",
                                tint = if (isDebugEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 2. 刷新按钮 (同时重新读取配置)
                        IconButton(onClick = {
                            webViewRef?.reload()
                            configRefreshTrigger = System.currentTimeMillis() // 触发重读配置
                        }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        // 3. 进入全屏按钮
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
                            configureFullWebView(this, ctx, config,projectDir = projectDir,
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

            // 全屏模式下的悬浮控件
            if (isFullScreenMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 半透明返回按钮
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

                    // 退出全屏按钮
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

@SuppressLint("SetJavaScriptEnabled")
private fun configureFullWebView(
    webView: WebView,
    context: Context,
    config: JSONObject?,
    projectDir: File,
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
    webView.addJavascriptInterface(FullWebAppInterface(context, webView, packageName, projectDir), "Android")

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



class FullWebAppInterface(
    private val context: Context,
    private val webView: WebView,
    private val packageName: String,
    private val projectDir: File // 用于模拟文件系统
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("WebAppPrefs", Context.MODE_PRIVATE) // 统一名称
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensorListener: SensorEventListener? = null

    // 权限回调模拟（注意：在 IDE 预览中，onRequestPermissionsResult 很难准确回调到这里，
    // 这里主要做检查和发起请求，实际结果可能无法精确回传给 JS，但能触发系统弹窗）
    private val permissionCallbacks =  mutableMapOf<String, MutableList<String>>() // Permission -> List<CallbackId>

    // ===========================
    // 工具方法
    // ===========================

    private fun sendResultToJs(callbackId: String, success: Boolean, data: String) {
        val jsonStr = JSONObject().put("success", success).put("data", data).toString()
        val base64 = Base64.encodeToString(jsonStr.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

        mainHandler.post {
            // 保持与 Java 版本一致的调用方式
            val js = "if(window.onAndroidResponse) window.onAndroidResponse('$callbackId', '$base64')"
            webView.evaluateJavascript(js, null)
        }
    }

    private fun runOnMain(block: () -> Unit) = mainHandler.post(block)

    // ===========================
    // 1. 网络请求 (绕过 CORS) - 保持一致
    // ===========================

    @JavascriptInterface
    fun httpRequest(method: String, urlStr: String, headersJson: String, body: String, callbackId: String) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method.uppercase()
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (headersJson.isNotEmpty()) {
                    val headers = JSONObject(headersJson)
                    headers.keys().forEach { key ->
                        conn.setRequestProperty(key, headers.getString(key))
                    }
                }

                if (body.isNotEmpty() && (method.equals("POST", true) || method.equals("PUT", true))) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                }

                val code = conn.responseCode
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

                val resultJson = JSONObject()
                resultJson.put("status", code)
                resultJson.put("body", responseText)

                // 添加 Headers 返回，与 Java 版一致
                val responseHeaders = JSONObject()
                conn.headerFields.forEach { (k, v) ->
                    if (k != null) responseHeaders.put(k, v.joinToString(","))
                }
                resultJson.put("headers", responseHeaders)

                sendResultToJs(callbackId, true, resultJson.toString())

            } catch (e: Exception) {
                val err = JSONObject().put("status", 0).put("error", e.message ?: "Unknown Error")
                sendResultToJs(callbackId, false, err.toString())
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    // ===========================
    // 2. UI 交互
    // ===========================

    @JavascriptInterface
    fun showToast(message: String) {
        runOnMain { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // Kotlin 独有保留功能
    @JavascriptInterface
    fun showDialog(title: String, message: String, positiveText: String, negativeText: String, callbackId: String) {
        runOnMain {
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(positiveText) { _, _ -> sendResultToJs(callbackId, true, "positive") }

            if (negativeText.isNotEmpty()) {
                builder.setNegativeButton(negativeText) { _, _ -> sendResultToJs(callbackId, true, "negative") }
            } else {
                builder.setPositiveButton("OK") { _, _ -> sendResultToJs(callbackId, true, "positive") }
            }
            builder.show()
        }
    }

    // Kotlin 独有保留功能
    @JavascriptInterface
    fun showNotification(id: Int, title: String, content: String) {
        runOnMain {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channelId = "webapp_preview"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(channelId, "Preview Notification", android.app.NotificationManager.IMPORTANCE_HIGH)
                    nm.createNotificationChannel(channel)
                }
                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                nm.notify(id, builder.build())
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ===========================
    // 3. 硬件/系统 (补全 Java 功能)
    // ===========================

    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(milliseconds)
            }
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val info = JSONObject()
        try {
            info.put("model", Build.MODEL)
            info.put("manufacturer", Build.MANUFACTURER)
            info.put("androidVersion", Build.VERSION.RELEASE)
            info.put("sdkInt", Build.VERSION.SDK_INT)
            info.put("screenWidth", context.resources.displayMetrics.widthPixels)
            info.put("screenHeight", context.resources.displayMetrics.heightPixels)

            // 尝试获取电话信息 (需要权限)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                tm?.let {
                    info.put("simOperator", it.simOperatorName)
                    info.put("networkOperator", it.networkOperatorName)
                }
            }

            // Wifi 信息
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && wm.isWifiEnabled) {
                val wifiInfo = wm.connectionInfo
                info.put("wifiSSID", wifiInfo.ssid.replace("\"", ""))
                info.put("wifiRSSI", wifiInfo.rssi)
            }

            // MAC (模拟 Java 逻辑)
            info.put("macAddress", getMacAddress())

        } catch (e: Exception) { e.printStackTrace() }
        return info.toString()
    }

    private fun getMacAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                if (!nif.name.equals("wlan0", true)) continue
                val macBytes = nif.hardwareAddress ?: return ""
                return macBytes.joinToString(":") { String.format("%02X", it) }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return ""
    }

    // ===========================
    // 4. 剪贴板
    // ===========================

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        runOnMain {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("WebApp", text))
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun getFromClipboard(callbackId: String) {
        runOnMain {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                var text = ""
                if (cm.hasPrimaryClip()) {
                    text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                }
                sendResultToJs(callbackId, true, text)
            } catch (e: Exception) {
                sendResultToJs(callbackId, false, e.message ?: "Error")
            }
        }
    }

    // ===========================
    // 5. 本地存储 (SharedPreferences) - 补全
    // ===========================

    @JavascriptInterface
    fun saveStorage(key: String, value: String) = prefs.edit().putString(key, value).apply()

    @JavascriptInterface
    fun getStorage(key: String): String = prefs.getString(key, "") ?: ""

    @JavascriptInterface
    fun removeStorage(key: String) = prefs.edit().remove(key).apply()

    @JavascriptInterface
    fun clearStorage() = prefs.edit().clear().apply()

    @JavascriptInterface
    fun getAllStorage(): String {
        val result = JSONObject()
        prefs.all.forEach { (k, v) -> result.put(k, v.toString()) }
        return result.toString()
    }

    // ===========================
    // 6. 文件系统 (模拟 Java 行为)
    // ===========================
    // 注意：Java 版本读取 assets/ 是从 APK 读取。这里我们从项目目录的 src/main/assets 读取。
    // Java 版本读取绝对路径。这里我们也尝试读取，但受限于 IDE 权限。

    @JavascriptInterface
    fun readFile(path: String): String {
        try {
            val fileToRead = resolveFile(path)
            if (fileToRead.exists() && fileToRead.canRead()) {
                return fileToRead.readText()
            }
        } catch (e: Exception) { e.printStackTrace() }
        return ""
    }

    @JavascriptInterface
    fun writeFile(path: String, content: String): Boolean {
        return try {
            val fileToWrite = resolveFile(path)
            fileToWrite.parentFile?.mkdirs()
            FileOutputStream(fileToWrite).use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun fileExists(path: String): Boolean = resolveFile(path).exists()

    @JavascriptInterface
    fun deleteFile(path: String): Boolean = resolveFile(path).delete()

    @JavascriptInterface
    fun listFiles(directory: String): String {
        try {
            val dir = resolveFile(directory)
            if (!dir.exists() || !dir.isDirectory) return "[]"
            val jsonArray = JSONArray()
            dir.listFiles()?.forEach { file ->
                val fileInfo = JSONObject()
                fileInfo.put("name", file.name)
                // 在预览模式下，尽量返回相对路径或模拟路径，避免暴露 IDE 真实路径结构太深
                fileInfo.put("path", file.absolutePath)
                fileInfo.put("isDirectory", file.isDirectory)
                fileInfo.put("size", file.length())
                fileInfo.put("lastModified", file.lastModified())
                jsonArray.put(fileInfo)
            }
            return jsonArray.toString()
        } catch (e: Exception) { return "[]" }
    }

    // 路径解析辅助方法：将 assets/ 映射到项目结构，其他视为相对项目根目录
    private fun resolveFile(path: String): File {
        return if (path.startsWith("assets/")) {
            // 模拟 APK 行为：assets/xxx -> project/src/main/assets/xxx
            File(projectDir, "src/main/assets/" + path.substring(7))
        } else if (path.startsWith("/")) {
            // 绝对路径 (尝试直接读取，可能受 Android 权限限制)
            File(path)
        } else {
            // 相对路径 -> 相对于项目根目录
            File(projectDir, path)
        }
    }

    // ===========================
    // 7. 系统功能 Intents - 补全
    // ===========================

    @JavascriptInterface
    fun openBrowser(url: String) {
        runOnMain {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @JavascriptInterface
    fun shareText(text: String) {
        runOnMain {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "分享"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @JavascriptInterface
    fun callPhone(phoneNumber: String) {
        runOnMain {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @JavascriptInterface
    fun sendSMS(phoneNumber: String, message: String) {
        runOnMain {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber"))
                intent.putExtra("sms_body", message)
                context.startActivity(intent)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @JavascriptInterface
    fun sendEmail(email: String, subject: String, body: String) {
        runOnMain {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                context.startActivity(Intent.createChooser(intent, "发送邮件"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    @JavascriptInterface
    fun openMap(latitude: Double, longitude: Double, label: String) {
        runOnMain {
            try {
                val uriStr = "geo:$latitude,$longitude?q=$latitude,$longitude($label)"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ===========================
    // 8. 屏幕与音量控制 - 补全
    // ===========================

    @JavascriptInterface
    fun keepScreenOn(keepOn: Boolean) {
        runOnMain {
            val window = (context as? Activity)?.window
            if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @JavascriptInterface
    fun setScreenBrightness(brightness: Float) {
        runOnMain {
            (context as? Activity)?.let { activity ->
                val lp = activity.window.attributes
                lp.screenBrightness = brightness
                activity.window.attributes = lp
            }
        }
    }

    @JavascriptInterface
    fun getScreenBrightness(): Float {
        return (context as? Activity)?.window?.attributes?.screenBrightness ?: -1f
    }

    // 注意：setVolume 需要反射或特殊权限，在部分设备可能不生效
    @JavascriptInterface
    fun setVolume(volume: Int) {
        try {
            val audioSystem = Class.forName("android.media.AudioSystem")
            val method = audioSystem.getMethod("setStreamVolume", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            // STREAM_MUSIC = 3
            method.invoke(null, 3, volume, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ===========================
    // 9. 传感器 - 补全
    // ===========================

    @JavascriptInterface
    fun startSensor(sensorType: String, callbackId: String) {
        runOnMain {
            val type = when (sensorType.lowercase()) {
                "accelerometer" -> Sensor.TYPE_ACCELEROMETER
                "gyroscope" -> Sensor.TYPE_GYROSCOPE
                "magnetometer" -> Sensor.TYPE_MAGNETIC_FIELD
                "light" -> Sensor.TYPE_LIGHT
                "proximity" -> Sensor.TYPE_PROXIMITY
                else -> {
                    sendResultToJs(callbackId, false, "Unsupported sensor type")
                    return@runOnMain
                }
            }

            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor == null) {
                sendResultToJs(callbackId, false, "Sensor not available")
                return@runOnMain
            }

            // 先停止旧的
            if (sensorListener != null) sensorManager.unregisterListener(sensorListener)

            sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        val values = JSONArray()
                        it.values.forEach { v -> values.put(v) }
                        sendResultToJs(callbackId + "_data", true, values.toString())
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            sendResultToJs(callbackId, true, "Sensor started")
        }
    }

    @JavascriptInterface
    fun stopSensor() {
        runOnMain {
            sensorListener?.let { sensorManager.unregisterListener(it) }
            sensorListener = null
        }
    }

    // ===========================
    // 10. 权限管理 - 补全
    // ===========================

    @JavascriptInterface
    fun requestPermission(permission: String, callbackId: String) {
        runOnMain {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                sendResultToJs(callbackId, true, "Permission already granted")
                return@runOnMain
            }

            // 注册回调等待（虽然 Activity 不一定能回传结果，但逻辑保留）
            // 注意：这里需要 host Activity 配合 onRequestPermissionsResult 才能真正工作
            // 在预览模式下，我们至少发起请求
            (context as? Activity)?.let { activity ->
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 100)

                // 临时模拟：告诉 JS 我们请求了，但无法得知用户点击了什么（除非修改 Activity）
                // Java 原版是通过 Activity 回调触发。这里我们做个延迟检查作为 workaround
                mainHandler.postDelayed({
                    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    sendResultToJs(callbackId, granted, if(granted) "Permission granted" else "Permission denied (or pending)")
                }, 5000)
            } ?: sendResultToJs(callbackId, false, "Context is not an Activity")
        }
    }

    @JavascriptInterface
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    @JavascriptInterface
    fun openAppSettings() {
        runOnMain {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        }
    }

    // ===========================
    // 11. 应用配置与生命周期 - 补全
    // ===========================

    @JavascriptInterface
    fun getAppConfig(): String {
        return try {
            // 尝试读取项目中的 webapp.json
            File(projectDir, "webapp.json").readText()
        } catch (e: Exception) { "{}" }
    }

    @JavascriptInterface
    fun reloadApp() {
        runOnMain { (context as? Activity)?.recreate() }
    }

    @JavascriptInterface
    fun exitApp() {
        runOnMain {
            (context as? Activity)?.finishAffinity()
            // 在预览中不建议 System.exit(0) 杀掉 IDE，仅 finish Activity
        }
    }

    // ===========================
    // 12. 日期时间 - 补全
    // ===========================

    @JavascriptInterface
    fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

    @JavascriptInterface
    fun formatDate(timestamp: Long, format: String): String {
        return SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))
    }

    // 清理资源
    fun onDestroy() {
        stopSensor()
    }
}