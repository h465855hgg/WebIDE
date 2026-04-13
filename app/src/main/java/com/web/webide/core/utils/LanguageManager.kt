package com.web.webide.core.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.web.webide.R
import java.util.Locale

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_follow_system),
    CHINESE("zh", R.string.language_chinese),
    ENGLISH("en", R.string.language_english),
    HINDI("hi", R.string.language_hindi),
    ARABIC("ar", R.string.language_arabic);

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            val normalizedTag = tag?.takeIf { it.isNotBlank() } ?: return SYSTEM
            return entries.find { language ->
                language != SYSTEM &&
                    (normalizedTag == language.tag || normalizedTag.startsWith("${language.tag}-"))
            } ?: SYSTEM
        }
    }
}

object LanguageManager {
    private const val PREFS_NAME = "WebIDE_Settings"
    private const val KEY_APP_LANGUAGE = "app_language"

    fun getSavedLanguage(context: Context): AppLanguage {
        val tag = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.tag)
        return AppLanguage.fromTag(tag)
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPlatformLanguage(context)
        } else {
            getSavedLanguage(context)
        }
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }

        val language = getSavedLanguage(base)
        if (language == AppLanguage.SYSTEM) {
            return base
        }

        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    fun applySavedLanguage(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val savedLanguage = getSavedLanguage(context)
        val platformLanguage = getPlatformLanguage(context)
        when {
            platformLanguage == AppLanguage.SYSTEM && savedLanguage != AppLanguage.SYSTEM -> {
                setPlatformLanguage(context, savedLanguage)
            }
            platformLanguage != savedLanguage -> {
                persistLanguage(context, platformLanguage)
            }
        }
    }

    fun updateLanguage(context: Context, language: AppLanguage) {
        val savedLanguage = getSavedLanguage(context)
        val currentLanguage = getCurrentLanguage(context)
        if (savedLanguage == language && currentLanguage == language) {
            return
        }

        persistLanguage(context, language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPlatformLanguage(context, language)
            return
        }

        (context as? Activity)?.recreate()
    }

    private fun persistLanguage(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, language.tag)
            .apply()
    }

    private fun getPlatformLanguage(context: Context): AppLanguage {
        val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)
            ?: return getSavedLanguage(context)
        val locales = localeManager.applicationLocales
        val languageTag = if (locales.isEmpty) null else locales[0].toLanguageTag()
        return AppLanguage.fromTag(languageTag)
    }

    private fun setPlatformLanguage(context: Context, language: AppLanguage) {
        val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)
            ?: return
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language.tag)
        }
        if (localeManager.applicationLocales.toLanguageTags() != locales.toLanguageTags()) {
            localeManager.applicationLocales = locales
        }
    }
}
