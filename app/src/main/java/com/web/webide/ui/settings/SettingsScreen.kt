package com.web.webide.ui.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.web.webide.core.utils.LogConfigState
import com.web.webide.core.utils.ThemeState
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.components.DirectorySelector
import com.web.webide.ui.welcome.ColorPickerDialog
import com.web.webide.ui.welcome.themeColors

// 扩展函数解决 luminance 报错
fun Color.luminance(): Float {
    return 0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue
}

private val PRESET_FONTS = listOf(
    "默认字体" to "",
    "JetBrains Mono" to "JetBrainsMono-Regular.ttf",
    "Roboto Mono" to "RobotoMono-Regular.ttf",
    "Source Code Pro" to "SourceCodePro-Regular.ttf",
    "Comic Sans" to "Comic-Sans-MS-Regular-2.ttf"
)

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
    val prefs = remember { context.getSharedPreferences("WebIDE_Editor_Settings", Context.MODE_PRIVATE) }

    val fontSize = prefs.getFloat("editor_font_size", 14f)

    var tabWidth by remember { mutableIntStateOf(prefs.getInt("editor_tab_width", 4)) }
    var wordWrap by remember { mutableStateOf(prefs.getBoolean("editor_word_wrap", false)) }
    var showInvisibles by remember { mutableStateOf(prefs.getBoolean("editor_show_invisibles", false)) }
    var showToolbar by remember { mutableStateOf(prefs.getBoolean("editor_show_toolbar", true)) }
    var fontPath by remember { mutableStateOf(prefs.getString("editor_font_path", "") ?: "") }
    var customSymbols by remember { mutableStateOf(prefs.getString("editor_custom_symbols", "Tab,<,>,/,=,\",',!,?,;,:,{,},[,],(,),+,-,*,_,&,|") ?: "") }

    // 自动保存
    LaunchedEffect(tabWidth, wordWrap, showInvisibles, showToolbar, fontPath, customSymbols) {
        prefs.edit {
            putFloat("editor_font_size", fontSize)
            putInt("editor_tab_width", tabWidth)
            putBoolean("editor_word_wrap", wordWrap)
            putBoolean("editor_show_invisibles", showInvisibles)
            putBoolean("editor_show_toolbar", showToolbar)
            putString("editor_font_path", fontPath)
            putString("editor_custom_symbols", customSymbols)
        }
    }

    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    var showFileSelector by remember { mutableStateOf(false) }
    var showLogPathSelector by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ThemeSettingsItem(
                    currentThemeState = currentThemeState,
                    onThemeChange = onThemeChange,
                    onCustomColorClick = { showColorPicker = true }
                )
            }

            item {
                EditorSettingsItem(
                    tabWidth = tabWidth,
                    onTabWidthChange = { tabWidth = it },
                    wordWrap = wordWrap,
                    onWordWrapChange = { wordWrap = it },
                    showInvisibles = showInvisibles,
                    onShowInvisiblesChange = { showInvisibles = it },
                    showToolbar = showToolbar,
                    onShowToolbarChange = { showToolbar = it },
                    fontPath = fontPath,
                    onFontPathChange = { fontPath = it },
                    customSymbols = customSymbols,
                    onCustomSymbolsChange = { customSymbols = it }
                )
            }

            item {
                Text(
                    text = "常规",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                SimpleSettingsCard(
                    icon = Icons.Outlined.Folder,
                    title = "工作目录",
                    subtitle = selectedWorkspace,
                    onClick = { showFileSelector = true }
                )
            }

            item {
                LogSettingsItem(
                    logConfigState = logConfigState,
                    onLogConfigChange = onLogConfigChange,
                    onPathClick = { showLogPathSelector = true }
                )
            }

            item {
                SimpleSettingsCard(
                    icon = Icons.Outlined.Info,
                    title = "关于",
                    subtitle = "版本信息与介绍",
                    onClick = { navController.navigate("about") }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Dialogs
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

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = currentThemeState.customColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onThemeChange(currentThemeState.selectedModeIndex, themeColors.size, color, false, true)
                showColorPicker = false
            }
        )
    }
}

// ================= 编辑器设置组件 (重构优化版) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsItem(
    tabWidth: Int,
    onTabWidthChange: (Int) -> Unit,
    wordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    showInvisibles: Boolean,
    onShowInvisiblesChange: (Boolean) -> Unit,
    showToolbar: Boolean,
    onShowToolbarChange: (Boolean) -> Unit,
    fontPath: String,
    onFontPathChange: (String) -> Unit,
    customSymbols: String,
    onCustomSymbolsChange: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandDuration = 200
    val textFadeDuration = 200
    val snappyEasing = LinearOutSlowInEasing

    var isFontDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(durationMillis = expandDuration, easing = snappyEasing)
            )
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "编辑器配置",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(tween(textFadeDuration)) + expandVertically(tween(textFadeDuration), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(textFadeDuration)) + shrinkVertically(tween(textFadeDuration), shrinkTowards = Alignment.Top)
                    ) {
                        val displayFont = if(fontPath.isBlank()) "系统默认" else fontPath.substringAfterLast("/")
                        Text(
                            text = "${tabWidth}空格缩进 · $displayFont",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "ArrowRotation",
                    animationSpec = tween(expandDuration)
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }

            // Expanded Content
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(expandDuration)) + expandVertically(animationSpec = tween(expandDuration, easing = snappyEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(textFadeDuration)) + shrinkVertically(animationSpec = tween(textFadeDuration, easing = snappyEasing), shrinkTowards = Alignment.Top)
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // === 1. 缩进设置 (Segmented Style) ===
                    Text("缩进宽度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp) // 按钮之间的间距
                    ) {
                        val options = listOf(2, 4, 8)
                        options.forEach { option ->
                            val isSelected = tabWidth == option

                            // 颜色动画：选中用主色(Primary)，未选中用高色调表面色(SurfaceContainerHigh)
                            val containerColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                animationSpec = tween(200),
                                label = "ButtonContainer"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                animationSpec = tween(200),
                                label = "ButtonContent"
                            )

                            Surface(
                                onClick = { onTabWidthChange(option) },
                                modifier = Modifier
                                    .weight(1f)      // 三个按钮平分宽度
                                    .height(32.dp),  // 【关键】高度压小，显得精致
                                shape = RoundedCornerShape(4.dp), // 【关键】4dp 小圆角，硬朗风格
                                color = containerColor,
                                contentColor = contentColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$option 空格",
                                        style = MaterialTheme.typography.labelMedium, // 使用较小的字号
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 2. 字体设置 (Combo Box 模式) ===
                    Text("编辑器字体", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Box 容器用于定位 DropdownMenu
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fontPath,
                            onValueChange = onFontPathChange, // 允许直接输入
                            modifier = Modifier.fillMaxWidth(), // 不使用 menuAnchor，防止输入框点击触发 Menu
                            label = { Text("请输入...") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { isFontDropdownExpanded = !isFontDropdownExpanded }) {
                                    Icon(Icons.Filled.ArrowDropDown, "选择预设")
                                }
                            }
                        )

                        // 下拉菜单
                        DropdownMenu(
                            expanded = isFontDropdownExpanded,
                            onDismissRequest = { isFontDropdownExpanded = false },
                            offset = DpOffset(0.dp, 0.dp),
                            modifier = Modifier.fillMaxWidth(0.9f) // 稍微调整宽度适应
                        ) {
                            PRESET_FONTS.forEach { (name, file) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(name, style = MaterialTheme.typography.bodyLarge)
                                            if (file.isNotEmpty()) {
                                                Text(file, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onFontPathChange(file)
                                        isFontDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 3. 行为开关 ===
                    Text("行为", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    CompactSwitchRow("显示工具栏", showToolbar, onShowToolbarChange)
                    CompactSwitchRow("自动换行", wordWrap, onWordWrapChange)
                    CompactSwitchRow("显示空白符", showInvisibles, onShowInvisiblesChange)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    // === 4. 符号栏 ===
                    Text("自定义符号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSymbols,
                        onValueChange = onCustomSymbolsChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        placeholder = { Text("Tab, <, >, ...") }
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

// 辅助
fun Modifier.scale(scale: Float) = this.graphicsLayer(scaleX = scale, scaleY = scale)

// ... 其他组件 ThemeSettingsItem, LogSettingsItem 等保持不变 (参考之前提供的完整代码) ...
@Composable
fun ThemeSettingsItem(
    currentThemeState: ThemeState,
    onThemeChange: (Int, Int, Color, Boolean, Boolean) -> Unit,
    onCustomColorClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandDuration = 200
    val textFadeDuration = 200
    val snappyEasing = LinearOutSlowInEasing

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(durationMillis = expandDuration, easing = snappyEasing)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "外观与主题",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(tween(textFadeDuration)) + expandVertically(tween(textFadeDuration), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(textFadeDuration)) + shrinkVertically(tween(textFadeDuration), shrinkTowards = Alignment.Top)
                    ) {
                        Text(
                            text = if (currentThemeState.isMonetEnabled) "动态色彩" else "自定义外观",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "ArrowRotation",
                    animationSpec = tween(expandDuration)
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(expandDuration)) +
                        expandVertically(animationSpec = tween(expandDuration, easing = snappyEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(textFadeDuration)) +
                        shrinkVertically(animationSpec = tween(textFadeDuration, easing = snappyEasing), shrinkTowards = Alignment.Top),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("动态色彩", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = currentThemeState.isMonetEnabled,
                                onCheckedChange = {
                                    onThemeChange(currentThemeState.selectedModeIndex, currentThemeState.selectedThemeIndex, currentThemeState.customColor, it, currentThemeState.isCustomTheme)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = !currentThemeState.isMonetEnabled) {
                        Column {
                            Text("主题色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                            ) {
                                itemsIndexed(themeColors) { index, theme ->
                                    val isSelected = !currentThemeState.isCustomTheme && currentThemeState.selectedThemeIndex == index
                                    ColorSelectionItem(
                                        color = theme.primaryColor,
                                        name = theme.name,
                                        isSelected = isSelected,
                                        onClick = {
                                            onThemeChange(currentThemeState.selectedModeIndex, index, currentThemeState.customColor, false, false)
                                        }
                                    )
                                }
                                item {
                                    CustomColorButton(
                                        isSelected = currentThemeState.isCustomTheme,
                                        customColor = currentThemeState.customColor,
                                        onClick = onCustomColorClick
                                    )
                                }
                            }
                        }
                    }

                    Text("显示模式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("跟随系统", "浅色", "深色")
                        modes.forEachIndexed { index, label ->
                            SmoothFilterChip(
                                selected = currentThemeState.selectedModeIndex == index,
                                label = label,
                                onClick = { onThemeChange(index, currentThemeState.selectedThemeIndex, currentThemeState.customColor, currentThemeState.isMonetEnabled, currentThemeState.isCustomTheme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleSettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LogSettingsItem(
    logConfigState: LogConfigState,
    onLogConfigChange: (Boolean, String) -> Unit,
    onPathClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用日志", style = MaterialTheme.typography.titleMedium)
                }
                Switch(checked = logConfigState.isLogEnabled, onCheckedChange = { onLogConfigChange(it, logConfigState.logFilePath) })
            }
            AnimatedVisibility(visible = logConfigState.isLogEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onPathClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(logConfigState.logFilePath, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun SmoothFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = 200
    val fastEasing = LinearEasing
    val colorAnimSpec = tween<Color>(durationMillis = duration, easing = fastEasing)
    val containerColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, colorAnimSpec, "Container")
    val borderColor by animateColorAsState(if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, colorAnimSpec, "Border")
    val contentColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface, colorAnimSpec, "Content")

    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = CircleShape,
        color = containerColor,
        border = if (!selected) BorderStroke(1.dp, borderColor) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(visible = selected) {
                Row {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = contentColor)
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}
@Composable
fun ColorSelectionItem(color: Color, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape).background(color)
        ) {
            if (isSelected) Icon(Icons.Default.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomColorButton(isSelected: Boolean, customColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (isSelected) {
                Box(Modifier.fillMaxSize().background(customColor))
                Icon(Icons.Default.Edit, null, tint = if (customColor.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Add, "Custom", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("自定义", style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}