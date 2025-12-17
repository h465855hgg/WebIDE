package com.web.webide.ui.preview

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import rrzt.web.web_bridge.SharedWebInterface
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class FullWebAppInterface(
    context: Context,
    webView: WebView,
    private val packageName: String,
    private val projectDir: File, // ✅ IDE 特有：直接操作本地文件
    private val onBackStateChange: (Boolean) -> Unit // ✅ IDE 特有：Compose 返回键控制
) : SharedWebInterface(context, webView) {

    @JavascriptInterface
    override fun setBackKeyInterceptor(enabled: Boolean) {
        runOnMain {
            onBackStateChange(enabled)
        }
    }

    @JavascriptInterface
    override fun getAppConfig(): String {
        return try { File(projectDir, "webapp.json").readText() } catch (e: Exception) { "{}" }
    }

    // --- 文件系统 (针对 IDE 中的 java.io.File) ---

    private fun resolveFile(path: String): File {
        return if (path.startsWith("assets/")) {
            File(projectDir, "src/main/assets/" + path.substring(7))
        } else if (path.startsWith("/")) {
            File(path)
        } else {
            File(projectDir, path)
        }
    }

    @JavascriptInterface
    override fun readFile(path: String): String {
        return try {
            val file = resolveFile(path)
            if (file.exists() && file.canRead()) file.readText() else ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JavascriptInterface
    override fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = resolveFile(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    override fun fileExists(path: String): Boolean = resolveFile(path).exists()

    @JavascriptInterface
    override fun deleteFile(path: String): Boolean = resolveFile(path).delete()

    @JavascriptInterface
    override fun listFiles(directory: String): String {
        try {
            val dir = resolveFile(directory)
            if (!dir.exists() || !dir.isDirectory) return "[]"
            val jsonArray = JSONArray()
            dir.listFiles()?.forEach { file ->
                val fileInfo = JSONObject()
                fileInfo.put("name", file.name)
                fileInfo.put("path", file.absolutePath)
                fileInfo.put("isDirectory", file.isDirectory)
                fileInfo.put("size", file.length())
                fileInfo.put("lastModified", file.lastModified())
                jsonArray.put(fileInfo)
            }
            return jsonArray.toString()
        } catch (e: Exception) { return "[]" }
    }
}