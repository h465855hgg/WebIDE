package com.web.webide.build

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

    /**
     * 调起安装器
     * @param context 上下文
     * @param apkFile apk文件对象
     */
    fun install(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "安装文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Android 8.0+ 需要检查“允许安装未知应用”权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // 如果没有权限，引导用户去设置页开启
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Toast.makeText(context, "请先授予安装未知应用权限", Toast.LENGTH_LONG).show()
                context.startActivity(intent)
                return
            }
        }

        // 2. 核心安装逻辑
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri

            // 判断版本，Android 7.0 (N) 必须使用 FileProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 注意：这里的 authority 必须和 AndroidManifest.xml 里的一致
                val authority = "${context.packageName}.fileprovider"
                uri = FileProvider.getUriForFile(context, authority, apkFile)

                // 关键点：授予临时读取权限，否则安装器读不到文件
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // 极旧设备（现在很少见，但为了兼容保留）
                uri = Uri.fromFile(apkFile)
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 非 Activity 环境启动必须加

            context.startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "调起安装器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}