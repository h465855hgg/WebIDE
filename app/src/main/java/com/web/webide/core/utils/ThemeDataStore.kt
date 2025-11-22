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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webide_theme_settings")

/**
 * 封装应用主题设置的状态
 */
data class ThemeState(
    val selectedModeIndex: Int,
    val selectedThemeIndex: Int,
    val isMonetEnabled: Boolean, // ✅
    val isCustomTheme: Boolean,
    val customColor: Color,
    val isLoaded: Boolean = false
)

/**
 * 主题设置的持久化仓库（DataStore版本）
 */
class ThemeDataStoreRepository(private val context: Context) {

    // Preferences Keys
    private object PreferencesKeys {
        val SELECTED_MODE = intPreferencesKey("selected_mode")
        val SELECTED_THEME = intPreferencesKey("selected_theme")
        val IS_MONET_ENABLED = booleanPreferencesKey("is_monet_enabled") // ✅
        val IS_CUSTOM = booleanPreferencesKey("is_custom")
        val CUSTOM_COLOR = longPreferencesKey("custom_color")
    }

    /**
     * 从DataStore加载主题状态的Flow
     */
    val themeStateFlow: Flow<ThemeState> = context.dataStore.data
        .map { preferences ->
            val modeIndex = preferences[PreferencesKeys.SELECTED_MODE] ?: 0
            val themeIndex = preferences[PreferencesKeys.SELECTED_THEME] ?: 0
            // ✅ 读取新字段，如果设备支持，则默认为 true
            val isMonet = preferences[PreferencesKeys.IS_MONET_ENABLED] ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            val isCustom = preferences[PreferencesKeys.IS_CUSTOM] ?: false
            val customColorValue = preferences[PreferencesKeys.CUSTOM_COLOR] ?: 0xFF6750A4
            
            ThemeState(
                selectedModeIndex = modeIndex,
                selectedThemeIndex = themeIndex,
                isMonetEnabled = isMonet, // ✅
                isCustomTheme = isCustom,
                customColor = Color(customColorValue),
                isLoaded = true
            )
        }

    /**
     * ✅ 保存主题配置，包含所有新参数
     */
    suspend fun saveThemeConfig(
        selectedModeIndex: Int,
        selectedThemeIndex: Int,
        customColor: Color,
        isMonetEnabled: Boolean,
        isCustom: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODE] = selectedModeIndex
            preferences[PreferencesKeys.SELECTED_THEME] = selectedThemeIndex
            preferences[PreferencesKeys.IS_MONET_ENABLED] = isMonetEnabled
            preferences[PreferencesKeys.IS_CUSTOM] = isCustom
            preferences[PreferencesKeys.CUSTOM_COLOR] = customColor.value.toLong()
        }
    }
}