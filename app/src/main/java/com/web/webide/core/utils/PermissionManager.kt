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
import java.io.File

object PermissionManager {

    private const val TAG = "PermissionManager"

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    // 兼容旧代码调用
    fun hasRequiredPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesAccess()
        } else {
            hasBasicStoragePermission(context)
        }
    }

    /**
     * ✅ 智能判断：指定路径是否需要申请系统权限
     * 私有目录 (Android/data/...) -> 不需要 -> 返回 false
     * 公共目录 (SDCard/...) -> 需要 -> 返回 true
     */
    fun isSystemPermissionRequiredForPath(context: Context, path: String): Boolean {
        // 获取私有目录根路径 .../Android/data/包名
        val appExternalDir = context.getExternalFilesDir(null)?.parentFile?.parentFile?.absolutePath

        // 如果获取失败，为了安全默认需要权限
        if (appExternalDir == null) return true

        // 如果路径是私有目录的子目录，直接豁免
        if (path.startsWith(appExternalDir)) {
            return false
        }

        // 其他目录根据版本判断
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        } else {
            return !hasBasicStoragePermission(context)
        }
    }

    fun hasBasicStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    @Composable
    fun rememberPermissionRequest(
        onPermissionGranted: () -> Unit = {},
        onPermissionDenied: () -> Unit = {}
    ): PermissionRequestState {
        val context = LocalContext.current
        var showRationale by remember { mutableStateOf(false) }

        val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllFilesAccess()) onPermissionGranted() else onPermissionDenied()
        }

        val basicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) onPermissionGranted() else {
                onPermissionDenied()
                showRationale = true
            }
        }

        return remember(context) {
            PermissionRequestState(
                requestPermissions = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (hasAllFilesAccess()) {
                            onPermissionGranted()
                        } else {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                allFilesLauncher.launch(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                allFilesLauncher.launch(intent)
                            }
                        }
                    } else {
                        if (hasBasicStoragePermission(context)) {
                            onPermissionGranted()
                        } else {
                            basicLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        }
                    }
                },
                showRationale = showRationale,
                hasPermissions = { hasRequiredPermissions(context) }
            )
        }
    }

    data class PermissionRequestState(
        val requestPermissions: () -> Unit,
        val showRationale: Boolean,
        val hasPermissions: () -> Boolean
    )
}
