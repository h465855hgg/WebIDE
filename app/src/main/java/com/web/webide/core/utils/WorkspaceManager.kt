package com.web.webide.core.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.core.content.edit
import java.io.File

object WorkspaceManager {

    private const val PREFS_NAME = "webide_prefs"
    private const val KEY_WORKSPACE_PATH = "workspace_path"
    private const val KEY_IS_CONFIGURED = "is_workspace_configured" // ✅ 新增标记

    fun getDefaultPath(context: Context): String {
        return context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
    }

    fun getWorkspacePath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WORKSPACE_PATH, null) ?: getDefaultPath(context)
    }

    // ✅ 新增：检查用户是否已经确认过工作空间
    fun isWorkspaceConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    fun getWorkspacePathFlow(context: Context): Flow<String> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultPath = getDefaultPath(context)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == KEY_WORKSPACE_PATH) {
                val path = sharedPreferences.getString(key, null) ?: defaultPath
                trySend(path)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getString(KEY_WORKSPACE_PATH, null) ?: defaultPath)

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun saveWorkspacePath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_WORKSPACE_PATH, path)
            putBoolean(KEY_IS_CONFIGURED, true) // ✅ 保存时标记为已配置
        }

        // 确保目录存在
        try {
            File(path).mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}