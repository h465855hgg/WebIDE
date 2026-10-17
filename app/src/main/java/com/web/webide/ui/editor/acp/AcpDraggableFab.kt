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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.web.webide.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 收起后的悬浮球。
 *
 * - 垂直方向可拖动，松手后吸附到左右屏幕边缘；
 * - 吸附时只露出一半（半隐藏），点一下展开面板；
 * - 左右侧与垂直位置会记住，下次收起还是老地方。
 */
@Composable
internal fun AcpDraggableFab(
    containerWidth: Dp,
    containerHeight: Dp,
    side: AcpDockSide,
    yFraction: Float,
    connectionState: AcpConnectionState,
    isPrompting: Boolean,
    hasPendingPermission: Boolean,
    onSideChange: (AcpDockSide) -> Unit,
    onYFractionChange: (Float) -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val fabSize = 56.dp
    val sizePx = with(density) { fabSize.toPx() }
    val widthPx = with(density) { containerWidth.toPx() }
    val heightPx = with(density) { containerHeight.toPx() }

    fun targetX(targetSide: AcpDockSide, snapped: Boolean): Float = when {
        !snapped && targetSide == AcpDockSide.RIGHT -> (widthPx - sizePx - 8f).coerceAtLeast(0f)
        !snapped -> 8f
        targetSide == AcpDockSide.RIGHT -> (widthPx - sizePx * 0.45f).coerceAtLeast(0f)
        else -> -sizePx * 0.55f
    }

    fun targetY(fraction: Float): Float {
        val travel = (heightPx - sizePx).coerceAtLeast(0f)
        return (travel * fraction.coerceIn(0f, 1f)).coerceIn(0f, travel)
    }

    val offsetX = remember { Animatable(targetX(side, snapped = false)) }
    val offsetY = remember { Animatable(targetY(yFraction)) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var snapped by remember { mutableStateOf(false) }
    var idleTick by remember { mutableIntStateOf(0) }

    // 停留 3 秒没有操作才吸附到侧边；拖动期间与刚松手时保持完整可见。
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

    val container = when {
        hasPendingPermission -> MaterialTheme.colorScheme.tertiaryContainer
        connectionState == AcpConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
        connectionState == AcpConnectionState.READY -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        hasPendingPermission -> MaterialTheme.colorScheme.onTertiaryContainer
        connectionState == AcpConnectionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        connectionState == AcpConnectionState.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .size(fabSize)
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
                                    .coerceIn(-sizePx * 0.55f, widthPx - sizePx * 0.45f)
                            )
                            offsetY.snapTo(
                                (offsetY.value + dragAmount.y)
                                    .coerceIn(0f, (heightPx - sizePx).coerceAtLeast(0f))
                            )
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        val newSide = if (offsetX.value + sizePx / 2 < widthPx / 2) {
                            AcpDockSide.LEFT
                        } else {
                            AcpDockSide.RIGHT
                        }
                        val travel = (heightPx - sizePx).coerceAtLeast(1f)
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
            shape = CircleShape,
            color = container,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp
        ) {
            Box(
                modifier = Modifier.background(container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isPrompting -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = content
                    )

                    hasPendingPermission -> Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(R.string.acp_action_expand),
                        modifier = Modifier.size(22.dp),
                        tint = content
                    )

                    else -> Icon(
                        painter = painterResource(R.drawable.acp_logo),
                        contentDescription = stringResource(R.string.acp_action_expand),
                        modifier = Modifier.size(30.dp, 12.dp),
                        tint = content
                    )
                }
            }
        }
    }
}
