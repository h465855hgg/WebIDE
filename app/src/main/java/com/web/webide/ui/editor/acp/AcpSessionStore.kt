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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * 会话的本地副本。
 *
 * ACP 的 `session/list` / `session/load` 完全依赖 Agent：换一个 Agent、升级后
 * 丢了 session、或者离线打开面板，历史就没了。这里把时间线按工作区落盘，
 * 做到三件事：
 *
 * 1. 面板一打开就能看到上次的对话（即使没连 Agent）；
 * 2. 会话可以重命名、置顶、搜索、导出 Markdown；
 * 3. Agent 不支持 `session/load` 时，至少历史不会丢。
 *
 * 存储结构（`filesDir/acp_sessions/<workspaceHash>/`）：
 * - `index.json`  —— 会话元数据索引，列表页只读它
 * - `<id>.json`   —— 单个会话的完整时间线
 */
internal class AcpSessionStore(context: Context) {

    companion object {
        private const val TAG = "AcpSessionStore"
        private const val FORMAT_VERSION = 1
        private const val INDEX_FILE = "index.json"

        /** 单条工具输出落盘上限，避免一次 `cat` 把存储撑爆。 */
        private const val MAX_RAW_FIELD_CHARS = 16_000

        /** 单条 diff 单侧内容落盘上限。 */
        private const val MAX_DIFF_FIELD_CHARS = 256_000

        private val TITLE_MAX = 48
        private val PREVIEW_MAX = 120

        private val HEX = "0123456789abcdef".toCharArray()
    }

    private val root = File(context.filesDir, "acp_sessions")
    private val lock = Any()

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 读取某个工作区的会话索引（置顶优先，其次按更新时间倒序）。 */
    suspend fun list(workspace: String): List<AcpSessionMeta> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readIndex(workspaceDir(workspace))
        }
    }

    /** 最近一次更新过的会话 id，用于面板启动时恢复现场。 */
    suspend fun lastSessionId(workspace: String): String? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readIndex(workspaceDir(workspace)).firstOrNull()?.id
        }
    }

    suspend fun load(workspace: String, id: String): AcpStoredSession? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val file = sessionFile(workspaceDir(workspace), id)
            if (!file.isFile) return@synchronized null
            runCatching { sessionFromJson(JSONObject(file.readText())) }
                .onFailure { Log.w(TAG, "读取会话失败: ${file.name}", it) }
                .getOrNull()
        }
    }

    /**
     * 搜索会话：先匹配元数据（标题 / 摘要 / Agent 名），再按需深扫正文。
     *
     * 深扫是 O(会话数)，但只会在用户真的输入关键词时发生，且一旦命中就停；
     * 对手机上的几十上百条会话足够快。
     */
    suspend fun search(workspace: String, query: String): List<AcpSessionMeta> =
        withContext(Dispatchers.IO) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext list(workspace)
            synchronized(lock) {
                val dir = workspaceDir(workspace)
                val metas = readIndex(dir)
                val lowered = keyword.lowercase()
                val matched = LinkedHashMap<String, AcpSessionMeta>()
                metas.filterTo(mutableListOf()) {
                    it.title.lowercase().contains(lowered) ||
                        it.preview.lowercase().contains(lowered) ||
                        it.agentName.lowercase().contains(lowered)
                }.forEach { matched[it.id] = it }

                metas.forEach { meta ->
                    if (meta.id in matched) return@forEach
                    val text = runCatching {
                        sessionFile(dir, meta.id).takeIf { it.isFile }?.readText()
                    }.getOrNull() ?: return@forEach
                    val haystack = runCatching { searchableText(JSONObject(text)) }.getOrNull()
                        ?: text
                    if (haystack.contains(keyword, ignoreCase = true)) matched[meta.id] = meta
                }
                matched.values.toList()
            }
        }

    /** 该工作区会话占用的大致字节数。 */
    suspend fun totalBytes(workspace: String): Long = withContext(Dispatchers.IO) {
        synchronized(lock) {
            workspaceDir(workspace).listFiles()
                ?.filter { it.isFile }
                ?.sumOf { it.length() }
                ?: 0L
        }
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    suspend fun save(meta: AcpSessionMeta, items: List<AcpTimelineItem>): AcpSessionMeta =
        withContext(Dispatchers.IO) {
            val computed = meta.copy(
                title = meta.title.ifBlank { titleOf(items) },
                preview = previewOf(items),
                updatedAt = System.currentTimeMillis()
            )
            synchronized(lock) {
                val dir = workspaceDir(computed.workspace)
                val payload = JSONObject().apply {
                    put("version", FORMAT_VERSION)
                    put("meta", metaToJson(computed))
                    put("items", JSONArray().apply { items.forEach { put(itemToJson(it)) } })
                }
                writeAtomic(sessionFile(dir, computed.id), payload.toString())
                val index = readIndex(dir).toMutableList()
                index.removeAll { it.id == computed.id }
                index.add(computed)
                writeIndex(dir, index)
            }
            computed
        }

    suspend fun rename(workspace: String, id: String, title: String) = withContext(Dispatchers.IO) {
        updateMeta(workspace, id) { it.copy(title = title.trim().ifBlank { it.title }) }
    }

    suspend fun setPinned(workspace: String, id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        updateMeta(workspace, id) { it.copy(pinned = pinned) }
    }

    suspend fun delete(workspace: String, id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val dir = workspaceDir(workspace)
            sessionFile(dir, id).delete()
            writeIndex(dir, readIndex(dir).filterNot { it.id == id })
            Unit
        }
    }

    /** 清空工作区历史，返回删除的会话数量。 */
    suspend fun clear(workspace: String): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val dir = workspaceDir(workspace)
            val count = readIndex(dir).size
            dir.listFiles()?.forEach { it.delete() }
            count
        }
    }

    suspend fun exportMarkdown(session: AcpStoredSession, target: File): File =
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            writeAtomic(target, toMarkdown(session))
            target
        }

    private fun updateMeta(workspace: String, id: String, transform: (AcpSessionMeta) -> AcpSessionMeta) {
        synchronized(lock) {
            val dir = workspaceDir(workspace)
            val index = readIndex(dir).toMutableList()
            val position = index.indexOfFirst { it.id == id }
            if (position < 0) return
            val updated = transform(index[position]).copy(updatedAt = System.currentTimeMillis())
            index[position] = updated
            writeIndex(dir, index)

            val file = sessionFile(dir, id)
            if (file.isFile) {
                runCatching {
                    val json = JSONObject(file.readText())
                    json.put("meta", metaToJson(updated))
                    writeAtomic(file, json.toString())
                }.onFailure { Log.w(TAG, "更新会话元数据失败", it) }
            }
        }
    }

    // ------------------------------------------------------------------
    // 索引
    // ------------------------------------------------------------------

    private fun readIndex(dir: File): List<AcpSessionMeta> {
        val indexFile = File(dir, INDEX_FILE)
        if (!indexFile.isFile) return rebuildIndex(dir)
        val parsed = runCatching {
            val json = JSONObject(indexFile.readText())
            val array = json.optJSONArray("sessions") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::metaFromJson)
            }
        }.getOrNull() ?: return rebuildIndex(dir)
        return parsed.sortedWith(compareByDescending<AcpSessionMeta> { it.pinned }.thenByDescending { it.updatedAt })
    }

    /** 索引丢失 / 损坏时，从会话文件重建。 */
    private fun rebuildIndex(dir: File): List<AcpSessionMeta> {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") && file.name != INDEX_FILE }
            ?: return emptyList()
        val metas = files.mapNotNull { file ->
            runCatching { metaFromJson(JSONObject(file.readText()).getJSONObject("meta")) }.getOrNull()
        }
        if (metas.isNotEmpty()) writeIndex(dir, metas)
        return metas.sortedWith(compareByDescending<AcpSessionMeta> { it.pinned }.thenByDescending { it.updatedAt })
    }

    private fun writeIndex(dir: File, sessions: List<AcpSessionMeta>) {
        val payload = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("sessions", JSONArray().apply { sessions.forEach { put(metaToJson(it)) } })
        }
        writeAtomic(File(dir, INDEX_FILE), payload.toString())
    }

    // ------------------------------------------------------------------
    // 路径与原子写
    // ------------------------------------------------------------------

    private fun workspaceDir(workspace: String): File {
        return File(root, digest(workspace, 16)).apply { mkdirs() }
    }

    private fun sessionFile(dir: File, id: String): File {
        return File(dir, "${digest(id, 24)}.json")
    }

    private fun digest(value: String, length: Int): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val v = byte.toInt() and 0xFF
            builder.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return builder.toString().take(length)
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (file.exists() && !file.delete()) {
            tmp.delete()
            return
        }
        if (!tmp.renameTo(file)) {
            runCatching { file.writeText(text) }
            tmp.delete()
        }
    }

    // ------------------------------------------------------------------
    // 元数据 / 时间线序列化
    // ------------------------------------------------------------------

    private fun metaToJson(meta: AcpSessionMeta): JSONObject = JSONObject().apply {
        put("id", meta.id)
        put("workspace", meta.workspace)
        put("agentId", meta.agentId)
        put("agentName", meta.agentName)
        put("title", meta.title)
        put("preview", meta.preview)
        put("createdAt", meta.createdAt)
        put("updatedAt", meta.updatedAt)
        put("pinned", meta.pinned)
        put("turns", meta.turns)
        put("inputTokens", meta.inputTokens)
        put("outputTokens", meta.outputTokens)
        meta.model?.let { put("model", it) }
    }

    private fun metaFromJson(json: JSONObject): AcpSessionMeta? {
        val id = json.strOrNull("id") ?: return null
        return AcpSessionMeta(
            id = id,
            workspace = json.strOrEmpty("workspace"),
            agentId = json.strOrEmpty("agentId"),
            agentName = json.strOrEmpty("agentName"),
            title = json.strOrEmpty("title"),
            preview = json.strOrEmpty("preview"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            pinned = json.optBoolean("pinned", false),
            turns = json.optInt("turns", 0),
            inputTokens = json.optLong("inputTokens", 0L),
            outputTokens = json.optLong("outputTokens", 0L),
            model = json.strOrNull("model")
        )
    }

    private fun sessionFromJson(json: JSONObject): AcpStoredSession? {
        val meta = metaFromJson(json.getJSONObject("meta")) ?: return null
        val array = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::itemFromJson)
        }
        return AcpStoredSession(meta, items)
    }

    private fun itemToJson(item: AcpTimelineItem): JSONObject = when (item) {
        is AcpUserMessage -> JSONObject()
            .put("type", "user")
            .put("id", item.id)
            .put("text", item.text)

        is AcpAgentMessage -> JSONObject()
            .put("type", "agent")
            .put("id", item.id)
            .put("text", item.text)

        is AcpThoughtMessage -> JSONObject()
            .put("type", "thought")
            .put("id", item.id)
            .put("text", item.text)

        is AcpNoticeMessage -> JSONObject()
            .put("type", "notice")
            .put("id", item.id)
            .put("text", item.text)
            .put("error", item.isError)

        is AcpPlanItem -> JSONObject()
            .put("type", "plan")
            .put("id", item.id)
            .put("entries", JSONArray().apply {
                item.entries.forEach { entry ->
                    put(
                        JSONObject()
                            .put("content", entry.content)
                            .put("priority", entry.priority)
                            .put("status", entry.status)
                    )
                }
            })

        is AcpToolCallItem -> JSONObject()
            .put("type", "tool")
            .put("id", item.id)
            .put("toolCallId", item.toolCallId)
            .put("title", item.title)
            .put("kind", item.kind)
            .put("status", item.status)
            .put("contents", JSONArray().apply {
                item.contents.forEach { content ->
                    when (content) {
                        is AcpToolContent.Text -> put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", content.text.take(MAX_RAW_FIELD_CHARS))
                        )

                        is AcpToolContent.Diff -> put(
                            JSONObject()
                                .put("type", "diff")
                                .put("path", content.path)
                                .put("oldText", content.oldText?.take(MAX_DIFF_FIELD_CHARS) ?: JSONObject.NULL)
                                .put("newText", content.newText.take(MAX_DIFF_FIELD_CHARS))
                        )

                        is AcpToolContent.TerminalRef -> put(
                            JSONObject()
                                .put("type", "terminal")
                                .put("terminalId", content.terminalId)
                        )
                    }
                }
            })
            .put("locations", JSONArray().apply {
                item.locations.forEach { location ->
                    put(
                        JSONObject()
                            .put("path", location.path)
                            .put("line", location.line ?: JSONObject.NULL)
                    )
                }
            })
            .put("rawInput", item.rawInput?.take(MAX_RAW_FIELD_CHARS) ?: JSONObject.NULL)
            .put("rawOutput", item.rawOutput?.take(MAX_RAW_FIELD_CHARS) ?: JSONObject.NULL)
            .put("startedAt", item.startedAt)
            .put("finishedAt", item.finishedAt ?: JSONObject.NULL)
    }

    private fun itemFromJson(json: JSONObject): AcpTimelineItem? {
        val id = json.strOrNull("id") ?: return null
        return when (json.strOrEmpty("type")) {
            "user" -> AcpUserMessage(id, json.strOrEmpty("text"))
            "agent" -> AcpAgentMessage(id, json.strOrEmpty("text"))
            "thought" -> AcpThoughtMessage(id, json.strOrEmpty("text"))
            "notice" -> AcpNoticeMessage(id, json.strOrEmpty("text"), json.optBoolean("error", false))
            "plan" -> AcpPlanItem(
                id,
                json.arrOrEmpty("entries").let { entries ->
                    (0 until entries.length()).mapNotNull { index ->
                        entries.optJSONObject(index)?.let { entry ->
                            AcpPlanEntry(
                                content = entry.strOrEmpty("content"),
                                priority = entry.strOrEmpty("priority"),
                                status = entry.strOrEmpty("status")
                            )
                        }
                    }
                }
            )

            "tool" -> AcpToolCallItem(
                id = id,
                toolCallId = json.strOrEmpty("toolCallId"),
                title = json.strOrEmpty("title"),
                kind = json.strOrEmpty("kind"),
                status = json.strOrEmpty("status"),
                contents = json.arrOrEmpty("contents").let { contents ->
                    (0 until contents.length()).mapNotNull { index ->
                        contents.optJSONObject(index)?.let { content ->
                            when (content.strOrEmpty("type")) {
                                "text" -> AcpToolContent.Text(content.strOrEmpty("text"))
                                "diff" -> AcpToolContent.Diff(
                                    path = content.strOrEmpty("path"),
                                    oldText = content.strOrNull("oldText"),
                                    newText = content.strOrEmpty("newText")
                                )

                                "terminal" -> AcpToolContent.TerminalRef(content.strOrEmpty("terminalId"))
                                else -> null
                            }
                        }
                    }
                },
                locations = json.arrOrEmpty("locations").let { locations ->
                    (0 until locations.length()).mapNotNull { index ->
                        locations.optJSONObject(index)?.let { location ->
                            AcpToolLocation(location.strOrEmpty("path"), location.intOrNull("line"))
                        }
                    }
                },
                rawInput = json.strOrNull("rawInput"),
                rawOutput = json.strOrNull("rawOutput"),
                startedAt = json.optLong("startedAt", 0L),
                finishedAt = if (json.has("finishedAt") && !json.isNull("finishedAt")) {
                    json.optLong("finishedAt", 0L)
                } else {
                    null
                }
            )

            else -> null
        }
    }

    // ------------------------------------------------------------------
    // 摘要与导出
    // ------------------------------------------------------------------

    private fun titleOf(items: List<AcpTimelineItem>): String {
        val firstUser = items.filterIsInstance<AcpUserMessage>().firstOrNull()?.text
        val raw = firstUser?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: return "新会话"
        return raw.trim().take(TITLE_MAX)
    }

    /** 只拼接用户/Agent 真正能看到的内容，避免把 JSON 字段名（type/title）当成正文命中。 */
    private fun searchableText(json: JSONObject): String {
        val builder = StringBuilder()
        json.optJSONObject("meta")?.optString("agentName")?.let { builder.append(it).append('\n') }
        val items = json.optJSONArray("items") ?: return builder.toString()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            when (item.optString("type")) {
                "user", "agent", "thought", "notice" -> builder.append(item.optString("text")).append('\n')
                "tool" -> {
                    builder.append(item.optString("title")).append('\n')
                    val contents = item.optJSONArray("contents") ?: continue
                    for (c in 0 until contents.length()) {
                        val content = contents.optJSONObject(c) ?: continue
                        builder.append(content.optString("text")).append('\n')
                        builder.append(content.optString("path")).append('\n')
                        builder.append(content.optString("newText").take(2_000)).append('\n')
                    }
                }

                "plan" -> {
                    val entries = item.optJSONArray("entries") ?: continue
                    for (e in 0 until entries.length()) {
                        builder.append(entries.optJSONObject(e)?.optString("content")).append('\n')
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun previewOf(items: List<AcpTimelineItem>): String {        val last = items.asReversed().firstOrNull {
            (it is AcpAgentMessage && it.text.isNotBlank()) || (it is AcpUserMessage && it.text.isNotBlank())
        } ?: return ""
        val text = when (last) {
            is AcpAgentMessage -> last.text
            is AcpUserMessage -> last.text
            else -> ""
        }
        return text.replace(Regex("\\s+"), " ").trim().take(PREVIEW_MAX)
    }

    private fun toMarkdown(session: AcpStoredSession): String {
        val meta = session.meta
        val stamp = runCatching {
            Instant.ofEpochMilli(meta.updatedAt).toString()
        }.getOrDefault("")
        return buildString {
            appendLine("# ${meta.title.ifBlank { "ACP 会话" }}")
            appendLine()
            appendLine("> Agent: ${meta.agentName.ifBlank { meta.agentId }} · 更新于 $stamp")
            meta.model?.takeIf { it.isNotBlank() }?.let { appendLine("> 模型: $it") }
            appendLine()
            session.items.forEach { item ->
                when (item) {
                    is AcpUserMessage -> {
                        appendLine("## 你")
                        appendLine()
                        appendLine(item.text)
                        appendLine()
                    }

                    is AcpAgentMessage -> {
                        appendLine("## ${meta.agentName.ifBlank { "Agent" }}")
                        appendLine()
                        appendLine(item.text)
                        appendLine()
                    }

                    is AcpThoughtMessage -> {
                        appendLine("<details><summary>思考过程</summary>")
                        appendLine()
                        appendLine(item.text)
                        appendLine()
                        appendLine("</details>")
                        appendLine()
                    }

                    is AcpToolCallItem -> {
                        appendLine("### 工具调用 · ${item.title.ifBlank { item.kind }}")
                        appendLine()
                        appendLine("- 状态: ${item.status}")
                        appendLine("- 类型: ${item.kind}")
                        item.locations.forEach { appendLine("- 文件: `${it.path}`${it.line?.let { l -> ":$l" } ?: ""}") }
                        appendLine()
                        item.contents.forEach { content ->
                            when (content) {
                                is AcpToolContent.Text -> {
                                    appendLine("```")
                                    appendLine(content.text.take(4000))
                                    appendLine("```")
                                }

                                is AcpToolContent.Diff -> {
                                    appendLine("```diff")
                                    appendLine(diffForExport(content.oldText, content.newText))
                                    appendLine("```")
                                }

                                is AcpToolContent.TerminalRef -> appendLine("终端: `${content.terminalId}`")
                            }
                        }
                        appendLine()
                    }

                    is AcpPlanItem -> {
                        appendLine("### 执行计划")
                        appendLine()
                        item.entries.forEach { entry ->
                            val box = if (entry.status == Acp.STATUS_COMPLETED) "x" else " "
                            appendLine("- [$box] ${entry.content}")
                        }
                        appendLine()
                    }

                    is AcpNoticeMessage -> {
                        appendLine("> ${item.text}")
                        appendLine()
                    }
                }
            }
        }
    }

    private fun diffForExport(oldText: String?, newText: String): String {
        // 导出用简易整体 diff：旧内容全删、新内容全增，避免在客户端重复实现 LCS。
        return buildString {
            oldText?.lineSequence()?.forEach { appendLine("-$it") }
            newText.lineSequence().forEach { appendLine("+$it") }
        }.trimEnd()
    }
}

/** 会话索引条目。 */
internal data class AcpSessionMeta(
    val id: String,
    val workspace: String,
    val agentId: String,
    val agentName: String,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val turns: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val model: String? = null
)

/** 会话索引 + 完整时间线。 */
internal data class AcpStoredSession(
    val meta: AcpSessionMeta,
    val items: List<AcpTimelineItem>
)
