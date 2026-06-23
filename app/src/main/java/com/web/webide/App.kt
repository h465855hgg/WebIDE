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

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.web.webide.core.utils.LogConfigRepository
import com.web.webide.core.utils.LogConfigState
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.editor.CodeEditScreen
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import com.web.webide.ui.preview.WebPreviewScreen
import com.web.webide.ui.projects.NewProjectScreen
import com.web.webide.ui.projects.ProjectConfigScreen
import com.web.webide.ui.projects.ProjectListScreen
import com.web.webide.ui.projects.WorkspaceSelectionScreen
import com.web.webide.ui.settings.AboutScreen
import com.web.webide.ui.settings.SettingsScreen
import com.web.webide.ui.terminal.TerminalScreen
import kotlinx.coroutines.launch

import com.web.webide.ui.welcome.WelcomeScreen
import com.web.webide.ui.editor.doc.JsInterfaceDocScreen

@Composable
fun App(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    logConfigRepository: LogConfigRepository,
    logConfigState: LogConfigState
) {
    val mainViewModel: EditorViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        mainViewModel.initializePermissions(context)
    }

    val startDestination = if (
        WorkspaceManager.getWorkspacePath(context) != WorkspaceManager.getDefaultPath(context)
    ) {
        "project_list"
    } else {
        "workspace_selection"
    }
    val themeState by themeViewModel.themeState.collectAsState()

    // 优化后的动画配置
    // 使用更自然的缓动曲线（类似iOS的平滑感）
    val predictiveEasing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
    val duration = 350 // 稍微缩短时间，让响应更快

    // 进入动画（向前导航）
    val enterTransition = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + fadeIn(
            animationSpec = tween(duration, easing = predictiveEasing)
        )
    }

    // 退出动画（向前导航时）
    val exitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -(it * 0.3f).toInt() },
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + fadeOut(
            targetAlpha = 0.7f,
            animationSpec = tween(duration, easing = predictiveEasing)
        )
    }

    // 返回进入动画（返回时底层页面重新出现）
    val popEnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -(it * 0.3f).toInt() },
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + fadeIn(
            initialAlpha = 0.7f,
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = tween(duration, easing = predictiveEasing)
        )
    }

    // 返回退出动画（返回时当前页面消失）
    val popExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + fadeOut(
            targetAlpha = 0f,
            animationSpec = tween(duration, easing = predictiveEasing)
        ) + scaleOut(
            targetScale = 1.1f,
            animationSpec = tween(duration, easing = predictiveEasing)
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,

        // 应用优化后的动画
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() }
    ) {
        composable("workspace_selection") {
            WorkspaceSelectionScreen(navController = navController)
        }

        composable("project_list") {
            ProjectListScreen(navController = navController)
        }

        composable(
            route = "code_edit/{folderName}",
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName")
            if (folderName != null) {
                CodeEditScreen(folderName, navController, mainViewModel)
            }
        }

        composable(
            route = "preview/{folderName}",
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName")
            if (folderName != null) {
                WebPreviewScreen(folderName, navController)
            }
        }

        composable("new_project") {
            NewProjectScreen(navController = navController)
        }

        composable(
            route = "project_config/{filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath")
            if (filePath != null) {
                ProjectConfigScreen(navController, filePath, mainViewModel)
            }
        }

        composable("settings") {
            SettingsScreen(
                navController = navController,
                currentThemeState = themeState,
                logConfigState = logConfigState,
                onThemeChange = { modeIndex, themeIndex, customColor, isMonet, isCustom ->
                    themeViewModel.saveThemeConfig(modeIndex, themeIndex, customColor, isMonet, isCustom)
                },
                onLogConfigChange = { enabled, path ->
                    scope.launch { logConfigRepository.saveLogConfig(enabled, path) }
                },
                editorViewModel = mainViewModel
            )
        }
        
        composable("welcome") {
            WelcomeScreen(
                themeViewModel = themeViewModel,
                onWelcomeFinished = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("js_interface_doc") {
            JsInterfaceDocScreen(navController)
        }

        composable("about") {
            AboutScreen(navController = navController)
        }
        composable("terminal") {
            TerminalScreen(navController = navController, themeViewModel = themeViewModel)
        }
    }
}

fun NavController.safeNavigate(route: String) {
    val current = currentBackStackEntry?.destination?.route
    if (current != route) {
        navigate(route) {
            launchSingleTop = true
        }
    }
}