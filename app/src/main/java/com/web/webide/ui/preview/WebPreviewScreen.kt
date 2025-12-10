package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.*
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(folderName: String, navController: NavController, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(workspacePath, folderName)

    // --- 0. 🔥🔥🔥 关键修复：进入预览时，向 Android 系统申请硬件权限 ---
    // 如果 WebIDE 自身没有相机权限，WebView 里的网页永远无法调用摄像头
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 权限回调，这里可以做日志，但我们主要目的是确保用户点了“允许”
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

    // --- 1. 读取配置 ---
    val webAppConfig = produceState<JSONObject?>(initialValue = null, key1 = projectDir) {
        value = withContext(Dispatchers.IO) {
            val configFile = File(projectDir, "webapp.json")
            if (configFile.exists()) {
                try { JSONObject(configFile.readText()) } catch (e: Exception) { null }
            } else null
        }
    }
    val config = webAppConfig.value

    // --- 2. 屏幕方向与全屏 (保持不变) ---
    DisposableEffect(config) {
        if (config != null && activity != null) {
            val orientation = config.optString("orientation", "portrait")
            activity.requestedOrientation = when (orientation) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "sensor" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val isFullscreen = config.optBoolean("fullscreen", false)
            if (isFullscreen) hideSystemUI(activity)
            onDispose {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (isFullscreen) showSystemUI(activity)
            }
        } else { onDispose { } }
    }

    // --- 3. URL 计算 (保持不变) ---
    val targetUrl = remember(projectDir, config) {
        val rawUrl = config?.optString("targetUrl")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("url")?.takeIf { it.isNotEmpty() }
            ?: config?.optString("entry")?.takeIf { it.isNotEmpty() }

        when {
            rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) -> rawUrl
            rawUrl != null -> {
                val f = File(projectDir, rawUrl)
                val af = File(projectDir, "src/main/assets/$rawUrl")
                if(f.exists()) "file://${f.absolutePath}" else "file://${af.absolutePath}"
            }
            else -> {
                val af = File(projectDir, "src/main/assets/index.html")
                if(af.exists()) "file://${af.absolutePath}" else "file://${File(projectDir, "index.html").absolutePath}"
            }
        }
    }

    // --- 4. 文件/相机回调 ---
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
    val refreshKey = remember(config) { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            if (config?.optBoolean("fullscreen", false) != true) {
                TopAppBar(
                    title = { Text(if(targetUrl.startsWith("http")) "网页预览" else "App 预览") },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = { IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, "刷新") } }
                )
            }
        },
        containerColor = if (config?.optBoolean("fullscreen", false) == true) Color.Black else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val actualPadding = if (config?.optBoolean("fullscreen", false) == true) PaddingValues(0.dp) else innerPadding
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
                    update = { webView -> if (webView.url != targetUrl) webView.loadUrl(targetUrl) }
                )
            }
            if (config?.optBoolean("fullscreen", false) == true) {
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

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

    // 🔥🔥🔥 关键：允许文件访问和媒体播放
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    settings.allowFileAccessFromFileURLs = true
    settings.allowUniversalAccessFromFileURLs = true
    settings.mediaPlaybackRequiresUserGesture = false // 允许自动播放视频流

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

        // 🔥🔥🔥 关键：无脑允许所有权限请求 (相机/麦克风)
        // 这一步对于 file:// 协议调用 getUserMedia 至关重要
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

// FullWebAppInterface 和辅助方法保持不变 (与上一版一致)
class FullWebAppInterface(private val context: Context, private val webView: WebView, private val packageName: String) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("WebIDE_Preview_${packageName}", Context.MODE_PRIVATE)

    @JavascriptInterface fun showToast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

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

private fun hideSystemUI(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).let {
        it.hide(WindowInsetsCompat.Type.systemBars()); it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
private fun showSystemUI(activity: Activity) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
}