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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

internal enum class AcpDockSide {
    RIGHT,
    LEFT
}

/** ACP 面板的窗口状态（停靠侧、尺寸、最大化、收起为气泡）。 */
@Stable
internal class AcpPanelState {
    var dockSide by mutableStateOf(AcpDockSide.RIGHT)
    var isMaximized by mutableStateOf(false)

    /** 收起为悬浮气泡，面板主体隐藏但连接保持。 */
    var isCollapsed by mutableStateOf(false)

    var panelWidth by mutableStateOf(340.dp)
    var panelHeight by mutableStateOf(460.dp)
}

@Composable
internal fun rememberAcpPanelState(): AcpPanelState = remember { AcpPanelState() }
