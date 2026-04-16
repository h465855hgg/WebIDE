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
package com.web.webide.ui.editor.git

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.web.webide.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun GitGraphListCompact(commits: List<GitCommitUI>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        itemsIndexed(commits) { index, commit ->
            // 为了画出连续的线，我们需要知道下一个 commit 的 totalLanes
            // 但在 compose 绘制中，我们只画 "Downwards" 线，所以只需要当前 commit 的数据即可
            GitLogItemAligned(commit)
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun GitLogItemAligned(commit: GitCommitUI) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 配色池 (View层使用，保持一致)
    val laneColors = listOf(
        Color(0xFFFF5252), Color(0xFF40C4FF), Color(0xFFE040FB),
        Color(0xFF69F0AE), Color(0xFFFFAB40), Color(0xFFFFD740),
        Color(0xFF9E9E9E), Color(0xFF795548)
    )

    // 尺寸常量
    val rowHeight = if (expanded) 80.dp else 40.dp // IDEA 默认是比较矮的
    val graphWidth = 40.dp // 🔥 强制固定左侧宽度，保证右侧对齐！
    val laneW = 12.dp      // 轨道间距
    val dotR = 5.dp        // 圆点半径

    val surfaceColor = MaterialTheme.colorScheme.surface
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (expanded) highlightColor else Color.Transparent)
            .clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        // --- 1. 左侧绘图区 (固定宽度) ---
        Box(
            modifier = Modifier
                .width(graphWidth)
                .fillMaxHeight()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2

                // 1. 画穿透竖线 (Passing Lines)
                // 逻辑：假设最大轨道数为 totalLanes。
                // 凡是不等于 myLane 的，且在 parentLanes 范围内的(或者上一行遗留的)，通常都需要画竖线。
                // 这里简化：除了 myLane，其他 < totalLanes 的都画竖线
                // 注意：这只是为了视觉连贯。

                // 更精确的逻辑：画所有 "Active" 的线。
                // 也就是：所有 < max(lane, parentLanes.max) 的轨道，如果不是当前点，就画线连接上下。
                // 这里我们画所有非当前的轨道直线。
                val maxLaneIdx = max(commit.lane, commit.parentLanes.maxOrNull() ?: 0)
                // 限制一下最大绘制轨道，防止画到屏幕外面
                val drawLimit = 4

                for (i in 0..minOf(maxLaneIdx + 1, drawLimit)) {
                    if (i != commit.lane) {
                        val x = (i * laneW.toPx()) + (laneW.toPx() / 2) + 6f
                        val color = laneColors[i % laneColors.size]

                        // 画一条贯穿线
                        drawLine(
                            color = color,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // 2. 画当前点 -> 父节点的线 (Bezier)
                val myX = (commit.lane * laneW.toPx()) + (laneW.toPx() / 2) + 6f

                // 先画上半截 (从天上来)，连接到自己
                drawLine(
                    color = commit.color,
                    start = Offset(myX, 0f),
                    end = Offset(myX, centerY),
                    strokeWidth = 2.dp.toPx()
                )

                commit.parentLanes.forEach { pLane ->
                    if (pLane <= drawLimit) { // 只画可视区域内的线
                        val pX = (pLane * laneW.toPx()) + (laneW.toPx() / 2) + 6f
                        val color = commit.color // 用自己的颜色连接父节点

                        val path = Path().apply {
                            moveTo(myX, centerY)
                            if (pLane == commit.lane) {
                                lineTo(pX, size.height)
                            } else {
                                // S 型曲线
                                cubicTo(
                                    myX, size.height * 0.9f,
                                    pX, centerY + (size.height - centerY) * 0.1f,
                                    pX, size.height
                                )
                            }
                        }
                        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                    }
                }

                // 3. 画圆点 (在最上层)
                if (commit.lane <= drawLimit) {
                    drawCircle(color = surfaceColor, radius = dotR.toPx() + 3f, center = Offset(myX, centerY))
                    drawCircle(color = commit.color, radius = dotR.toPx(), center = Offset(myX, centerY))
                }
            }
        }

        // --- 2. 右侧文字区 (对齐！) ---
        Column(
            modifier = Modifier
                .weight(1f) // 占据剩余所有空间
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // 第一行：[标签] 消息
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (commit.refs.isNotEmpty()) {
                    commit.refs.forEach { ref ->
                        GitRefChipNano(ref)
                        Spacer(Modifier.width(4.dp))
                    }
                }

                Text(
                    text = if (expanded) commit.fullMessage else commit.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    maxLines = if (expanded) 10 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f) // 让文字自适应宽度
                )
            }

            // 第二行：作者 · 时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(
                    text = commit.author,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = getRelativeTimeShort(context, commit.time),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.weight(1f)) // 撑开中间

                // 只有展开时显示 Hash 复制
                if (expanded) {
                    Icon(
                        Icons.Default.ContentCopy, null,
                        Modifier.size(12.dp).clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.git_clipboard_hash_label), commit.hash)
                            clipboard.setPrimaryClip(clip)
                        },
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = commit.shortHash,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun GitRefChipNano(ref: GitRefUI) {
    // 极简风格标签
    val bgColor = when(ref.type) {
        RefType.HEAD -> Color(0xFF607D8B)
        RefType.LOCAL_BRANCH -> Color(0xFF009688)
        RefType.REMOTE_BRANCH -> Color(0xFF673AB7)
        RefType.TAG -> Color(0xFFEF6C00)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(3.dp),
        modifier = Modifier.height(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
            Text(
                text = ref.name,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

private fun getRelativeTimeShort(context: Context, timeMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timeMs
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> context.getString(R.string.git_time_now)
        minutes < 60 -> context.getString(R.string.git_time_minutes_short, minutes)
        hours < 24 -> context.getString(R.string.git_time_hours_short, hours)
        days < 30 -> context.getString(R.string.git_time_days_short, days)
        else -> DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(timeMs))
    }
}
