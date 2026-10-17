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
package com.web.webide.ui.editor.acp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.web.webide.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 收起后的 ACP chip。
 *
 * - 纯黑灰底、胶囊形，不跟随主题色；
 * - 垂直方向可拖动，松手后吸附到左右屏幕边缘；
 * - 停留 3 秒自动吸附半隐藏，点一下展开面板；
 * - 左右侧与垂直位置会记住。
 */
@Composable
internal fun AcpDraggableFab(
    containerWidth: Dp,
    containerHeight: Dp,
    side: AcpDockSide,
    yFraction: Float,
    brandModifier: Modifier = Modifier,
    connectionState: AcpConnectionState,
    isPrompting: Boolean,
    hasPendingPermission: Boolean,
    onSideChange: (AcpDockSide) -> Unit,
    onYFractionChange: (Float) -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val chipWidth = 96.dp
    val chipHeight = 40.dp
    val widthPx = with(density) { containerWidth.toPx() }
    val heightPx = with(density) { containerHeight.toPx() }
    val chipWpx = with(density) { chipWidth.toPx() }
    val chipHpx = with(density) { chipHeight.toPx() }

    fun targetX(targetSide: AcpDockSide, snapped: Boolean): Float = when {
        !snapped && targetSide == AcpDockSide.RIGHT -> (widthPx - chipWpx - 8f).coerceAtLeast(0f)
        !snapped -> 8f
        targetSide == AcpDockSide.RIGHT -> (widthPx - chipWpx * 0.45f).coerceAtLeast(0f)
        else -> -chipWpx * 0.55f
    }

    fun targetY(fraction: Float): Float {
        val travel = (heightPx - chipHpx).coerceAtLeast(0f)
        return (travel * fraction.coerceIn(0f, 1f)).coerceIn(0f, travel)
    }

    val offsetX = remember { Animatable(targetX(side, snapped = false)) }
    val offsetY = remember { Animatable(targetY(yFraction)) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var snapped by remember { mutableStateOf(false) }
    var idleTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(idleTick) {
        delay(3_000)
        snapped = true
    }

    LaunchedEffect(snapped, side, widthPx, heightPx) {
        if (!dragging) {
            offsetX.animateTo(targetX(side, snapped), tween(260))
            offsetY.animateTo(targetY(yFraction), tween(260))
        }
    }

    val statusColor = when {
        hasPendingPermission -> Color(0xFFF59E0B)
        connectionState == AcpConnectionState.READY -> Color(0xFF22C55E)
        connectionState == AcpConnectionState.ERROR -> Color(0xFFEF4444)
        connectionState == AcpConnectionState.CONNECTING -> Color(0xFFF59E0B)
        else -> Color(0xFF9CA3AF)
    }
    val chipContainer = Color(0xFF26262B)
    val chipContent = Color(0xFFF5F5F5)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .size(width = chipWidth, height = chipHeight)
            .pointerInput(containerWidth, containerHeight) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        snapped = false
                        idleTick++
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + dragAmount.x)
                                    .coerceIn(-chipWpx * 0.55f, widthPx - chipWpx * 0.45f)
                            )
                            offsetY.snapTo(
                                (offsetY.value + dragAmount.y)
                                    .coerceIn(0f, (heightPx - chipHpx).coerceAtLeast(0f))
                            )
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        val newSide = if (offsetX.value + chipWpx / 2 < widthPx / 2) {
                            AcpDockSide.LEFT
                        } else {
                            AcpDockSide.RIGHT
                        }
                        val travel = (heightPx - chipHpx).coerceAtLeast(1f)
                        val fraction = (offsetY.value / travel).coerceIn(0f, 1f)
                        onSideChange(newSide)
                        onYFractionChange(fraction)
                        snapped = false
                        idleTick++
                        scope.launch {
                            offsetX.animateTo(targetX(newSide, snapped = false), tween(220))
                            offsetY.animateTo(targetY(fraction), tween(220))
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        snapped = false
                        idleTick++
                        scope.launch {
                            offsetX.animateTo(targetX(side, snapped = false), tween(220))
                            offsetY.animateTo(targetY(yFraction), tween(220))
                        }
                    }
                )
            }
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(chipHeight / 2),
            color = chipContainer,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = brandModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.acp_logo),
                        contentDescription = stringResource(R.string.acp_action_expand),
                        modifier = Modifier.size(24.dp, 10.dp),
                        tint = chipContent
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "ACP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = chipContent
                    )
                }
                Spacer(Modifier.weight(1f))
                when {
                    isPrompting -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = chipContent
                    )

                    hasPendingPermission -> Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFF59E0B)
                    )

                    else -> Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }
        }
    }
}
