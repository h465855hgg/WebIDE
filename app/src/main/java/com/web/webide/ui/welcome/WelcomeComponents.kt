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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
internal fun BottomNavigation(currentStep: WelcomeStep, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        StepIndicator(currentStep, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(currentStep != WelcomeStep.INTRO, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(20.dp))
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
                Icon(if (currentStep == WelcomeStep.THEME_SETUP) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
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