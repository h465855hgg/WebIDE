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


package com.web.webide.ui.editor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorToolbar(
    onSave: () -> Unit,
    onSearch: () -> Unit,
    onJump: () -> Unit,      // 新增
    onCreate: () -> Unit,    // 新增
    onPalette: () -> Unit,   // 新增
    onBuild: () -> Unit,
    onFormat: () -> Unit,
    isBuilding: Boolean,
    hasWebAppConfig: Boolean
) {
    Surface(
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarItem("保存", onSave)
            ToolbarItem("新建", onCreate)
            ToolbarItem("搜索", onSearch)
            ToolbarItem("跳转指定", onJump)
            ToolbarItem( "格式化", onFormat)
            ToolbarItem("配色", onPalette)

            if (hasWebAppConfig) {
                 ToolbarItem(
                    label = if (isBuilding) "构建中..." else "构建APK",
                    onClick = onBuild,
                    enabled = !isBuilding,
                    colors = if (isBuilding) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    VerticalDivider(modifier = Modifier.padding(vertical = 12.dp).width(1.dp))

    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(20.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (enabled) colors else MaterialTheme.colorScheme.outline
            )
        }
    }
}