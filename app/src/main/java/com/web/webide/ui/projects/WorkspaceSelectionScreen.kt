/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.web.webide.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.LogConfigRepository
import com.web.webide.core.utils.PermissionManager
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.components.DirectorySelector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 初始化时获取当前路径
    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    var showFileSelector by remember { mutableStateOf(false) }

    // 权限请求回调
    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = {
            saveAndNavigate(context, selectedWorkspace, navController, scope)
        },
        onPermissionDenied = { /* 可选：提示用户 */ }
    )

    // ✅✅✅ 修复点 1：进入页面时，检查是否已经配置过。
    // 如果已配置，直接跳转到主页，不再让用户重新选择。
    LaunchedEffect(Unit) {
        if (WorkspaceManager.isWorkspaceConfigured(context)) {
            navController.navigate("project_list") {
                // 清除返回栈，防止按返回键回到这里
                popUpTo("workspace_selection") { inclusive = true }
            }
        } else {
            // 只有完全未配置（第一次安装）时，才考虑是否自动弹窗
            // 这里建议设为 false，让用户先看界面文字，手动点按钮再弹窗，体验更好
            showFileSelector = false
        }
    }

    // 只有在未配置时才渲染 UI 内容，避免跳转时的闪烁（可选优化）
    if (!WorkspaceManager.isWorkspaceConfigured(context)) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("WebIDE") }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "选择工作目录",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "所有项目文件将存储在此目录中。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedWorkspace.contains("Android/data")) {
                    Text(
                        "使用App私有目录 (推荐)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showFileSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("更改目录")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("当前:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            selectedWorkspace,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (PermissionManager.isSystemPermissionRequiredForPath(
                                context,
                                selectedWorkspace
                            )
                        ) {
                            permissionState.requestPermissions()
                        } else {
                            saveAndNavigate(context, selectedWorkspace, navController, scope)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("确认并继续")
                }
            }
        }
    }

    if (showFileSelector) {
        DirectorySelector(
            initialPath = selectedWorkspace,
            onPathSelected = { selectedWorkspace = it; showFileSelector = false },
            onDismissRequest = { showFileSelector = false }
        )
    }
}

private fun saveAndNavigate(
    context: android.content.Context,
    path: String,
    navController: NavController,
    scope: kotlinx.coroutines.CoroutineScope
) {
    WorkspaceManager.saveWorkspacePath(context, path)

    scope.launch {
        try {
            LogConfigRepository(context).resetLogPath()
        } catch (_: Exception) {
        }
    }
    navController.navigate("project_list") {
        popUpTo("workspace_selection") { inclusive = true }
    }
}