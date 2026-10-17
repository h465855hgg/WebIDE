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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** MCP 服务器的两种接法：本地命令（stdio）或远程地址（HTTP/SSE）。 */
internal enum class AcpMcpTransport {
    STDIO,
    URL
}

/** 一条 MCP 服务器配置。 */
internal data class AcpMcpServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val transport: AcpMcpTransport = AcpMcpTransport.STDIO,
    val command: String = "",
    val args: List<String> = emptyList(),
    val url: String = "",
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
) {
    val summary: String
        get() = when (transport) {
            AcpMcpTransport.STDIO -> listOf(command).plus(args).joinToString(" ")
            AcpMcpTransport.URL -> url
        }
}

/**
 * MCP 服务器配置。
 *
 * ACP 的 `session/new` / `session/load` 都接受 `mcpServers`，Agent 会替我们拉起
 * 这些 MCP 进程 / 连接；之前客户端永远传空数组，等于把这个能力浪费了。
 * 配置是全局的（按 Agent 能力通用），在新建/加载会话时下发。
 */
@Stable
internal class AcpMcpController(context: Context) {

    companion object {
        private const val PREFS = "acp_mcp"
        private const val KEY_SERVERS = "servers"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var servers by mutableStateOf(load())
        private set

    fun upsert(server: AcpMcpServer) {
        val updated = servers.toMutableList()
        val index = updated.indexOfFirst { it.id == server.id }
        if (index >= 0) updated[index] = server else updated.add(server)
        servers = updated
        persist()
    }

    fun remove(id: String) {
        servers = servers.filterNot { it.id == id }
        persist()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        servers = servers.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persist()
    }

    /** 下发给 Agent 的 `mcpServers` 数组（只包含启用的）。 */
    fun toJsonArray(): JSONArray = JSONArray().apply {
        servers.filter { it.enabled }.forEach { server ->
            put(
                when (server.transport) {
                    AcpMcpTransport.STDIO -> JSONObject().apply {
                        put("name", server.name)
                        put("command", server.command)
                        put("args", JSONArray(server.args))
                        put("env", envArray(server.env))
                    }

                    AcpMcpTransport.URL -> JSONObject().apply {
                        put("name", server.name)
                        put("url", server.url)
                        put("headers", envArray(server.env))
                    }
                }
            )
        }
    }

    private fun envArray(env: Map<String, String>): JSONArray = JSONArray().apply {
        env.forEach { (name, value) ->
            put(JSONObject().put("name", name).put("value", value))
        }
    }

    private fun persist() {
        val array = JSONArray().apply {
            servers.forEach { server ->
                put(
                    JSONObject().apply {
                        put("id", server.id)
                        put("name", server.name)
                        put("transport", server.transport.name)
                        put("command", server.command)
                        put("args", JSONArray(server.args))
                        put("url", server.url)
                        put("env", JSONObject().apply { server.env.forEach { (k, v) -> put(k, v) } })
                        put("enabled", server.enabled)
                    }
                )
            }
        }
        prefs.edit { putString(KEY_SERVERS, array.toString()) }
    }

    private fun load(): List<AcpMcpServer> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val name = json.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AcpMcpServer(
                    id = json.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    name = name,
                    transport = runCatching {
                        AcpMcpTransport.valueOf(json.optString("transport"))
                    }.getOrDefault(AcpMcpTransport.STDIO),
                    command = json.optString("command"),
                    args = json.optJSONArray("args")?.let { args ->
                        (0 until args.length()).mapNotNull { args.optString(it)?.takeIf(String::isNotBlank) }
                    } ?: emptyList(),
                    url = json.optString("url"),
                    env = json.optJSONObject("env")?.let { env ->
                        env.keys().asSequence().associateWith { env.optString(it) }
                    } ?: emptyMap(),
                    enabled = json.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** 解析多行 `KEY=VALUE`（环境变量 / 请求头）。 */
internal fun parseKeyValueLines(text: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val separator = line.indexOf('=')
        if (separator <= 0) return@forEach
        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) return@forEach
        var value = line.substring(separator + 1).trim()
        if (value.length >= 2 &&
            ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\'')))
        ) {
            value = value.substring(1, value.length - 1)
        }
        result[key] = value
    }
    return result
}

internal fun keyValueLines(env: Map<String, String>): String =
    env.entries.joinToString("\n") { "${it.key}=${it.value}" }
