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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 一个项目技能：名称 + 描述 + Markdown 内容，落盘为 .webide/skills 下的 md 文件。 */
internal data class AcpSkill(
    val id: String,
    val name: String,
    val description: String,
    val content: String,
    val path: String
)

/**
 * 项目技能管理。
 *
 * ACP 没有 skills 协议，所以用「项目文件 + 首轮清单注入」实现：
 * - 技能正文保存成 Markdown，Agent 可以按路径直接读取；
 * - 新会话首轮把「技能名 + 描述 + 路径」清单注入 prompt，Agent 按需读取，
 *   不把整篇技能塞进上下文。
 */
@Stable
internal class AcpSkillsController(private val context: Context) {

    companion object {
        private const val PREFS = "acp_skills"
        private const val KEY_INJECT = "inject"
        private const val SKILLS_DIR = ".webide/skills"

        private const val FILE_HEADER_PREFIX = "# "
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var skills by mutableStateOf<List<AcpSkill>>(emptyList())
        private set

    var loadedWorkspace by mutableStateOf<String?>(null)
        private set

    var isLoaded by mutableStateOf(false)
        private set

    var loadVersion by mutableStateOf(0)
        private set

    var injectSkills by mutableStateOf(prefs.getBoolean(KEY_INJECT, true))
        private set

    fun applyInjectSkills(enabled: Boolean) {
        injectSkills = enabled
        prefs.edit { putBoolean(KEY_INJECT, enabled) }
    }

    suspend fun load(workspace: String) {
        val loaded = withContext(Dispatchers.IO) { readAll(workspace) }
        skills = loaded
        loadedWorkspace = workspace
        isLoaded = true
        loadVersion += 1
    }

    suspend fun save(workspace: String, skill: AcpSkill): AcpSkill =
        withContext(Dispatchers.IO) {
            val dir = skillsDir(workspace).apply { mkdirs() }
            val safeId = skill.id.ifBlank { newId(skill.name) }
            val file = File(dir, "$safeId.md")
            val saved = skill.copy(id = safeId, path = file.absolutePath)
            file.writeText(
                buildString {
                    appendLine("${FILE_HEADER_PREFIX}${saved.name.trim()}")
                    appendLine()
                    appendLine(saved.description.trim())
                    appendLine()
                    append(saved.content.trimEnd())
                }
            )
            return@withContext saved.also { updated ->
                skills = skills.filterNot { it.id == updated.id } + updated
            }
        }

    suspend fun delete(workspace: String, id: String) = withContext(Dispatchers.IO) {
        val file = File(skillsDir(workspace), "$id.md")
        if (file.exists()) file.delete()
        skills = skills.filterNot { it.id == id }
    }

    /** 新会话首轮注入的技能清单；没有技能或关闭注入时返回 null。 */
    fun buildPreamble(): String? {
        if (!injectSkills) return null
        val list = skills
        if (list.isEmpty()) return null
        val chinese = java.util.Locale.getDefault().language.startsWith("zh")
        return buildString {
            appendLine(AcpMemoryController.PREAMBLE_START)
            if (chinese) {
                appendLine("【本项目可用技能】需要时读取下列文件，不要凭空假设内容：")
                list.forEach { skill ->
                    appendLine("- ${skill.name}：${skill.description.ifBlank { "无描述" }}（${skill.path}）")
                }
                appendLine()
                appendLine("【用户请求】")
            } else {
                appendLine("[Project skills] Read the following files when relevant. Do not guess their content:")
                list.forEach { skill ->
                    appendLine("- ${skill.name}: ${skill.description.ifBlank { "no description" }} (${skill.path})")
                }
                appendLine()
                appendLine("[User request]")
            }
            append(AcpMemoryController.USER_PROMPT_MARKER)
        }
    }

    private fun skillsDir(workspace: String): File = File(workspace, SKILLS_DIR)

    private fun readAll(workspace: String): List<AcpSkill> {
        val dir = skillsDir(workspace)
        val files = dir.listFiles { file -> file.isFile && file.extension == "md" } ?: return emptyList()
        return files.sortedBy { it.name }.mapNotNull { file ->
            runCatching { parse(file) }.getOrNull()
        }
    }

    private fun parse(file: File): AcpSkill {
        val id = file.nameWithoutExtension
        val lines = file.readLines()
        val name = lines.firstOrNull { it.startsWith(FILE_HEADER_PREFIX) }
            ?.removePrefix(FILE_HEADER_PREFIX)
            ?.trim()
            ?.ifBlank { id }
            ?: id
        val bodyStart = lines.indexOfFirst { it.startsWith(FILE_HEADER_PREFIX) }
            .let { if (it < 0) 0 else it + 1 }
        val body = lines.drop(bodyStart)
        val description = body.dropWhile { it.isBlank() }
            .takeWhile { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        val content = body.dropWhile { it.isBlank() }
            .dropWhile { it.isNotBlank() }
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
        return AcpSkill(
            id = id,
            name = name,
            description = description,
            content = content,
            path = file.absolutePath
        )
    }

    private fun newId(name: String): String {
        val base = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
            .trim('-')
            .take(32)
            .ifBlank { "skill" }
        return "$base-${System.currentTimeMillis() % 100_000}"
    }
}
