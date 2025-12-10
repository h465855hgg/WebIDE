package com.web.webide.ui.welcome

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.PermissionManager
import com.web.webide.ui.ThemeViewModel

@Composable
fun WelcomeScreen(
    themeViewModel: ThemeViewModel,
    onWelcomeFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val themeState by themeViewModel.themeState.collectAsState()

    var currentStep by remember { mutableStateOf(WelcomeStep.INTRO) }

    // --- 权限状态 ---
    var storageGranted by remember { mutableStateOf(false) }
    var installGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true // Android 8.0 以下无需此特殊权限
            }
        )
    }

    // --- 主题状态 ---
    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(themeState.customColor) }
    var selectedModeIndex by remember { mutableStateOf(themeState.selectedModeIndex) }
    var selectedThemeIndex by remember {
        mutableStateOf(if (themeState.isCustomTheme) themeColors.size else themeState.selectedThemeIndex)
    }
    var isMonetEnabled by remember { mutableStateOf(themeState.isMonetEnabled) }

    // 存储权限请求器
    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = {
            storageGranted = true
            // 可以加个日志
            LogCatcher.i("WelcomeScreen", "回调：权限已授予，UI应更新")
        },
        onPermissionDenied = {
            // 可以在这里提示用户需要权限
        }
    )

    // 监听生命周期: 当用户从设置页返回时，刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 刷新存储权限
                storageGranted = permissionState.hasPermissions()

                // 刷新安装权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    installGranted = context.packageManager.canRequestPackageInstalls()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "contentTransition"
                ) { step ->
                    when (step) {
                        WelcomeStep.INTRO -> IntroContent()

                        // ✅ 修复：PermissionsContent 的调用逻辑简化
                        WelcomeStep.PERMISSIONS -> PermissionsContent(
                            storageGranted = storageGranted,
                            installGranted = installGranted,
                            onRequestStoragePermission = {
                                // ✅ 关键修复：不再手动判断 SDK_INT，统一交给 permissionState 处理
                                // 这样 Android 11+ 也会通过 launcher 启动，能收到回调
                                permissionState.requestPermissions()
                            },
                            onRequestInstallPermission = {
                                // 安装权限逻辑保持不变（因为它不影响文件核心功能，且 ResultAPI 对它支持较弱）
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (!context.packageManager.canRequestPackageInstalls()) {
                                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                        intent.setData(Uri.parse("package:" + context.packageName))
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        )


                        WelcomeStep.THEME_SETUP -> ThemeSetupContent(
                            selectedModeIndex = selectedModeIndex,
                            selectedThemeIndex = selectedThemeIndex,
                            isMonetEnabled = isMonetEnabled,
                            onMonetToggle = { enabled ->
                                isMonetEnabled = enabled
                                themeViewModel.saveThemeConfig(selectedModeIndex, selectedThemeIndex, customColor, enabled, selectedThemeIndex == themeColors.size)
                            },
                            onModeSelected = { index ->
                                selectedModeIndex = index
                                themeViewModel.saveThemeConfig(index, selectedThemeIndex, customColor, isMonetEnabled, selectedThemeIndex == themeColors.size)
                            },
                            onThemeSelected = { index ->
                                selectedThemeIndex = index
                                // 选中预设主题时，强制关闭动态色彩
                                themeViewModel.saveThemeConfig(selectedModeIndex, index, customColor, false, index == themeColors.size)
                            },
                            onCustomColorClick = {
                                selectedThemeIndex = themeColors.size
                                showColorPicker = true
                            }
                        )
                    }
                }
            }

            BottomNavigation(
                currentStep = currentStep,
                onBack = {
                    currentStep = when (currentStep) {
                        WelcomeStep.PERMISSIONS -> WelcomeStep.INTRO
                        WelcomeStep.THEME_SETUP -> WelcomeStep.PERMISSIONS
                        else -> currentStep
                    }
                },
                onNext = {
                    when (currentStep) {
                        WelcomeStep.INTRO -> currentStep = WelcomeStep.PERMISSIONS
                        WelcomeStep.PERMISSIONS -> currentStep = WelcomeStep.THEME_SETUP
                        WelcomeStep.THEME_SETUP -> {
                            themeViewModel.saveThemeConfig(selectedModeIndex, selectedThemeIndex, customColor, isMonetEnabled, selectedThemeIndex == themeColors.size)
                            onWelcomeFinished()
                        }
                    }
                }
            )
        }

        // 颜色选择器弹窗
        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = customColor, // 参数名已适配
                onDismiss = { showColorPicker = false },
                onColorSelected = { color ->
                    customColor = color
                    showColorPicker = false
                    selectedThemeIndex = themeColors.size

                    // ✅ 关键修复: 立即保存，触发全局主题刷新
                    themeViewModel.saveThemeConfig(
                        selectedModeIndex,
                        themeColors.size,
                        color,
                        false, // 选了自定义色，肯定不是 Monet
                        true   // isCustom = true
                    )
                }
            )
        }
    }
}

@Composable
private fun IntroContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WebIDE", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("移动端的专业代码编辑器", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        FeatureItem(Icons.Default.Speed, "高效流畅", "专为移动端优化的编辑体验")
        Spacer(Modifier.height(20.dp))
        FeatureItem(Icons.Default.Palette, "多主题支持", "自定义你喜欢的编辑器外观")
        Spacer(Modifier.height(20.dp))
        FeatureItem(Icons.Default.Edit, "Sora Editor", "强大的代码编辑器，写的更爽")
    }
}

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
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()), // 防止小屏幕显示不全
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("需要一些权限", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("为了让应用正常工作", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))

        // 1. 存储权限
        val storageTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问权限" else "存储权限"
        val storageDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "管理和保存项目文件" else "读取和保存代码文件"
        PermissionCard(
            Icons.Default.Folder,
            storageTitle,
            storageDesc,
            storageGranted,
            onRequestStoragePermission
        )

        Spacer(Modifier.height(16.dp))

        // 2. 安装权限
        PermissionCard(
            Icons.Default.Download, // 使用下载图标表示安装
            "安装应用权限",
            "用于应用内更新或构建APK",
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
    onMonetToggle: (Boolean) -> Unit,
    onModeSelected: (Int) -> Unit,
    onThemeSelected: (Int) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val modeOptions = listOf("跟随系统", "浅色模式", "深色模式")
    val colorOptions = themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 如果内容超出一屏，仍然允许滚动
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        // ✅ 核心修改：让内容垂直居中，与其他页面（介绍页、权限页）保持一致
        verticalArrangement = Arrangement.Center
    ) {
        // ❌ 删除原来的 Spacer(Modifier.height(64.dp))，不需要强制顶部留白了

        Text(
            text = "选择主题",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "随时可以在设置中更改",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // 1. 模式选择
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    modeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedModeIndex == index,
                            onClick = { onModeSelected(index) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
                            icon = {}
                        ) {
                            Text(label)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )

                // 2. 动态色彩开关
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("动态色彩", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "从壁纸提取颜色",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMonetEnabled,
                            onCheckedChange = onMonetToggle
                        )
                    }
                }

                // 3. 颜色选择列表
                AnimatedVisibility(!isMonetEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.width(16.dp))

                        colorOptions.forEachIndexed { index, theme ->
                            ThemePreviewCard(
                                theme = theme,
                                isSelected = selectedThemeIndex == index,
                                onClick = { onThemeSelected(index) }
                            )
                        }

                        CustomThemeCard(
                            isSelected = selectedThemeIndex == themeColors.size,
                            onClick = onCustomColorClick
                        )

                        Spacer(Modifier.width(16.dp))
                    }
                }
            }
        }
    }
}