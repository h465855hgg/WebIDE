package com.web.webide.core.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import androidx.core.content.edit

object WorkspaceManager {

    private const val PREFS_NAME = "webide_prefs"
    private const val KEY_WORKSPACE_PATH = "workspace_path"

    private fun getDefaultPath(context: Context): String {
        // 优先使用外部私有目录 /sdcard/Android/data/com.web.webide/files
        // 这样在用户没选目录前，日志不会乱跑
        return context.getExternalFilesDir(null)?.absolutePath
            ?: context.filesDir.absolutePath
    }

    fun getWorkspacePath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // ✅ 使用动态获取的默认值
        return prefs.getString(KEY_WORKSPACE_PATH, null) ?: getDefaultPath(context)
    }

    fun getWorkspacePathFlow(context: Context): Flow<String> = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultPath = getDefaultPath(context) // ✅ 获取默认值

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

    // saveWorkspacePath 保持不变
    fun saveWorkspacePath(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_WORKSPACE_PATH, path) }
    }
}