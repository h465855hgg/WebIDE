package com.web.webide.core.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 管理欢迎页面显示状态的工具类
 */
object WelcomePreferences {
    private const val PREFS_NAME = "welcome_prefs"
    private const val KEY_WELCOME_COMPLETED = "welcome_completed"
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 检查是否已完成欢迎流程
     */
    fun isWelcomeCompleted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_WELCOME_COMPLETED, false)
    }
    
    /**
     * 标记欢迎流程已完成
     */
    fun setWelcomeCompleted(context: Context) {
        getPreferences(context).edit().putBoolean(KEY_WELCOME_COMPLETED, true).apply()
        LogCatcher.i("WelcomePreferences", "欢迎流程已标记为完成")
    }
    
    /**
     * 重置欢迎流程状态（用于测试）
     */
    fun resetWelcome(context: Context) {
        getPreferences(context).edit().putBoolean(KEY_WELCOME_COMPLETED, false).apply()
        LogCatcher.i("WelcomePreferences", "欢迎流程状态已重置")
    }
}
