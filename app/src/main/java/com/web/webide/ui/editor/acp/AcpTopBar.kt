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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.web.webide.R

/**
 * 面板顶部状态条：上下文占用 + 权限级别。
 *
 * 统计属于「此刻的状态」，放在最上面一眼可见；点击进会话统计。
 */
@Composable
internal fun AcpTopStatusBar(
    viewModel: AcpViewModel,
    onOpenStats: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val usage = viewModel.usage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onOpenStats,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val fraction = usage.contextFraction
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        fraction > 0.85f -> MaterialTheme.colorScheme.error
                                        fraction > 0.6f -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(
                        R.string.acp_stats_short,
                        AcpUsageTracker.formatTokens(usage.contextTokens),
                        AcpUsageTracker.formatTokens(usage.contextWindow)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            onClick = onOpenPermissions,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = stringResource(
                    R.string.acp_approval_mode_short,
                    approvalModeLabel(viewModel.permissions.mode)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * 输入区上方：只放模型 / 模式胶囊。
 *
 * 历史、搜索、设置属于「会话/应用级」操作，放在头部；这里只保留和当前输入
 * 直接相关的两个切换器，避免一排图标把输入区挤满。
 */
@Composable
internal fun AcpComposerToolbar(
    viewModel: AcpViewModel,
    onOpenModel: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenFullscreen: () -> Unit
) {
    val hasModel = viewModel.configOptions.any {
        it.category == Acp.CAT_MODEL ||
            it.category == Acp.CAT_THOUGHT_LEVEL ||
            it.category == Acp.CAT_MODEL_CONFIG
    }
    val hasMode = viewModel.configOptions.any { it.category == Acp.CAT_MODE } ||
        viewModel.sessionModes.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        if (hasModel) AcpModelChip(viewModel = viewModel, onClick = onOpenModel)
        if (hasMode) AcpModeChip(viewModel = viewModel, onClick = onOpenMode)
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = onOpenTasks,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                Icons.Default.Checklist,
                contentDescription = stringResource(R.string.acp_tasks_title),
                modifier = Modifier.size(20.dp)
            )
        }
        FilledTonalIconButton(
            onClick = onOpenFullscreen,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = stringResource(R.string.acp_input_fullscreen),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToolbarTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .height(24.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

