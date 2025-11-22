package com.web.webide.core.utils

import android.Manifest
import android.app.Activity
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
import androidx.core.app.ActivityCompat
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
        
        // 基础存储权限请求器
        val basicPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                LogCatcher.i("PermissionManager", "基础存储权限已授予")
                // 基础权限授予后，检查是否需要请求所有文件访问权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (hasAllFilesAccess()) {
                        onPermissionGranted()
                    } else {
                        // 需要跳转到设置页面
                        requestAllFilesAccess(context)
                    }
                } else {
                    onPermissionGranted()
                }
            } else {
                LogCatcher.w("PermissionManager", "部分基础权限被拒绝: $permissions")
                onPermissionDenied()
                showRationale = true
            }
        }
        
        // 所有文件访问权限检查器（从设置返回后）
        val allFilesAccessLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            if (hasAllFilesAccess()) {
                LogCatcher.i("PermissionManager", "所有文件访问权限已授予")
                onPermissionGranted()
            } else {
                LogCatcher.w("PermissionManager", "所有文件访问权限被拒绝")
                onPermissionDenied()
                showRationale = true
            }
        }
        
        return PermissionRequestState(
            requestPermissions = {
                LogCatcher.d("PermissionManager", "开始请求权限")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ 直接请求所有文件访问权限
                    if (!hasAllFilesAccess()) {
                        requestAllFilesAccess(context)
                    } else {
                        onPermissionGranted()
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6-10 请求传统存储权限
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                    // Android 5 及以下不需要运行时权限
                    onPermissionGranted()
                }
            },
            showRationale = showRationale,
            hasPermissions = { hasRequiredPermissions(context) }
        )
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