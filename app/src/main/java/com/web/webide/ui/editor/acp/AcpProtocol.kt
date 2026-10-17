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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent Client Protocol (ACP) 协议常量。
 *
 * 传输层：stdio 上的 JSON-RPC 2.0，每条消息占一行（换行分隔）。
 * 规范：https://agentclientprotocol.com/protocol/v1/overview
 */
internal object Acp {

    /** 本客户端实现的 ACP 主版本号，在 initialize 时与 Agent 协商。 */
    const val PROTOCOL_VERSION = 1

    // ---------------- Client -> Agent：请求 / 通知 ----------------
    const val M_INITIALIZE = "initialize"
    const val M_AUTHENTICATE = "authenticate"
    const val M_SESSION_NEW = "session/new"
    const val M_SESSION_LOAD = "session/load"
    const val M_SESSION_RESUME = "session/resume"
    const val M_SESSION_LIST = "session/list"
    const val M_SESSION_CLOSE = "session/close"
    const val M_SESSION_FORK = "session/fork"
    const val M_SESSION_PROMPT = "session/prompt"
    const val M_SESSION_CANCEL = "session/cancel"
    const val M_SESSION_SET_MODE = "session/set_mode"

    /** 现代 ACP：统一设置会话配置项（模型 / 模式 / 思考等级等）。 */
    const val M_SESSION_SET_CONFIG_OPTION = "session/set_config_option"

    // ---------------- Agent -> Client：请求 ----------------
    const val M_REQUEST_PERMISSION = "session/request_permission"
    const val M_FS_READ_TEXT_FILE = "fs/read_text_file"
    const val M_FS_WRITE_TEXT_FILE = "fs/write_text_file"
    const val M_TERMINAL_CREATE = "terminal/create"
    const val M_TERMINAL_OUTPUT = "terminal/output"
    const val M_TERMINAL_WAIT_FOR_EXIT = "terminal/wait_for_exit"
    const val M_TERMINAL_KILL = "terminal/kill"
    const val M_TERMINAL_RELEASE = "terminal/release"

    // ---------------- Agent -> Client：通知 ----------------
    const val N_SESSION_UPDATE = "session/update"

    // ---------------- session/update 变体（params.update.sessionUpdate） ----------------
    const val U_USER_MESSAGE_CHUNK = "user_message_chunk"
    const val U_AGENT_MESSAGE_CHUNK = "agent_message_chunk"
    const val U_AGENT_THOUGHT_CHUNK = "agent_thought_chunk"
    const val U_TOOL_CALL = "tool_call"
    const val U_TOOL_CALL_UPDATE = "tool_call_update"
    const val U_PLAN = "plan"
    const val U_AVAILABLE_COMMANDS_UPDATE = "available_commands_update"
    const val U_CURRENT_MODE_UPDATE = "current_mode_update"
    const val U_CONFIG_OPTION_UPDATE = "config_option_update"
    const val U_NOTICE = "notice"

    // ---------------- ContentBlock.type ----------------
    const val C_TEXT = "text"
    const val C_IMAGE = "image"
    const val C_AUDIO = "audio"
    const val C_RESOURCE = "resource"
    const val C_RESOURCE_LINK = "resource_link"

    // ---------------- ToolCallContent.type ----------------
    const val TC_CONTENT = "content"
    const val TC_DIFF = "diff"
    const val TC_TERMINAL = "terminal"

    // ---------------- ToolKind ----------------
    const val KIND_READ = "read"
    const val KIND_EDIT = "edit"
    const val KIND_DELETE = "delete"
    const val KIND_MOVE = "move"
    const val KIND_SEARCH = "search"
    const val KIND_EXECUTE = "execute"
    const val KIND_THINK = "think"
    const val KIND_FETCH = "fetch"
    const val KIND_SWITCH_MODE = "switch_mode"
    const val KIND_OTHER = "other"

    // ---------------- ToolCallStatus ----------------
    const val STATUS_PENDING = "pending"
    const val STATUS_IN_PROGRESS = "in_progress"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_FAILED = "failed"

    // ---------------- PermissionOptionKind ----------------
    const val PERM_ALLOW_ONCE = "allow_once"
    const val PERM_ALLOW_ALWAYS = "allow_always"
    const val PERM_REJECT_ONCE = "reject_once"
    const val PERM_REJECT_ALWAYS = "reject_always"

    // ---------------- StopReason ----------------
    const val STOP_END_TURN = "end_turn"
    const val STOP_MAX_TOKENS = "max_tokens"
    const val STOP_MAX_TURN_REQUESTS = "max_turn_requests"
    const val STOP_REFUSAL = "refusal"
    const val STOP_CANCELLED = "cancelled"

    // ---------------- ConfigOption.category（会话配置项语义分类） ----------------
    const val CAT_MODE = "mode"
    const val CAT_MODEL = "model"
    const val CAT_MODEL_CONFIG = "model_config"
    const val CAT_THOUGHT_LEVEL = "thought_level"

    // ---------------- ConfigOption.type ----------------
    const val CTYPE_SELECT = "select"
    const val CTYPE_BOOLEAN = "boolean"

    // ---------------- JSON-RPC 2.0 错误码 ----------------
    const val ERR_PARSE = -32700
    const val ERR_INVALID_REQUEST = -32600
    const val ERR_METHOD_NOT_FOUND = -32601
    const val ERR_INVALID_PARAMS = -32602
    const val ERR_INTERNAL = -32603

    /** 会话取消时 request_permission 必须回传 cancelled 结果。 */
    fun permissionCancelledResult(): JSONObject = JSONObject().put(
        "outcome",
        JSONObject().put("outcome", "cancelled")
    )

    /** 用户选择了某个授权选项时的结果。 */
    fun permissionSelectedResult(optionId: String): JSONObject = JSONObject().put(
        "outcome",
        JSONObject().put("outcome", "selected").put("optionId", optionId)
    )

    // ---------------- 会话配置项（Session Config Options，取代旧 Session Modes） ----------------

    /**
     * 从 session/new 结果或 config_option_update 通知里解析配置项列表。
     * 结构：ConfigOption { id, name, description?, category?, type, currentValue, options? }
     * options 既可能是扁平的 ConfigOptionValue[]，也可能是 ConfigOptionGroup[]（含 group/name/options）。
     */
    fun parseConfigOptions(array: JSONArray?): List<AcpConfigOption> {
        if (array == null) return emptyList()
        val result = mutableListOf<AcpConfigOption>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.strOrNull("id") ?: continue
            val type = obj.strOrEmpty("type").ifEmpty { CTYPE_SELECT }
            val rawOptions = obj.arrOrNull("options")
            val flat = mutableListOf<AcpConfigOptionValue>()
            val groups = mutableListOf<AcpConfigOptionGroup>()
            if (rawOptions != null) {
                for (j in 0 until rawOptions.length()) {
                    val entry = rawOptions.optJSONObject(j) ?: continue
                    if (entry.has("group") || entry.has("options")) {
                        val groupValues = mutableListOf<AcpConfigOptionValue>()
                        entry.arrOrEmpty("options").let { values ->
                            for (k in 0 until values.length()) {
                                val value = values.optJSONObject(k) ?: continue
                                val valueId = value.strOrNull("value") ?: continue
                                groupValues.add(
                                    AcpConfigOptionValue(
                                        value = valueId,
                                        name = value.strOrNull("name") ?: valueId,
                                        description = value.strOrNull("description")
                                    )
                                )
                            }
                        }
                        groups.add(
                            AcpConfigOptionGroup(
                                group = entry.strOrNull("group").orEmpty(),
                                name = entry.strOrNull("name").orEmpty(),
                                options = groupValues
                            )
                        )
                    } else {
                        val valueId = entry.strOrNull("value") ?: continue
                        flat.add(
                            AcpConfigOptionValue(
                                value = valueId,
                                name = entry.strOrNull("name") ?: valueId,
                                description = entry.strOrNull("description")
                            )
                        )
                    }
                }
            }
            val current = obj.opt("currentValue")
            result.add(
                AcpConfigOption(
                    id = id,
                    name = obj.strOrNull("name") ?: id,
                    description = obj.strOrNull("description"),
                    category = obj.strOrNull("category"),
                    type = type,
                    currentValue = if (current == null || current == JSONObject.NULL) null else current,
                    values = flat,
                    groups = groups
                )
            )
        }
        return result
    }
}

/** 一个会话配置项（模型、模式、思考等级等）。 */
internal data class AcpConfigOption(
    val id: String,
    val name: String,
    val description: String?,
    val category: String?,
    val type: String,
    /** 当前值：select 为字符串，boolean 为 Boolean。 */
    val currentValue: Any?,
    /** 扁平的可选值（与 [groups] 二选一）。 */
    val values: List<AcpConfigOptionValue>,
    /** 分组可选值。 */
    val groups: List<AcpConfigOptionGroup>
) {
    val currentValueString: String? get() = currentValue?.toString()

    val currentValueBoolean: Boolean? get() = currentValue as? Boolean

    /** 所有可选值（含分组内），用于查找当前值的显示名。 */
    val allValues: List<AcpConfigOptionValue>
        get() = if (groups.isNotEmpty()) groups.flatMap { it.options } else values

    val currentDisplayName: String
        get() = allValues.firstOrNull { it.value == currentValueString }?.name
            ?: currentValueString
            ?: name
}

internal data class AcpConfigOptionValue(
    val value: String,
    val name: String,
    val description: String?
)

internal data class AcpConfigOptionGroup(
    val group: String,
    val name: String,
    val options: List<AcpConfigOptionValue>
)

/** Agent 通过 JSON-RPC error 返回的失败。 */
internal class AcpException(
    val code: Int,
    message: String,
    val data: Any? = null
) : Exception(message)

/** Agent 进程退出或管道中断。 */
internal class AcpConnectionLostException(message: String) : Exception(message)

// ---------------------------------------------------------------------------
// org.json 安全读取辅助
// ---------------------------------------------------------------------------

internal fun JSONObject.strOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key, "")
}

internal fun JSONObject.strOrEmpty(key: String): String = strOrNull(key).orEmpty()

internal fun JSONObject.objOrNull(key: String): JSONObject? {
    if (!has(key) || isNull(key)) return null
    return optJSONObject(key)
}

internal fun JSONObject.arrOrNull(key: String): JSONArray? {
    if (!has(key) || isNull(key)) return null
    return optJSONArray(key)
}

internal fun JSONObject.arrOrEmpty(key: String): JSONArray = arrOrNull(key) ?: JSONArray()

internal fun JSONObject.boolOrFalse(key: String): Boolean {
    if (!has(key) || isNull(key)) return false
    return optBoolean(key, false)
}

internal fun JSONObject.intOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

/** 取出 JSON 值并转成展示用字符串（对象/数组走 toString）。 */
internal fun jsonValueToDisplay(value: Any?): String? {
    if (value == null || value == JSONObject.NULL) return null
    return when (value) {
        is String -> value
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> value.toString()
    }
}

/** 从 ContentBlock 提取纯文本，非文本块返回 null。 */
internal fun contentBlockToText(block: JSONObject?): String? {
    if (block == null) return null
    return when (block.optString("type")) {
        Acp.C_TEXT -> block.strOrEmpty("text")
        Acp.C_RESOURCE_LINK -> block.strOrNull("uri")?.let { "[$it]" }
        Acp.C_RESOURCE -> block.objOrNull("resource")?.strOrNull("uri")?.let { "[$it]" }
        else -> null
    }
}
