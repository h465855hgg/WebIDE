package com.web.webide.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(folderName: String, navController: NavController, viewModel: EditorViewModel) {
    val context = LocalContext.current
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(workspacePath, folderName)

    // ✅ 1. 智能查找入口文件 logic (兼容 WebApp 模板和普通 Web 模板)
    // 优先查找 WebApp 标准结构 src/main/assets/index.html
    // 如果没有，则查找根目录 index.html
    val entryFile = remember(projectDir) {
        val assetsIndex = File(projectDir, "src/main/assets/index.html")
        if (assetsIndex.exists()) {
            LogCatcher.d("WebPreview", "检测到 WebApp 结构，加载: ${assetsIndex.absolutePath}")
            assetsIndex
        } else {
            val rootIndex = File(projectDir, "index.html")
            LogCatcher.d("WebPreview", "检测到普通 Web 结构，加载: ${rootIndex.absolutePath}")
            rootIndex
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览: $folderName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!entryFile.exists()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "错误：找不到入口文件\n请确保存在 index.html 或 src/main/assets/index.html",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        configureWebView(this, ctx)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // 使用 file:/// 协议直接加载，配合 WebSettings 解决跨域
                    val url = "file://${entryFile.absolutePath}"
                    if (webView.url != url) {
                        webView.loadUrl(url)
                    }
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView, context: Context) {
    val settings = webView.settings

    // 基础设置
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true

    // ✅ 2. 关键权限设置：解决跨域和本地文件访问问题 (兼容普通 Web 模板的网络请求)
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    // 允许 file:// 页面访问其他 file:// 资源
    settings.allowFileAccessFromFileURLs = true
    // 允许 file:// 页面访问任何来源 (解决 file:// 请求 http/https 接口的 CORS 问题)
    settings.allowUniversalAccessFromFileURLs = true
    // 允许 HTTPS 页面加载 HTTP 资源 (混合内容)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

    // ✅ 3. 注入 Native 接口：兼容 WebApp 模板
    // 这使得 JS 中的 window.Android.showToast() 在预览模式下也能工作
    webView.addJavascriptInterface(PreviewWebAppInterface(context, webView), "Android")

    webView.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
                // 将 WebView 的 console.log 输出到 IDE 的日志系统中，方便调试
                LogCatcher.d("WebPreview_JS", "[${it.messageLevel()}] ${it.message()} -- line ${it.lineNumber()} of ${it.sourceId()}")
            }
            return true
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }
    }

    // 开启远程调试 (Chrome chrome://inspect)
    WebView.setWebContentsDebuggingEnabled(true)
}

/**
 * 模拟真实 App 运行环境的接口
 * 这里的代码逻辑与最终打包模板中的 WebAppInterface.java 保持一致
 * 这样开发者在预览界面就能测试原生交互功能
 */
class PreviewWebAppInterface(private val context: Context, private val webView: WebView) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // 使用 SharedPreferences 模拟本地存储，并在 IDE 预览模式下使用独立的文件名
    private val prefs = context.getSharedPreferences("WebIDE_Preview_Prefs", Context.MODE_PRIVATE)

    // --- 基础功能 ---


    @RequiresPermission(Manifest.permission.VIBRATE)
    @JavascriptInterface
    fun vibrate(milliseconds: Long) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(milliseconds)
            }
        } catch (e: Exception) {
            LogCatcher.e("WebPreview", "震动失败 (可能缺少权限)", e)
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        // 返回真实设备信息，帮助开发者调试适配
        val json = JSONObject()
        json.put("model", Build.MODEL)
        json.put("manufacturer", Build.MANUFACTURER)
        json.put("android_version", Build.VERSION.RELEASE)
        json.put("sdk_int", Build.VERSION.SDK_INT)
        json.put("preview_mode", true) // 标识当前是在 IDE 预览模式
        return json.toString()
    }

    // --- 剪贴板 ---

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        mainHandler.post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WebIDE Preview", text)
            clipboard.setPrimaryClip(clip)
        }
    }

    @JavascriptInterface
    fun getFromClipboard(callbackId: String) {
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                var text = ""
                if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0) {
                    val item = clipboard.primaryClip!!.getItemAt(0)
                    text = item.text?.toString() ?: ""
                }
                sendResultToJs(callbackId, true, text)
            } catch (e: Exception) {
                sendResultToJs(callbackId, false, e.message ?: "Unknown error")
            }
        }
    }

    // --- 存储 ---

    @JavascriptInterface
    fun saveStorage(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun getStorage(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    // --- 内部回调机制 ---
    // 这与 api.js 中的 window.onAndroidResponse 配合工作
    private fun sendResultToJs(callbackId: String, success: Boolean, data: String) {
        try {
            val result = JSONObject()
            result.put("success", success)
            result.put("data", data)

            val jsonResult = result.toString()
            // Base64 编码防止特殊字符破坏 JS 字符串
            val base64Result = Base64.encodeToString(
                jsonResult.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

            mainHandler.post {
                val jsCode = "if(window.onAndroidResponse) window.onAndroidResponse('$callbackId', '$base64Result')"
                webView.evaluateJavascript(jsCode, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}