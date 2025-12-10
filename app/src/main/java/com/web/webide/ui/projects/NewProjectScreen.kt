package com.web.webide.ui.projects

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.WorkspaceManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// 定义项目类型枚举
enum class ProjectType {
    NORMAL, // 普通 Web
    WEBAPP, // Android WebApp (本地 HTML)
    WEBSITE // 网页套壳 (在线 URL)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(navController: NavController) {
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("com.example.myapp") }
    // 新增：目标网址
    var targetUrl by remember { mutableStateOf("https://") }

    var selectedType by remember { mutableStateOf(ProjectType.NORMAL) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("新建项目") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- 1. 项目类型选择 ---
                Text(
                    "选择模板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TemplateSelectionCard(
                        Modifier.weight(1f),
                        "Web",
                        Icons.Default.Language,
                        selectedType == ProjectType.NORMAL
                    ) { selectedType = ProjectType.NORMAL }
                    TemplateSelectionCard(
                        Modifier.weight(1f),
                        "WebApp",
                        Icons.Default.Android,
                        selectedType == ProjectType.WEBAPP
                    ) { selectedType = ProjectType.WEBAPP }
                    TemplateSelectionCard(
                        Modifier.weight(1f),
                        "套壳",
                        Icons.Default.Public,
                        selectedType == ProjectType.WEBSITE
                    ) { selectedType = ProjectType.WEBSITE }
                }

                AnimatedContent(targetState = selectedType, label = "desc") { type ->
                    Text(
                        text = when (type) {
                            ProjectType.NORMAL -> "创建标准的 HTML/CSS/JS 项目，适用于纯前端开发。"
                            ProjectType.WEBAPP -> "创建包含 Native 接口的本地 WebApp，支持构建 APK。"
                            ProjectType.WEBSITE -> "输入一个网址 (如 Google)，直接打包成 App，无需写代码。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }

                // --- 2. 基本信息 ---
                Text(
                    "项目信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        if (selectedType != ProjectType.NORMAL) {
                            val cleanName =
                                it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase(Locale.ROOT)
                            if (cleanName.isNotEmpty()) packageName = "com.example.$cleanName"
                        }
                    },
                    label = { Text("项目名称") },
                    placeholder = { Text("") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 包名输入框 (仅 App 模式显示)
                AnimatedVisibility(visible = selectedType != ProjectType.NORMAL) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("包名 (Package Name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 网址输入框 (仅套壳模式显示)
                AnimatedVisibility(visible = selectedType == ProjectType.WEBSITE) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = targetUrl,
                            onValueChange = { targetUrl = it },
                            label = { Text("目标网址 (URL)") },
                            placeholder = { Text("https://www.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- 3. 提交按钮 ---
                Button(
                    onClick = {
                        // --- 1. 统一校验逻辑 ---

                        // 校验项目名称
                        if (projectName.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("请输入项目名称") }
                            return@Button // 显示完提示后，终止后续操作
                        }

                        // 校验包名 (非普通项目)
                        if (selectedType != ProjectType.NORMAL && packageName.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("请输入包名") }
                            return@Button
                        }

                        // 校验网址 (套壳项目)
                        if (selectedType == ProjectType.WEBSITE && targetUrl.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("请输入目标网址") }
                            return@Button
                        }

                        // --- 2. 校验通过，执行创建 ---
                        isLoading = true
                        createNewProject(
                            context, projectName, packageName, targetUrl, selectedType,
                            onSuccess = {
                                isLoading = false
                                scope.launch {
                                    // 显示成功提示 (Short 持续时间较短)
                                    // 注意：这里使用 Short 让它快点结束，或者你可以用 delay 控制
                                    val job = launch {
                                        snackbarHostState.showSnackbar(
                                            message = "创建成功！",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    // 延迟 800ms 让用户看到提示，然后退出
                                    // 如果不加延迟直接 pop，Snackbar 会随页面销毁而看不见
                                    kotlinx.coroutines.delay(800)
                                    navController.popBackStack()
                                    job.cancel() // 退出时取消 Snackbar
                                }
                            },
                            onError = { errorMsg ->
                                isLoading = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("创建失败: $errorMsg")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !isLoading,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("创建中...")
                    } else {
                        Text(text = "创建项目", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

@Composable
fun TemplateSelectionCard(modifier: Modifier = Modifier, title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    val containerColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)

    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = containerColor), border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)) {
        Column(Modifier.padding(vertical = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun createNewProject(
    context: Context, projectName: String, packageName: String, targetUrl: String, type: ProjectType,
    onSuccess: () -> Unit, onError: (String) -> Unit
) {
    val projectDir = File(WorkspaceManager.getWorkspacePath(context), projectName)
    GlobalScope.launch(Dispatchers.IO) {
        try {
            if (projectDir.exists()) { withContext(Dispatchers.Main) { onError("项目已存在") }; return@launch }
            projectDir.mkdirs()

            when (type) {
                ProjectType.NORMAL -> createNormalStructure(projectDir)
                ProjectType.WEBAPP -> createWebAppStructure(projectDir, packageName)
                ProjectType.WEBSITE -> createWebsiteStructure(projectDir, packageName, targetUrl)
            }
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "未知错误") } }
    }
}

private fun createNormalStructure(projectDir: File) {
    val css = File(projectDir, "css").apply { mkdirs() }
    val js = File(projectDir, "js").apply { mkdirs() }
    File(projectDir, "index.html").writeText(ProjectTemplates.normalIndexHtml)
    File(css, "style.css").writeText(ProjectTemplates.normalCss)
    File(js, "script.js").writeText(ProjectTemplates.normalJs)
}

private fun createWebAppStructure(projectDir: File, packageName: String) {
    val assets = File(projectDir, "src/main/assets").apply { mkdirs() }
    File(assets, "js").mkdirs(); File(assets, "css").mkdirs()
    File(assets, "index.html").writeText(ProjectTemplates.webAppIndexHtml)
    File(assets, "js/api.js").writeText(ProjectTemplates.apiJs)
    File(assets, "js/index.js").writeText(ProjectTemplates.webAppIndexJs)
    File(assets, "css/style.css").writeText(ProjectTemplates.webAppCss)
    File(projectDir, "webapp.json").writeText(ProjectTemplates.getConfigFile(packageName, projectDir.name, "index.html"))
}

// 🔥 新增：创建套壳项目结构
private fun createWebsiteStructure(projectDir: File, packageName: String, targetUrl: String) {
    // 即使是套壳，我们也创建一个假的 index.html，防止 APK 模板因为找不到入口而崩溃
    // 并且这个 HTML 会自动跳转到目标网址，作为双重保险
    val assets = File(projectDir, "src/main/assets").apply { mkdirs() }
    File(assets, "index.html").writeText("""
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"><title>Redirecting...</title></head>
        <body>
            <p>Loading...</p>
            <script>window.location.href = "$targetUrl";</script>
        </body>
        </html>
    """.trimIndent())

    // 生成 webapp.json，重点是 targetUrl
    File(projectDir, "webapp.json").writeText(ProjectTemplates.getConfigFile(packageName, projectDir.name, targetUrl))
}