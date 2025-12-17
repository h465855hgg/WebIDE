package com.web.webide.ui.projects

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.FileOutputStream
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
    // 🔥 新增：图标路径状态
    var iconPath by remember { mutableStateOf("") }

    var selectedType by remember { mutableStateOf(ProjectType.NORMAL) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var packageError by remember { mutableStateOf<String?>(null) }

    // 🔥 新增：图片选择器
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            iconPath = uri.toString()
        }
    }

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
                        // 过滤掉数字，只保留字母
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
                        onValueChange = { input ->
                            // 🔥🔥🔥 核心修改 1：输入拦截过滤 🔥🔥🔥
                            // 逻辑：遍历输入的每一个字符，只有符合条件的才保留
                            // 条件：必须是 字母(a-z/A-Z) 或 点(.) 或 下划线(_)
                            val filtered = input.filter { char ->
                                char.isLetter() || char == '.' || char == '_'
                            }

                            // 更新状态
                            packageName = filtered

                            // 继续执行校验（检查格式是否完整，比如是否有点号分隔）
                            packageError = validatePackageName(filtered)
                        },
                        label = { Text("包名 (Package Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),

                        // 🔥🔥🔥 核心修改 2：键盘属性优化 🔥🔥🔥
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii, // 告诉输入法尽量显示英文键盘
                            autoCorrect = false, // 🔴 必须关闭！否则输入 com 会被自动纠正为 Come 等单词
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ),

                        isError = packageError != null,
                        supportingText = {
                            if (packageError != null) {
                                Text(packageError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )


                    // 🔥🔥🔥 新增：图标选择输入框 🔥🔥🔥
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = iconPath,
                        onValueChange = { iconPath = it },
                        label = { Text("应用图标 (可选)") },
                        placeholder = { Text("选择图片或输入绝对路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                // 启动相册选择器
                                imageLauncher.launch("image/*")
                            }) {
                                Icon(Icons.Default.Image, "从相册选择")
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

                    // 包名严格校验（禁止中文、禁止数字、必须完整）
                    if (selectedType != ProjectType.NORMAL) {
                        val error = validatePackageName(packageName)
                        if (error != null) {
                            packageError = error // 让输入框变红
                            scope.launch { snackbarHostState.showSnackbar("包名错误: $error") }
                            return@Button
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
                    // 🔥 传入 iconPath
                    createNewProject(
                        context, projectName, packageName, targetUrl, iconPath, selectedType,
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
    context: Context, projectName: String, packageName: String, targetUrl: String, iconPathSource: String, type: ProjectType,
    onSuccess: () -> Unit, onError: (String) -> Unit
) {
    // 1. 获取当前配置的路径字符串
    val savedPath = WorkspaceManager.getWorkspacePath(context)
    val appPackageName = context.packageName

    GlobalScope.launch(Dispatchers.IO) {
        try {
            val projectParentDir: File

            // 核心判断：如果路径里包含包名，说明是私有目录，强制走系统API
            if (savedPath.contains("/Android/data/$appPackageName")) {
                val systemPrivateDir = context.getExternalFilesDir(null)

                if (systemPrivateDir == null) {
                    withContext(Dispatchers.Main) { onError("系统错误：无法访问私有存储 (ExternalFilesDir is null)") }
                    return@launch
                }
                projectParentDir = systemPrivateDir
            } else {
                projectParentDir = File(savedPath)
            }

            // 2. 目标项目文件夹
            val projectDir = File(projectParentDir, projectName)
            println("正在创建项目于: ${projectDir.absolutePath}")

            // 3. 检查是否存在
            if (projectDir.exists()) {
                withContext(Dispatchers.Main) { onError("该项目已存在") }
                return@launch
            }

            // 4. 暴力创建目录
            var success = projectDir.mkdirs()
            if (!success) {
                if (!projectParentDir.exists()) {
                    projectParentDir.mkdirs()
                }
                success = projectDir.mkdirs()
            }

            if (!success && !projectDir.exists()) {
                withContext(Dispatchers.Main) {
                    onError("无法创建目录！\n尝试路径: ${projectDir.absolutePath}\n请确认不是在根目录或受保护的系统目录。")
                }
                return@launch
            }

            // 🔥🔥🔥 5. 处理图标复制逻辑 🔥🔥🔥
            var iconFileName = "" // 默认为空，表示没有图标
            if (type != ProjectType.NORMAL && iconPathSource.isNotBlank()) {
                try {
                    val destIconFile = File(projectDir, "icon.png")
                    val uri = Uri.parse(iconPathSource)

                    // 判断是 Content Uri (相册选择) 还是 File Path (手动输入)
                    if (iconPathSource.startsWith("content://")) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destIconFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        // 认为是绝对路径
                        val sourceFile = File(iconPathSource)
                        if (sourceFile.exists()) {
                            sourceFile.copyTo(destIconFile, overwrite = true)
                        }
                    }

                    if (destIconFile.exists()) {
                        iconFileName = "icon.png" // 复制成功，标记文件名
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 图标复制失败不应该阻断项目创建，只打印日志即可
                    println("图标复制失败: ${e.message}")
                }
            }

            // 6. 开始写入文件
            when (type) {
                ProjectType.NORMAL -> createNormalStructure(projectDir)
                // 将 iconFileName 传给结构生成函数
                ProjectType.WEBAPP -> createWebAppStructure(projectDir, packageName, iconFileName)
                ProjectType.WEBSITE -> createWebsiteStructure(projectDir, packageName, targetUrl, iconFileName)
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
    val css = File(projectDir, "css"); css.mkdirs()
    val js = File(projectDir, "js"); js.mkdirs()

    safeWrite(File(projectDir, "index.html"), ProjectTemplates.normalIndexHtml)
    safeWrite(File(css, "style.css"), ProjectTemplates.normalCss)
    safeWrite(File(js, "script.js"), ProjectTemplates.normalJs)
}

// 🔥 新增 icon 参数
private fun createWebAppStructure(projectDir: File, packageName: String, icon: String) {
    val assets = File(projectDir, "src/main/assets")
    assets.mkdirs()
    File(assets, "js").mkdirs()
    File(assets, "css").mkdirs()

    safeWrite(File(assets, "index.html"), ProjectTemplates.webAppIndexHtml)
    safeWrite(File(assets, "js/api.js"), ProjectTemplates.apiJs)
    safeWrite(File(assets, "js/index.js"), ProjectTemplates.webAppIndexJs)
    safeWrite(File(assets, "css/style.css"), ProjectTemplates.webAppCss)

    // 生成配置，这里假设你的 ProjectTemplates.getConfigFile 支持 icon 参数，或者你需要手动拼装 JSON
    // 如果 ProjectTemplates 不支持，建议修改它或者在这里手动覆盖
    val configContent = ProjectTemplates.getConfigFile(packageName, projectDir.name, "index.html")

    // 简单的 JSON 插入逻辑 (如果 template 没有支持的话)
    val finalConfig = if (icon.isNotEmpty()) {
        insertIconToJson(configContent, icon)
    } else {
        configContent
    }

    safeWrite(File(projectDir, "webapp.json"), finalConfig)
}

// 🔥 新增 icon 参数
private fun createWebsiteStructure(projectDir: File, packageName: String, targetUrl: String, icon: String) {
    val assets = File(projectDir, "src/main/assets")
    assets.mkdirs()

    safeWrite(File(assets, "index.html"), """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"><title>Redirecting...</title></head>
        <body><script>window.location.href = "$targetUrl";</script></body>
        </html>
    """.trimIndent())

    val configContent = ProjectTemplates.getConfigFile(packageName, projectDir.name, targetUrl)
    // 简单的 JSON 插入逻辑
    val finalConfig = if (icon.isNotEmpty()) {
        insertIconToJson(configContent, icon)
    } else {
        configContent
    }

    safeWrite(File(projectDir, "webapp.json"), finalConfig)
}

// 辅助函数：向 JSON 字符串中插入 icon 字段 (防止 Template 类没更新)
private fun insertIconToJson(json: String, iconPath: String): String {
    // 找到最后一个大括号，在它之前插入 icon 字段
    val lastBraceIndex = json.lastIndexOf('}')
    if (lastBraceIndex != -1) {
        val prefix = json.substring(0, lastBraceIndex).trimEnd()
        // 检查前面是否有逗号，如果没有且前面有内容，可能需要补逗号(简化处理：假设template生成的总是标准的)
        // 比较安全的做法是直接追加
        val needsComma = !prefix.endsWith("{") && !prefix.endsWith(",")
        val comma = if (needsComma) "," else ""
        return "$prefix$comma\n  \"icon\": \"$iconPath\"\n}"
    }
    return json
}

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