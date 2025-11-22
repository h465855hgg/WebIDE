package com.web.webide.ui

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.web.webide.core.utils.ThemeDataStoreRepository
import com.web.webide.core.utils.ThemeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val repository: ThemeDataStoreRepository) : ViewModel() {

    val themeState: StateFlow<ThemeState> = repository.themeStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        // ✅ 初始值包含 isMonetEnabled
        initialValue = ThemeState(0, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, false, Color(0xFF6750A4), false)
    )

    /**
     * ✅ 核心修复: 更新函数签名以匹配 Repository
     */
    fun saveThemeConfig(
        selectedModeIndex: Int,
        selectedThemeIndex: Int,
        customColor: Color,
        isMonetEnabled: Boolean,
        isCustom: Boolean
    ) {
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