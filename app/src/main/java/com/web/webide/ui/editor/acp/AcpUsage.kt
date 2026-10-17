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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Token 估算。
 *
 * ACP 本身**不要求** Agent 上报 token 用量，所以客户端必须有一个兜底估算：
 * - 中日韩字符 ≈ 1 token/字（BPE 里常用汉字大多独占一个 token）
 * - 其余字符 ≈ 1 token / 3.6 字符（代码比自然语言更碎，取偏保守值）
 *
 * 结果只用于「上下文大概用了多少」，界面统一带 `≈`，不冒充账单级精度。
 */
internal object AcpTokenEstimator {

    fun estimate(text: String): Long {
        if (text.isEmpty()) return 0L
        var cjk = 0L
        var other = 0L
        for (ch in text) {
            val code = ch.code
            val isCjk = code in 0x4E00..0x9FFF ||
                code in 0x3400..0x4DBF ||
                code in 0x3040..0x30FF ||
                code in 0xAC00..0xD7AF ||
                code in 0x3000..0x303F ||
                code in 0xFF00..0xFFEF
            if (isCjk) cjk++ else other++
        }
        val tokens = cjk * 1.0 + other / 3.6
        return tokens.toLong().coerceAtLeast(1L)
    }

    fun estimateItems(items: List<AcpTimelineItem>): Long =
        items.sumOf { item ->
            when (item) {
                is AcpUserMessage -> estimate(item.text)
                is AcpAgentMessage -> estimate(item.text)
                is AcpThoughtMessage -> estimate(item.text)
                is AcpNoticeMessage -> estimate(item.text)
                is AcpPlanItem -> item.entries.sumOf { estimate(it.content) }
                is AcpToolCallItem -> estimate(item.title) +
                    item.contents.sumOf { content ->
                        when (content) {
                            is AcpToolContent.Text -> estimate(content.text)
                            is AcpToolContent.Diff ->
                                estimate(content.oldText.orEmpty()) + estimate(content.newText)

                            is AcpToolContent.TerminalRef -> 0L
                        }
                    } +
                    estimate(item.rawInput.orEmpty()) / 2
            }
        }
}

/** 用量来源：估算值 / Agent 上报。 */
internal enum class AcpUsageSource { ESTIMATED, REPORTED }

/**
 * 单个会话的用量与上下文统计。
 *
 * 字段全部是 Compose state，面板可以直接观察；会话切换时 [reset]。
 */
@Stable
internal class AcpUsageTracker {

    var inputTokens by mutableStateOf(0L)
        private set

    var outputTokens by mutableStateOf(0L)
        private set

    /** 当前对话塞进模型的大致 token 数（含工具输出与思考）。 */
    var contextTokens by mutableStateOf(0L)
        private set

    var contextWindow by mutableStateOf(DEFAULT_CONTEXT_WINDOW)
        private set

    var model by mutableStateOf<String?>(null)
        private set

    var turns by mutableStateOf(0)
        private set

    var toolCalls by mutableStateOf(0)
        private set

    var source by mutableStateOf(AcpUsageSource.ESTIMATED)
        private set

    val contextFraction: Float
        get() = if (contextWindow <= 0L) 0f
        else (contextTokens.toFloat() / contextWindow.toFloat()).coerceIn(0f, 1f)

    fun reset() {
        inputTokens = 0L
        outputTokens = 0L
        contextTokens = 0L
        contextWindow = DEFAULT_CONTEXT_WINDOW
        model = null
        turns = 0
        toolCalls = 0
        source = AcpUsageSource.ESTIMATED
    }

    /** 从本地会话恢复累计用量，并用时间线重新估算上下文占用。 */
    fun restore(input: Long, output: Long, turns: Int, toolCalls: Int, items: List<AcpTimelineItem>) {
        inputTokens = input
        outputTokens = output
        this.turns = turns
        this.toolCalls = toolCalls
        contextTokens = AcpTokenEstimator.estimateItems(items)
        source = AcpUsageSource.ESTIMATED
    }

    fun onToolCallCount() {
        toolCalls += 1
    }

    fun onPrompt(text: String) {
        val tokens = AcpTokenEstimator.estimate(text)
        inputTokens += tokens
        contextTokens += tokens
        turns += 1
    }

    fun onAgentText(text: String) {
        if (text.isEmpty()) return
        val tokens = AcpTokenEstimator.estimate(text)
        outputTokens += tokens
        contextTokens += tokens
    }

    fun onToolCall(title: String, detail: String) {
        val tokens = AcpTokenEstimator.estimate(title) + AcpTokenEstimator.estimate(detail)
        contextTokens += tokens
        toolCalls += 1
    }

    fun applyModel(name: String?) {
        val clean = name?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == model) return
        model = clean
        contextWindow = contextWindowFor(clean)
    }

    /** 从 `session/prompt` 响应或 `session/update._meta` 里尽力解析真实用量。 */
    fun applyReported(usage: JSONObject?) {
        if (usage == null) return
        val reportedIn = firstLong(usage, "inputTokens", "input_tokens", "promptTokens", "prompt_tokens")
        val reportedOut = firstLong(usage, "outputTokens", "output_tokens", "completionTokens", "completion_tokens")
        val total = firstLong(usage, "totalTokens", "total_tokens")
        if (reportedIn == null && reportedOut == null && total == null) return

        reportedIn?.let {
            // 兼容「累计」与「本回合」两种上报口径：
            // 单调递增且不小于当前值 -> 视为累计总量；否则视为本回合增量。
            inputTokens = if (it >= inputTokens) it else inputTokens + it
        }
        reportedOut?.let {
            outputTokens = if (it >= outputTokens) it else outputTokens + it
        }
        val inValue = reportedIn ?: 0L
        val outValue = reportedOut ?: 0L
        val turnTotal = if (inValue + outValue > 0) inValue + outValue else total ?: 0L
        if (turnTotal > 0) {
            contextTokens = maxOf(contextTokens, turnTotal)
            source = AcpUsageSource.REPORTED
        }
    }

    private fun firstLong(json: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            if (json.has(key) && !json.isNull(key)) {
                return json.optLong(key, -1L).takeIf { it >= 0L }
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_CONTEXT_WINDOW = 128_000L

        /** 常见模型上下文窗口的保守估计（拿不准就给 128k）。 */
        fun contextWindowFor(model: String?): Long {
            if (model.isNullOrBlank()) return DEFAULT_CONTEXT_WINDOW
            val name = model.lowercase()
            return when {
                name.contains("gemini") -> 1_048_576L
                name.contains("gpt-5") || name.contains("gpt5") -> 400_000L
                name.contains("codex") -> 272_000L
                name.contains("claude") -> 200_000L
                name.contains("glm") -> 204_800L
                name.contains("qwen") -> 262_144L
                name.contains("kimi") || name.contains("moonshot") -> 262_144L
                name.contains("deepseek") -> 131_072L
                name.contains("grok") -> 256_000L
                name.contains("mistral") || name.contains("codestral") -> 128_000L
                name.contains("llama") -> 131_072L
                name.contains("opencode") -> 200_000L
                else -> DEFAULT_CONTEXT_WINDOW
            }
        }

        fun formatTokens(tokens: Long): String = when {
            tokens < 1_000L -> tokens.toString()
            tokens < 10_000L -> String.format(java.util.Locale.US, "%.1fk", tokens / 1_000.0)
            tokens < 1_000_000L -> String.format(java.util.Locale.US, "%.0fk", tokens / 1_000.0)
            else -> String.format(java.util.Locale.US, "%.2fm", tokens / 1_000_000.0)
        }
    }
}
