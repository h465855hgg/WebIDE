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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.web.webide.R

private val MODEL_CATEGORIES = setOf(
    Acp.CAT_MODEL,
    Acp.CAT_THOUGHT_LEVEL,
    Acp.CAT_MODEL_CONFIG
)

/**
 * 模型胶囊。点击后在胶囊下方弹一个轻量菜单（不是 BottomSheet），
 * 只影响模型 / 思考等级 / 模型参数。
 */
@Composable
internal fun AcpModelChip(viewModel: AcpViewModel, onMenuOpenChange: (Boolean) -> Unit = {}) {
    val options = viewModel.configOptions.filter { it.category in MODEL_CATEGORIES }
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val model = options.firstOrNull { it.category == Acp.CAT_MODEL }
    LaunchedEffect(expanded) { onMenuOpenChange(expanded) }
    DisposableEffect(Unit) { onDispose { onMenuOpenChange(false) } }
    val label = model?.currentDisplayName
        ?: viewModel.usage.model
        ?: stringResource(R.string.acp_config_model)

    Box {
        ConfigChip(
            icon = Icons.Default.SmartToy,
            label = label,
            maxTextWidth = 96.dp,
            onClick = { expanded = true }
        )
        ConfigPopup(expanded = expanded, onDismiss = { expanded = false }) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                OptionSection(
                    option = option,
                    onSelect = { value ->
                        viewModel.setConfigOption(option.id, value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 模式胶囊。模式与模型彻底分开，菜单只列 mode。 */
@Composable
internal fun AcpModeChip(viewModel: AcpViewModel, onMenuOpenChange: (Boolean) -> Unit = {}) {
    val modeOption = viewModel.configOptions.firstOrNull { it.category == Acp.CAT_MODE }
    val legacyModes = if (modeOption == null) viewModel.sessionModes else emptyList()
    if (modeOption == null && legacyModes.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val current = legacyModes.firstOrNull { it.id == viewModel.currentModeId }
    LaunchedEffect(expanded) { onMenuOpenChange(expanded) }
    DisposableEffect(Unit) { onDispose { onMenuOpenChange(false) } }
    val label = modeOption?.currentDisplayName
        ?: current?.name
        ?: viewModel.currentModeId
        ?: stringResource(R.string.acp_config_mode)

    Box {
        ConfigChip(
            icon = Icons.Default.Tune,
            label = label,
            maxTextWidth = 84.dp,
            onClick = { expanded = true }
        )
        ConfigPopup(expanded = expanded, onDismiss = { expanded = false }) {
            if (modeOption != null) {
                OptionSection(
                    option = modeOption,
                    onSelect = { value ->
                        viewModel.setConfigOption(modeOption.id, value)
                        expanded = false
                    }
                )
            }
            legacyModes.forEach { mode ->
                SimpleSelectRow(
                    title = mode.name.ifBlank { mode.id },
                    description = mode.description,
                    selected = mode.id == viewModel.currentModeId,
                    onClick = {
                        viewModel.setMode(mode.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    maxTextWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxTextWidth)
            )
        }
    }
}

@Composable
private fun ConfigPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .heightIn(max = 460.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 300.dp)
                .heightIn(max = 460.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun OptionSection(
    option: AcpConfigOption,
    onSelect: (Any) -> Unit
) {
    Text(
        text = option.name.ifBlank { option.id },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp)
    )
    if (option.type == Acp.CTYPE_BOOLEAN) {
        val current = option.currentValueBoolean == true
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSelect(!current) }
                .padding(start = 10.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (current) {
                    stringResource(R.string.acp_config_on)
                } else {
                    stringResource(R.string.acp_config_off)
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = current, onCheckedChange = { onSelect(it) })
        }
        return
    }
    val values = if (option.groups.isNotEmpty()) {
        option.groups.flatMap { it.options }
    } else {
        option.allValues
    }
    values.forEach { value ->
        SimpleSelectRow(
            title = value.name,
            description = value.description,
            selected = value.value == option.currentValueString,
            onClick = { onSelect(value.value) }
        )
    }
}

@Composable
private fun SimpleSelectRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
