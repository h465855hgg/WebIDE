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
