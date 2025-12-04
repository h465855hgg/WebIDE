package com.web.webide.ui.projects

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// 定义项目类型枚举
enum class ProjectType {
    NORMAL, // 普通 Web
    WEBAPP  // Android WebApp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(navController: NavController) {
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("com.example.myapp") }
    var selectedType by remember { mutableStateOf(ProjectType.NORMAL) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                // 【关键点1】添加 animateContentSize，使内部元素(如包名输入框)出现时，布局会自动平滑过渡
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
                text = "选择模板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplateSelectionCard(
                    modifier = Modifier.weight(1f),
                    title = "普通 Web",
                    icon = Icons.Default.Language,
                    isSelected = selectedType == ProjectType.NORMAL,
                    onClick = { selectedType = ProjectType.NORMAL }
                )
                TemplateSelectionCard(
                    modifier = Modifier.weight(1f),
                    title = "Web App",
                    icon = Icons.Default.Android,
                    isSelected = selectedType == ProjectType.WEBAPP,
                    onClick = { selectedType = ProjectType.WEBAPP }
                )
            }

            // 【关键点2】使用 AnimatedContent 平滑切换说明文字
            AnimatedContent(
                targetState = selectedType,
                label = "description_anim",
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                        .togetherWith(fadeOut(animationSpec = tween(300)))
                }
            ) { type ->
                Text(
                    text = if (type == ProjectType.NORMAL)
                        "创建标准的 HTML/CSS/JS 项目结构，适用于普通网页开发。"
                    else
                        "创建包含 Android 原生接口 (Toast, 震动等) 的项目，可构建为 APK。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }

            // --- 2. 基本信息 ---
            Text(
                text = "项目信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = projectName,
                onValueChange = {
                    projectName = it
                    if (selectedType == ProjectType.WEBAPP) {
                        val cleanName = it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase(Locale.ROOT)
                        if (cleanName.isNotEmpty()) {
                            packageName = "com.example.$cleanName"
                        }
                    }
                },
                label = { Text("项目名称") },
                placeholder = { Text("MyProject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            // 【关键点3】使用 AnimatedVisibility 控制包名输入框的展开和收起
            AnimatedVisibility(
                visible = selectedType == ProjectType.WEBAPP,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("包名 (Package Name)") },
                        placeholder = { Text("com.example.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 3. 提交按钮 ---
            Button(
                onClick = {
                    if (projectName.isBlank()) {
                        Toast.makeText(context, "请输入项目名称", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedType == ProjectType.WEBAPP && packageName.isBlank()) {
                        Toast.makeText(context, "请输入包名", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    createNewProject(
                        context = context,
                        projectName = projectName,
                        packageName = packageName,
                        type = selectedType,
                        onSuccess = {
                            isLoading = false
                            navController.popBackStack()
                        },
                        onError = { error ->
                            isLoading = false
                            Toast.makeText(context, "创建失败: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp), // 稍微加高一点，更有现代感
                enabled = !isLoading,
                shape = MaterialTheme.shapes.medium
            ) {
                // 按钮内容的过渡动画
                AnimatedContent(
                    targetState = isLoading,
                    label = "button_content",
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                ) { loading ->
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在创建...")
                        }
                    } else {
                        Text(
                            text = if (selectedType == ProjectType.WEBAPP) "创建 WebApp 项目" else "创建普通项目",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            // 底部留白，防止到底部很难看
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TemplateSelectionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit // 这是外部传入的点击逻辑
) {
    // 颜色动画
    val borderColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "border",
        animationSpec = tween(300)
    )
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "container",
        animationSpec = tween(300)
    )

    // 如果你需要 interactionSource (通常用于处理点击涟漪或拖拽状态)，
    // 应该在这里定义，而不是在 onClick 里。
    // 如果不需要特殊处理，可以直接省略这个变量，Card 内部会自动处理。
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        // 1. 正确调用外部传入的 onClick
        onClick = onClick,

        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),

        // 2. interactionSource 应该作为参数传递（可选）
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 20.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ... createNewProject 和其他逻辑代码保持不变 ...
private fun createNewProject(
    context: Context,
    projectName: String,
    packageName: String,
    type: ProjectType,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val workspacePath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(workspacePath, projectName)

    GlobalScope.launch(Dispatchers.IO) {
        try {
            if (projectDir.exists()) {
                withContext(Dispatchers.Main) { onError("项目文件夹已存在") }
                return@launch
            }
            projectDir.mkdirs()

            // 根据类型生成不同的结构
            when (type) {
                ProjectType.NORMAL -> createNormalStructure(projectDir)
                ProjectType.WEBAPP -> createWebAppStructure(projectDir, packageName)
            }

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

// 创建普通 Web 项目结构 (扁平化)
private fun createNormalStructure(projectDir: File) {
    val cssDir = File(projectDir, "css")
    val jsDir = File(projectDir, "js")

    cssDir.mkdirs()
    jsDir.mkdirs()

    File(projectDir, "index.html").writeText(ProjectTemplates.normalIndexHtml)
    File(cssDir, "style.css").writeText(ProjectTemplates.normalCss)
    File(jsDir, "script.js").writeText(ProjectTemplates.normalJs)
}

// 创建 WebApp 项目结构 (Assets 嵌套结构 + Native Bridge)
private fun createWebAppStructure(projectDir: File, packageName: String) {
    // 1. 标准 Android 目录结构: src/main/assets
    val assetsDir = File(projectDir, "src/main/assets")

    File(assetsDir, "js").mkdirs()
    File(assetsDir, "css").mkdirs()

    // 2. 写入资源文件
    File(assetsDir, "index.html").writeText(ProjectTemplates.webAppIndexHtml)
    File(assetsDir, "js/api.js").writeText(ProjectTemplates.apiJs) // 核心 Native API
    File(assetsDir, "js/index.js").writeText(ProjectTemplates.webAppIndexJs)
    File(assetsDir, "css/style.css").writeText(ProjectTemplates.webAppCss)

    // 3. 写入项目配置 (供 APK 构建器使用)
    File(projectDir, "webapp.json").writeText(ProjectTemplates.getConfigFile(packageName, projectDir.name))
}