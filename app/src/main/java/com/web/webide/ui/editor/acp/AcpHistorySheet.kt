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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.web.webide.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 会话历史：本地记录（不依赖 Agent）+ Agent 侧会话。
 *
 * 本地记录是这次改造的核心：即使 Agent 未连接、Agent 不支持 session/list，
 * 也能看到完整对话、搜索、重命名、置顶、导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpHistorySheet(
    viewModel: AcpViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) }
    var renameTarget by remember { mutableStateOf<AcpSessionMeta?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<AcpSessionMeta?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalSessions()
    }
    LaunchedEffect(tab) {
        if (tab == 1) viewModel.refreshSessions()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.acp_sessions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.newSession() },
                    enabled = viewModel.connectionState == AcpConnectionState.READY,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.acp_action_new_session),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.refreshLocalSessions()
                        if (tab == 1) viewModel.refreshSessions()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.acp_action_refresh_sessions),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(stringResource(R.string.acp_sessions_local)) }
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text(stringResource(R.string.acp_sessions_agent)) }
                )
            }

            if (tab == 0) {
                OutlinedTextField(
                    value = viewModel.localSearchQuery,
                    onValueChange = viewModel::updateLocalSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.acp_sessions_search_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                0 -> LocalSessionList(
                    viewModel = viewModel,
                    menuFor = menuFor,
                    onMenuFor = { menuFor = it },
                    onOpen = { meta ->
                        viewModel.openLocalSession(meta.id)
                        onDismiss()
                    },
                    onRename = { meta ->
                        renameTarget = meta
                        renameText = meta.title
                    },
                    onDelete = { meta -> deleteTarget = meta }
                )

                else -> AgentSessionList(
                    viewModel = viewModel,
                    onOpen = { id ->
                        viewModel.loadSession(id)
                        onDismiss()
                    }
                )
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.acp_sessions_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameLocalSession(target.id, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.acp_sessions_delete)) },
            text = { Text(stringResource(R.string.acp_sessions_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLocalSession(target.id)
                        deleteTarget = null
                    }
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun LocalSessionList(
    viewModel: AcpViewModel,
    menuFor: String?,
    onMenuFor: (String?) -> Unit,
    onOpen: (AcpSessionMeta) -> Unit,
    onRename: (AcpSessionMeta) -> Unit,
    onDelete: (AcpSessionMeta) -> Unit
) {
    val sessions = viewModel.localSessions
    if (viewModel.isLoadingLocalSessions && sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(
                    if (viewModel.localSearchQuery.isBlank()) {
                        R.string.acp_sessions_empty_local
                    } else {
                        R.string.acp_sessions_empty_search
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Text(
        text = stringResource(R.string.acp_sessions_count, sessions.size),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(sessions, key = { it.id }) { meta ->
            val isCurrent = viewModel.currentSessionMeta?.id == meta.id
            Surface(
                onClick = { onOpen(meta) },
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 9.dp, bottom = 7.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (meta.pinned) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = stringResource(R.string.acp_sessions_pin),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(13.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = meta.title.ifBlank { stringResource(R.string.acp_session_untitled) },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            IconButton(
                                onClick = { onMenuFor(meta.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.acp_action_more),
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = menuFor == meta.id,
                                onDismissRequest = { onMenuFor(null) }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.acp_sessions_rename)) },
                                    onClick = {
                                        onMenuFor(null)
                                        onRename(meta)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (meta.pinned) R.string.acp_sessions_unpin
                                                else R.string.acp_sessions_pin
                                            )
                                        )
                                    },
                                    onClick = {
                                        onMenuFor(null)
                                        viewModel.toggleLocalSessionPin(meta.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.acp_sessions_export)) },
                                    onClick = {
                                        onMenuFor(null)
                                        viewModel.exportLocalSession(meta.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.acp_sessions_delete)) },
                                    onClick = {
                                        onMenuFor(null)
                                        onDelete(meta)
                                    }
                                )
                            }
                        }
                    }
                    if (meta.preview.isNotBlank()) {
                        Text(
                            text = meta.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 3.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.acp_session_turns, meta.turns),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (meta.inputTokens + meta.outputTokens > 0) {
                            Text(
                                text = " · " + stringResource(
                                    R.string.acp_session_tokens,
                                    AcpUsageTracker.formatTokens(meta.inputTokens + meta.outputTokens)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = " · " + meta.agentName.ifBlank { meta.agentId },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        formatSessionTime(meta.updatedAt)?.let { time ->
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSessionList(
    viewModel: AcpViewModel,
    onOpen: (String) -> Unit
) {
    val sessions = viewModel.sessions
    if (viewModel.isLoadingSessions && sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(
                    if (viewModel.supportsSessionList) {
                        R.string.acp_sessions_empty
                    } else {
                        R.string.acp_hint_agent_no_session_list
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(sessions, key = { it.sessionId }) { session ->
            Surface(
                onClick = { onOpen(session.sessionId) },
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title ?: session.sessionId.take(18),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val cwd = session.cwd
                        if (!cwd.isNullOrBlank()) {
                            Text(
                                text = cwd,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSessionTime(raw: Long): String? {
    if (raw <= 0L) return null
    return runCatching {
        DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(Instant.ofEpochMilli(raw).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}
