


package com.web.webide.ui.settings
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.ui.components.DirectorySelector
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.core.utils.ThemeState
import com.web.webide.core.utils.LogConfigState
import com.web.webide.ui.welcome.themeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    currentThemeState: ThemeState,
    logConfigState: LogConfigState,
    onThemeChange: (modeIndex: Int, themeIndex: Int, customColor: Color, isMonet: Boolean, isCustom: Boolean) -> Unit,
    onLogConfigChange: (enabled: Boolean, filePath: String) -> Unit
) {
    val context = LocalContext.current

    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    var showFileSelector by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLogPathSelector by remember { mutableStateOf(false) }

    val currentModeIndex = currentThemeState.selectedModeIndex
    val currentThemeIndex = currentThemeState.selectedThemeIndex
    val isMonetEnabled = currentThemeState.isMonetEnabled

    // 计算当前显示的主题名称
    val currentThemeName = when {
        isMonetEnabled -> "动态色彩 (壁纸取色)"
        currentThemeState.isCustomTheme -> "自定义颜色"
        else -> themeColors.getOrNull(currentThemeIndex)?.name ?: "默认主题"
    }

    // 计算预览色块颜色
    val previewColor = when {
        isMonetEnabled -> MaterialTheme.colorScheme.primary
        currentThemeState.isCustomTheme -> currentThemeState.customColor
        else -> themeColors.getOrNull(currentThemeIndex)?.primaryColor ?: MaterialTheme.colorScheme.primary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 主题设置卡片 ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("主题设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    // 当前主题预览
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier
                                .size(40.dp)
                                .background(previewColor, MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = currentThemeName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = when (currentModeIndex) { 0 -> "跟随系统"; 1 -> "浅色模式"; 2 -> "深色模式"; else -> "" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 动态色彩开关 (仅在 Android 12+ 显示)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("动态色彩", style = MaterialTheme.typography.bodyLarge)
                                Text("使用壁纸颜色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isMonetEnabled,
                                onCheckedChange = { enabled ->
                                    // 仅切换 Monet 状态，保持其他参数不变
                                    onThemeChange(currentModeIndex, currentThemeIndex, currentThemeState.customColor, enabled, currentThemeState.isCustomTheme)
                                }
                            )
                        }
                    }

                    // 更改主题按钮
                    // 【关键修复】：始终显示按钮，而不是用 AnimatedVisibility 隐藏。
                    // 这样用户即使开着动态色彩，也能点进来选主题，选完自动关闭动态色彩。
                    Button(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Palette, "更改主题")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isMonetEnabled) "切换预设/自定义主题" else "更改预设主题")
                    }
                }
            }

            // --- 工作目录设置 (保持原样) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("工作目录设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("当前工作目录:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = selectedWorkspace, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(onClick = { showFileSelector = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.FolderOpen, "更改工作目录"); Spacer(modifier = Modifier.width(8.dp)); Text("更改工作目录")
                    }
                }
            }

            // --- 日志设置卡片 (保持原样) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("日志设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("启用日志记录", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = logConfigState.isLogEnabled,
                            onCheckedChange = { enabled ->
                                onLogConfigChange(enabled, logConfigState.logFilePath)
                                Toast.makeText(context, if (enabled) "日志已启用" else "日志已禁用", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = logConfigState.isLogEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HorizontalDivider()
                            Text("日志文件路径:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = logConfigState.logFilePath, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Button(onClick = { showLogPathSelector = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.FolderOpen, "更改日志路径"); Spacer(modifier = Modifier.width(8.dp)); Text("更改日志路径")
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 弹窗处理 ---

    if (showFileSelector) {
        DirectorySelector(
            initialPath = selectedWorkspace,
            onPathSelected = { path ->
                selectedWorkspace = path
                WorkspaceManager.saveWorkspacePath(context, path)
                showFileSelector = false
                Toast.makeText(context, "工作目录已更新", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showFileSelector = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { modeIndex, themeIndex, customColor, isCustom ->
                // 【关键修复】：应用参考代码的逻辑。
                // 如果用户在这里手动选了主题，说明他想覆盖“动态色彩”。
                // 强制将 isMonetEnabled 设为 false。
                onThemeChange(
                    modeIndex,
                    themeIndex,
                    customColor,
                    false, // <--- 强制关闭 Monet
                    isCustom
                )
                Toast.makeText(context, "主题已更新", Toast.LENGTH_SHORT).show()
            },
            initialModeIndex = currentModeIndex,
            // 如果当前是自定义主题，让 UI 选中最后一个位置
            initialThemeIndex = if (currentThemeState.isCustomTheme) themeColors.size else currentThemeIndex,
        )
    }

    if (showLogPathSelector) {
        DirectorySelector(
            initialPath = logConfigState.logFilePath,
            onPathSelected = { path ->
                onLogConfigChange(logConfigState.isLogEnabled, path)
                showLogPathSelector = false
                Toast.makeText(context, "日志路径已更新", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showLogPathSelector = false }
        )
    }
}