package com.web.webide.ui.projects

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(navController: NavController) {
    var projectName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建项目") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("项目名称") },
                placeholder = { Text("输入项目名称，将作为文件夹名称") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "将使用空项目模板创建项目，包含：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• HTML、CSS、JavaScript 文件\n• 项目配置文件\n• Android Web App 构建配置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        isLoading = true
                        createProjectFromTemplate(
                            context = context,
                            projectName = projectName,
                            onSuccess = {
                                isLoading = false
                                navController.popBackStack()
                            },
                            onError = { error ->
                                isLoading = false
                                Toast.makeText(context, "创建失败: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "请输入项目名称", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && projectName.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建中...")
                } else {
                    Text("创建项目")
                }
            }
        }
    }
}

private fun createProjectFromTemplate(
    context: Context,
    projectName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(workspacePath, projectName)

    GlobalScope.launch(Dispatchers.IO) {
        try {
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }

            // 此处省略了解压zip的逻辑，直接创建文件
            createBasicProjectStructure(projectDir, projectName)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "项目 '$projectName' 创建成功", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onError(e.message ?: "未知错误")
            }
        }
    }
}

private fun createBasicProjectStructure(projectDir: File, projectName: String) {
    val cssDir = File(projectDir, "css")
    val jsDir = File(projectDir, "js")
    val webideDir = File(projectDir, ".WebIDE")

    cssDir.mkdirs()
    jsDir.mkdirs()
    webideDir.mkdirs()

    val htmlFile = File(projectDir, "index.html")
    htmlFile.writeText("""
        <!DOCTYPE html>
        <html>
        <head>
            <title>$projectName</title>
            <link rel="stylesheet" href="css/style.css">
        </head>
        <body>
            <h1>$projectName</h1>
            <p>欢迎使用 WebIDE 创建的项目</p>
            <script src="js/index.js"></script>
        </body>
        </html>
    """.trimIndent())

    val cssFile = File(cssDir, "style.css")
    cssFile.writeText("""
        body {
            font-family: Arial, sans-serif;
            text-align: center;
            padding: 50px;
            background-color: #f0f0f0;
        }

        h1 {
            color: #333;
            margin-bottom: 20px;
        }

        p {
            color: #666;
        }
    """.trimIndent())

    val jsFile = File(jsDir, "index.js")
    jsFile.writeText("""
        console.log('$projectName - JavaScript loaded');

        document.addEventListener('DOMContentLoaded', function() {
            console.log('页面加载完成');

            const h1 = document.querySelector('h1');
            if (h1) {
                h1.addEventListener('click', function() {
                    this.style.color = this.style.color === 'red' ? '#333' : 'red';
                });
            }
        });
    """.trimIndent())

    val propertiesFile = File(webideDir, "application.properties")
    propertiesFile.writeText("""
        # WebIDE Android Web App 配置
        app.name=$projectName
        app.version=1.0.0
        app.package=com.example.$projectName
        app.versionCode=1

        # 构建配置
        build.targetSdk=34
        build.minSdk=21
        build.compileSdk=34

        # 权限配置
        permissions=INTERNET,ACCESS_NETWORK_STATE

        # WebView配置
        webview.allowFileAccess=true
        webview.allowContentAccess=true
        webview.domStorageEnabled=true
        webview.databaseEnabled=true
        webview.javaScriptEnabled=true

        # 启动配置
        launch.url=file:///android_asset/www/index.html
        launch.theme=@android:style/Theme.Light.NoTitleBar

        # 其他配置
        orientation=portrait
        fullscreen=false
    """.trimIndent())
}