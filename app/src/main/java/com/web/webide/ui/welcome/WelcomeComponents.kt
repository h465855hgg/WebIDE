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

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.web.webide.R
import kotlin.math.cos
import kotlin.math.sin

// --- 背景组件：修复浅色底色过亮问题 ---
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun WelcomeBackground(
    currentTheme: ThemeColor?,
    isDarkTheme: Boolean,
    monetPrimary: Color? = null, // 新增：Monet 模式下的主色
    monetTertiary: Color? = null // 新增：Monet 模式下的强调色
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 1. 确定背景基色
    val baseBg = if (currentTheme != null) {
        if (isDarkTheme) currentTheme.dark.background else currentTheme.light.background
    } else {
        // Monet / 跟随系统
        if (isDarkTheme) colorScheme.surface else colorScheme.surfaceContainerLowest // 浅色用最亮的背景
    }

    // 2. 确定光球颜色
    // 逻辑：如果有选定主题，用主题色。如果是 Monet，用传入的动态色或默认色。
    val spec = if (isDarkTheme) currentTheme?.dark else currentTheme?.light
    val rawPrimary = spec?.primary ?: monetPrimary ?: colorScheme.primary
    val rawAccent = spec?.accent ?: monetTertiary ?: colorScheme.tertiary

    // 3. 关键修复：光球在浅色模式下的可见性
    // 浅色模式下，单纯的 alpha 0.05 看不见。
    // 技巧：让光球颜色稍微深一点 (compositeOver Gray)，然后 alpha 给高一点。
    val blobAlpha = if (isDarkTheme) 0.15f else 0.12f

    // 浅色模式下，让光球颜色稍微“重”一点，否则在白色背景上像脏印子
    val effectivePrimary = if (isDarkTheme) rawPrimary else rawPrimary.compositeOver(Color.Gray)
    val effectiveAccent = if (isDarkTheme) rawAccent else rawAccent.compositeOver(Color.Gray)

    // 动画过渡
    val animBg by animateColorAsState(baseBg, tween(600), label = "bg")
    val animPrimary by animateColorAsState(effectivePrimary, tween(600), label = "prim")
    val animAccent by animateColorAsState(effectiveAccent, tween(600), label = "acc")

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart), label = "t1"
    )
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "t2"
    )

    Box(modifier = Modifier.fillMaxSize().background(animBg)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp) // 模糊半径
                .graphicsLayer { alpha = 1f }
        ) {
            val offset1 = Offset(
                x = screenWidth * 0.5f + (screenWidth * 0.35f) * cos(t1),
                y = screenHeight * 0.4f + (screenHeight * 0.3f) * sin(t1)
            )
            val offset2 = Offset(
                x = screenWidth * 0.5f - (screenWidth * 0.35f) * cos(t2),
                y = screenHeight * 0.6f - (screenHeight * 0.3f) * sin(t2)
            )

            drawCircle(color = animPrimary.copy(alpha = blobAlpha), center = offset1, radius = screenWidth * 0.6f)
            drawCircle(color = animAccent.copy(alpha = blobAlpha), center = offset2, radius = screenWidth * 0.5f)
        }
    }
}

// --- 权限卡片 ---
@Composable
internal fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f),
        label = "border"
    )
    // 容器颜色：未授权时给一点点半透明背景，使其在不同底色上都可见
    val containerColor by animateColorAsState(
        if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        label = "container"
    )

    Surface(
        onClick = { if (!isGranted) onRequest() },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 注意：这里不硬编码颜色，依赖 LocalContentColor
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    // 使用 LocalContentColor 并加透明度
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.content_desc_permission_granted),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Button(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.action_enable), fontSize = 13.sp)
                }
            }
        }
    }
}

// --- 主题预览卡片 ---

@Composable
internal fun ThemePreviewCard(
    theme: ThemeColor,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    // 1. 选中状态动画 (缩放)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val borderWidth by animateDpAsState(if (isSelected) 3.dp else 0.dp, label = "borderW")

    // 2. 颜色平滑过渡动画 (关键！解决生硬问题)
    // 根据传入的 isDarkTheme 决定目标颜色，并应用 500ms 动画
    val targetSpec = if (isDarkTheme) theme.dark else theme.light

    val animBgColor by animateColorAsState(targetSpec.background, tween(500), label = "bgColor")
    val animPrimaryColor by animateColorAsState(targetSpec.primary, tween(500), label = "primColor")
    val animSurfaceColor by animateColorAsState(targetSpec.surface, tween(500), label = "surfColor")
    val animBorderColor by animateColorAsState(targetSpec.primary, tween(500), label = "borderColor")

    // 3. "两球接触" 位置动画
    // 我们定义两个状态的位置，当模式切换时，通过颜色过渡 + 位置微调产生动态感
    // 这里设计为：Light 模式下球稍微分开一点，Dark 模式下球稍微紧凑一点，或者反过来
    val circleOffsetOne by animateDpAsState(
        targetValue = if (isDarkTheme) 12.dp else 8.dp, // 稍微移动位置
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "offset1"
    )
    val circleOffsetTwo by animateDpAsState(
        targetValue = if (isDarkTheme) 12.dp else 18.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "offset2"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(80.dp, 100.dp),
            shape = RoundedCornerShape(20.dp),
            color = animBgColor, // 使用动画颜色
            border = BorderStroke(borderWidth, animBorderColor), // 使用动画颜色
            shadowElevation = if (isSelected) 8.dp else 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 上方的球 (Primary)
                Box(
                    modifier = Modifier
                        // 使用动画 Offset 实现移动效果
                        .offset(x = circleOffsetOne, y = -circleOffsetOne)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(animPrimaryColor.copy(alpha = 0.8f))
                )

                // 下方的球 (Surface/Accent)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // 使用动画 Offset 实现移动效果
                        .offset(x = circleOffsetTwo, y = circleOffsetTwo)
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(animSurfaceColor)
                )

                // 选中打钩 (保持居中)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color.Black else Color.White)
                            .border(1.dp, animPrimaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = animPrimaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// --- 自定义主题卡片 ---
@Composable
internal fun CustomThemeCard(isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (isSelected) 1.1f else 1f, label = "scale")
    val borderWidth by animateDpAsState(if (isSelected) 3.dp else 0.dp, label = "borderW")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(80.dp, 100.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
            shadowElevation = if (isSelected) 6.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Palette,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_custom_theme),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// --- 底部导航栏 ---
@Composable
internal fun WelcomeBottomBar(
    pagerState: androidx.compose.foundation.pager.PagerState,
    activeColor: Color,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isLastPage: Boolean
) {
    // 强制导航栏图标颜色，确保可见
    val iconColor = LocalContentColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onBack,
            enabled = pagerState.currentPage > 0,
            modifier = Modifier.size(56.dp)
        ) {
            if (pagerState.currentPage > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = iconColor
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(pagerState.pageCount) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "w")
                val color by animateColorAsState(
                    if (isSelected) activeColor else iconColor.copy(alpha = 0.3f),
                    label = "c"
                )
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.action_next),
                tint = activeColor
            )
        }
    }
}
