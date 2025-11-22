// LogConfigRepository.kt

package com.web.webide.core.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ✅ 核心修复：将 LogConfigState 从 LogConfigRepository 类中移出，成为一个独立的顶层类。
/**
 * 日志配置状态
 */
data class LogConfigState(
    val isLogEnabled: Boolean = true,  // 默认启用日志
    val logFilePath: String = "/sdcard/WebIDE/logs",  // 默认日志路径
    val isLoaded: Boolean = false
)

/**
 * 日志配置管理器
 */
class LogConfigRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webide_log_config")

    // Preferences Keys
    private object PreferencesKeys {
        val LOG_ENABLED = booleanPreferencesKey("log_enabled")
        val LOG_FILE_PATH = stringPreferencesKey("log_file_path")
    }

    /**
     * 日志配置的Flow
     */
    val logConfigFlow: Flow<LogConfigState> = context.dataStore.data
        .map { preferences ->
            LogConfigState(
                isLogEnabled = preferences[PreferencesKeys.LOG_ENABLED] ?: true,
                logFilePath = preferences[PreferencesKeys.LOG_FILE_PATH] ?: "/sdcard/WebIDE/logs",
                isLoaded = true
            )
        }

    /**
     * 保存日志配置
     */
    suspend fun saveLogConfig(isEnabled: Boolean, filePath: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_ENABLED] = isEnabled
            preferences[PreferencesKeys.LOG_FILE_PATH] = filePath
        }
    }

    /**
     * 切换日志开关状态
     */
    suspend fun toggleLogging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_ENABLED] = enabled
        }
    }

    /**
     * 更新日志文件路径
     */
    suspend fun updateLogFilePath(filePath: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOG_FILE_PATH] = filePath
        }
    }
}

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

    @JvmStatic
    fun permission(tag: String, action: String, result: String) {
        if (shouldLog()) {
            val message = "权限操作: $action - $result"
            android.util.Log.i("$tag-Permission", message)
            writeToFile("PERMISSION", tag, message)
        }
    }

    @JvmStatic
    fun fileOperation(tag: String, operation: String, filePath: String, result: String) {
        if (shouldLog()) {
            val message = "文件操作: $operation - $filePath - $result"
            android.util.Log.i("$tag-File", message)
            writeToFile("FILE", tag, message)
        }
    }

    private fun shouldLog(): Boolean {
        return isInitialized && logConfig?.isLogEnabled == true
    }

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

    @JvmStatic
    fun getCurrentConfig(): LogConfigState? {
        return logConfig
    }
}