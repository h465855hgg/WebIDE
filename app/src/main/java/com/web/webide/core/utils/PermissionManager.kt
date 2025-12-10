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
 * 支持 Android 11+ 的 MANAGE_EXTERNAL_STORAGE 权限
 */
object PermissionManager {
    
    /**
     * 检查是否已授予所有文件访问权限
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 及以下不需要此权限
        }
    }
    
    /**
     * 检查是否已授予基础存储权限
     */
    fun hasBasicStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用分区存储权限
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 主要依赖 MANAGE_EXTERNAL_STORAGE
            true
        } else {
            // Android 10 及以下使用传统权限
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    /**
     * 检查是否已授予所有必要权限
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // 只要拥有所有文件访问权限或基础存储权限之一即可
        return hasAllFilesAccess() || hasBasicStoragePermission(context)
    }
    
    /**
     * 请求所有文件访问权限（跳转到系统设置）
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
                LogCatcher.i("PermissionManager", "跳转到所有文件访问权限设置页面")
            } catch (e: Exception) {
                LogCatcher.e("PermissionManager", "无法打开权限设置页面", e)
                // 降级到通用设置页面
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }
    
    /**
     * 请求权限的可组合函数
     */
    @Composable
    fun rememberPermissionRequest(
        onPermissionGranted: () -> Unit = {},
        onPermissionDenied: () -> Unit = {}
    ): PermissionRequestState {
        val context = LocalContext.current
        var showRationale by remember { mutableStateOf(false) }

        // 1. 定义 Android 11+ 的启动器
        // 专门处理 "所有文件访问权限" 的回调
        val allFilesAccessLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            // 当从设置页面返回时，无论结果代码是什么，都检查一次权限
            if (hasAllFilesAccess()) {
                LogCatcher.i("PermissionManager", "所有文件访问权限已授予 (Callback)")
                onPermissionGranted()
            } else {
                LogCatcher.w("PermissionManager", "所有文件访问权限仍未授予 (Callback)")
                onPermissionDenied()
                // 对于这个特殊权限，通常不显示Rationale，或者你可以根据需求开启
            }
        }

        // 2. 定义 Android 10及以下的普通权限启动器
        val basicPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                LogCatcher.i("PermissionManager", "基础存储权限已授予")
                onPermissionGranted()
            } else {
                LogCatcher.w("PermissionManager", "部分基础权限被拒绝: $permissions")
                onPermissionDenied()
                showRationale = true
            }
        }

        return remember(context) { // 使用 remember 优化性能
            PermissionRequestState(
                requestPermissions = {
                    LogCatcher.d("PermissionManager", "开始请求权限")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ 逻辑：检查是否有权限，没有则通过 Launcher 跳转
                        if (hasAllFilesAccess()) {
                            onPermissionGranted()
                        } else {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                // ✅ 关键修复：使用 launcher 启动，而不是 context.startActivity
                                allFilesAccessLauncher.launch(intent)
                            } catch (_: Exception) {
                                // 容错处理：部分机型可能不支持该 Intent，尝试通用设置页
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                allFilesAccessLauncher.launch(intent)
                            }
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Android 6-10 逻辑
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13+ 实际上也更推荐走 Photo Picker，但如果是全文件管理类应用，逻辑会很复杂
                            // 这里保留你原有的逻辑，或者根据 targetSdk 调整
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_AUDIO
                            )
                        } else {
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        }
                        basicPermissionLauncher.launch(permissions)
                    } else {
                        // Android 5 及以下
                        onPermissionGranted()
                    }
                },
                showRationale = showRationale,
                hasPermissions = { hasRequiredPermissions(context) }
            )
        }
    }
    
    /**
     * 权限请求状态
     */
    data class PermissionRequestState(
        val requestPermissions: () -> Unit,
        val showRationale: Boolean,
        val hasPermissions: () -> Boolean
    )
    
    /**
     * 权限检查结果
     */
    sealed class PermissionResult {
        object Granted : PermissionResult()
        object Denied : PermissionResult()
        data class Rationale(val message: String) : PermissionResult()
    }
    
    /**
     * 检查权限并返回详细结果
     */
    fun checkPermissionsWithResult(context: Context): PermissionResult {
        return if (hasRequiredPermissions(context)) {
            PermissionResult.Granted
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionResult.Rationale("需要访问所有文件权限来管理项目文件")
            } else {
                PermissionResult.Rationale("需要存储权限来访问和保存文件")
            }
        }
    }
}