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

/** 会话列表条目（session/list）。 */
internal data class AcpSessionSummary(
    val sessionId: String,
    val title: String?,
    val cwd: String?,
    val updatedAt: String?
)

/**
 * 一次文件写入前的快照，用于「撤销 Agent 修改」。
 *
 * opencode 的 ACP 没有 revert/undo 方法，因此由客户端在收到
 * `fs/write_text_file` 时记录旧内容，回合结束后可一键回退。
 */
internal data class AcpFileSnapshot(
    val path: String,
    val existedBefore: Boolean,
    val previousContent: String?
)

/** 面板要渲染的时间线条目。 */
internal sealed interface AcpTimelineItem {
    val id: String
}

/** 用户发出的消息。 */
internal data class AcpUserMessage(
    override val id: String,
    val text: String
) : AcpTimelineItem

/** Agent 的回复（流式累积）。 */
internal data class AcpAgentMessage(
    override val id: String,
    val text: String
) : AcpTimelineItem

/** Agent 的思考过程（reasoning）。 */
internal data class AcpThoughtMessage(
    override val id: String,
    val text: String
) : AcpTimelineItem

/** 系统/状态提示，或 Agent 的 notice 更新。 */
internal data class AcpNoticeMessage(
    override val id: String,
    val text: String,
    val isError: Boolean = false
) : AcpTimelineItem

/** Agent 的执行计划。 */
internal data class AcpPlanItem(
    override val id: String,
    val entries: List<AcpPlanEntry>
) : AcpTimelineItem

internal data class AcpPlanEntry(
    val content: String,
    val priority: String,
    val status: String
)

/** 一次工具调用及其进展。 */
internal data class AcpToolCallItem(
    override val id: String,
    val toolCallId: String,
    val title: String,
    val kind: String,
    val status: String,
    val contents: List<AcpToolContent>,
    val locations: List<AcpToolLocation>,
    val rawInput: String?,
    val rawOutput: String?,
    /** 客户端收到 tool_call 的时间，用于展示耗时。 */
    val startedAt: Long = 0L,
    /** 进入 completed / failed 的时间。 */
    val finishedAt: Long? = null
) : AcpTimelineItem

internal sealed interface AcpToolContent {
    data class Text(val text: String) : AcpToolContent
    data class Diff(val path: String, val oldText: String?, val newText: String) : AcpToolContent
    data class TerminalRef(val terminalId: String) : AcpToolContent
}

internal data class AcpToolLocation(val path: String, val line: Int?)

/** Agent 请求授权时给出的一个可选项。 */
internal data class AcpPermissionOption(
    val optionId: String,
    val name: String,
    val kind: String
)

/** 等待用户决定的授权请求。 */
internal data class AcpPermissionPrompt(
    val title: String,
    val kind: String,
    val toolCallId: String,
    val options: List<AcpPermissionOption>,
    val rawInput: String?
)

/** Agent 声明的斜杠命令。 */
internal data class AcpSlashCommand(
    val name: String,
    val description: String
)

/** Agent 声明的会话模式（旧 API，仅用于兼容回退）。 */
internal data class AcpSessionMode(
    val id: String,
    val name: String,
    val description: String
)

/** Agent 声明的认证方式。 */
internal data class AcpAuthMethod(
    val id: String,
    val name: String,
    val description: String?
)

/** 连接状态。 */
internal enum class AcpConnectionState {
    DISCONNECTED,
    CONNECTING,
    READY,
    ERROR
}
