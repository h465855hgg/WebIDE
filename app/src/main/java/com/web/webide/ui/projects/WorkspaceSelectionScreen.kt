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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.LogConfigRepository
import com.web.webide.core.utils.PermissionManager // 导入
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.components.DirectorySelector
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    var showFileSelector by remember { mutableStateOf(false) }

    // 权限请求状态
    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = {
            // 权限获取成功后，保存并跳转
            saveAndNavigate(context, selectedWorkspace, navController, scope)
        },
        onPermissionDenied = {
            // 可以显示个 Toast 提示用户必须授权才能使用外部目录
        }
    )

    // ✅ 修复逻辑：只在“从未配置过”时才自动弹出选择器
    // 如果用户使用了默认路径，但已经保存过(Configured=true)，则不再自动弹出
    LaunchedEffect(Unit) {
        if (!WorkspaceManager.isWorkspaceConfigured(context)) {
            // 只有第一次安装，且未配置时，可能需要提示
            // 或者你可以选择第一次完全不弹窗，显示默认路径让用户自己点确认
            showFileSelector = false // 建议设为 false，让用户看到界面后再决定改不改
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebIDE") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
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
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "选择工作目录",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "选择工作目录",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "请选择一个目录作为WebIDE的工作空间。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // 提示用户私有目录更稳定
            if (selectedWorkspace.contains("Android/data")) {
                Text(
                    text = "当前使用App私有目录",
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
                Icon(Icons.Default.FolderOpen, "选择目录")
                Spacer(modifier = Modifier.width(8.dp))
                Text("更改工作目录")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前工作目录:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedWorkspace,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, // 允许多行显示长路径
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // ✅ 点击确认时的核心逻辑

                    // 1. 判断该路径是否需要特殊权限
                    if (PermissionManager.isSystemPermissionRequiredForPath(context, selectedWorkspace)) {
                        // 需要权限，发起请求
                        permissionState.requestPermissions()
                    } else {
                        // 不需要权限（是私有目录），直接保存跳转
                        saveAndNavigate(context, selectedWorkspace, navController, scope)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, "确认")
                Spacer(modifier = Modifier.width(8.dp))
                Text("确认并继续")
            }
        }
    }

    if (showFileSelector) {
        DirectorySelector(
            initialPath = selectedWorkspace,
            onPathSelected = { path ->
                selectedWorkspace = path
                showFileSelector = false
            },
            onDismissRequest = {
                showFileSelector = false
            }
        )
    }
}

// 抽取出来的保存并跳转逻辑
private fun saveAndNavigate(
    context: android.content.Context,
    path: String,
    navController: NavController,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // 再次尝试创建目录，确保万无一失
    try {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        if (!dir.canWrite()) {
            // 如果创建了但不可写（极端情况），可能需要报错提示
            // LogCatcher.e("Workspace", "目录不可写: $path")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    WorkspaceManager.saveWorkspacePath(context, path)

    scope.launch {
        LogConfigRepository(context).resetLogPath()
    }

    navController.navigate("project_list") {
        popUpTo("workspace_selection") {
            inclusive = true
        }
    }
}