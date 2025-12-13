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
import com.web.webide.core.utils.PermissionManager
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
    var targetUrl by remember { mutableStateOf("https://") }

    var selectedType by remember { mutableStateOf(ProjectType.NORMAL) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var packageError by remember { mutableStateOf<String?>(null) }
    fun validatePackageName(name: String): String? {
        if (name.isBlank()) return "包名不能为空"
        if (name.any { it.isDigit() }) return "包名不能包含数字" // 禁止数字
        if (name.any { it.code > 127 }) return "包名不能包含中文" // 禁止中文

        // 正则严格校验结构
        val regex = Regex("^[a-zA-Z_]+(\\.[a-zA-Z_]+)+$")
        if (!name.matches(regex)) return "格式不完整 (例: com.test.app)"

        return null
    }
    // 获取当前工作空间路径
    val workspacePath = WorkspaceManager.getWorkspacePath(context)

    // 权限请求状态
    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = {
            scope.launch { snackbarHostState.showSnackbar("权限已获取，请再次点击创建") }
        },
        onPermissionDenied = {
            scope.launch { snackbarHostState.showSnackbar("无权限，无法在 SD 卡创建项目") }
        }
    )

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

            // 显示路径，方便调试
            Text(
                text = "存储位置: $workspacePath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                TemplateSelectionCard(Modifier.weight(1f), "Web", Icons.Default.Language, selectedType == ProjectType.NORMAL) { selectedType = ProjectType.NORMAL }
                TemplateSelectionCard(Modifier.weight(1f), "WebApp", Icons.Default.Android, selectedType == ProjectType.WEBAPP) { selectedType = ProjectType.WEBAPP }
                TemplateSelectionCard(Modifier.weight(1f), "套壳", Icons.Default.Public, selectedType == ProjectType.WEBSITE) { selectedType = ProjectType.WEBSITE }
            }

            AnimatedContent(targetState = selectedType, label = "desc") { type ->
                Text(
                    text = when (type) {
                        ProjectType.NORMAL -> "创建标准的 HTML/CSS/JS 项目。"
                        ProjectType.WEBAPP -> "创建包含 Native 接口的本地 WebApp。"
                        ProjectType.WEBSITE -> "输入网址，直接打包成 App。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }

            // --- 2. 基本信息 ---
            Text("项目信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = projectName,
                onValueChange = {
                    projectName = it
                    if (selectedType != ProjectType.NORMAL) {
                        // 🔥🔥🔥 [修改 2/4] 修改：过滤掉数字，只保留字母
                        // 原代码是: it.filter { c -> c.isLetterOrDigit() }
                        val cleanName = it.filter { c -> c.isLetter() }.lowercase(Locale.ROOT)

                        if (cleanName.isNotEmpty()) {
                            packageName = "com.example.$cleanName"
                            packageError = null // 自动生成时清除错误
                        }
                    }
                },
                label = { Text("项目名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = selectedType != ProjectType.NORMAL) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it

                            // 🔥🔥🔥 [修改 3/4] 新增：实时校验
                            packageError = validatePackageName(it)
                        },
                        label = { Text("包名 (Package Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),

                        // 🔥🔥🔥 [修改 3/4] 新增：绑定错误状态和提示文字
                        isError = packageError != null,
                        supportingText = {
                            if (packageError != null) {
                                Text(packageError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
                }


            AnimatedVisibility(visible = selectedType == ProjectType.WEBSITE) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = { Text("目标网址 (URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // 1. 基础非空检查
                    if (projectName.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("请输入项目名称") }
                        return@Button
                    }
                    if (projectName.contains(Regex("[/\\\\:*?\"<>|]"))) {
                        scope.launch { snackbarHostState.showSnackbar("项目名称不能包含特殊字符") }
                        return@Button
                    }

                    // 2. 网址检查
                    if (selectedType == ProjectType.WEBSITE && targetUrl.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("请输入目标网址") }
                        return@Button
                    }

                    // 🔥🔥🔥 [修改 4/4] 必须放在这里！在创建项目之前！🔥🔥🔥
                    // 包名严格校验（禁止中文、禁止数字、必须完整）
                    if (selectedType != ProjectType.NORMAL) {
                        // 注意：这里需要你上面定义的 validatePackageName 函数
                        val error = validatePackageName(packageName)
                        if (error != null) {
                            packageError = error // 让输入框变红
                            scope.launch { snackbarHostState.showSnackbar("包名错误: $error") }
                            return@Button // ❌ 拦截成功，不再往下执行
                        }
                    }

                    // 3. 权限检查
                    if (PermissionManager.isSystemPermissionRequiredForPath(context, workspacePath) &&
                        !PermissionManager.hasRequiredPermissions(context)) {
                        permissionState.requestPermissions()
                        return@Button
                    }

                    // 4. 一切检查通过，才开始创建
                    isLoading = true
                    createNewProject(
                        context, projectName, packageName, targetUrl, selectedType,
                        onSuccess = {
                            isLoading = false
                            scope.launch {
                                val job = launch { snackbarHostState.showSnackbar("创建成功！", duration = SnackbarDuration.Short) }
                                kotlinx.coroutines.delay(800)
                                navController.popBackStack()
                                job.cancel()
                            }
                        },
                        onError = { errorMsg ->
                            isLoading = false
                            scope.launch { snackbarHostState.showSnackbar("创建失败: $errorMsg") }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !isLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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
    // 1. 获取当前配置的路径字符串
    val savedPath = WorkspaceManager.getWorkspacePath(context)
    val appPackageName = context.packageName

    GlobalScope.launch(Dispatchers.IO) {
        try {
            val projectParentDir: File

            // 🔥🔥🔥 核心判断：如果路径里包含包名，说明是私有目录，强制走系统API 🔥🔥🔥
            if (savedPath.contains("/Android/data/$appPackageName")) {
                // 不要信任 savedPath 字符串！直接找系统要最新的对象！
                // 这一步是 100% 成功的关键，系统会保证返回的 File 对象有写入权限
                val systemPrivateDir = context.getExternalFilesDir(null)

                if (systemPrivateDir == null) {
                    withContext(Dispatchers.Main) { onError("系统错误：无法访问私有存储 (ExternalFilesDir is null)") }
                    return@launch
                }
                projectParentDir = systemPrivateDir
            } else {
                // 如果是用户选的 SD 卡其他目录（非私有），才使用字符串构建 File
                projectParentDir = File(savedPath)
            }

            // 2. 目标项目文件夹
            val projectDir = File(projectParentDir, projectName)

            // 3. 打印调试信息 (如果失败，能在报错里看到真实路径)
            println("正在创建项目于: ${projectDir.absolutePath}")

            // 4. 检查是否存在
            if (projectDir.exists()) {
                withContext(Dispatchers.Main) { onError("该项目已存在") }
                return@launch
            }

            // 5. 暴力创建目录
            // 先尝试直接创建
            var success = projectDir.mkdirs()

            // 如果失败，尝试先创建父级（针对某些极端情况）
            if (!success) {
                if (!projectParentDir.exists()) {
                    projectParentDir.mkdirs() // 尝试创建 /files 目录
                }
                success = projectDir.mkdirs() // 再试一次
            }

            // 6. 最终审判
            if (!success && !projectDir.exists()) {
                // 获取具体的错误原因很困难，但通常是权限或路径问题
                withContext(Dispatchers.Main) {
                    onError("无法创建目录！\n尝试路径: ${projectDir.absolutePath}\n请确认不是在根目录或受保护的系统目录。")
                }
                return@launch
            }

            // 7. 开始写入文件 (逻辑保持不变)
            when (type) {
                ProjectType.NORMAL -> createNormalStructure(projectDir)
                ProjectType.WEBAPP -> createWebAppStructure(projectDir, packageName)
                ProjectType.WEBSITE -> createWebsiteStructure(projectDir, packageName, targetUrl)
            }

            withContext(Dispatchers.Main) { onSuccess() }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onError("发生未知异常: ${e.javaClass.simpleName}\n${e.message}")
            }
        }
    }
}
private fun createNormalStructure(projectDir: File) {
    // 确保子目录存在
    val css = File(projectDir, "css"); css.mkdirs()
    val js = File(projectDir, "js"); js.mkdirs()

    // 写入文件 (使用 safe write)
    safeWrite(File(projectDir, "index.html"), ProjectTemplates.normalIndexHtml)
    safeWrite(File(css, "style.css"), ProjectTemplates.normalCss)
    safeWrite(File(js, "script.js"), ProjectTemplates.normalJs)
}

private fun createWebAppStructure(projectDir: File, packageName: String) {
    val assets = File(projectDir, "src/main/assets")
    assets.mkdirs()
    File(assets, "js").mkdirs()
    File(assets, "css").mkdirs()

    safeWrite(File(assets, "index.html"), ProjectTemplates.webAppIndexHtml)
    safeWrite(File(assets, "js/api.js"), ProjectTemplates.apiJs)
    safeWrite(File(assets, "js/index.js"), ProjectTemplates.webAppIndexJs)
    safeWrite(File(assets, "css/style.css"), ProjectTemplates.webAppCss)

    // 生成配置
    safeWrite(File(projectDir, "webapp.json"), ProjectTemplates.getConfigFile(packageName, projectDir.name, "index.html"))
}

private fun createWebsiteStructure(projectDir: File, packageName: String, targetUrl: String) {
    val assets = File(projectDir, "src/main/assets")
    assets.mkdirs()

    safeWrite(File(assets, "index.html"), """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"><title>Redirecting...</title></head>
        <body><script>window.location.href = "$targetUrl";</script></body>
        </html>
    """.trimIndent())

    safeWrite(File(projectDir, "webapp.json"), ProjectTemplates.getConfigFile(packageName, projectDir.name, targetUrl))
}

// 辅助方法：安全写入，防止父目录不存在导致崩溃
private fun safeWrite(file: File, content: String) {
    try {
        if (!file.parentFile!!.exists()) {
            file.parentFile!!.mkdirs()
        }
        file.writeText(content)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}