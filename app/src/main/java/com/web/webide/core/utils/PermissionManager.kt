package com.web.webide.core.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 权限管理器 - 统一管理存储权限
 * 修复了对 App 私有目录 (Android/data) 的权限误判问题
 * 并保留了 hasRequiredPermissions 以兼容旧代码
 */
object PermissionManager {

    private const val TAG = "PermissionManager"

    /**
     * 检查是否已授予 Android 11+ 的所有文件访问权限
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 及以下不需要此特殊权限
        }
    }

    /**
     * ✅ 修复点 1：加回这个方法，解决 EditorViewModel 报错
     * 检查是否已授予“必要”的权限（根据系统版本自动判断）
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesAccess()
        } else {
            hasBasicStoragePermission(context)
        }
    }

    /**
     * ✅ 修复点 2：智能判断指定路径是否需要“系统级存储权限”
     * 用于 WorkspaceSelectionScreen 判断是否需要弹窗
     *
     * @param path 用户选择的目标路径
     * @return true=需要申请权限, false=不需要(是私有目录或已授权)
     */
    fun isSystemPermissionRequiredForPath(context: Context, path: String): Boolean {
        // 1. 获取 App 的外部私有根目录 (通常是 /storage/emulated/0/Android/data/com.web.webide/)
        val appExternalDir = context.getExternalFilesDir(null)?.parentFile?.parentFile?.absolutePath

        // 2. 如果路径为空或无法获取私有目录，为了安全起见，认为需要检查权限
        if (appExternalDir == null) return true

        // 3. 判断：如果用户选择的 path 是 App 私有目录的子路径
        if (path.startsWith(appExternalDir)) {
            // 是私有目录，完全不需要 MANAGE_EXTERNAL_STORAGE 权限即可读写
            return false
        }

        // 4. 如果不是私有目录（例如是 SD卡根目录），则需要根据系统版本判断
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        } else {
            return !hasBasicStoragePermission(context)
        }
    }

    /**
     * 检查是否已授予基础存储权限 (Android 10及以下)
     */
    fun hasBasicStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 逻辑上依赖 MANAGE_EXTERNAL_STORAGE，这里返回 true 以通过旧逻辑检查
            true
        } else {
            // Android 10 及以下使用传统权限
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求权限的可组合函数 (UI层调用)
     */
    @Composable
    fun rememberPermissionRequest(
        onPermissionGranted: () -> Unit = {},
        onPermissionDenied: () -> Unit = {}
    ): PermissionRequestState {
        val context = LocalContext.current
        var showRationale by remember { mutableStateOf(false) }

        // --- 1. Android 11+ (SDK 30+) 的处理逻辑 ---
        val allFilesAccessLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            if (hasAllFilesAccess()) {
                LogCatcher.i(TAG, "Android 11+ 权限授予成功")
                onPermissionGranted()
            } else {
                LogCatcher.w(TAG, "Android 11+ 权限仍未授予")
                onPermissionDenied()
            }
        }

        // --- 2. Android 10及以下 (SDK < 30) 的处理逻辑 ---
        val basicPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                LogCatcher.i(TAG, "基础存储权限授予成功")
                onPermissionGranted()
            } else {
                LogCatcher.w(TAG, "基础存储权限被拒绝")
                onPermissionDenied()
                showRationale = true
            }
        }

        return remember(context) {
            PermissionRequestState(
                requestPermissions = {
                    LogCatcher.d(TAG, "发起权限请求...")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ 逻辑
                        if (hasAllFilesAccess()) {
                            onPermissionGranted()
                        } else {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                allFilesAccessLauncher.launch(intent)
                            } catch (e: Exception) {
                                LogCatcher.e(TAG, "无法打开特定应用权限页，尝试通用页", e)
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                allFilesAccessLauncher.launch(intent)
                            }
                        }
                    } else {
                        // Android 10及以下 逻辑
                        if (hasBasicStoragePermission(context)) {
                            onPermissionGranted()
                        } else {
                            basicPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                },
                showRationale = showRationale,
                hasPermissions = {
                    hasRequiredPermissions(context)
                }
            )
        }
    }

    data class PermissionRequestState(
        val requestPermissions: () -> Unit,
        val showRationale: Boolean,
        val hasPermissions: () -> Boolean
    )
}
