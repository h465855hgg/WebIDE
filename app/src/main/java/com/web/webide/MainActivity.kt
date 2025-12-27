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


package com.web.webide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.web.webide.core.utils.*
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.ThemeViewModelFactory
import com.web.webide.ui.editor.components.TextMateInitializer
import com.web.webide.ui.theme.MyComposeApplicationTheme
import com.web.webide.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getExternalFilesDir("logs")

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        window.isNavigationBarContrastEnforced = false


        initApp()
    }

    private fun initApp() {
        // 初始化基础组件
        TextMateInitializer.initialize(this)


        setContent {
            val context = LocalContext.current
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
            val themeState by themeViewModel.themeState.collectAsState()

            // 日志配置
            val logConfigRepository = remember { LogConfigRepository(context) }
            val logConfigState by logConfigRepository.logConfigFlow.collectAsState(
                initial = LogConfigState()
            )
            
            // ✅ 核心改动 1: 将依赖项改为 logConfigState，以便配置变化时能重新初始化
            LaunchedEffect(logConfigState) {
                if (logConfigState.isLoaded) {
                    // ✅ 改动: 调用 updateConfig 来动态更新配置
                    LogCatcher.updateConfig(logConfigState)
                    LogCatcher.i("MainActivity", "日志系统配置已更新 - 启用: ${logConfigState.isLogEnabled}")
                }
            }

            if (!themeState.isLoaded || !logConfigState.isLoaded) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                MyComposeApplicationTheme(themeState = themeState) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // ✅ 核心改动 2: 根据 WelcomePreferences 初始化状态
                        var showWelcomeScreen by remember {
                            mutableStateOf(!WelcomePreferences.isWelcomeCompleted(context))
                        }

                        AnimatedContent(
                            targetState = showWelcomeScreen,
                            label = "ScreenTransition",
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 500))
                            }
                        ) { isWelcomeTarget ->
                            if (isWelcomeTarget) {
                                WelcomeScreen(
                                    themeViewModel = themeViewModel,
                                    onWelcomeFinished = { 
                                        // ✅ 核心改动 3: 在欢迎流程结束时，标记为已完成
                                        WelcomePreferences.setWelcomeCompleted(context)
                                        LogCatcher.i("MainActivity", "欢迎流程完成，进入主应用")
                                        showWelcomeScreen = false 
                                    }
                                )
                            } else {
                                App(
                                    themeViewModel = themeViewModel,
                                    logConfigRepository = logConfigRepository,
                                    logConfigState = logConfigState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

