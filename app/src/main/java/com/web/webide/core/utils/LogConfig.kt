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

// LogConfigRepository.kt

// LogConfigRepository.kt

package com.web.webide.core.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webide_log_config")


data class LogConfigState(
    val isLogEnabled: Boolean = true,
    val logFilePath: String = "",
    val isLoaded: Boolean = false
)

class LogConfigRepository(private val context: Context) {


    private object PreferencesKeys {
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
        val LOG_FILE_PATH = stringPreferencesKey("log_file_path")
    }

    /**
     * ✅ 核心修复：使用 combine 合并 DataStore 和 WorkspaceManager 的流
     * 这样无论是修改了日志设置，还是修改了工作目录，这里都会重新计算
     */
    val logConfigFlow: Flow<LogConfigState> = context.dataStore.data
        .combine(WorkspaceManager.getWorkspacePathFlow(context)) { preferences, workspacePath ->

            // 1. 获取动态的工作目录
            // workspacePath 是从 Flow 实时传过来的

            // 2. 构建默认的日志目录：工作目录/logs
            val defaultLogPath = File(workspacePath, "logs").absolutePath

            // 3. 确定最终路径：
            // 如果 DataStore 中用户手动指定了路径（savedPath 不为空），则优先使用手动指定的
            // 如果没有手动指定（null 或空），则自动跟随工作目录
            val savedPath = preferences[PreferencesKeys.LOG_FILE_PATH]
            val finalPath = if (savedPath.isNullOrEmpty()) defaultLogPath else savedPath

            LogConfigState(
                isLogEnabled = preferences[PreferencesKeys.LOG_ENABLED] ?: true,
                logFilePath = finalPath,
                isLoaded = true
            )
        }

    suspend fun saveLogConfig(isEnabled: Boolean, filePath: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_ENABLED] = isEnabled

            // 可选优化：如果用户保存的路径和当前的默认路径一致，则存为 null/empty，
            // 这样以后修改工作目录时，日志路径能继续自动跟随。
            val currentWorkspace = WorkspaceManager.getWorkspacePath(context)
            val defaultPath = File(currentWorkspace, "logs").absolutePath

            if (filePath == defaultPath) {
                preferences.remove(PreferencesKeys.LOG_FILE_PATH)
            } else {
                preferences[PreferencesKeys.LOG_FILE_PATH] = filePath
            }
        }
    }
    suspend fun resetLogPath() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LOG_FILE_PATH)
        }
    }
}
// LogCatcher 保持不变 ...
/**
 * 增强的日志捕获器，支持文件输出和开关控制
 */
object LogCatcher {

    // ✅ 修复后，类型引用变为更简单的 LogConfigState
    private var logConfig: LogConfigState? = null

    @Volatile
    private var isInitialized = false

    @JvmStatic
    fun updateConfig(config: LogConfigState) {
        logConfig = config
        isInitialized = true
        i("LogCatcher", "日志系统已配置 - 启用: ${config.isLogEnabled}, 路径: ${config.logFilePath}")
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        if (shouldLog()) {
            android.util.Log.d(tag, message)
            writeToFile("DEBUG", tag, message)
        }
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (shouldLog()) {
            android.util.Log.i(tag, message)
            writeToFile("INFO", tag, message)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        if (shouldLog()) {
            android.util.Log.w(tag, message)
            writeToFile("WARN", tag, message)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String, message: String, exception: Exception? = null) {
        android.util.Log.e(tag, message, exception)
        if (shouldLog()) {
            writeToFile("ERROR", tag, "$message${exception?.let { " - ${it.message}" } ?: ""}")
        }
    }

    private fun shouldLog(): Boolean {
        return isInitialized && logConfig?.isLogEnabled == true
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun writeToFile(level: String, tag: String, message: String) {
        val config = logConfig ?: return
        if (!config.isLogEnabled) return

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val logDir = File(config.logFilePath)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val logFile = File(logDir, "webide.log")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val logEntry = "[$timestamp] [$level] [$tag] $message\n"
                logFile.appendText(logEntry)
            } catch (e: Exception) {
                android.util.Log.e("LogCatcher", "写入日志文件失败: ${e.message}")
            }
        }
    }

}