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

    /**
     * 获取系统分配给当前 App 的绝对私有路径
     * 调用此方法会强制系统在磁盘上创建目录
     */
    fun getDefaultPath(context: Context): String {
        // getExternalFilesDir(null) 会自动创建 /storage/emulated/0/Android/data/包名/files
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

        // 🔥🔥🔥 核心修复：自动纠错僵尸路径 🔥🔥🔥
        // 如果保存的路径是 "Android/data" 下的，但不是当前 App 的包名
        // (比如之前安装的是 debug 版，现在是 release 版，或者反之)
        if (savedPath.contains("/Android/data/")) {
            val currentAppPrivateDir = context.getExternalFilesDir(null)?.parentFile?.parentFile?.absolutePath

            // 如果能获取到当前 App 的私有根目录
            if (currentAppPrivateDir != null) {
                // 如果保存的路径 不以 当前App路径开头
                // 说明这个路径属于别的 App (或者旧版 App)，我们根本没有权限写！
                if (!savedPath.startsWith(currentAppPrivateDir)) {
                    android.util.Log.e("WorkspaceManager", "检测到失效的私有路径: $savedPath，自动重置为默认路径")
                    // 自动修正为正确的默认路径，并保存
                    val validPath = getDefaultPath(context)
                    saveWorkspacePath(context, validPath)
                    return validPath
                }
            }
        }

        return savedPath
    }

    fun isWorkspaceConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            putBoolean(KEY_IS_CONFIGURED, true)
        }
        // 保存时尝试初始化
        ensurePathExists(context, path)
    }

    /**
     * 强力初始化路径
     */
    fun ensurePathExists(context: Context, path: String): Boolean {
        val file = File(path)

        // 1. 如果已经存在且是文件夹，直接成功
        if (file.exists() && file.isDirectory) return true

        // 2. 这里的 path 可能是 getDefaultPath 获取的，也可能是用户手选的
        // 如果它属于当前 App 的私有目录，必须调用系统 API 来“激活”它
        try {
            val defaultPath = getDefaultPath(context) // 这行代码本身就会触发系统创建目录

            // 如果目标路径就是默认路径，或者在默认路径里面
            if (path.startsWith(defaultPath)) {
                // 此时系统应该已经创建好了，再次尝试 mkdirs
                return file.mkdirs() || file.exists()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. 最后尝试常规创建 (针对 SD 卡非私有目录)
        return file.mkdirs() || file.exists()
    }
}