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

import androidx.annotation.StringRes
import com.web.webide.R

/**
 * 一个 ACP Agent 预设。
 *
 * @param commands 候选启动命令，**按顺序尝试**。ACP 的启动参数（尤其
 *        `--acp` / `--experimental-acp`）会随 CLI 版本漂移，第一个跑不通就自动换下一个，
 *        避免用户被迫自己猜参数。
 * @param npmPackages 一键安装所需的 npm 包（第一个是主包，用于版本比对）。
 *        适配器类 Agent（Claude Code / Codex）需要同时装底层 CLI。
 */
internal data class AcpAgentPreset(
    val id: String,
    val displayName: String,
    val commands: List<String>,
    val npmPackages: List<String>,
    @param:StringRes val descriptionRes: Int
) {
    /** 界面上预填的命令。 */
    val defaultCommand: String get() = commands.firstOrNull().orEmpty()

    /** 用于查询最新版本的 npm 包。 */
    val primaryPackage: String? get() = npmPackages.firstOrNull()

    val supportsInstall: Boolean get() = npmPackages.isNotEmpty()
}

internal object AcpAgents {

    /**
     * 内置预设。包名与版本均于 2026-10 核对过 npm registry。
     * 命令在 proot Alpine 环境内执行。
     */
    val PRESETS: List<AcpAgentPreset> = listOf(
        AcpAgentPreset(
            id = "gemini",
            displayName = "Gemini CLI",
            commands = listOf("gemini --acp", "gemini --experimental-acp"),
            npmPackages = listOf("@google/gemini-cli"),
            descriptionRes = R.string.acp_agent_gemini_desc
        ),
        AcpAgentPreset(
            id = "qwen",
            displayName = "Qwen Code",
            commands = listOf("qwen --acp", "qwen --experimental-acp"),
            npmPackages = listOf("@qwen-code/qwen-code"),
            descriptionRes = R.string.acp_agent_qwen_desc
        ),
        AcpAgentPreset(
            id = "claude",
            displayName = "Claude Code",
            commands = listOf(
                "claude-agent-acp",
                "npx -y @zed-industries/claude-agent-acp"
            ),
            npmPackages = listOf(
                "@zed-industries/claude-agent-acp",
                "@anthropic-ai/claude-code"
            ),
            descriptionRes = R.string.acp_agent_claude_desc
        ),
        AcpAgentPreset(
            id = "codex",
            displayName = "Codex",
            commands = listOf(
                "codex-acp",
                "npx -y @agentclientprotocol/codex-acp"
            ),
            npmPackages = listOf(
                "@agentclientprotocol/codex-acp",
                "@openai/codex"
            ),
            descriptionRes = R.string.acp_agent_codex_desc
        ),
        AcpAgentPreset(
            id = "opencode",
            displayName = "OpenCode",
            commands = listOf("opencode acp"),
            npmPackages = listOf("opencode-ai"),
            descriptionRes = R.string.acp_agent_opencode_desc
        ),
        AcpAgentPreset(
            id = "cline",
            displayName = "Cline",
            commands = listOf("cline --acp", "cline acp"),
            npmPackages = listOf("cline"),
            descriptionRes = R.string.acp_agent_cline_desc
        ),
        AcpAgentPreset(
            id = "crush",
            displayName = "Crush",
            commands = listOf("crush acp", "crush --acp"),
            npmPackages = listOf("@charmland/crush"),
            descriptionRes = R.string.acp_agent_crush_desc
        ),
        AcpAgentPreset(
            id = "custom",
            displayName = "Custom",
            commands = emptyList(),
            npmPackages = emptyList(),
            descriptionRes = R.string.acp_agent_custom_desc
        )
    )

    val DEFAULT: AcpAgentPreset = PRESETS.first()

    fun byId(id: String?): AcpAgentPreset =
        PRESETS.firstOrNull { it.id == id } ?: DEFAULT

    /**
     * 组装实际要尝试的命令序列：用户当前命令优先，其后跟预设的其它候选。
     * 自定义预设不做回退，尊重用户输入。
     */
    fun candidatesFor(preset: AcpAgentPreset, userCommand: String): List<String> {
        val trimmed = userCommand.trim()
        if (preset.id == "custom" || preset.commands.isEmpty()) {
            return if (trimmed.isEmpty()) emptyList() else listOf(trimmed)
        }
        val rest = preset.commands.filter { it != trimmed }
        return if (trimmed.isEmpty()) preset.commands else listOf(trimmed) + rest
    }
}

/**
 * 把命令字符串拆成 argv。支持单引号、双引号与反斜杠转义，
 * 这样用户可以写 `sh -c "cd app && npm run build"` 这类命令。
 */
internal fun splitCommand(raw: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var hasToken = false
    var index = 0
    while (index < raw.length) {
        val ch = raw[index]
        when {
            quote != null -> {
                if (ch == quote) {
                    quote = null
                } else if (ch == '\\' && index + 1 < raw.length) {
                    index++
                    current.append(raw[index])
                } else {
                    current.append(ch)
                }
            }

            ch == '\'' || ch == '"' -> {
                quote = ch
                hasToken = true
            }

            ch == '\\' && index + 1 < raw.length -> {
                index++
                current.append(raw[index])
                hasToken = true
            }

            ch.isWhitespace() -> {
                if (hasToken) {
                    tokens.add(current.toString())
                    current.setLength(0)
                    hasToken = false
                }
            }

            else -> {
                current.append(ch)
                hasToken = true
            }
        }
        index++
    }
    if (hasToken) tokens.add(current.toString())
    return tokens
}
