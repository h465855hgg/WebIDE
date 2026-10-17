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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.web.webide.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 协作风格预设。注入到新会话的第一条 prompt 里。 */
internal enum class AcpBehaviorPreset {
    /** 不额外注入任何指令。 */
    BALANCED,
    CONCISE,
    THOROUGH,
    PLAN_FIRST,
    REVIEW;

    companion object {
        fun fromKey(key: String?): AcpBehaviorPreset =
            entries.firstOrNull { it.name == key } ?: BALANCED
    }
}

/**
 * 项目记忆与行为指令。
 *
 * ACP 没有 system prompt 字段，所以客户端只能用两种「所有 Agent 都能吃到」的通道：
 *
 * 1. **项目文件**：`.webide/agent/memory.md`，以及可选的 `AGENTS.md` 受管区块；
 * 2. **首轮 prompt 注入**：新会话第一条消息前拼一段上下文，Agent 把它当成用户
 *    交代的背景即可。注入内容会在 UI 里只显示用户原文（[USER_PROMPT_MARKER] 之后）。
 *
 * 两条通道互补：文件适合长期、可版本化的约定；注入保证 Agent 第一轮就能看到，
 * 不用等它自己想起来去读文件。
 */
@Stable
internal class AcpMemoryController(private val context: Context) {

    companion object {
        private const val PREFS = "acp_memory"
        private const val KEY_BEHAVIOR = "behavior"
        private const val KEY_INJECT = "inject_on_new_session"

        private const val MEMORY_DIR = ".webide/agent"
        private const val MEMORY_FILE = "memory.md"

        private const val AGENTS_FILE = "AGENTS.md"
        private const val AGENTS_START = "<!-- webide-agent-memory:start -->"
        private const val AGENTS_END = "<!-- webide-agent-memory:end -->"

        /** 注入块结尾的哨兵：重放历史时据此裁掉前缀，只显示用户原文。 */
        const val USER_PROMPT_MARKER = "<!-- webide:user-prompt -->"

        /** 注入块开头的哨兵（语言无关），用于判断重放内容是否带前缀。 */
        const val PREAMBLE_START = "<!-- webide:context -->"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var behavior by mutableStateOf(AcpBehaviorPreset.fromKey(prefs.getString(KEY_BEHAVIOR, null)))
        private set

    /** 新会话首轮是否自动注入记忆与行为。 */
    var injectOnNewSession by mutableStateOf(prefs.getBoolean(KEY_INJECT, true))
        private set

    var memoryText by mutableStateOf("")
        private set

    var loadedWorkspace by mutableStateOf<String?>(null)
        private set

    var isLoaded by mutableStateOf(false)
        private set

    /** 每次从磁盘重载自增，UI 据此刷新编辑框。 */
    var loadVersion by mutableStateOf(0)
        private set

    var lastSavedAt by mutableStateOf(0L)
        private set

    fun applyBehavior(preset: AcpBehaviorPreset) {
        behavior = preset
        prefs.edit { putString(KEY_BEHAVIOR, preset.name) }
    }

    fun applyInjectOnNewSession(enabled: Boolean) {
        injectOnNewSession = enabled
        prefs.edit { putBoolean(KEY_INJECT, enabled) }
    }

    fun memoryFile(workspace: String): File = File(workspace, "$MEMORY_DIR/$MEMORY_FILE")

    /** 读取工作区记忆文件；切项目时重新加载。 */
    suspend fun load(workspace: String) {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                memoryFile(workspace).takeIf { it.isFile }?.readText().orEmpty()
            }.getOrDefault("")
        }
        memoryText = text
        loadedWorkspace = workspace
        isLoaded = true
        loadVersion += 1
    }

    suspend fun updateMemoryText(text: String) {
        memoryText = text
        save(loadedWorkspace)
    }

    suspend fun save(workspace: String?) {
        val ws = workspace ?: return
        val text = memoryText.trim()
        withContext(Dispatchers.IO) {
            runCatching {
                val file = memoryFile(ws)
                if (text.isEmpty()) {
                    if (file.exists()) file.delete()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                }
            }
        }
        lastSavedAt = System.currentTimeMillis()
    }

    /** 把记忆 + 行为写进项目根目录的 `AGENTS.md` 受管区块（保留其它内容）。 */
    suspend fun writeAgentsMd(workspace: String): File = withContext(Dispatchers.IO) {
        val file = File(workspace, AGENTS_FILE)
        val block = buildString {
            appendLine(AGENTS_START)
            appendLine("<!-- Maintained by WebIDE. Edit in the Memory & behavior panel or inside this block. -->")
            appendLine()
            if (memoryText.isNotBlank()) {
                appendLine("## ${context.getString(R.string.acp_preamble_memory)}")
                appendLine()
                appendLine(memoryText.trim())
                appendLine()
            }
            val instruction = behaviorInstruction(context)
            if (instruction.isNotBlank()) {
                appendLine("## ${context.getString(R.string.acp_preamble_behavior)}")
                appendLine()
                appendLine(instruction)
                appendLine()
            }
            append(AGENTS_END)
        }
        val existing = if (file.isFile) file.readText() else ""
        val updated = if (existing.contains(AGENTS_START) && existing.contains(AGENTS_END)) {
            val start = existing.indexOf(AGENTS_START)
            val end = existing.indexOf(AGENTS_END) + AGENTS_END.length
            existing.substring(0, start) + block + existing.substring(end)
        } else if (existing.isBlank()) {
            block + "\n"
        } else {
            existing.trimEnd() + "\n\n" + block + "\n"
        }
        file.parentFile?.mkdirs()
        file.writeText(updated)
        lastSavedAt = System.currentTimeMillis()
        file
    }

    /** 首轮注入内容；不需要注入时返回 null。 */
    fun buildPreamble(): String? {
        val instruction = behaviorInstruction(context)
        val memory = memoryText.trim()
        if (memory.isEmpty() && (instruction.isEmpty() || behavior == AcpBehaviorPreset.BALANCED)) {
            return null
        }
        return buildString {
            appendLine(PREAMBLE_START)
            appendLine(context.getString(R.string.acp_preamble_intro))
            appendLine()
            if (memory.isNotEmpty()) {
                appendLine("【${context.getString(R.string.acp_preamble_memory)}】")
                appendLine(memory)
                appendLine()
            }
            if (instruction.isNotEmpty()) {
                appendLine("【${context.getString(R.string.acp_preamble_behavior)}】")
                appendLine(instruction)
                appendLine()
            }
            appendLine("【${context.getString(R.string.acp_preamble_user)}】")
            append(USER_PROMPT_MARKER)
        }
    }

    private fun behaviorInstruction(context: Context): String = when (behavior) {
        AcpBehaviorPreset.BALANCED -> ""
        AcpBehaviorPreset.CONCISE -> context.getString(R.string.acp_behavior_concise_prompt)
        AcpBehaviorPreset.THOROUGH -> context.getString(R.string.acp_behavior_thorough_prompt)
        AcpBehaviorPreset.PLAN_FIRST -> context.getString(R.string.acp_behavior_plan_first_prompt)
        AcpBehaviorPreset.REVIEW -> context.getString(R.string.acp_behavior_review_prompt)
    }.trim()
}
