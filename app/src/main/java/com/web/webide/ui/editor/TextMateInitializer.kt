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

package com.web.webide.ui.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.tm4e.core.registry.IThemeSource

object TextMateInitializer {
    @Volatile
    private var isInitialized = false
    private val mutex = Mutex()
    private val callbacks = mutableListOf<() -> Unit>()

    const val THEME_LIGHT = "quietlight"
    const val THEME_DARK = "darcula"

    fun isReady() = isInitialized

    @OptIn(DelicateCoroutinesApi::class)
    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        
        synchronized(callbacks) {
            if (onComplete != null) callbacks.add(onComplete)
        }

        GlobalScope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (isInitialized) {
                    notifyCallbacks()
                    return@withLock
                }

                try {
                    val appContext = context.applicationContext
                    // Prevent duplicate provider registration
                    try {
                        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(appContext.assets))
                    } catch (_: Exception) {}

                    val themeRegistry = ThemeRegistry.getInstance()
                    val themes = mapOf(
                        THEME_LIGHT to "textmate/$THEME_LIGHT.json",
                        THEME_DARK to "textmate/$THEME_DARK.json"
                    )

                    themes.forEach { (name, path) ->
                        try {
                            FileProviderRegistry.getInstance().tryGetInputStream(path)?.use { inputStream ->
                                themeRegistry.loadTheme(
                                    ThemeModel(
                                        IThemeSource.fromInputStream(inputStream, path, null),
                                        name
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Load grammars
                    try {
                        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    isInitialized = true
                    notifyCallbacks()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun notifyCallbacks() {
        synchronized(callbacks) {
            val iter = callbacks.iterator()
            while (iter.hasNext()) {
                val cb = iter.next()
                try {
                    GlobalScope.launch(Dispatchers.Main) { cb() }
                } catch (_: Exception) {}
                iter.remove()
            }
        }
    }
}
