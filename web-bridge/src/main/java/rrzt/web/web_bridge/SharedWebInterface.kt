package rrzt.web.web_bridge

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.component1
import kotlin.collections.component2
import android.app.DownloadManager
import android.webkit.CookieManager
import android.webkit.URLUtil

open class SharedWebInterface(
    protected val context: Context,
    protected val webView: WebView
) {
    protected val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("WebAppPrefs", Context.MODE_PRIVATE)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sensorListener: SensorEventListener? = null

    protected fun sendResultToJs(callbackId: String, success: Boolean, data: String) {
        val jsonStr = JSONObject().put("success", success).put("data", data).toString()
        val base64 = Base64.encodeToString(jsonStr.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        mainHandler.post {
            val js = "if(window.onAndroidResponse) window.onAndroidResponse('$callbackId', '$base64')"
            webView.evaluateJavascript(js, null)
        }
    }

    protected fun runOnMain(block: () -> Unit) = mainHandler.post(block)

    // ==========================================================
    // 抽象/虚方法区域 (由子类实现)
    // ==========================================================

    @JavascriptInterface
    open fun setBackKeyInterceptor(enabled: Boolean) {
    }

    @JavascriptInterface
    open fun readFile(path: String): String {
        return ""
    }

    @JavascriptInterface
    open fun writeFile(path: String, content: String): Boolean {
        return false
    }

    @JavascriptInterface
    open fun listFiles(directory: String): String {
        return "[]"
    }

    @JavascriptInterface
    open fun fileExists(path: String): Boolean = false

    @JavascriptInterface
    open fun deleteFile(path: String): Boolean = false

    @JavascriptInterface
    open fun getAppConfig(): String {
        return "{}"
    }

    // ==========================================================
    // 通用方法区域
    // ==========================================================

    @RequiresApi(Build.VERSION_CODES.Q)
    @JavascriptInterface
    fun saveToDownloads(filename: String, content: String, mimeType: String): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
                }
                runOnMain { Toast.makeText(context, "已导出: $filename", Toast.LENGTH_LONG).show() }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            runOnMain { Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            false
        }
    }

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
                    headers.keys().forEach { key -> conn.setRequestProperty(key, headers.getString(key)) }
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
                val responseHeaders = JSONObject()
                conn.headerFields.forEach { (k, v) -> if (k != null) responseHeaders.put(k, v.joinToString(",")) }
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
    /**
     * 调用系统下载管理器下载文件
     * @param url 下载链接
     * @param userAgent (可选) UserAgent
     * @param contentDisposition (可选) 用于提取文件名
     * @param mimeType (可选) 文件类型
     */
    @JavascriptInterface
    fun downloadFile(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        runOnMain {
            try {
                val request = DownloadManager.Request(Uri.parse(url))

                // 1. 设置文件类型
                if (!mimeType.isNullOrEmpty()) {
                    request.setMimeType(mimeType)
                }

                // 2. 提取文件名 (优先用参数，没有则从 URL 猜)
                var filename = URLUtil.guessFileName(url, contentDisposition, mimeType)

                // 3. 设置 Cookies (如果是登录后的下载，必须带 Cookie)
                val cookie = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("Cookie", cookie)
                if (!userAgent.isNullOrEmpty()) {
                    request.addRequestHeader("User-Agent", userAgent)
                }

                // 4. 设置通知栏可见
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // 5. 设置保存路径 (Downloads 文件夹)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

                // 6. 加入队列
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)

                Toast.makeText(context, "开始下载: $filename", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
    // 供 JS 直接调用的简化版
    @JavascriptInterface
    fun triggerDownload(url: String) {
        downloadFile(url, null, null, null)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        runOnMain { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // ✅ 修复：弹窗崩溃修复
    @JavascriptInterface
    fun showDialog(title: String, message: String, positiveText: String, negativeText: String, callbackId: String) {
        runOnMain {
            // 必须使用 Activity Context，否则无法显示 Dialog
            if (context !is Activity) {
                sendResultToJs(callbackId, false, "Context is not an Activity")
                return@runOnMain
            }
            if (context.isFinishing || context.isDestroyed) {
                return@runOnMain
            }

            try {
                val builder = AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(positiveText) { _, _ -> sendResultToJs(callbackId, true, "positive") }

                if (negativeText.isNotEmpty()) {
                    builder.setNegativeButton(negativeText) { _, _ -> sendResultToJs(callbackId, true, "negative") }
                } else {
                    // 如果没有负面按钮，也得确保点击 OK 能够回调
                    // 这里通常只需要 Positive
                }
                builder.show()
            } catch (e: Exception) {
                e.printStackTrace()
                sendResultToJs(callbackId, false, e.message ?: "Dialog Error")
            }
        }
    }

    // ✅ 修复：通知权限检查和 Icon 引用
    @JavascriptInterface
    fun showNotification(id: Int, title: String, content: String) {
        runOnMain {
            try {
                // 1. 检查权限 (Android 13+)
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "请先授予通知权限", Toast.LENGTH_SHORT).show()
                        // 尝试请求权限（如果在 Activity 中）
                        (context as? Activity)?.let {
                            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                        }
                        return@runOnMain
                    }
                }

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "webapp_channel_default"

                // 2. 创建 Channel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(channelId, "应用通知", NotificationManager.IMPORTANCE_HIGH)
                    channel.description = "来自 WebApp 的通知"
                    nm.createNotificationChannel(channel)
                }

                // 3. 构建通知 (使用系统图标，绝对安全)
                val builder = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_chat) // 使用系统图标
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                nm.notify(id, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "通知发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

            // 权限检查加固
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    tm?.let {
                        info.put("simOperator", it.simOperatorName)
                        info.put("networkOperator", it.networkOperatorName)
                    }
                } catch (e: Exception) {}
            }

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                try {
                    if (wm.isWifiEnabled) {
                        val wifiInfo = wm.connectionInfo
                        info.put("wifiSSID", wifiInfo.ssid.replace("\"", ""))
                        info.put("wifiRSSI", wifiInfo.rssi)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) { e.printStackTrace() }
        return info.toString()
    }

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

    @JavascriptInterface
    fun openBrowser(url: String) {
        runOnMain { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { e.printStackTrace() } }
    }
    @JavascriptInterface
    fun shareText(text: String) {
        runOnMain { try { val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }; context.startActivity(Intent.createChooser(intent, "分享")) } catch (e: Exception) { e.printStackTrace() } }
    }
    @JavascriptInterface
    fun callPhone(phoneNumber: String) {
        runOnMain { try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))) } catch (e: Exception) { e.printStackTrace() } }
    }
    @JavascriptInterface
    fun sendSMS(phoneNumber: String, message: String) {
        runOnMain { try { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")); intent.putExtra("sms_body", message); context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() } }
    }
    @JavascriptInterface
    fun sendEmail(email: String, subject: String, body: String) {
        runOnMain { try { val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf(email)); putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body) }; context.startActivity(Intent.createChooser(intent, "发送邮件")) } catch (e: Exception) { e.printStackTrace() } }
    }
    @JavascriptInterface
    fun openMap(latitude: Double, longitude: Double, label: String) {
        runOnMain { try { val uriStr = "geo:$latitude,$longitude?q=$latitude,$longitude($label)"; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))) } catch (e: Exception) { e.printStackTrace() } }
    }

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
        runOnMain { (context as? Activity)?.let { activity -> val lp = activity.window.attributes; lp.screenBrightness = brightness; activity.window.attributes = lp } }
    }
    @JavascriptInterface
    fun getScreenBrightness(): Float {
        return (context as? Activity)?.window?.attributes?.screenBrightness ?: -1f
    }
    @JavascriptInterface
    fun setVolume(volume: Int) {
        try {
            val audioSystem = Class.forName("android.media.AudioSystem")
            val method = audioSystem.getMethod("setStreamVolume", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            method.invoke(null, 3, volume, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    @JavascriptInterface
    fun startSensor(sensorType: String, callbackId: String) {
        runOnMain {
            val type = when (sensorType.lowercase()) {
                "accelerometer" -> Sensor.TYPE_ACCELEROMETER
                "gyroscope" -> Sensor.TYPE_GYROSCOPE
                "magnetometer" -> Sensor.TYPE_MAGNETIC_FIELD
                "light" -> Sensor.TYPE_LIGHT
                "proximity" -> Sensor.TYPE_PROXIMITY
                else -> { sendResultToJs(callbackId, false, "Unsupported sensor type"); return@runOnMain }
            }
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor == null) { sendResultToJs(callbackId, false, "Sensor not available"); return@runOnMain }
            if (sensorListener != null) sensorManager.unregisterListener(sensorListener)
            sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let { val values = JSONArray(); it.values.forEach { v -> values.put(v) }; sendResultToJs(callbackId + "_data", true, values.toString()) }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            sendResultToJs(callbackId, true, "Sensor started")
        }
    }
    @JavascriptInterface
    fun stopSensor() {
        runOnMain { sensorListener?.let { sensorManager.unregisterListener(it) }; sensorListener = null }
    }

    @JavascriptInterface
    fun requestPermission(permission: String, callbackId: String) {
        runOnMain {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                sendResultToJs(callbackId, true, "Permission already granted")
                return@runOnMain
            }
            (context as? Activity)?.let { activity ->
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 100)
                // 简单的延时检查，实际应由 Activity 回调处理
                mainHandler.postDelayed({
                    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    sendResultToJs(callbackId, granted, if(granted) "Permission granted" else "Permission denied (or pending)")
                }, 5000)
            } ?: sendResultToJs(callbackId, false, "Context is not an Activity")
        }
    }
    @JavascriptInterface
    fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    @JavascriptInterface
    fun openAppSettings() {
        runOnMain { val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); intent.data = Uri.fromParts("package", context.packageName, null); context.startActivity(intent) }
    }

    @JavascriptInterface
    fun startActivity(jsonStr: String, callbackId: String) {
        runOnMain {
            try {
                val config = JSONObject(jsonStr)
                val intent = Intent()

                // 1. 设置 Action
                config.optString("action").takeIf { it.isNotEmpty() }?.let { intent.action = it }

                // 2. 设置 Data 和 Type (注意顺序，setDataAndType 会互相清除)
                val uriStr = config.optString("uri")
                val type = config.optString("type")
                if (uriStr.isNotEmpty() && type.isNotEmpty()) {
                    intent.setDataAndType(Uri.parse(uriStr), type)
                } else if (uriStr.isNotEmpty()) {
                    intent.data = Uri.parse(uriStr)
                } else if (type.isNotEmpty()) {
                    intent.type = type
                }

                // 3. 设置 Component (包名类名)
                val pkg = config.optString("package")
                val cls = config.optString("className")
                if (pkg.isNotEmpty() && cls.isNotEmpty()) {
                    intent.setClassName(pkg, cls)
                } else if (pkg.isNotEmpty()) {
                    intent.setPackage(pkg)
                }

                // 4. 设置 Extras (根据类型自动转换)
                config.optJSONObject("extras")?.let { extrasJson ->
                    val keys = extrasJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val value = extrasJson.get(key)) {
                            is String -> intent.putExtra(key, value)
                            is Int -> intent.putExtra(key, value)
                            is Boolean -> intent.putExtra(key, value)
                            is Double -> intent.putExtra(key, value)
                            is Long -> intent.putExtra(key, value)
                        }
                    }
                }

                // 5. 设置 Flags
                config.optJSONArray("flags")?.let { flagsArr ->
                    for (i in 0 until flagsArr.length()) {
                        intent.addFlags(flagsArr.getInt(i))
                    }
                }

                // 确保非 Activity Context 能正常启动
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // 6. 执行启动
                if (intent.resolveActivity(context.packageManager) != null || pkg.isNotEmpty()) {
                    context.startActivity(intent)
                    sendResultToJs(callbackId, true, "Success")
                } else {
                    sendResultToJs(callbackId, false, "No activity found to handle this intent")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                sendResultToJs(callbackId, false, "Error: ${e.message}")
            }
        }
    }

    /**
     * 快捷跳转系统特定设置页面
     * @param type 设置类型：wifi, bluetooth, display, battery, location, nfc 等
     */
    @JavascriptInterface
    fun openSystemSetting(type: String) {
        runOnMain {
            val action = when (type.lowercase()) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "nfc" -> Settings.ACTION_NFC_SETTINGS
                "internal_storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                "date" -> Settings.ACTION_DATE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            try {
                val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                showToast("无法打开设置: $type")
            }
        }
    }
    @JavascriptInterface
    fun reloadApp() { runOnMain { (context as? Activity)?.recreate() } }
    @JavascriptInterface
    fun exitApp() { runOnMain { (context as? Activity)?.finishAffinity() } }
    @JavascriptInterface
    fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
    @JavascriptInterface
    fun formatDate(timestamp: Long, format: String): String = SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))

    fun onDestroy() { stopSensor() }
}