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


package com.web.webide.ui

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.web.webide.core.utils.ThemeDataStoreRepository
import com.web.webide.core.utils.ThemeState
import com.web.webide.core.utils.LogCatcher // 导入日志工具
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val repository: ThemeDataStoreRepository) : ViewModel() {

    val themeState: StateFlow<ThemeState> = repository.themeStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeState(0, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, false, Color(0xFF6750A4), false)
    )

    fun saveThemeConfig(
        selectedModeIndex: Int,
        selectedThemeIndex: Int,
        customColor: Color,
        isMonetEnabled: Boolean,
        isCustom: Boolean
    ) {
        // [Debug Log] ViewModel接收层
        LogCatcher.d("ThemeDebug_VM", "ViewModel准备保存: Monet=$isMonetEnabled, Custom=$isCustom, Color=${customColor.value}")

        viewModelScope.launch {
            repository.saveThemeConfig(selectedModeIndex, selectedThemeIndex, customColor, isMonetEnabled, isCustom)
        }
    }
}

class ThemeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ThemeViewModel(ThemeDataStoreRepository(context.applicationContext)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}