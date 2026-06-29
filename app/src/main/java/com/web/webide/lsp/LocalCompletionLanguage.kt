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
package com.web.webide.lsp

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import java.util.regex.Pattern

/**
 * 本地补全 Language 包装类
 *
 * 包装现有的 Language（TsLanguage / TextMateLanguage），保留原有语法高亮能力，
 * 同时提供纯离线的代码补全：语言关键字 + 当前文件标识符提取。
 *
 * 完全不依赖 Node.js / Alpine / LSP 进程，零网络依赖。
 */
class LocalCompletionLanguage(
    private val delegate: Language,
    fileExtension: String
) : Language {

    private val autoComplete = IdentifierAutoComplete()
    private val identifierPattern: Pattern

    init {
        val keywords = LanguageKeywords.getKeywords(fileExtension)
        autoComplete.setKeywords(keywords, true)
        // 标识符正则：字母/数字/下划线/美元符号（兼容 JS/TS/PHP）
        // 对于 HTML/CSS，连字符也作为标识符的一部分
        identifierPattern = when (fileExtension.lowercase()) {
            "html", "htm", "css", "scss", "sass", "less" ->
                Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*")
            else ->
                Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*")
        }
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // 1. 计算当前前缀（光标前的单词）
        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            ch.isLetterOrDigit() || ch == '_' || ch == '$' || ch == '-'
        }

        if (prefix.isEmpty()) return

        // 2. 从当前文件内容提取标识符
        val identifiers = extractIdentifiers(content, position)

        // 3. 使用 sora-editor 内置的 IdentifierAutoComplete 提供补全
        try {
            autoComplete.requireAutoComplete(content, position, prefix, publisher, identifiers)
        } catch (_: CompletionCancelledException) {
            // 正常现象：用户继续输入时旧的补全请求会被取消
        }
    }

    /**
     * 从文件内容中提取标识符
     * 限制扫描行数以避免大文件卡顿
     */
    private fun extractIdentifiers(content: ContentReference, cursor: CharPosition): IdentifierAutoComplete.DisposableIdentifiers {
        val identifiers = IdentifierAutoComplete.DisposableIdentifiers()
        identifiers.beginBuilding()

        try {
            val maxLines = minOf(content.lineCount, 800)
            val matcher = identifierPattern.matcher("")

            for (line in 0 until maxLines) {
                val lineText = content.getLine(line).toString()
                matcher.reset(lineText)
                while (matcher.find()) {
                    val word = matcher.group()
                    // 过滤太短的标识符（1个字符的通常没意义）和纯数字
                    if (word.length >= 2 && !word[0].isDigit()) {
                        identifiers.addIdentifier(word)
                    }
                }
            }
        } catch (_: Exception) {
            // ContentReference 读取可能抛出 CompletionCancelledException，忽略即可
        }

        identifiers.finishBuilding()
        return identifiers
    }

    // ===== 以下全部委托给原始 Language，保留语法高亮/格式化/符号配对等能力 =====

    override fun getAnalyzeManager(): AnalyzeManager = delegate.analyzeManager

    override fun getInterruptionLevel(): Int = delegate.interruptionLevel

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int =
        delegate.getIndentAdvance(content, line, column)

    override fun getIndentAdvance(
        content: ContentReference,
        line: Int,
        column: Int,
        spaceCountOnLine: Int,
        tabCountOnLine: Int
    ): Int = delegate.getIndentAdvance(content, line, column, spaceCountOnLine, tabCountOnLine)

    override fun useTab(): Boolean = delegate.useTab()

    override fun getFormatter(): Formatter = delegate.formatter

    override fun getSymbolPairs(): SymbolPairMatch = delegate.symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler?> = delegate.newlineHandlers ?: emptyArray()

    override fun getQuickQuoteHandler() = delegate.quickQuoteHandler

    override fun destroy() {
        delegate.destroy()
    }
}
