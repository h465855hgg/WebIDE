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
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.components.DirectorySelector
import kotlinx.coroutines.launch // 导入
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSelectionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 获取协程作用域
    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    
    // ✅ 改动 2: 只有在需要时才显示文件选择器，而不是默认就显示
    var showFileSelector by remember { mutableStateOf(false) } 
    
    // 如果是首次进入（路径为默认值），则自动弹出选择器
    LaunchedEffect(Unit) {
        // ✅ 修复：不再对比 "/storage/emulated/0"，而是对比 getDefaultPath
        if (WorkspaceManager.getWorkspacePath(context) == WorkspaceManager.getDefaultPath(context)) {
            showFileSelector = true
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
                text = "请选择一个目录作为WebIDE的工作空间，所有项目将在此目录下创建和管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { showFileSelector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, "选择目录")
                Spacer(modifier = Modifier.width(8.dp))
                Text("更改工作目录") // 文本可以改为"更改"
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // 保存新工作目录
                    WorkspaceManager.saveWorkspacePath(context, selectedWorkspace)

                    // ✅ 新增：重置日志路径，让其重新跟随工作目录
                    scope.launch {
                        LogConfigRepository(context).resetLogPath()
                    }

                    navController.navigate("project_list") {
                        popUpTo("workspace_selection") {
                            inclusive = true
                        }
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