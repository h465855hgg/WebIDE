/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.web.webide.ui.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.web.webide.R
import com.web.webide.core.utils.AppLanguageManager
import com.web.webide.core.utils.AppLanguageOption
import com.web.webide.core.utils.LogConfigState
import com.web.webide.core.utils.ThemeState
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.safeNavigate
import com.web.webide.ui.components.DirectorySelector
import com.web.webide.ui.components.ColorPickerDialog
import com.web.webide.ui.welcome.themeColors

// 自动保存选项枚举
enum class AutoSaveOption(@StringRes val labelRes: Int, val interval: Long) {
    OFF(R.string.auto_save_off, 0L),
    SEC_30(R.string.auto_save_30_seconds, 30_000L),
    MIN_1(R.string.auto_save_1_minute, 60_000L),
    MIN_5(R.string.auto_save_5_minutes, 300_000L),
    MIN_10(R.string.auto_save_10_minutes, 600_000L)
}

// 扩展函数解决 luminance 报错
fun Color.luminance(): Float {
    return 0.2126f * this.red + 0.7152f * this.green + 0.0722f * this.blue
}

private data class FontPresetOption(val label: String, val file: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    currentThemeState: ThemeState,
    logConfigState: LogConfigState,
    onThemeChange: (modeIndex: Int, themeIndex: Int, customColor: Color, isMonet: Boolean, isCustom: Boolean) -> Unit,
    onLogConfigChange: (enabled: Boolean, filePath: String) -> Unit,
    editorViewModel: com.web.webide.ui.editor.viewmodel.EditorViewModel? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("WebIDE_Editor_Settings", Context.MODE_PRIVATE) }

    // 使用与 ViewModel 中加载自动保存设置一致的 SharedPreferences 文件名
    val generalPrefs = remember { context.getSharedPreferences("WebIDE_Settings", Context.MODE_PRIVATE) }

    val fontSize = prefs.getFloat("editor_font_size", 14f)

    var tabWidth by remember { mutableIntStateOf(prefs.getInt("editor_tab_width", 4)) }
    var wordWrap by remember { mutableStateOf(prefs.getBoolean("editor_word_wrap", false)) }
    var showInvisibles by remember { mutableStateOf(prefs.getBoolean("editor_show_invisibles", false)) }
    var codeFolding by remember { mutableStateOf(prefs.getBoolean("editor_code_folding", true)) }
    var showToolbar by remember { mutableStateOf(prefs.getBoolean("editor_show_toolbar", true)) }
    var showHistory by remember { mutableStateOf(prefs.getBoolean("editor_show_history", true)) }
    var lspEnabled by remember { mutableStateOf(prefs.getBoolean("editor_lsp_enabled", false)) }
    var aiEnabled by remember { mutableStateOf(prefs.getBoolean("editor_ai_enabled", true)) }
    var fontPath by remember { mutableStateOf(prefs.getString("editor_font_path", "") ?: "") }
    var customSymbols by remember { mutableStateOf(prefs.getString("editor_custom_symbols", "Tab,<,>,/,=,\",',!,?,;,:,{,},[,],(,),+,-,*,_,&,|") ?: "") }
    var autoSaveInterval by remember { mutableLongStateOf(generalPrefs.getLong("auto_save_interval", 0L)) }
    var showAutoSaveDialog by remember { mutableStateOf(false) }
    // 保存之前的 LSP 状态，用于检测变化
    var previousLspEnabled by remember { mutableStateOf(lspEnabled) }

    // 自动保存
    LaunchedEffect(tabWidth, wordWrap, showInvisibles, codeFolding, showToolbar, showHistory, lspEnabled, aiEnabled, fontPath, customSymbols) {
        prefs.edit {
            putFloat("editor_font_size", fontSize)
            putInt("editor_tab_width", tabWidth)
            putBoolean("editor_word_wrap", wordWrap)
            putBoolean("editor_show_invisibles", showInvisibles)
            putBoolean("editor_code_folding", codeFolding)
            putBoolean("editor_show_toolbar", showToolbar)
            putBoolean("editor_show_history", showHistory)
            putBoolean("editor_lsp_enabled", lspEnabled)
            putBoolean("editor_ai_enabled", aiEnabled)
            putString("editor_font_path", fontPath)
            putString("editor_custom_symbols", customSymbols)
        }

        // 检测 LSP 状态变化，重新加载所有编辑器
        if (lspEnabled != previousLspEnabled) {
            editorViewModel?.reloadAllEditors(context)
            previousLspEnabled = lspEnabled
        }
    }

    var selectedWorkspace by remember { mutableStateOf(WorkspaceManager.getWorkspacePath(context)) }
    var showFileSelector by remember { mutableStateOf(false) }
    var showLogPathSelector by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLanguageOption by AppLanguageManager.currentOption.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
            item(key = "theme_settings") {
                ThemeSettingsItem(
                    currentThemeState = currentThemeState,
                    onThemeChange = onThemeChange,
                    onCustomColorClick = { showColorPicker = true }
                )
            }

            item(key = "editor_settings") {
                EditorSettingsItem(
                    tabWidth = tabWidth,
                    onTabWidthChange = { tabWidth = it },
                    wordWrap = wordWrap,
                    onWordWrapChange = { wordWrap = it },
                    showInvisibles = showInvisibles,
                    onShowInvisiblesChange = { showInvisibles = it },
                    codeFolding = codeFolding,
                    onCodeFoldingChange = { codeFolding = it },
                    showToolbar = showToolbar,
                    onShowToolbarChange = { showToolbar = it },
                    showHistory = showHistory,
                    onShowHistoryChange = { showHistory = it },
                    lspEnabled = lspEnabled,
                    onLspEnabledChange = { lspEnabled = it },
                    isAiEnabled = aiEnabled,
                    onIsAiEnabledChange = { aiEnabled = it },
                    fontPath = fontPath,
                    onFontPathChange = { fontPath = it },
                    customSymbols = customSymbols,
                    onCustomSymbolsChange = { customSymbols = it }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_general),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            // 🔥 新增：自动保存设置入口
            item {
                SimpleSettingsCard(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.settings_language_title),
                    subtitle = stringResource(currentLanguageOption.labelRes),
                    onClick = { showLanguageDialog = true }
                )
            }
            item {
                val currentOption = AutoSaveOption.entries.find { it.interval == autoSaveInterval } ?: AutoSaveOption.OFF
                val currentOptionLabel = stringResource(currentOption.labelRes)
                SimpleSettingsCard(
                    icon = Icons.Outlined.SaveAs, // 需要确保有此图标，如果没有可以使用 Icons.Default.Save
                    title = stringResource(R.string.settings_auto_save_backup_title),
                    subtitle = if (currentOption == AutoSaveOption.OFF) {
                        stringResource(R.string.status_disabled)
                    } else {
                        stringResource(R.string.settings_auto_save_frequency, currentOptionLabel)
                    },
                    onClick = { showAutoSaveDialog = true }
                )
            }
            item {
                SimpleSettingsCard(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.settings_workspace_title),
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
                    icon = Icons.Outlined.WavingHand,
                    title = stringResource(R.string.settings_welcome_title),
                    subtitle = stringResource(R.string.settings_welcome_subtitle),
                    onClick = { navController.safeNavigate("welcome") }
                )
            }

            item {
                SimpleSettingsCard(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    onClick = { navController.safeNavigate("about") }
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
                Toast.makeText(context, context.getString(R.string.toast_workspace_updated), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.toast_log_path_updated), Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showLogPathSelector = false }
        )
    }
    if (showAutoSaveDialog) {
        AutoSaveDialog(
            selectedInterval = autoSaveInterval,
            onDismiss = { showAutoSaveDialog = false },
            onOptionSelected = { option, toastMessage ->
                autoSaveInterval = option.interval
                generalPrefs.edit { putLong("auto_save_interval", option.interval) }
                showAutoSaveDialog = false
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_language_dialog_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    AppLanguageOption.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageDialog = false
                                    AppLanguageManager.updateLanguage(context, option)
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = option == currentLanguageOption,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
@Composable
private fun AutoSaveDialog(
    selectedInterval: Long,
    onDismiss: () -> Unit,
    onOptionSelected: (AutoSaveOption, String) -> Unit
) {
    val optionLabels = mapOf(
        AutoSaveOption.OFF to stringResource(R.string.auto_save_off),
        AutoSaveOption.SEC_30 to stringResource(R.string.auto_save_30_seconds),
        AutoSaveOption.MIN_1 to stringResource(R.string.auto_save_1_minute),
        AutoSaveOption.MIN_5 to stringResource(R.string.auto_save_5_minutes),
        AutoSaveOption.MIN_10 to stringResource(R.string.auto_save_10_minutes)
    )
    val optionToastMessages = mapOf(
        AutoSaveOption.OFF to stringResource(R.string.toast_auto_save_disabled),
        AutoSaveOption.SEC_30 to stringResource(R.string.toast_auto_save_set, optionLabels.getValue(AutoSaveOption.SEC_30)),
        AutoSaveOption.MIN_1 to stringResource(R.string.toast_auto_save_set, optionLabels.getValue(AutoSaveOption.MIN_1)),
        AutoSaveOption.MIN_5 to stringResource(R.string.toast_auto_save_set, optionLabels.getValue(AutoSaveOption.MIN_5)),
        AutoSaveOption.MIN_10 to stringResource(R.string.toast_auto_save_set, optionLabels.getValue(AutoSaveOption.MIN_10))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_auto_save_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_auto_save_dialog_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                AutoSaveOption.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionSelected(option, optionToastMessages.getValue(option))
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = option.interval == selectedInterval,
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = optionLabels.getValue(option),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsItem(
    tabWidth: Int,
    onTabWidthChange: (Int) -> Unit,
    wordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    showInvisibles: Boolean,
    onShowInvisiblesChange: (Boolean) -> Unit,
    codeFolding: Boolean,
    onCodeFoldingChange: (Boolean) -> Unit,
    showToolbar: Boolean,
    onShowToolbarChange: (Boolean) -> Unit,
    showHistory: Boolean,
    onShowHistoryChange: (Boolean) -> Unit,
    lspEnabled: Boolean,
    onLspEnabledChange: (Boolean) -> Unit,
    isAiEnabled: Boolean,
    onIsAiEnabledChange: (Boolean) -> Unit,
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
    val fontPresetOptions = listOf(
        FontPresetOption(stringResource(R.string.font_default), ""),
        FontPresetOption(stringResource(R.string.font_jetbrains_mono), "ttf/JetBrainsMono-Regular.ttf"),
        FontPresetOption(stringResource(R.string.font_roboto_mono), "ttf/RobotoMono-Regular.ttf"),
        FontPresetOption(stringResource(R.string.font_source_code_pro), "ttf/SourceCodePro-Regular.ttf"),
        FontPresetOption(stringResource(R.string.font_comic_sans), "ttf/Comic-Sans-MS-Regular-2.ttf")
    )

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
                        text = stringResource(R.string.settings_editor_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(tween(textFadeDuration)) + expandVertically(tween(textFadeDuration), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(textFadeDuration)) + shrinkVertically(tween(textFadeDuration), shrinkTowards = Alignment.Top)
                    ) {
                        val displayFont = if (fontPath.isBlank()) {
                            stringResource(R.string.font_system_default)
                        } else {
                            fontPath.substringAfterLast("/")
                        }
                        Text(
                            text = stringResource(R.string.settings_editor_summary, tabWidth, displayFont),
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

                    Text(stringResource(R.string.settings_assistance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    CompactSwitchRow(stringResource(R.string.settings_ai_assistant), isAiEnabled, onIsAiEnabledChange)
                    CompactSwitchRow(stringResource(R.string.settings_lsp_completion), lspEnabled, onLspEnabledChange)
                    Spacer(modifier = Modifier.height(24.dp))

                    // === 1. 缩进设置 (Segmented Style) ===
                    Text(stringResource(R.string.settings_indent_width), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                                        text = stringResource(R.string.settings_spaces_format, option),
                                        style = MaterialTheme.typography.labelMedium, // 使用较小的字号
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 2. 字体设置 (Combo Box 模式) ===
                    Text(stringResource(R.string.settings_editor_font), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Box 容器用于定位 DropdownMenu
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fontPath,
                            onValueChange = onFontPathChange, // 允许直接输入
                            modifier = Modifier.fillMaxWidth(), // 不使用 menuAnchor，防止输入框点击触发 Menu
                            label = { Text(stringResource(R.string.settings_input_hint)) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { isFontDropdownExpanded = !isFontDropdownExpanded }) {
                                    Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.settings_select_preset))
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
                            fontPresetOptions.forEach { preset ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(preset.label, style = MaterialTheme.typography.bodyLarge)
                                            if (preset.file.isNotEmpty()) {
                                                Text(preset.file, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onFontPathChange(preset.file)
                                        isFontDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 3. 行为开关 ===
                    Text(stringResource(R.string.settings_behavior), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    CompactSwitchRow(stringResource(R.string.settings_show_toolbar), showToolbar, onShowToolbarChange)
                    CompactSwitchRow(stringResource(R.string.settings_show_history_tabs), showHistory, onShowHistoryChange)
                    CompactSwitchRow(stringResource(R.string.settings_word_wrap), wordWrap, onWordWrapChange)
                    CompactSwitchRow(stringResource(R.string.settings_show_whitespace), showInvisibles, onShowInvisiblesChange)
                    CompactSwitchRow(stringResource(R.string.settings_code_folding), codeFolding, onCodeFoldingChange)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                    // === 4. 符号栏 ===
                    Text(stringResource(R.string.settings_custom_symbols), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSymbols,
                        onValueChange = onCustomSymbolsChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        placeholder = { Text(stringResource(R.string.settings_symbols_placeholder)) }
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
                        text = stringResource(R.string.settings_theme_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(tween(textFadeDuration)) + expandVertically(tween(textFadeDuration), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(textFadeDuration)) + shrinkVertically(tween(textFadeDuration), shrinkTowards = Alignment.Top)
                    ) {
                        Text(
                            text = if (currentThemeState.isMonetEnabled) {
                                stringResource(R.string.settings_dynamic_color)
                            } else {
                                stringResource(R.string.settings_custom_appearance)
                            },
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
                                Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = currentThemeState.isMonetEnabled,
                                onCheckedChange = {
                                    // 互斥逻辑：启用 Monet 时，强制关闭 CustomTheme
                                    val newIsCustom = if (it) false else currentThemeState.isCustomTheme
                                    onThemeChange(currentThemeState.selectedModeIndex, currentThemeState.selectedThemeIndex, currentThemeState.customColor, it, newIsCustom)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = !currentThemeState.isMonetEnabled) {
                        Column {
                            Text(stringResource(R.string.settings_theme_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

                    Text(stringResource(R.string.settings_display_mode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            stringResource(R.string.action_follow_system),
                            stringResource(R.string.action_light),
                            stringResource(R.string.action_dark)
                        )
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
                    Text(stringResource(R.string.settings_enable_log), style = MaterialTheme.typography.titleMedium)
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
                Icon(Icons.Default.Add, stringResource(R.string.settings_custom), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.settings_custom), style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
