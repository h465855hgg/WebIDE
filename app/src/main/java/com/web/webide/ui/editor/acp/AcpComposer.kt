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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.web.webide.R

/**
 * 输入区。
 *
 * 使用 [TextFieldValue] 而不是 String，是为了在中文输入法组词期间保留
 * composition/selection —— 直接回写 String 会把拼音候选打断，这是「全拼打不出字」
 * 的典型原因之一。面板在软键盘弹出时会整体上移/展开，输入框不会再被键盘盖住。
 */
@Composable
internal fun AcpComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isPrompting: Boolean,
    connected: Boolean,
    readOnlyReplay: Boolean,
    commands: List<AcpSlashCommand>,
    onFullscreen: () -> Unit,
    onOpenTasks: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val slashQuery = value.text.takeIf { it.startsWith("/") && !it.contains(' ') && !it.contains('\n') }
    val matches = remember(slashQuery, commands) {
        if (slashQuery == null) {
            emptyList()
        } else {
            commands.filter { it.name.startsWith(slashQuery, ignoreCase = true) }.take(8)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = matches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                matches.forEach { command ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val text = "${command.name} "
                                onValueChange(
                                    TextFieldValue(
                                        text = text,
                                        selection = androidx.compose.ui.text.TextRange(text.length)
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = command.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (command.description.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 文件夹标签式入口：任务清单 + 全屏，贴在输入卡片右上角外侧并与其同色融合。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
        ) {
            FolderTab(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.acp_tasks_title),
                onClick = onOpenTasks
            )
            FolderTab(
                icon = Icons.Default.OpenInFull,
                label = stringResource(R.string.acp_input_fullscreen_tab),
                onClick = onFullscreen
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 0.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 2.dp)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 40.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.acp_composer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    if (isPrompting) {
                        FilledIconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.acp_action_cancel),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = connected && !readOnlyReplay && value.text.isNotBlank(),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.acp_action_send),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 上下文占用 + 权限级别的一行摘要。 */
@Composable
internal fun AcpContextRow(
    usage: AcpUsageTracker,
    approvalMode: AcpApprovalMode,
    onOpenStats: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenStats)
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                val fraction = usage.contextFraction
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                            .clip(CircleShape)
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
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    R.string.acp_stats_short,
                    AcpUsageTracker.formatTokens(usage.contextTokens),
                    AcpUsageTracker.formatTokens(usage.contextWindow)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenPermissions)
        ) {
            Text(
                text = stringResource(
                    R.string.acp_approval_mode_short,
                    approvalModeLabel(approvalMode)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
internal fun approvalModeLabel(mode: AcpApprovalMode): String = stringResource(
    when (mode) {
        AcpApprovalMode.ASK -> R.string.acp_permission_mode_ask
        AcpApprovalMode.SMART -> R.string.acp_permission_mode_smart
        AcpApprovalMode.AUTO -> R.string.acp_permission_mode_auto
        AcpApprovalMode.READ_ONLY -> R.string.acp_permission_mode_readonly
    }
)

/** 工具类型的中文/英文展示名（走 i18n，不再拼接硬编码中文）。 */
@Composable
internal fun permissionKindLabel(kind: String): String = stringResource(
    when (kind) {
        Acp.KIND_READ -> R.string.acp_kind_read
        Acp.KIND_EDIT -> R.string.acp_kind_edit
        Acp.KIND_DELETE -> R.string.acp_kind_delete
        Acp.KIND_MOVE -> R.string.acp_kind_move
        Acp.KIND_SEARCH -> R.string.acp_kind_search
        Acp.KIND_EXECUTE -> R.string.acp_kind_execute
        Acp.KIND_THINK -> R.string.acp_kind_think
        Acp.KIND_FETCH -> R.string.acp_kind_fetch
        Acp.KIND_SWITCH_MODE -> R.string.acp_kind_switch_mode
        else -> R.string.acp_kind_other
    }
)

internal fun approvalModeDescription(mode: AcpApprovalMode): Int = when (mode) {
    AcpApprovalMode.ASK -> R.string.acp_permission_mode_ask_desc
    AcpApprovalMode.SMART -> R.string.acp_permission_mode_smart_desc
    AcpApprovalMode.AUTO -> R.string.acp_permission_mode_auto_desc
    AcpApprovalMode.READ_ONLY -> R.string.acp_permission_mode_readonly_desc
}

@Composable
private fun FolderTab(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpFullscreenInputDialog(
    initial: TextFieldValue,
    isPrompting: Boolean,
    onDismiss: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }

    Dialog(
        onDismissRequest = { onDismiss(value) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismiss(value) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                    Text(
                        text = stringResource(R.string.acp_input_fullscreen),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onSend(value.text) },
                        enabled = !isPrompting && value.text.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.acp_action_send)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.acp_composer_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

