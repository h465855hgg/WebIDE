package com.web.webide

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.web.webide.core.utils.LogConfigRepository
import com.web.webide.core.utils.LogConfigState
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.editor.CodeEditScreen
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import com.web.webide.ui.preview.WebPreviewScreen
import com.web.webide.ui.projects.NewProjectScreen
import com.web.webide.ui.projects.ProjectListScreen
import com.web.webide.ui.projects.WorkspaceSelectionScreen
import com.web.webide.ui.settings.AboutScreen
import com.web.webide.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun App(
    themeViewModel: ThemeViewModel,
    logConfigRepository: LogConfigRepository,
    logConfigState: LogConfigState
) {
    val navController = rememberNavController()
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

    val animationDuration = 400
    val animationSpec = tween<Float>(animationDuration)
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(animationDuration)

    NavHost(
        navController = navController,
        startDestination = startDestination,

        // ✅ 打开动画: 水平滑入 + 垂直向上
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = slideSpec) +
                    slideInVertically(initialOffsetY = { it / 4 }, animationSpec = slideSpec) +
                    fadeIn(animationSpec = animationSpec)
        },
        // 当打开新页面时，旧页面的退出动画（保持水平对称）
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = slideSpec) +
                    fadeOut(animationSpec = animationSpec)
        },

        // ✅ 返回动画: 水平滑出
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = slideSpec) +
                    fadeOut(animationSpec = animationSpec)
        },
        // 当返回时，目标页面的进入动画（保持水平对称）
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }, animationSpec = slideSpec) +
                    fadeIn(animationSpec = animationSpec)
        }
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
                CodeEditScreen(
                    folderName = folderName,
                    navController = navController,
                    viewModel = mainViewModel
                )
            }
        }
        composable(
            route = "preview/{folderName}",
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName")
            if (folderName != null) {
                WebPreviewScreen(
                    folderName = folderName,
                    navController = navController,
                    viewModel = mainViewModel
                )
            }
        }
        composable("new_project") {
            NewProjectScreen(navController = navController)
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                currentThemeState = themeState,
                logConfigState = logConfigState,
                onThemeChange = { mode, theme, color, isMonet, isCustom ->
                    themeViewModel.saveThemeConfig(mode, theme, color, isMonet, isCustom)
                },
                onLogConfigChange = { enabled, filePath ->
                    scope.launch {
                        logConfigRepository.saveLogConfig(enabled, filePath)
                    }
                }
            )
        }
        composable("about") {
            AboutScreen(navController = navController)
        }
    }
}