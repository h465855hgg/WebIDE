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
package com.web.webide.ui.terminal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalEmulator
import com.web.webide.R
import com.web.webide.core.utils.WorkspaceManager
import java.io.File

enum class SessionType {
    NORMAL,
    ALPINE
}

/**
 * 包装类：解决 TerminalSession 内部 mTitle 不可见的问题
 * 我们自己维护 Session 对象和它的标题
 */
data class SessionWrapper(
    val session: TerminalSession,
    val title: String
)

object SessionManager {
    // 使用 Compose 的 mutableStateListOf，保证 UI 能监听到列表变化
    val sessions = mutableStateListOf<SessionWrapper>()

    // 当前选中的会话索引
    var currentSessionIndex by mutableIntStateOf(0)

    // 获取当前活动的 Session 对象（供 UI 使用）
    // 如果列表为空或索引越界，返回 null
    val currentSession: TerminalSession?
        get() = if (sessions.isNotEmpty() && currentSessionIndex in sessions.indices) {
            sessions[currentSessionIndex].session
        } else null

    /**
     * 创建新会话并添加到列表
     */
    fun addNewSession(context: Context, type: SessionType = SessionType.ALPINE) {
        // 定义 Session 的回调接口
        val client = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                // 文本变化时，UI 层的 TerminalView 会自动重绘
            }

            override fun onTitleChanged(changedSession: TerminalSession) {
                // 如果你想支持动态标题（例如显示当前目录），可以在这里更新 wrapper 的 title
                // 目前暂时留空，使用静态标题
            }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                // 当 Shell 退出（例如用户输入 exit）时，自动移除该会话
                // 1. 在列表中找到包含此 session 的 wrapper
                val wrapper = sessions.find { it.session == finishedSession }
                // 2. 移除它
                if (wrapper != null) {
                    removeSession(wrapper)
                }
            }

            // --- 下面是其他必须实现但暂时不需要的接口 ---
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                e.printStackTrace()
            }
            override fun logStackTrace(tag: String, e: Exception) {
                e.printStackTrace()
            }
        }

        val session = when (type) {
            SessionType.NORMAL -> createNormalSession(context, client)
            SessionType.ALPINE -> AlpineManager.createSession(context, client)
        }

        val titleRes = when (type) {
            SessionType.NORMAL -> R.string.terminal_session_normal
            SessionType.ALPINE -> R.string.terminal_session_alpine
        }
        val title = "${context.getString(titleRes)} ${sessions.size + 1}"

        // 包装并添加到列表
        sessions.add(SessionWrapper(session, title))

        // 自动切换到新创建的会话
        currentSessionIndex = sessions.lastIndex
    }

    private fun createNormalSession(context: Context, client: TerminalSessionClient): TerminalSession {
        val workspacePath = WorkspaceManager.getWorkspacePath(context)
        val workingDirectory = listOfNotNull(AlpineManager.currentProject, workspacePath)
            .map { File(it) }
            .firstOrNull { it.isDirectory && it.canRead() && it.canExecute() }
            ?: context.filesDir
        val env = System.getenv().toMutableMap().apply {
            put("PATH", System.getenv("PATH") ?: "/system/bin:/system/xbin")
            put("HOME", context.filesDir.absolutePath)
            put("SHELL", "/system/bin/sh")
            put("TERM", "xterm-256color")
            put("LANG", "C.UTF-8")
            put("TMPDIR", context.cacheDir.absolutePath)
            put("WEBIDE_WORKSPACE", workspacePath)
            put("WEBIDE_PROJECT_DIR", AlpineManager.currentProject.orEmpty())
        }
        return TerminalSession(
            "/system/bin/sh",
            workingDirectory.absolutePath,
            arrayOf("/system/bin/sh", "-i"),
            env.map { (key, value) -> "$key=$value" }.toTypedArray(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            client
        )
    }

    /**
     * 移除指定的会话
     */
    fun removeSession(wrapper: SessionWrapper) {
        // 1. 确保底层会话停止
        wrapper.session.finishIfRunning()

        // 2. 从列表中移除
        sessions.remove(wrapper)

        // 3. 修正当前索引，防止越界
        if (currentSessionIndex >= sessions.size) {
            currentSessionIndex = (sessions.size - 1).coerceAtLeast(0)
        }
    }

    /**
     * 切换到指定索引的会话
     */
    fun switchTo(index: Int) {
        if (index in sessions.indices) {
            currentSessionIndex = index
        }
    }
}
