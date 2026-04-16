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
package com.web.webide.ui.welcome

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.web.webide.R
import com.web.webide.core.utils.PermissionManager
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.components.ColorPickerDialog
import com.web.webide.ui.components.WebIDE_Icon
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    themeViewModel: ThemeViewModel,
    onWelcomeFinished: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val themeState by themeViewModel.themeState.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var storageGranted by remember { mutableStateOf(false) }
    var installGranted by remember { mutableStateOf(true) }

    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(themeState.customColor) }
    var selectedModeIndex by remember { mutableIntStateOf(themeState.selectedModeIndex) }
    var selectedThemeIndex by remember {
        mutableIntStateOf(if (themeState.isCustomTheme) themeColors.size else themeState.selectedThemeIndex)
    }
    var isMonetEnabled by remember { mutableStateOf(themeState.isMonetEnabled) }

    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = remember(selectedModeIndex, systemDark) {
        when (selectedModeIndex) {
            1 -> false
            2 -> true
            else -> systemDark
        }
    }

    // --- WelcomeScreen.kt 中的逻辑修复 ---

// 1. 计算当前预览的主题数据
    val currentPreviewTheme: ThemeColor? = remember(selectedThemeIndex, customColor, isMonetEnabled, isDarkTheme) {
        if (isMonetEnabled) {
            null
        } else if (selectedThemeIndex < themeColors.size) {
            themeColors[selectedThemeIndex]
        } else {
            // [自定义模式]
            // 1. 确定背景色：深色用纯黑微亮，浅色用纯白微灰
            val bgDark = Color(0xFF121212)
            val bgLight = Color(0xFFF8F9FA) // 稍微带一点灰，避免瞎眼

            // 2. 关键点：把 customColor 塞给 Primary 和 Accent
            // 这样 WelcomeBackground 里的光球就能读到你的自定义颜色了！
            val customSpecDark = ThemeColorSpec(
                background = bgDark,
                surface = Color(0xFF1E1E1E),
                primary = customColor,
                accent = customColor // 让两个光球都是你的自定义色，或者让第二个球稍微变淡一点
            )
            val customSpecLight = ThemeColorSpec(
                background = bgLight,
                surface = Color.White,
                primary = customColor,
                accent = customColor
            )

            ThemeColor("Custom", customSpecDark, customSpecLight)
        }
    }

    // 2. 计算目标背景色 (用于文字反色计算)
    val targetBg = if (isMonetEnabled) {
        if (isDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest
    } else if (selectedThemeIndex < themeColors.size) {
        val theme = themeColors[selectedThemeIndex]
        if (isDarkTheme) theme.dark.background else theme.light.background
    } else {
        // 自定义颜色模式：如果选了自定义，背景使用 Theme.kt 逻辑生成的颜色
        MaterialTheme.colorScheme.background
    }

    val animatedBgColor by animateColorAsState(targetBg, tween(600), label = "bg_color")

    // 3. 智能文字颜色：增加阈值，避免浅灰背景下文字变白
    val contentColor by animateColorAsState(
        if (animatedBgColor.luminance() > 0.45f) Color.Black else Color.White,
        tween(600),
        label = "content_color"
    )

    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = { storageGranted = true },
        onPermissionDenied = { }
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageGranted =
                permissionState.hasPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 计算底部导航栏的高亮色
                val activeColor = when {
                    isMonetEnabled -> MaterialTheme.colorScheme.primary
                    // 如果是自定义模式 (index == size)，直接用 customColor
                    selectedThemeIndex == themeColors.size -> customColor
                    // 否则用主题色
                    else -> if (isDarkTheme) themeColors[selectedThemeIndex].dark.primary else themeColors[selectedThemeIndex].light.primary
                }

                WelcomeBottomBar(
                    pagerState = pagerState,
                    activeColor = activeColor,
                    isLastPage = pagerState.currentPage == 2,
                    onBack = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = {
                        if (pagerState.currentPage < 2) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            themeViewModel.saveThemeConfig(
                                selectedModeIndex, selectedThemeIndex, customColor, isMonetEnabled,
                                selectedThemeIndex == themeColors.size
                            )
                            onWelcomeFinished()
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 背景层
                WelcomeBackground(
                    currentTheme = currentPreviewTheme,
                    isDarkTheme = isDarkTheme,
                    monetPrimary = MaterialTheme.colorScheme.primary,
                    monetTertiary = MaterialTheme.colorScheme.tertiary
                )

                // 内容层
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) { page ->
                    when (page) {
                        0 -> IntroContent()
                        1 -> PermissionsContent(
                            storageGranted = storageGranted,
                            installGranted = installGranted,
                            onRequestStoragePermission = { permissionState.requestPermissions() },
                            onRequestInstallPermission = { /* ... */ }
                        )

                        2 -> ThemeSetupContent(
                            selectedModeIndex = selectedModeIndex,
                            selectedThemeIndex = selectedThemeIndex,
                            isMonetEnabled = isMonetEnabled,
                            isDarkTheme = isDarkTheme, // 传入当前模式
                            onMonetToggle = { isMonetEnabled = it },
                            onModeSelected = { selectedModeIndex = it },
                            onThemeSelected = { selectedThemeIndex = it },
                            onCustomColorClick = {
                                selectedThemeIndex = themeColors.size
                                showColorPicker = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = customColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                customColor = color
                showColorPicker = false
                selectedThemeIndex = themeColors.size
            }
        )
    }
}

// --- 页面 1: Intro ---
@Composable
private fun IntroContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(250.dp)) { WebIDE_Icon() }
           // Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
        }
    }
}

// --- 页面 2: Permissions ---
@Composable
private fun PermissionsContent(
    storageGranted: Boolean,
    installGranted: Boolean,
    onRequestStoragePermission: () -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.welcome_permissions_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_permissions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.8f)
        )

        Spacer(Modifier.height(32.dp))

        PermissionCard(
            Icons.Default.Folder,
            stringResource(R.string.welcome_permission_storage_title),
            stringResource(R.string.welcome_permission_storage_description),
            storageGranted,
            onRequestStoragePermission
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            Icons.Default.Download,
            stringResource(R.string.welcome_permission_install_title),
            stringResource(R.string.welcome_permission_install_description),
            installGranted,
            onRequestInstallPermission
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSetupContent(
    selectedModeIndex: Int,
    selectedThemeIndex: Int,
    isMonetEnabled: Boolean,
    isDarkTheme: Boolean,
    onMonetToggle: (Boolean) -> Unit,
    onModeSelected: (Int) -> Unit,
    onThemeSelected: (Int) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val modeOptions = listOf(
        stringResource(R.string.action_follow_system),
        stringResource(R.string.action_light),
        stringResource(R.string.action_dark)
    )

    // 1. 父容器去掉 padding，只保留垂直滚动
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        // 2. 给内部元素单独加 Padding
        Text(
            stringResource(R.string.welcome_appearance_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 24.dp) // <--- 这里加
        )
        Spacer(Modifier.height(32.dp))

        // 模式选择按钮
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp) // <--- 这里加
        ) {
            modeOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedModeIndex == index,
                    onClick = { onModeSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = LocalContentColor.current
                    )
                ) { Text(label) }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.welcome_dynamic_color)) },
                trailingContent = { Switch(checked = isMonetEnabled, onCheckedChange = onMonetToggle) },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = LocalContentColor.current,
                    trailingIconColor = LocalContentColor.current
                ),
                modifier = Modifier.padding(horizontal = 8.dp) // ListItem 自带一些内边距，这里稍微调整即可
            )
        }

        // 3. 主题列表：改用 LazyRow 修复截断问题
        AnimatedVisibility(visible = !isMonetEnabled) {
            Column {
                Spacer(Modifier.height(24.dp))

                // 使用 LazyRow 代替 Row + Scroll
                LazyRow(
                    // 关键点：contentPadding 让内容可以滚到屏幕边缘，但起始位置有缩进
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 渲染预设主题
                    itemsIndexed(themeColors) { index, theme ->
                        ThemePreviewCard(
                            theme = theme,
                            isSelected = selectedThemeIndex == index,
                            isDarkTheme = isDarkTheme,
                            onClick = { onThemeSelected(index) }
                        )
                    }

                    // 渲染自定义按钮
                    item {
                        CustomThemeCard(
                            isSelected = selectedThemeIndex == themeColors.size,
                            onClick = onCustomColorClick
                        )
                    }
                }
            }
        }
    }
}
