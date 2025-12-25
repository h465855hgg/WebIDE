/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb // 必须引入这个扩展函数
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webide_theme_settings")

data class ThemeState(
    val selectedModeIndex: Int,
    val selectedThemeIndex: Int,
    val isMonetEnabled: Boolean,
    val isCustomTheme: Boolean,
    val customColor: Color,
    val isLoaded: Boolean = false
)

class ThemeDataStoreRepository(private val context: Context) {

    private object PreferencesKeys {
        val SELECTED_MODE = intPreferencesKey("selected_mode")
        val SELECTED_THEME = intPreferencesKey("selected_theme")
        val IS_MONET_ENABLED = booleanPreferencesKey("is_monet_enabled")
        val IS_CUSTOM = booleanPreferencesKey("is_custom")
        val CUSTOM_COLOR = longPreferencesKey("custom_color")
    }

    val themeStateFlow: Flow<ThemeState> = context.dataStore.data
        .map { preferences ->
            val modeIndex = preferences[PreferencesKeys.SELECTED_MODE] ?: 0
            val themeIndex = preferences[PreferencesKeys.SELECTED_THEME] ?: 0
            val isMonet = preferences[PreferencesKeys.IS_MONET_ENABLED] ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            val isCustom = preferences[PreferencesKeys.IS_CUSTOM] ?: false

            // ✅ [修复点1] 读取逻辑：先转 Int，再用 Color(Int) 构造函数
            val customColorValue = preferences[PreferencesKeys.CUSTOM_COLOR] ?: 0xFF6750A4
            val decodedColor = Color(customColorValue.toInt())

            LogCatcher.d("ThemeDebug_Repo", "Repo读取: Monet=$isMonet, Custom=$isCustom, DecodedColorArg=${decodedColor.toArgb()}")

            ThemeState(
                selectedModeIndex = modeIndex,
                selectedThemeIndex = themeIndex,
                isMonetEnabled = isMonet,
                isCustomTheme = isCustom,
                customColor = decodedColor,
                isLoaded = true
            )
        }

    suspend fun saveThemeConfig(
        selectedModeIndex: Int,
        selectedThemeIndex: Int,
        customColor: Color,
        isMonetEnabled: Boolean,
        isCustom: Boolean
    ) {
        // ✅ [修复点2] 写入逻辑：使用 .toArgb() 获取标准的 Int 值
        // 这样可以屏蔽 Color 内部是 Float 还是 Int 的差异
        val colorInt = customColor.toArgb()

        LogCatcher.w("ThemeDebug_Repo", "Repo写入: Monet=$isMonetEnabled, Custom=$isCustom, ColorInt=$colorInt")

        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODE] = selectedModeIndex
            preferences[PreferencesKeys.SELECTED_THEME] = selectedThemeIndex
            preferences[PreferencesKeys.IS_MONET_ENABLED] = isMonetEnabled
            preferences[PreferencesKeys.IS_CUSTOM] = isCustom
            preferences[PreferencesKeys.CUSTOM_COLOR] = colorInt.toLong() // Int 转 Long 保存
        }
    }
}