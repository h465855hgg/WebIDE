package com.web.webide.core.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.web.webide.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguageOption(@StringRes val labelRes: Int, val languageTag: String) {
    SYSTEM(R.string.action_follow_system, ""),
    CHINESE(R.string.language_chinese, "zh"),
    ENGLISH(R.string.language_english, "en");

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguageOption {
            return entries.firstOrNull { it.languageTag == languageTag } ?: SYSTEM
        }
    }
}

object AppLanguageManager {
    private const val PREFS_NAME = "WebIDE_Settings"
    private const val KEY_APP_LANGUAGE = "app_language"

    private val _currentOption = MutableStateFlow(AppLanguageOption.SYSTEM)
    val currentOption: StateFlow<AppLanguageOption> = _currentOption.asStateFlow()

    fun initialize(context: Context) {
        val savedOption = getSavedOption(context)
        _currentOption.value = savedOption
        updateDefaultLocale(context, savedOption)
    }

    fun getSavedOption(context: Context): AppLanguageOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguageOption.fromLanguageTag(
            prefs.getString(KEY_APP_LANGUAGE, AppLanguageOption.SYSTEM.languageTag)
        )
    }

    fun updateLanguage(context: Context, option: AppLanguageOption) {
        if (_currentOption.value == option) return

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_APP_LANGUAGE, option.languageTag)
        }
        updateDefaultLocale(context, option)
        _currentOption.value = option
    }

    fun createLocalizedContext(baseContext: Context, option: AppLanguageOption): Context {
        if (option == AppLanguageOption.SYSTEM) return baseContext

        val locale = Locale.forLanguageTag(option.languageTag)
        val configuration = Configuration(baseContext.resources.configuration)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return baseContext.createConfigurationContext(configuration)
    }

    private fun updateDefaultLocale(context: Context, option: AppLanguageOption) {
        val locale = if (option == AppLanguageOption.SYSTEM) {
            context.resources.configuration.locales[0]
        } else {
            Locale.forLanguageTag(option.languageTag)
        }
        Locale.setDefault(locale)
    }
}
