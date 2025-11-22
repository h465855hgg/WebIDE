package com.web.webide.ui.preview

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // ✅ 1. Add import for context
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import com.web.webide.core.utils.WorkspaceManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(folderName: String, navController: NavController, viewModel: EditorViewModel) {
    var htmlContent by remember { mutableStateOf("<p>加载中...</p>") }
    val context = LocalContext.current // ✅ Get the context

    // ▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼ 核心修正 ▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼
    // val projectPath = "/storage/emulated/0/WebProject/$folderName" // 删掉这行错误的代码

    // ✅ 使用 WorkspaceManager 动态获取正确的工作目录，并拼接成完整的项目路径
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectPath = File(workspacePath, folderName).absolutePath
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ 核心修正 ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

    LaunchedEffect(key1 = projectPath) {
        // ViewModel现在会接收到正确的路径
        htmlContent = viewModel.buildHtmlContentFromProject(projectPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览: $folderName") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            factory = { factoryContext ->
                WebView(factoryContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            },
            update = { webView ->
                // 使用 file:// 协议作为基准 URL 可以帮助加载本地资源（如果未来需要）
                val baseUrl = "file://$projectPath/"
                webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
            }
        )
    }
}