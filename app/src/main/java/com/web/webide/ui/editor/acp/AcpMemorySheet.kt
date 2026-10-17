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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.web.webide.R

/**
 * 记忆与行为面板。
 *
 * ACP 没有 system prompt，所以这里做两件事：
 * - 把项目约定保存成 `.webide/agent/memory.md`（可版本化、可在文件树里看）；
 * - 新会话首轮把记忆 + 协作风格拼进 prompt（可在历史里裁掉，不污染阅读）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AcpMemorySheet(
    viewModel: AcpViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memory = viewModel.memory
    var text by remember { mutableStateOf(memory.memoryText) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshMemory() }
    LaunchedEffect(memory.loadVersion) {
        if (memory.isLoaded) {
            text = memory.memoryText
            saved = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.acp_memory_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.acp_memory_project),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.acp_memory_behavior),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AcpBehaviorPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = memory.behavior == preset,
                        onClick = { viewModel.setBehaviorPreset(preset) },
                        label = { Text(behaviorLabel(preset)) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.acp_memory_inject),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = memory.injectOnNewSession,
                    onCheckedChange = { viewModel.setMemoryInjection(it) }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    saved = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 240.dp),
                placeholder = {
                    Text(
                        text = stringResource(
                            if (memory.memoryText.isBlank()) R.string.acp_memory_empty
                            else R.string.acp_memory_hint
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                minLines = 6,
                maxLines = 12,
                shape = RoundedCornerShape(12.dp)
            )

            memory.loadedWorkspace?.let { workspace ->
                Text(
                    text = stringResource(
                        R.string.acp_memory_file,
                        memory.memoryFile(workspace).absolutePath
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        viewModel.saveMemoryText(text)
                        saved = true
                    }
                ) {
                    Text(stringResource(R.string.acp_memory_save))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.writeMemoryToAgentsMd() }) {
                    Text(stringResource(R.string.acp_memory_write_agents))
                }
                if (saved) {
                    Text(
                        text = stringResource(R.string.acp_memory_saved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun behaviorLabel(preset: AcpBehaviorPreset): String = stringResource(
    when (preset) {
        AcpBehaviorPreset.BALANCED -> R.string.acp_memory_behavior_balanced
        AcpBehaviorPreset.CONCISE -> R.string.acp_memory_behavior_concise
        AcpBehaviorPreset.THOROUGH -> R.string.acp_memory_behavior_thorough
        AcpBehaviorPreset.PLAN_FIRST -> R.string.acp_memory_behavior_plan_first
        AcpBehaviorPreset.REVIEW -> R.string.acp_memory_behavior_review
    }
)
