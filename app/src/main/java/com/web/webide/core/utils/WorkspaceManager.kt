package com.web.webide.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

object WorkspaceManager {

    private const val PREFS_NAME = "webide_prefs"
    private const val KEY_WORKSPACE_PATH = "workspace_path"
    private const val KEY_IS_CONFIGURED = "is_workspace_configured"

    fun getDefaultPath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
        return dir?.absolutePath ?: context.filesDir.absolutePath
    }

    /**
     * 获取工作目录（带自动纠错功能）
     */
    fun getWorkspacePath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(KEY_WORKSPACE_PATH, null)

        // 1. 如果没存过，返回默认
        if (savedPath.isNullOrBlank()) {
            return getDefaultPath(context)
        }

        // 🔥🔥🔥 修复点 2：更稳健的路径检查逻辑 🔥🔥🔥
        // 之前的逻辑依赖绝对路径字符串匹配，容易因为 /sdcard 与 /storage/emulated/0 的差异导致误判
        // 现在的逻辑：只要路径包含 "Android/data"，就检查它是否包含"当前App的包名"
        if (savedPath.contains("/Android/data/")) {
            val packageName = context.packageName
            // 如果路径里连包名都不包含，说明这个路径肯定是其他App的（或者旧包名的），我们没有权限，必须重置
            if (!savedPath.contains(packageName)) {
                android.util.Log.e("WorkspaceManager", "检测到失效路径(包名不匹配): $savedPath，重置为默认")
                val validPath = getDefaultPath(context)
                saveWorkspacePath(context, validPath) // 自动保存纠正后的路径
                return validPath
            }
        }

        return savedPath
    }

    fun isWorkspaceConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 只要这个值为 true，就说明用户点击过“确认并继续”
        return prefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    fun getWorkspacePathFlow(context: Context): Flow<String> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WORKSPACE_PATH) {
                trySend(getWorkspacePath(context))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getWorkspacePath(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveWorkspacePath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_WORKSPACE_PATH, path)
            // ✅ 关键：设置为 true，表示用户已完成初始化向导
            putBoolean(KEY_IS_CONFIGURED, true)
        }
        ensurePathExists(context, path)
    }

    fun ensurePathExists(context: Context, path: String): Boolean {
        val file = File(path)
        if (file.exists() && file.isDirectory) return true

        try {
            val defaultPath = getDefaultPath(context)
            // 简单的字符串包含检查，兼容性更好
            if (path.contains(context.packageName)) {
                return file.mkdirs() || file.exists()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file.mkdirs() || file.exists()
    }
}