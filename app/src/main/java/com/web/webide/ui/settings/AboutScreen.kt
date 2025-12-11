package com.web.webide.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.web.webide.R
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 定义开发者数据模型
data class Developer(
    val name: String,
    val role: String,
    val description: String,
    val color: Color,
    val url: String = ""
)

// 全局缓存，实现真正的“秒加载”，只要 App 不杀进程，第二次进来不需要解析 JSON
private var cachedLibraries: List<Library>? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("webide_settings", Context.MODE_PRIVATE)
    }

    var showAuthorNote by remember {
        mutableStateOf(prefs.getBoolean("show_author_note", true))
    }

    // --- 数据加载优化 ---
    // 使用 mutableStateOf 而不是 produceState，配合全局缓存
    var libraries by remember { mutableStateOf(cachedLibraries ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedLibraries == null) }
    var selectedLib by remember { mutableStateOf<Library?>(null) }

    LaunchedEffect(Unit) {
        if (cachedLibraries == null) {
            // 只有在没有缓存时才去 IO 线程加载
            withContext(Dispatchers.IO) {
                // 手动构建 Libs，比 produceLibraries 更可控
                val loaded = Libs.Builder()
                    .withContext(context)
                    .build()
                    .libraries
                    .sortedBy { it.name.lowercase() } // 提前排好序

                cachedLibraries = loaded // 存入缓存
                libraries = loaded
                isLoading = false
            }
        }
    }

    val teamMembers = remember {
        listOf(
            Developer("h465855hgg", "Lead", "Maintainer", Color(0xFF009688), "https://github.com/h465855hgg"),
            Developer("Claude", "UI", "Design", Color(0xFFD97757)),
            Developer("Gemini", "Arch", "Core", Color(0xFF4E8CFF)),
            Developer("DeepSeek", "Logic", "Editor", Color(0xFF6C5CE7))
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. App 头部
            item { AppHeaderSection() }

            // 2. 开发团队
            item {
                SectionTitle("WebIDE Team")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(teamMembers) { dev -> DeveloperChip(dev) }
                }

                AnimatedVisibility(
                    visible = showAuthorNote,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AuthorNoteCard(
                        onClose = {
                            showAuthorNote = false
                            prefs.edit { putBoolean("show_author_note", false) }
                        }
                    )
                }
            }

            // 3. 开源协议标题
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Licenses")
            }

            // 4. 库列表内容 - 核心优化点
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            } else if (libraries.isEmpty()) {
                item {
                    Text("暂无信息", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                // 【关键优化】：直接使用 itemsIndexed，让 LazyColumn 进行视图复用
                // 不要外层包 Card，而是把 Card 的样式应用到每一个 Item 上
                itemsIndexed(
                    items = libraries,
                    key = { _, lib -> lib.uniqueId } // 加上 Key 优化性能
                ) { index, lib ->
                    // 动态计算圆角，模拟“一组大卡片”的效果
                    val shape = when {
                        libraries.size == 1 -> RoundedCornerShape(16.dp) // 只有一个
                        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) // 第一个
                        index == libraries.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp) // 最后一个
                        else -> RectangleShape // 中间的
                    }

                    // 模拟卡片背景
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp), // 保持左右间距
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = shape
                    ) {
                        Column {
                            ImprovedLibraryListItem(lib = lib, onClick = { selectedLib = lib })

                            // 只有不是最后一个时，才显示分割线
                            if (index < libraries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Copyright © 2025 WebIDE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (selectedLib != null) {
        LibraryDetailDialog(lib = selectedLib!!, onDismiss = { selectedLib = null })
    }
}
// --- 原有组件保持不变 ---
@Composable
private fun AuthorNoteCard(onClose: () -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "本项目由人类开发者与 AI 协作开发，探索代码生成的无限可能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable { onClose() }
                )
            }
        }
    }
}
@Composable
private fun AppHeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_w),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_code),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WebIDE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "v0.0.9 Beta",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun DeveloperChip(dev: Developer) {
    val context = LocalContext.current

    Surface(
        onClick = {
            if (dev.url.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, dev.url.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dev.color)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = dev.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = dev.role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ImprovedLibraryListItem(lib: Library, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lib.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val author = lib.developers.firstOrNull()?.name ?: lib.organization?.name
            val license = lib.licenses.firstOrNull()?.name

            val subtitle = buildString {
                if (!author.isNullOrBlank()) append(author)
                if (!author.isNullOrBlank() && !license.isNullOrBlank()) append("  •  ")
                if (!license.isNullOrBlank()) append(license)
            }

            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!lib.artifactVersion.isNullOrEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "v${lib.artifactVersion}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun LibraryDetailDialog(lib: Library, onDismiss: () -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val licenseText = remember(lib) {
        if (lib.licenses.isNotEmpty()) {
            lib.licenses.joinToString("\n\n") { license ->
                val content = license.licenseContent ?: license.url ?: "See project website for license."
                content
            }
        } else {
            "No license info."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Column(
                    modifier = Modifier
                        .offset(y = (-40).dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = lib.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = lib.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val version = lib.artifactVersion
                    if (version != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "v$version",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-20).dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "License",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(licenseText)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = licenseText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(scrollState)
                        )
                    }
                }

                if (!lib.website.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, lib.website!!.toUri())
                                    context.startActivity(intent)
                                } catch (e: Exception) { e.printStackTrace() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Visit Website")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}