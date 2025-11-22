package com.web.webide

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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
import com.web.webide.ui.preview.WebPreviewScreen
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import com.web.webide.ui.projects.ProjectListScreen
import com.web.webide.ui.projects.NewProjectScreen
import com.web.webide.ui.projects.WorkspaceSelectionScreen
import com.web.webide.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

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

    val startDestination = if (WorkspaceManager.getWorkspacePath(context) != "/storage/emulated/0") {
        "project_list"
    } else {
        "workspace_selection"
    }

    val themeState by themeViewModel.themeState.collectAsState()

    NavHost(navController = navController, startDestination = startDestination) {
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
                // ✅ 核心修复: 更新 lambda 以匹配新的5参数签名
                onThemeChange = { mode: Int, theme: Int, color: Color, isMonet: Boolean, isCustom: Boolean ->
                    themeViewModel.saveThemeConfig(mode, theme, color, isMonet, isCustom)
                },
                onLogConfigChange = { enabled, filePath ->
                    scope.launch {
                        logConfigRepository.saveLogConfig(enabled, filePath)
                    }
                }
            )
        }
    }
}