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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.web.webide.R

/**
 * MCP 服务器管理。
 *
 * 配置会在 `session/new` / `session/load` 时通过 `mcpServers` 下发给 Agent；
 * 修改后需要新建（或重新加载）会话才会生效——这一点在界面上直接写明，避免误解。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpMcpSheet(
    viewModel: AcpViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mcp = viewModel.mcp
    var editing by remember { mutableStateOf<AcpMcpServer?>(null) }

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
                    text = stringResource(R.string.acp_mcp_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { editing = AcpMcpServer(name = "") }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.acp_mcp_add),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.acp_action_hide),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (mcp.servers.isEmpty()) {
                Text(
                    text = stringResource(R.string.acp_mcp_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(mcp.servers, key = { it.id }) { server ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp, end = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = server.summary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Switch(
                                        checked = server.enabled,
                                        onCheckedChange = { mcp.setEnabled(server.id, it) }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(
                                            if (server.transport == AcpMcpTransport.STDIO) {
                                                R.string.acp_mcp_stdio
                                            } else {
                                                R.string.acp_mcp_url
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { editing = server },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.acp_mcp_edit),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { mcp.remove(server.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.acp_mcp_delete),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { initial ->
        AcpMcpEditorDialog(
            initial = initial,
            onSave = { server ->
                viewModel.mcp.upsert(server)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun AcpMcpEditorDialog(
    initial: AcpMcpServer,
    onSave: (AcpMcpServer) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var transport by remember { mutableStateOf(initial.transport) }
    var commandText by remember {
        mutableStateOf(
            if (initial.transport == AcpMcpTransport.STDIO) {
                listOf(initial.command).filter { it.isNotBlank() }.plus(initial.args).joinToString(" ")
            } else {
                ""
            }
        )
    }
    var url by remember { mutableStateOf(initial.url) }
    var envText by remember { mutableStateOf(keyValueLines(initial.env)) }
    var error by remember { mutableStateOf<String?>(null) }

    val nameError = stringResource(R.string.acp_mcp_error_name)
    val targetError = stringResource(R.string.acp_mcp_error_target)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial.name.isBlank()) R.string.acp_mcp_add else R.string.acp_mcp_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.acp_mcp_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = transport == AcpMcpTransport.STDIO,
                        onClick = { transport = AcpMcpTransport.STDIO },
                        label = { Text(stringResource(R.string.acp_mcp_stdio)) }
                    )
                    FilterChip(
                        selected = transport == AcpMcpTransport.URL,
                        onClick = { transport = AcpMcpTransport.URL },
                        label = { Text(stringResource(R.string.acp_mcp_url)) }
                    )
                }

                if (transport == AcpMcpTransport.STDIO) {
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text(stringResource(R.string.acp_mcp_command)) },
                        placeholder = { Text(stringResource(R.string.acp_mcp_command_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 2,
                        maxLines = 3
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.acp_mcp_url_field)) },
                        placeholder = { Text(stringResource(R.string.acp_mcp_url_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                OutlinedTextField(
                    value = envText,
                    onValueChange = { envText = it },
                    label = { Text(stringResource(R.string.acp_mcp_env)) },
                    placeholder = { Text(stringResource(R.string.acp_mcp_env_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    maxLines = 4
                )

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        error = nameError
                        return@TextButton
                    }
                    val env = parseKeyValueLines(envText)
                    if (transport == AcpMcpTransport.STDIO) {
                        val parts = splitCommand(commandText)
                        if (parts.isEmpty()) {
                            error = targetError
                            return@TextButton
                        }
                        onSave(
                            initial.copy(
                                name = trimmedName,
                                transport = AcpMcpTransport.STDIO,
                                command = parts.first(),
                                args = parts.drop(1),
                                url = "",
                                env = env
                            )
                        )
                    } else {
                        if (url.isBlank()) {
                            error = targetError
                            return@TextButton
                        }
                        onSave(
                            initial.copy(
                                name = trimmedName,
                                transport = AcpMcpTransport.URL,
                                command = "",
                                args = emptyList(),
                                url = url.trim(),
                                env = env
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
