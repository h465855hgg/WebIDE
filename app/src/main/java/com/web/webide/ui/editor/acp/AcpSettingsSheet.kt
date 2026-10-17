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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.web.webide.R

/**
 * Agent 设置：整屏 Dialog + 三个页签，避免一条长列表从头滑到尾。
 *
 * 修掉的旧问题：
 * - 透明遮罩吞点击（旧下拉是把 Box 盖在输入框上）；
 * - 主保存用预设默认命令覆盖手改命令；
 * - 环境变量只有旁边小按钮才保存；
 * - 登录后乐观显示「已登录」，实际 Agent 并未登录成功。
 */
@Composable
internal fun AcpAgentSettingsDialog(
    viewModel: AcpViewModel,
    state: AcpPanelState,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(viewModel.preset) }
    var command by remember(selected.id) {
        mutableStateOf(
            if (selected.id == viewModel.agentPresetId) viewModel.agentCommand else selected.defaultCommand
        )
    }
    var envText by remember(selected.id) {
        mutableStateOf(viewModel.envTextFor(selected.id))
    }
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var savedHint by remember { mutableStateOf(false) }

    LaunchedEffect(selected.id) {
        viewModel.refreshVersions(selected, command)
        savedHint = false
    }
    LaunchedEffect(Unit) { viewModel.refreshAgentLog() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.acp_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        R.string.acp_settings_tab_agent,
                        R.string.acp_settings_tab_permissions,
                        R.string.acp_settings_tab_diagnostics
                    ).forEachIndexed { index, labelRes ->
                        FilterChip(
                            selected = tab == index,
                            onClick = { tab = index },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (tab) {
                        0 -> {
                            item {
                                SettingsSection("Agent") {
                                    AcpAgents.PRESETS.forEach { preset ->
                                        PresetRow(
                                            preset = preset,
                                            selected = preset.id == selected.id,
                                            onSelect = {
                                                selected = preset
                                                savedHint = false
                                            }
                                        )
                                    }
                                }
                            }
                            item {
                                SettingsSection(stringResource(R.string.acp_settings_section_command)) {
                                    OutlinedTextField(
                                        value = command,
                                        onValueChange = {
                                            command = it
                                            savedHint = false
                                        },
                                        label = { Text(stringResource(R.string.acp_command_label)) },
                                        placeholder = { Text(stringResource(R.string.acp_command_hint)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        minLines = 1,
                                        maxLines = 2
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = envText,
                                        onValueChange = {
                                            envText = it
                                            savedHint = false
                                        },
                                        label = { Text(stringResource(R.string.acp_env_title)) },
                                        placeholder = { Text(stringResource(R.string.acp_env_hint)) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 76.dp, max = 160.dp),
                                        textStyle = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        minLines = 2,
                                        maxLines = 6
                                    )
                                }
                            }
                            item {
                                SettingsSection(stringResource(R.string.acp_settings_section_install)) {
                                    VersionBlock(
                                        viewModel = viewModel,
                                        preset = selected,
                                        command = command,
                                        envText = envText
                                    )
                                }
                            }
                            if (viewModel.authMethods.isNotEmpty()) {
                                item {
                                    SettingsSection(stringResource(R.string.acp_auth_title)) {
                                        Text(
                                            text = stringResource(
                                                if (viewModel.authRequested) {
                                                    R.string.acp_auth_pending_hint
                                                } else {
                                                    R.string.acp_auth_desc
                                                }
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(
                                            onClick = {
                                                viewModel.authMethods.firstOrNull()
                                                    ?.let { viewModel.authenticate(it.id) }
                                            }
                                        ) {
                                            Text(stringResource(R.string.acp_action_authenticate))
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            item {
                                SettingsSection(stringResource(R.string.acp_permission_mode_title)) {
                                    AcpApprovalMode.entries.forEach { mode ->
                                        val modeSelected = viewModel.permissions.mode == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.updateApprovalMode(mode) }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = modeSelected,
                                                onClick = { viewModel.updateApprovalMode(mode) }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = approvalModeLabel(mode),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (modeSelected) {
                                                        FontWeight.Medium
                                                    } else {
                                                        FontWeight.Normal
                                                    }
                                                )
                                                Text(
                                                    text = stringResource(approvalModeDescription(mode)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    val savedRules = viewModel.permissions.alwaysAllowKinds +
                                        viewModel.permissions.alwaysDenyKinds
                                    if (savedRules.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.acp_permission_rules) +
                                                    ": " + savedRules.size,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { viewModel.permissions.clearRules() }) {
                                                Text(
                                                    text = stringResource(R.string.acp_permission_clear_rules),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                SettingsSection(stringResource(R.string.acp_settings_section_panel)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.acp_dock_left_switch),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = state.dockSide == AcpDockSide.LEFT,
                                            onCheckedChange = {
                                                state.dockSide = if (it) {
                                                    AcpDockSide.LEFT
                                                } else {
                                                    AcpDockSide.RIGHT
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            item {
                                SettingsSection(stringResource(R.string.acp_self_check)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.acp_self_check),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { viewModel.runSelfCheck(selected, command) },
                                            enabled = !viewModel.isSelfChecking
                                        ) {
                                            Text(
                                                if (viewModel.isSelfChecking) {
                                                    stringResource(R.string.acp_self_check_running)
                                                } else {
                                                    stringResource(R.string.acp_action_run)
                                                }
                                            )
                                        }
                                    }
                                    if (viewModel.selfCheckReport.isNotEmpty()) {
                                        DiagnosticText(viewModel.selfCheckReport.joinToString("\n"))
                                    }
                                }
                            }
                            item {
                                SettingsSection(stringResource(R.string.acp_agent_log)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.weight(1f))
                                        TextButton(
                                            onClick = { viewModel.refreshAgentLog() },
                                            enabled = !viewModel.isLoadingLog
                                        ) {
                                            Text(stringResource(R.string.acp_action_refresh_log))
                                        }
                                    }
                                    if (viewModel.agentLog.isNotEmpty()) {
                                        DiagnosticText(viewModel.agentLog.takeLast(80).joinToString("\n"))
                                    } else if (!viewModel.isLoadingLog) {
                                        Text(
                                            text = stringResource(R.string.acp_agent_log_none),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (viewModel.stderrTail.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.acp_diagnostics),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        DiagnosticText(viewModel.stderrTail.takeLast(1200))
                                    }
                                }
                            }
                            item {
                                DiagnosticsActions(viewModel)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val pendingChanges = viewModel.connectionState != AcpConnectionState.READY ||
                    !viewModel.isConnectedWith(selected.id, command) ||
                    envText != viewModel.envTextFor(selected.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (savedHint && !pendingChanges) {
                        Text(
                            text = stringResource(R.string.acp_settings_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            viewModel.saveAgentEnv(selected.id, envText)
                            viewModel.applyAgentConfig(selected, command)
                            if (pendingChanges) {
                                viewModel.connect()
                                onDismiss()
                            } else {
                                savedHint = true
                            }
                        }
                    ) {
                        Text(
                            when {
                                !pendingChanges -> stringResource(R.string.action_save)
                                viewModel.connectionState == AcpConnectionState.READY ->
                                    stringResource(R.string.acp_action_reconnect)

                                else -> stringResource(R.string.acp_action_connect)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: AcpAgentPreset,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(preset.descriptionRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticsActions(viewModel: AcpViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(viewModel.diagnosticsText()))
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.acp_diag_copy_done),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        ) {
            Text(stringResource(R.string.acp_diag_copy))
        }
        TextButton(onClick = { viewModel.exportDiagnostics() }) {
            Text(stringResource(R.string.acp_diag_export))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun VersionBlock(
    viewModel: AcpViewModel,
    preset: AcpAgentPreset,
    command: String,
    envText: String
) {
    val installed = viewModel.installedVersion
    val latest = viewModel.latestVersion
    if (preset.primaryPackage == null) {
        Text(
            text = stringResource(R.string.acp_version_none),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.acp_version_installed,
                    installed ?: stringResource(R.string.acp_version_none)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            when {
                viewModel.isCheckingVersion -> Text(
                    text = stringResource(R.string.acp_version_checking),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                latest != null && viewModel.hasUpdate -> Text(
                    text = stringResource(R.string.acp_version_update_available, latest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                latest != null -> Text(
                    text = stringResource(R.string.acp_version_latest, latest),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(
            onClick = { viewModel.refreshVersions(preset, command) },
            enabled = !viewModel.isCheckingVersion
        ) {
            Text(stringResource(R.string.acp_action_check_update))
        }
    }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = {
            viewModel.saveAgentEnv(preset.id, envText)
            viewModel.applyAgentConfig(preset, command)
            viewModel.installSelectedAgent(preset)
        },
        enabled = !viewModel.isInstalling,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                viewModel.isInstalling -> stringResource(R.string.acp_installing)
                installed == null -> stringResource(R.string.acp_action_install)
                viewModel.hasUpdate -> stringResource(R.string.acp_action_update)
                else -> stringResource(R.string.acp_action_reinstall)
            }
        )
    }
    if (viewModel.installLog.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        DiagnosticText(viewModel.installLog.takeLast(4_000))
    }
}

@Composable
private fun DiagnosticText(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        )
    }
}
