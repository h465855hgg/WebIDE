package com.web.webide.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- 可复用的UI组件 ---

@Composable
internal fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PermissionCard(icon: ImageVector, title: String, description: String, isGranted: Boolean, onRequest: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isGranted) {
                TextButton(onClick = onRequest) { Text("授权") }
            } else {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "已授权", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun ThemePreviewCard(theme: ThemeColor, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")
    Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(100.dp, 140.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(16.dp),
            color = theme.backgroundColor,
            shadowElevation = if (isSelected) 8.dp else 2.dp,
            border = if (isSelected) BorderStroke(3.dp, SolidColor(theme.primaryColor)) else null
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Mini UI Preview
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)).background(theme.surfaceColor).padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(theme.primaryColor))
                        Spacer(Modifier.width(3.dp))
                        Box(modifier = Modifier.width(20.dp).height(5.dp).clip(RoundedCornerShape(2.dp)).background(theme.primaryColor.copy(alpha = 0.6f)))
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(2.dp)).background(theme.accentColor))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(16.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(theme.surfaceColor).padding(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(4) { Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(1.dp)).background(theme.primaryColor.copy(alpha = 0.6f))) }
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(5) { index -> Box(modifier = Modifier.width((20 + index * 4).dp).height(4.dp).clip(RoundedCornerShape(1.dp)).background(if (index % 2 == 0) theme.primaryColor.copy(alpha = 0.7f) else theme.accentColor.copy(alpha = 0.7f))) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(theme.surfaceColor).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(theme.primaryColor))
                            Box(modifier = Modifier.width(15.dp).height(3.dp).clip(RoundedCornerShape(1.dp)).background(theme.accentColor.copy(alpha = 0.6f)))
                            Spacer(Modifier.weight(1f))
                            Box(modifier = Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(1.dp)).background(theme.primaryColor.copy(alpha = 0.5f)))
                        }
                    }
                }
                if (isSelected) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(theme.primaryColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "已选中", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(theme.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun CustomThemeCard(isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")
    Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(100.dp, 140.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = if (isSelected) 8.dp else 2.dp,
            border = if (isSelected) BorderStroke(3.dp, SolidColor(MaterialTheme.colorScheme.primary)) else null
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Palette, "自定义", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text("自定义", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (isSelected) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "已选中", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("自定义颜色", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun ColorPickerDialog(currentColor: Color, onColorSelected: (Color) -> Unit, onDismiss: () -> Unit) {
    var red by remember { mutableStateOf((currentColor.red * 255).toInt()) }
    var green by remember { mutableStateOf((currentColor.green * 255).toInt()) }
    var blue by remember { mutableStateOf((currentColor.blue * 255).toInt()) }
    val selectedColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题颜色") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(selectedColor).border(3.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("#${"%02X%02X%02X".format(red, green, blue)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        onClick = { red = (0..255).random(); green = (0..255).random(); blue = (0..255).random() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "随机", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("随机", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("常用颜色", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFF5722)).forEach { color ->
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color).border(2.dp, if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable {
                            red = (color.red * 255).toInt(); green = (color.green * 255).toInt(); blue = (color.blue * 255).toInt()
                        })
                    }
                }
                Spacer(Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    ColorSlider("R", Color.Red, red) { red = it }
                    ColorSlider("G", Color.Green, green) { green = it }
                    ColorSlider("B", Color.Blue, blue) { blue = it }
                }
            }
        },
        confirmButton = { Button(onClick = { onColorSelected(selectedColor) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ColorSlider(label: String, color: Color, value: Int, onValueChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(value.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..255f,
        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
    )
}

@Composable
internal fun BottomNavigation(currentStep: WelcomeStep, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        StepIndicator(currentStep, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(currentStep != WelcomeStep.INTRO, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.ArrowBack, "返回", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("返回", fontSize = 16.sp)
                }
            }
            Button(onClick = onNext, modifier = Modifier.weight(if (currentStep == WelcomeStep.INTRO) 1f else 1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = when (currentStep) {
                        WelcomeStep.INTRO -> "开始"
                        WelcomeStep.PERMISSIONS -> "下一步"
                        WelcomeStep.THEME_SETUP -> "完成"
                    },
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(if (currentStep == WelcomeStep.THEME_SETUP) Icons.Default.Check else Icons.Default.ArrowForward, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun StepIndicator(currentStep: WelcomeStep, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val entries = remember { WelcomeStep.entries }
        entries.forEach { step ->
            val isActive = step == currentStep
            val targetWidth by animateDpAsState(if (isActive) 32.dp else 8.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "")
            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.primary
                            step.ordinal < currentStep.ordinal -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}