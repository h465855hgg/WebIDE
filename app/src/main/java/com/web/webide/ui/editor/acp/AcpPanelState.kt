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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

    var panelWidth by mutableStateOf(372.dp)
    var panelHeight by mutableStateOf(520.dp)

    /** 收起后悬浮球吸附的屏幕侧与垂直位置（0..1）。 */
    var fabSide by mutableStateOf(AcpDockSide.RIGHT)
    var fabYFraction by mutableStateOf(0.62f)

    /** 尺寸 / 停靠侧 / 悬浮球位置按用户习惯落盘；最大化、收起属于临时状态。 */
    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_WIDTH, panelWidth.value.toInt())
            .putInt(KEY_HEIGHT, panelHeight.value.toInt())
            .putString(KEY_DOCK, dockSide.name)
            .putString(KEY_FAB_SIDE, fabSide.name)
            .putFloat(KEY_FAB_Y, fabYFraction)
            .apply()
    }

    companion object {
        private const val PREFS = "acp_panel_state"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DOCK = "dock"
        private const val KEY_FAB_SIDE = "fab_side"
        private const val KEY_FAB_Y = "fab_y"

        fun create(context: Context): AcpPanelState {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AcpPanelState().apply {
                panelWidth = prefs.getInt(KEY_WIDTH, 372).dp
                panelHeight = prefs.getInt(KEY_HEIGHT, 520).dp
                dockSide = if (prefs.getString(KEY_DOCK, AcpDockSide.RIGHT.name) == AcpDockSide.LEFT.name) {
                    AcpDockSide.LEFT
                } else {
                    AcpDockSide.RIGHT
                }
                fabSide = if (prefs.getString(KEY_FAB_SIDE, AcpDockSide.RIGHT.name) == AcpDockSide.LEFT.name) {
                    AcpDockSide.LEFT
                } else {
                    AcpDockSide.RIGHT
                }
                fabYFraction = prefs.getFloat(KEY_FAB_Y, 0.62f).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
internal fun rememberAcpPanelState(): AcpPanelState {
    val context = LocalContext.current
    return remember(context) { AcpPanelState.create(context) }
}
