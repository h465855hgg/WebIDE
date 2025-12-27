/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package rrzt.web.web_bridge

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import bsh.Interpreter
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.net.toUri


class WebsApiAdapter(
    private val context: Context,
    private val webView: WebView,
    private val sharedInterface: SharedWebInterface,
    private val pathResolver: (String) -> File?
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // 允许文件 Uri (为了兼容老旧 API 的 openFile/installAPK)
        // 注意：Android 7.0+ 正规做法是 FileProvider，这里为了最大兼容性尝试宽容模式
        try {
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- 路径解析逻辑 ---
    private fun resolvePath(path: String): String {
        var cleanPath = path.trim()
        val sdCard = Environment.getExternalStorageDirectory().absolutePath

        return when {
            cleanPath.startsWith("@") -> "assets/" + cleanPath.substring(1)
            cleanPath.startsWith("%") -> "$sdCard/${cleanPath.substring(1)}"
            cleanPath.startsWith("$") -> "${context.filesDir.absolutePath}/${cleanPath.substring(1)}"
            cleanPath.startsWith("#") -> "${
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).absolutePath}/${cleanPath.substring(1)}"
            cleanPath.startsWith("/sdcard/") -> cleanPath
            cleanPath.startsWith("/") -> cleanPath
            else -> "$sdCard/$cleanPath"
        }
    }

    // ==========================================
    // 一、浏览器相关
    // ==========================================

    @JavascriptInterface
    fun toast(msg: String) = sharedInterface.showToast(msg)

    @JavascriptInterface
    fun evalJavaCode(code: String) {
        // 建议在子线程执行，防止脚本卡死主线程
        Thread {
            try {
                // 1. 创建解释器
                val interpreter = Interpreter()

                // 2. 注入全局变量 (让脚本能用到这些对象)
                interpreter.set("context", context)
                interpreter.set("webView", webView)
                interpreter.set("adapter", this) // 把自己也传进去

                // 3. 执行代码
                // BeanShell 支持直接返回最后一行结果
                val result = interpreter.eval(code)

                // 4. 反馈结果到 UI (Toast)
                if (result != null) {
                    mainHandler.post {
                        Toast.makeText(context, "执行结果: $result", Toast.LENGTH_LONG).show()
                    }
                } else {
                    mainHandler.post {
                        Toast.makeText(context, "执行成功", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // 捕获脚本错误
                mainHandler.post {
                    // AlertDialog 可能更好，这里简单用 Toast
                    Toast.makeText(context, "脚本错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @JavascriptInterface
    fun evalJsCode(js: String) = mainHandler.post { webView.evaluateJavascript(js, null) }
    @JavascriptInterface
    fun addJScode(url: String, js: String) = evalJsCode(js)
    @JavascriptInterface
    fun cleanCache() = mainHandler.post { webView.clearCache(true) }
    @JavascriptInterface
    fun clearCache() = cleanCache()

    // 存根实现
    @JavascriptInterface
    fun openInApp() {}
    @JavascriptInterface
    fun closeOpenInApp() {}
    @JavascriptInterface
    fun openLongClickImage() { webView.settings.allowFileAccess = true } // 简单模拟
    @JavascriptInterface
    fun closeLongClickImage() {}

    @JavascriptInterface
    fun openNoImageMode() = mainHandler.post { webView.settings.loadsImagesAutomatically = false }

    @JavascriptInterface
    fun closeNoImageMode() = mainHandler.post { webView.settings.loadsImagesAutomatically = true }

    @JavascriptInterface
    fun setUserAgent(ua: String) = mainHandler.post { webView.settings.userAgentString = ua }

    // ==========================================
    // 二、UI交互相关
    // ==========================================

    @JavascriptInterface
    fun showDialog(title: String, content: String, btn1: String, btn2: String, js1: String, js2: String) {
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(title).setMessage(content)
                .setPositiveButton(btn1) { _, _ -> evalJsCode(js1) }
                .setNegativeButton(btn2) { _, _ -> evalJsCode(js2) }
                .create().show()
        }
    }

    @JavascriptInterface
    fun showDialog(title: String, content: String) {
        mainHandler.post {
            AlertDialog.Builder(context).setTitle(title).setMessage(content)
                .setPositiveButton("确定", null).show()
        }
    }

    @JavascriptInterface
    fun sendNotification(title: String, content: String, channelId: String?, channelName: String?, jsCode: String?) {
        val id = (System.currentTimeMillis() % 10000).toInt()
        sharedInterface.showNotification(id, title, content)
        if (!jsCode.isNullOrEmpty()) {
            evalJsCode(jsCode.replace("TZ_title", "\"$title\"").replace("TZ_message", "\"$content\""))
        }
    }
    // 兼容重载
    @JavascriptInterface
    fun sendNotification(title: String, content: String) = sendNotification(title, content, null, null, null)
    @JavascriptInterface
    fun sendNotification(title: String, content: String, jsCode: String) = sendNotification(title, content, null, null, jsCode)


    @JavascriptInterface
    fun shareText(text: String) = sharedInterface.shareText(text)

    @JavascriptInterface
    fun openActivity(className: String) {
        try {
            val intent = Intent().apply {
                component = ComponentName(context.packageName, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(className)
                if (intent != null) context.startActivity(intent)
                else sharedInterface.showToast("无法启动: $className")
            } catch (ex: Exception) { sharedInterface.showToast("启动错误: ${ex.message}") }
        }
    }

    @JavascriptInterface
    fun setStatuColor(color: String) {
        mainHandler.post {
            if (context is Activity) {
                try {
                    val parsedColor = Color.parseColor(color)
                    context.window.statusBarColor = parsedColor
                    val isLight = ColorUtils.calculateLuminance(parsedColor) > 0.5
                    WindowCompat.getInsetsController(context.window, context.window.decorView).isAppearanceLightStatusBars = isLight
                } catch (e: Exception) {}
            }
        }
    }

    @JavascriptInterface
    fun setNavColor(color: String) {
        mainHandler.post {
            if (context is Activity) {
                try { context.window.navigationBarColor = Color.parseColor(color) } catch (e: Exception) {}
            }
        }
    }

    // QQ 相关
    @JavascriptInterface
    fun joinQFriend(qq: String) {
        try {
            val url = "mqqwpa://im/chat?chat_type=wpa&uin=$qq&version=1"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { sharedInterface.showToast("未安装QQ或版本不支持") }
    }

    @JavascriptInterface
    fun joinQun(qunNumber: String): Boolean {
        return try {
            // 使用旧版协议，直接支持群号，不需要 Key
            val url = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qunNumber&card_type=group&source=qrcode"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            sharedInterface.showToast("未安装QQ或不支持")
            false
        }
    }

    @JavascriptInterface
    fun joinQQGroup(key: String): Boolean {
        return try {
            val intent = Intent()
            // 补全了 jump_from=webapi
            intent.data = Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            sharedInterface.showToast("Key错误或未安装QQ")
            false
        }
    }

    // 手电筒
    @JavascriptInterface
    fun openFlashLight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                manager.setTorchMode(manager.cameraIdList[0], true)
            } catch (e: Exception) { sharedInterface.showToast("无法开启手电筒") }
        }
    }
    @JavascriptInterface
    fun closeFlashLight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                manager.setTorchMode(manager.cameraIdList[0], false)
            } catch (e: Exception) {}
        }
    }

    // 壁纸
    @RequiresPermission(Manifest.permission.SET_WALLPAPER)
    @JavascriptInterface
    fun setWallpaper(path: String) {
        try {
            val file = File(resolvePath(path))
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                WallpaperManager.getInstance(context).setBitmap(bitmap)
                sharedInterface.showToast("壁纸设置成功")
            }
        } catch (e: Exception) { sharedInterface.showToast("设置壁纸失败: ${e.message}") }
    }

    // VPN & Hook
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @JavascriptInterface
    fun isVpnActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        return false
    }
    @JavascriptInterface
    fun isHook(): Boolean = false // 简单返回

    // 悬浮窗
    @JavascriptInterface
    fun checkFloatPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }
    @JavascriptInterface
    fun requestFloatPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    @JavascriptInterface
    fun showFloatView(url: String, w: Int, h: Int, x: Int, y: Int) = sharedInterface.showToast("悬浮窗需在原生代码实现")

    // 硬件状态
    @JavascriptInterface
    fun getCpuTemperature(): String = "40.0" // 需读取系统文件，由于权限限制通常难以在普通App获取
    @JavascriptInterface
    fun getBatteryTemperature(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return (temp / 10.0f).toString()
    }

    // Wifi
    @JavascriptInterface
    fun openWifi() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            if (!wm.isWifiEnabled) wm.isWifiEnabled = true
        } catch (e: Exception) { sharedInterface.showToast("无法修改WIFI状态(Android 10+限制)") }
    }
    @JavascriptInterface
    fun closeWifi() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            if (wm.isWifiEnabled) wm.isWifiEnabled = false
        } catch (e: Exception) {}
    }

    // 屏幕方向
    @JavascriptInterface
    fun setLandscape() {
        if (context is Activity) context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    @JavascriptInterface
    fun setPortrait() {
        if (context is Activity) context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    @JavascriptInterface
    fun setFullScreen() {
        mainHandler.post {
            if (context is Activity) {
                WindowCompat.getInsetsController(context.window, context.window.decorView).hide(
                    WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    @JavascriptInterface
    fun cancelFullScreen() {
        mainHandler.post {
            if (context is Activity) {
                WindowCompat.getInsetsController(context.window, context.window.decorView).show(
                    WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ==========================================
    // 三、文件系统相关
    // ==========================================

    @JavascriptInterface
    fun getStoragePermission(): Boolean = sharedInterface.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    @JavascriptInterface
    fun requestStoragePermissions() = sharedInterface.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, "storage_perm")

    @JavascriptInterface
    fun listFiles(path: String): String {
        val resolved = resolvePath(path)
        val file = File(resolved)
        return if (file.exists() && file.isDirectory) file.list()?.joinToString("[FG]") ?: "" else ""
    }

    @JavascriptInterface
    fun deletFiles(path: String) { File(resolvePath(path)).deleteRecursively() }

    @JavascriptInterface
    fun writeFile(path: String, content: String) {
        try {
            val file = File(resolvePath(path))
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (e: Exception) { e.printStackTrace() }
    }

    @JavascriptInterface
    fun readFile(path: String): String {
        val resolved = resolvePath(path)
        if (resolved.startsWith("assets/")) {
            val relative = resolved.removePrefix("assets/")
            // 尝试通过 pathResolver (IDE模式)
            val ideFile = pathResolver(if(pathResolver(relative) != null) relative else "src/main/assets/$relative")
            if (ideFile != null && ideFile.exists()) return ideFile.readText()
            // 尝试通过 AssetsManager (APK模式)
            return try {
                context.assets.open(relative.replace("./", "")).bufferedReader().use { it.readText() }
            } catch (e: Exception) { "" }
        }
        return try { File(resolved).readText() } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun renameFile(oldPath: String, newName: String) {
        val oldFile = File(resolvePath(oldPath))
        if (oldFile.exists()) oldFile.renameTo(File(oldFile.parent, newName))
    }

    @JavascriptInterface
    fun openFile(path: String) {
        try {
            val file = File(resolvePath(path))
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = getUriForFile(file)
            // 简单推断 MIME 类型
            val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            intent.setDataAndType(uri, mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: Exception) { sharedInterface.showToast("打开文件失败: ${e.message}") }
    }

    @JavascriptInterface
    fun fileExists(path: String): Boolean = File(resolvePath(path)).exists()

    @JavascriptInterface
    fun getFileByBase64(path: String): String {
        return try {
            val file = File(resolvePath(path))
            if (file.exists()) Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) else ""
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun saveFileByBase64(path: String, base64: String): Boolean {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val file = File(resolvePath(path))
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun getSDdir(subPath: String?): String {
        val base = Environment.getExternalStorageDirectory().absolutePath
        return if (subPath.isNullOrEmpty()) base else "$base/$subPath"
    }

    @JavascriptInterface
    fun fileCopy(srcPath: String, destPath: String): Boolean {
        return try {
            File(resolvePath(srcPath)).copyTo(File(resolvePath(destPath)), overwrite = true)
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun copyApkFileToSd(assetName: String, destPath: String): Boolean {
        return try {
            val out = File(resolvePath(destPath))
            context.assets.open(assetName).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun setLastModifiedTime(path: String): Boolean {
        return File(resolvePath(path)).setLastModified(System.currentTimeMillis())
    }

    // Zip 相关
    @JavascriptInterface
    fun zipFile(srcPath: String, destZip: String): Boolean {
        return try {
            val srcFile = File(resolvePath(srcPath))
            val zipFile = File(resolvePath(destZip))
            zipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                zipRecursively(srcFile, srcFile.name, zos)
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun zipRecursively(file: File, fileName: String, zos: ZipOutputStream) {
        if (file.isHidden) return
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                zipRecursively(child, "$fileName/${child.name}", zos)
            }
        } else {
            val entry = ZipEntry(fileName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    @JavascriptInterface
    fun zipFileWithPassword(src: String, dest: String, pass: String) = sharedInterface.showToast("加密压缩需引入第三方库，暂不支持")

    @JavascriptInterface
    fun unzipFile(zipPath: String, destDir: String): Boolean {
        return try {
            val zipFile = File(resolvePath(zipPath))
            val targetDir = File(resolvePath(destDir))
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val file = File(targetDir, entry!!.name)
                    if (entry!!.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun unzipFileWithPassword(zip: String, dest: String, pass: String) = sharedInterface.showToast("加密解压需引入第三方库，暂不支持")

    @JavascriptInterface
    fun downloadFile(url: String, filename: String) = sharedInterface.downloadFile(url, null, null, null)

    @JavascriptInterface
    fun getMediaMetadata(path: String): String = "{}" // 需引入 MetadataRetriever，简单处理返回空

    // ==========================================
    // 四、App操作相关
    // ==========================================

    @JavascriptInterface
    fun exitApp() = sharedInterface.exitApp()
    @JavascriptInterface
    fun restartApp() = sharedInterface.reloadApp()
    @JavascriptInterface
    fun getAppName(): String = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(context.packageName, 0)).toString() } catch (e: Exception) { "" }
    @JavascriptInterface
    fun getAppPackageName(): String = context.packageName
    @JavascriptInterface
    fun getAppVersion(): String? = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { "1.0" }
    @JavascriptInterface
    fun getAppVersionCode(): Int = try { context.packageManager.getPackageInfo(context.packageName, 0).versionCode } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun isAppInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun launchApp(packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) context.startActivity(intent)
        } catch (e: Exception) {}
    }

    @JavascriptInterface
    fun installAPK(path: String) {
        try {
            val file = File(resolvePath(path))
            if (!file.exists()) return
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = getUriForFile(file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: Exception) { sharedInterface.showToast("安装失败: ${e.message}") }
    }

    @JavascriptInterface
    fun block(): Boolean = false
    @JavascriptInterface
    fun gotoApkNotice() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        } catch (e: Exception) {}
    }

    @JavascriptInterface
    fun openURLInBrowser(url: String) = sharedInterface.openBrowser(url)

    // ==========================================
    // 六、设置与系统信息
    // ==========================================

    @JavascriptInterface
    fun getClipContent(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return if (cm.hasPrimaryClip()) cm.primaryClip?.getItemAt(0)?.text.toString() else ""
    }

    @JavascriptInterface
    fun setClipContent(txt: String) = sharedInterface.copyToClipboard(txt)

    @JavascriptInterface
    fun getOSID(): String = Build.VERSION.RELEASE

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @JavascriptInterface
    fun getNetworkStatus(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        return if (netInfo != null && netInfo.isConnected) netInfo.typeName else "NONE"
    }

    @JavascriptInterface
    fun getEquipmentModel(): String = Build.MODEL

    @RequiresPermission(Manifest.permission.VIBRATE)
    @JavascriptInterface
    fun shockDevice(millis: Long) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(millis)
            }
        } catch (e: Exception) {}
    }

    @JavascriptInterface
    fun getMediaVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    @JavascriptInterface
    fun setMediaVolume(vol: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
    }

    @JavascriptInterface
    fun getScreenBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { 0 }
    }

    @JavascriptInterface
    fun setScreenBrightness(value: Int) {
        // 需要 WRITE_SETTINGS 权限，这里只尝试设置 Activity 亮度
        mainHandler.post {
            if (context is Activity) {
                val lp = context.window.attributes
                lp.screenBrightness = value / 255f
                context.window.attributes = lp
            }
        }
    }

    @JavascriptInterface
    fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
    }

    @JavascriptInterface
    fun getTotalMemory(): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / 1024 / 1024 // MB
        } catch (e: Exception) { 0 }
    }

    @JavascriptInterface
    fun getDeviceStatus(): String = sharedInterface.getDeviceInfo()

    @JavascriptInterface
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    @JavascriptInterface
    fun getIMEI(): String = "Unknown (Permission Restricted)" // 现代Android禁止获取IMEI

    @JavascriptInterface
    fun openSettings() = sharedInterface.openAppSettings()

    // 辅助方法：获取 Uri
    private fun getUriForFile(file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 需要配置 FileProvider authorities，这里假设包名.provider
            // 如果没配置 Provider，此行会报错，回退到 file://
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
        } else {
            Uri.fromFile(file)
        }
    }
}