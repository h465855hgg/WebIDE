package com.web.webide.ui.welcome

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.web.webide.core.utils.PermissionManager
import com.web.webide.ui.ThemeViewModel


@Composable
fun WelcomeScreen(
    themeViewModel: ThemeViewModel,
    onWelcomeFinished: () -> Unit
) {
    val context = LocalContext.current
    val themeState by themeViewModel.themeState.collectAsState()
    
    var currentStep by remember { mutableStateOf(WelcomeStep.INTRO) }
    var storageGranted by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(themeState.customColor) }

    var selectedModeIndex by remember { mutableStateOf(themeState.selectedModeIndex) }
    // ✅ 修复: 如果是自定义主题，将 selectedThemeIndex 设置为 themeColors.size
    var selectedThemeIndex by remember { 
        mutableStateOf(if (themeState.isCustomTheme) themeColors.size else themeState.selectedThemeIndex) 
    }
    var isMonetEnabled by remember { mutableStateOf(themeState.isMonetEnabled) }
    
    val permissionState = PermissionManager.rememberPermissionRequest(
        onPermissionGranted = {
            storageGranted = true
            Toast.makeText(context, "权限已授予", Toast.LENGTH_SHORT).show()
        },
        onPermissionDenied = {
            Toast.makeText(context, "需要权限才能正常使用文件功能", Toast.LENGTH_LONG).show()
        }
    )
    
    LaunchedEffect(Unit) {
        storageGranted = permissionState.hasPermissions()
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
                        WelcomeStep.PERMISSIONS -> PermissionsContent(
                            storageGranted = storageGranted,
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    PermissionManager.requestAllFilesAccess(context)
                                } else {
                                    permissionState.requestPermissions()
                                }
                            }
                        )
                        WelcomeStep.THEME_SETUP -> ThemeSetupContent(
                            selectedModeIndex = selectedModeIndex,
                            selectedThemeIndex = selectedThemeIndex,
                            isMonetEnabled = isMonetEnabled,
                            onMonetToggle = { enabled ->
                                isMonetEnabled = enabled
                                // ✅ 修复: 调用正确的5参数函数
                                themeViewModel.saveThemeConfig(selectedModeIndex, selectedThemeIndex, customColor, enabled, selectedThemeIndex == themeColors.size)
                            },
                            onModeSelected = { index -> 
                                selectedModeIndex = index
                                // ✅ 修复: 调用正确的5参数函数
                                themeViewModel.saveThemeConfig(index, selectedThemeIndex, customColor, isMonetEnabled, selectedThemeIndex == themeColors.size)
                            },
                            onThemeSelected = { index -> 
                                selectedThemeIndex = index
                                // ✅ 修复: 调用正确的5参数函数
                                themeViewModel.saveThemeConfig(selectedModeIndex, index, customColor, isMonetEnabled, index == themeColors.size)
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
                            // ✅ 修复: 调用正确的5参数函数
                            themeViewModel.saveThemeConfig(selectedModeIndex, selectedThemeIndex, customColor, isMonetEnabled, selectedThemeIndex == themeColors.size)
                            onWelcomeFinished()
                        }
                    }
                }
            )
        }

        if (showColorPicker) {
            ColorPickerDialog(
                currentColor = customColor,
                onColorSelected = { color ->
                    customColor = color
                    selectedThemeIndex = themeColors.size
                    // ✅ 修复: 调用正确的5参数函数
                    themeViewModel.saveThemeConfig(selectedModeIndex, themeColors.size, color, isMonetEnabled, true)
                    showColorPicker = false
                },
                onDismiss = {
                    showColorPicker = false
                    if (selectedThemeIndex == themeColors.size) {
                        selectedThemeIndex = 0
                    }
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
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("需要一些权限", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("为了让应用正常工作", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        val title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问权限" else "存储权限"
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "管理和保存项目文件" else "读取和保存代码文件"
        PermissionCard(
            Icons.Default.Folder,
            title,
            description,
            storageGranted,
            onRequestPermission
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
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text("选择主题", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(16.dp))
        Text("随时可以在设置中更改", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                     Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("动态色彩", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = isMonetEnabled, onCheckedChange = onMonetToggle)
                    }
                }

                AnimatedVisibility(!isMonetEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.width(0.dp))
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
                        Spacer(Modifier.width(0.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(64.dp))
    }
}