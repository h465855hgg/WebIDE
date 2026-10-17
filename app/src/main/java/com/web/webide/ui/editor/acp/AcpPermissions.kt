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

/** 授权级别。 */
internal enum class AcpApprovalMode {
    /** 每个请求都问。 */
    ASK,

    /** 只读类工具（读文件 / 搜索 / 思考）自动放行，写文件与执行命令仍然问。 */
    SMART,

    /** 全部自动放行（危险，仅建议在受控项目里用）。 */
    AUTO,

    /** 只读模式：拒绝一切写文件 / 删文件 / 执行命令。 */
    READ_ONLY;

    companion object {
        fun fromKey(key: String?): AcpApprovalMode? = entries.firstOrNull { it.name == key }
    }
}

/** 自动裁决的结果。 */
internal sealed interface AcpPermissionVerdict {
    /** 自动选择某个选项。 */
    data class Select(val optionId: String, val allowed: Boolean, val reason: String) : AcpPermissionVerdict

    /** 交给用户决定。 */
    data class Ask(val reason: String) : AcpPermissionVerdict
}

/** 一次授权决策的审计记录。 */
internal data class AcpPermissionAudit(
    val at: Long,
    val kind: String,
    val title: String,
    val decision: String,
    val detail: String
)

/**
 * 工具权限策略。
 *
 * ACP 只提供「一次请求 + 若干选项」，没有能力分级的概念，所以分级逻辑必须由
 * 客户端实现：
 * - 按工具类型（read / edit / execute / delete …）给默认策略；
 * - 记住用户对某类工具的「总是允许 / 总是拒绝」；
 * - 在只读模式下硬性拦截写盘与执行。
 */
@Stable
internal class AcpPermissionController(context: Context) {

    companion object {
        private const val PREFS = "acp_permissions"
        private const val KEY_MODE = "mode"
        private const val KEY_ALLOW = "allow_kinds"
        private const val KEY_DENY = "deny_kinds"
        private const val LEGACY_PREFS = "acp_settings"
        private const val LEGACY_AUTO_APPROVE = "auto_approve"

        /** 只读类工具：不改变磁盘、不执行命令。 */
        val READ_ONLY_KINDS = setOf(
            Acp.KIND_READ,
            Acp.KIND_SEARCH,
            Acp.KIND_THINK
        )

        /** 高危工具：执行 / 删除 / 移动。 */
        val HIGH_RISK_KINDS = setOf(
            Acp.KIND_EXECUTE,
            Acp.KIND_DELETE,
            Acp.KIND_MOVE
        )

        fun kindLabel(kind: String, zh: Boolean = true): String = when (kind) {
            Acp.KIND_READ -> if (zh) "读取文件" else "Read files"
            Acp.KIND_EDIT -> if (zh) "修改文件" else "Edit files"
            Acp.KIND_DELETE -> if (zh) "删除文件" else "Delete files"
            Acp.KIND_MOVE -> if (zh) "移动文件" else "Move files"
            Acp.KIND_SEARCH -> if (zh) "搜索" else "Search"
            Acp.KIND_EXECUTE -> if (zh) "执行命令" else "Run commands"
            Acp.KIND_THINK -> if (zh) "思考" else "Think"
            Acp.KIND_FETCH -> if (zh) "联网/抓取" else "Fetch"
            Acp.KIND_SWITCH_MODE -> if (zh) "切换模式" else "Switch mode"
            else -> if (zh) "其他工具" else "Other tools"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    var mode by mutableStateOf(loadMode())
        private set

    var alwaysAllowKinds by mutableStateOf(loadKinds(KEY_ALLOW))
        private set

    var alwaysDenyKinds by mutableStateOf(loadKinds(KEY_DENY))
        private set

    /** 最近 40 条授权决策，设置页里展示。 */
    var audit by mutableStateOf<List<AcpPermissionAudit>>(emptyList())
        private set

    /** 兼容旧设置：面板里的「自动授权」开关现在映射到 AUTO。 */
    val autoApprove: Boolean get() = mode == AcpApprovalMode.AUTO

    fun applyMode(value: AcpApprovalMode) {
        mode = value
        prefs.edit { putString(KEY_MODE, value.name) }
    }

    /** 兼容旧开关。 */
    fun setAutoApprove(enabled: Boolean) {
        applyMode(if (enabled) AcpApprovalMode.AUTO else AcpApprovalMode.SMART)
    }

    fun remember(kind: String, allow: Boolean) {
        val key = kind.ifBlank { Acp.KIND_OTHER }
        if (allow) {
            alwaysDenyKinds = alwaysDenyKinds - key
            alwaysAllowKinds = alwaysAllowKinds + key
        } else {
            alwaysAllowKinds = alwaysAllowKinds - key
            alwaysDenyKinds = alwaysDenyKinds + key
        }
        persistKinds()
    }

    fun forget(kind: String) {
        alwaysAllowKinds = alwaysAllowKinds - kind
        alwaysDenyKinds = alwaysDenyKinds - kind
        persistKinds()
    }

    fun clearRules() {
        alwaysAllowKinds = emptySet()
        alwaysDenyKinds = emptySet()
        persistKinds()
    }

    /** 根据当前模式与记忆规则裁决；Ask 表示需要弹卡片。 */
    fun decide(kind: String, options: List<AcpPermissionOption>): AcpPermissionVerdict {
        val key = kind.ifBlank { Acp.KIND_OTHER }
        if (key in alwaysDenyKinds) {
            val reject = pickOption(options, Acp.PERM_REJECT_ONCE, Acp.PERM_REJECT_ALWAYS)
            return if (reject != null) {
                AcpPermissionVerdict.Select(reject.optionId, allowed = false, reason = "规则：总是拒绝 ${kindLabel(key)}")
            } else {
                AcpPermissionVerdict.Ask("规则命中拒绝，但 Agent 未提供拒绝选项")
            }
        }
        if (key in alwaysAllowKinds) {
            val allow = pickOption(options, Acp.PERM_ALLOW_ONCE, Acp.PERM_ALLOW_ALWAYS)
            return if (allow != null) {
                AcpPermissionVerdict.Select(allow.optionId, allowed = true, reason = "规则：总是允许 ${kindLabel(key)}")
            } else {
                AcpPermissionVerdict.Ask("规则命中允许，但 Agent 未提供允许选项")
            }
        }

        val autoAllow = when (mode) {
            AcpApprovalMode.AUTO -> true
            AcpApprovalMode.SMART -> key in READ_ONLY_KINDS
            AcpApprovalMode.READ_ONLY -> key in READ_ONLY_KINDS
            AcpApprovalMode.ASK -> false
        }
        val autoDeny = mode == AcpApprovalMode.READ_ONLY && key !in READ_ONLY_KINDS

        return when {
            autoAllow -> {
                val allow = pickOption(options, Acp.PERM_ALLOW_ONCE, Acp.PERM_ALLOW_ALWAYS)
                if (allow != null) {
                    AcpPermissionVerdict.Select(allow.optionId, allowed = true, reason = "模式：${mode.name}")
                } else {
                    AcpPermissionVerdict.Ask("自动放行失败：Agent 未提供允许选项")
                }
            }

            autoDeny -> {
                val reject = pickOption(options, Acp.PERM_REJECT_ONCE, Acp.PERM_REJECT_ALWAYS)
                if (reject != null) {
                    AcpPermissionVerdict.Select(reject.optionId, allowed = false, reason = "只读模式拦截 ${kindLabel(key)}")
                } else {
                    AcpPermissionVerdict.Ask("只读模式拦截，但 Agent 未提供拒绝选项")
                }
            }

            else -> AcpPermissionVerdict.Ask("需要确认")
        }
    }

    /** 记录一次决策。 */
    fun record(
        kind: String,
        title: String,
        decision: String,
        detail: String
    ) {
        val entry = AcpPermissionAudit(System.currentTimeMillis(), kind, title, decision, detail)
        audit = (listOf(entry) + audit).take(40)
    }

    private fun pickOption(
        options: List<AcpPermissionOption>,
        preferred: String,
        fallback: String
    ): AcpPermissionOption? =
        options.firstOrNull { it.kind == preferred }
            ?: options.firstOrNull { it.kind == fallback }
            ?: options.firstOrNull { it.kind.startsWith("allow_") || it.kind.startsWith("reject_") }

    private fun loadMode(): AcpApprovalMode {
        val stored = AcpApprovalMode.fromKey(prefs.getString(KEY_MODE, null))
        if (stored != null) return stored
        // 旧版本只有 auto_approve 布尔值：开 -> AUTO，关 -> SMART（读操作免打扰）
        return if (legacy.getBoolean(LEGACY_AUTO_APPROVE, false)) AcpApprovalMode.AUTO else AcpApprovalMode.SMART
    }

    private fun loadKinds(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun persistKinds() {
        prefs.edit {
            putStringSet(KEY_ALLOW, alwaysAllowKinds)
            putStringSet(KEY_DENY, alwaysDenyKinds)
        }
    }
}
