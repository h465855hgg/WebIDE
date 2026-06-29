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
package com.web.webide.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.web.webide.R
import com.web.webide.ui.terminal.SetupWorker
import kotlinx.coroutines.delay

/**
 * 后台环境准备进度提示条。
 *
 * 显示策略（避免打扰已经准备好的用户）：
 * - 进行中：显示进度条 + 当前阶段文案
 * - 成功：短暂显示 2.5s "已就绪 ✓" 后自动隐藏
 * - 失败/超时：显示 5s 错误提示后自动隐藏
 * - 未开始/已隐藏：不占空间
 *
 * App 启动时（MyApplication.onCreate）后台开始解压 rootfs，
 * 用户在主界面看到此 banner 就知道为什么 LSP/终端暂时用不了。
 */
@Composable
fun SetupBanner(modifier: Modifier = Modifier) {
    val progress by SetupWorker.progress.collectAsState()
    val phase = progress.phase

    // 成功/失败后自动隐藏
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(phase) {
        when (phase) {
            SetupWorker.Progress.Phase.SUCCESS -> {
                delay(2500)
                visible = false
            }
            SetupWorker.Progress.Phase.TIMEOUT,
            SetupWorker.Progress.Phase.ERROR -> {
                delay(5000)
                visible = false
            }
            SetupWorker.Progress.Phase.IDLE -> {
                visible = false
            }
            else -> {
                visible = true
            }
        }
    }

    AnimatedVisibility(
        visible = visible && phase != SetupWorker.Progress.Phase.IDLE,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        val (text, containerColor, contentColor) = when (phase) {
            SetupWorker.Progress.Phase.SUCCESS ->
                Triple(
                    stringResource(R.string.setup_banner_success),
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            SetupWorker.Progress.Phase.TIMEOUT,
            SetupWorker.Progress.Phase.ERROR ->
                Triple(
                    stringResource(R.string.setup_banner_failed),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            else -> {
                val percent = if (progress.percent in 0..100) progress.percent else 0
                Triple(
                    stringResource(R.string.setup_banner_extracting, percent),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (phase == SetupWorker.Progress.Phase.EXTRACTING_ROOTFS ||
                phase == SetupWorker.Progress.Phase.COPYING_PROOT ||
                phase == SetupWorker.Progress.Phase.FINALIZING
            ) {
                Spacer(Modifier.height(4.dp))
                val p = if (progress.percent in 0..100) progress.percent / 100f else 0f
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}
