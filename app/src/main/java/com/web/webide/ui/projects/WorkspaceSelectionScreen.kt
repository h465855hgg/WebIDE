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
import com.web.webide.core.utils.PermissionManager
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.components.DirectorySelector
import kotlinx.coroutines.launch
import java.io.File

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

    // ✅ 逻辑修复：只有首次未配置时才自动弹窗，而不是每次路径为默认值都弹
    LaunchedEffect(Unit) {
        if (!WorkspaceManager.isWorkspaceConfigured(context)) {
            // 这里可以设为 true 自动弹窗，也可以 false 让用户手动点
            showFileSelector = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("WebIDE") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text("选择工作目录", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("所有项目文件将存储在此目录中。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 提示私有目录优势
            if (selectedWorkspace.contains("Android/data")) {
                Text("使用App私有目录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top=8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { showFileSelector = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("更改目录")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前:", style = MaterialTheme.typography.bodySmall)
                    Text(selectedWorkspace, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // ✅ 核心修复：根据路径智能判断是否需要权限
                    if (PermissionManager.isSystemPermissionRequiredForPath(context, selectedWorkspace)) {
                        permissionState.requestPermissions()
                    } else {
                        // 私有目录，无需权限，直接保存
                        saveAndNavigate(context, selectedWorkspace, navController, scope)
                    }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("确认并继续")
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

private fun saveAndNavigate(context: android.content.Context, path: String, navController: NavController, scope: kotlinx.coroutines.CoroutineScope) {
    // 保存并强制初始化目录
    WorkspaceManager.saveWorkspacePath(context, path)

    scope.launch {
        try { LogConfigRepository(context).resetLogPath() } catch (_: Exception) {}
    }
    navController.navigate("project_list") {
        popUpTo("workspace_selection") { inclusive = true }
    }
}