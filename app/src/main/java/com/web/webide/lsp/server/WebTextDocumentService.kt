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


package com.web.webide.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class WebTextDocumentService : TextDocumentService {
    private var client: LanguageClient? = null
    private val documentCache = ConcurrentHashMap<String, String>()

    // 不需要闭合的标签
    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync {
            val items = ArrayList<CompletionItem>()
            try {
                val uri = params.textDocument.uri
                val content = documentCache[uri] ?: ""
                val position = params.position

                // 1. 获取上下文信息
                val (fullTextBefore, lineTextBefore, tokenStartCol) = getAnalysisContext(content, position)

                // 2. 获取当前正在输入的"Token" (例如 "<", "<h", "</", "</ht")
                // 注意：如果 tokenStartCol 越界，就默认空字符串防止崩溃
                val currentToken = if (tokenStartCol >= 0 && tokenStartCol < lineTextBefore.length) {
                    lineTextBefore.substring(tokenStartCol)
                } else {
                    ""
                }

                // 3. 确定替换范围：必须覆盖当前 Token 的所有内容 (包括 < 和 /)
                val replaceRange = Range(
                    Position(position.line, tokenStartCol),
                    Position(position.line, position.character)
                )

                // 4. 判定模式：用户是想闭合标签，还是想开启新标签？
                val isClosingMode = currentToken.startsWith("</")

                // ==========================================
                // 🧠 1. 智能闭合 (最高优先级)
                // ==========================================
                val unclosedTag = findLastUnclosedTag(fullTextBefore)
                if (unclosedTag != null) {
                    // 如果是闭合模式(</)，或者刚开始输入(<)，都推荐闭合它
                    if (isClosingMode || currentToken == "<") {
                        items.add(CompletionItem().apply {
                            label = "</$unclosedTag>" // UI显示明确带上 </
                            insertText = "</$unclosedTag>" // 插入纯净的闭合标签
                            kind = CompletionItemKind.Event
                            detail = "Auto Close"
                            sortText = "0000" // 绝对第一
                            // 必须覆盖 token
                            textEdit = Either.forLeft(TextEdit(replaceRange, insertText))
                        })
                    }
                }

                // ==========================================
                // 🏷️ 2. 常规 HTML 标签 (严格过滤)
                // ==========================================
                val htmlTags = listOf("div", "span", "p", "a", "button", "input", "script", "style", "html", "body", "head", "ul", "li", "h1", "h2", "h3", "form", "img", "table", "tr", "td", "link", "meta")

                htmlTags.forEach { tag ->
                    // 过滤：如果用户输入了字母，必须匹配才显示
                    // 例如输入 "<b"，只显示 body, button, ...
                    if (!isTokenMatch(currentToken, tag)) return@forEach

                    // --- 情况 A: 闭合模式 (用户输入了 </ ) ---
                    if (isClosingMode) {
                        // 只添加闭合标签建议！绝对不加开启标签！
                        items.add(CompletionItem().apply {
                            label = "</$tag>"
                            insertText = "</$tag>" // 替换掉 </... 变成 </tag>
                            kind = CompletionItemKind.Class // 用不同图标区分
                            sortText = "0010-$tag"
                            textEdit = Either.forLeft(TextEdit(replaceRange, insertText))
                        })
                    }
                    // --- 情况 B: 开启模式 (用户输入了 < ) ---
                    else {
                        // 1. 完整的开启标签 (<div>...</div>)
                        items.add(CompletionItem().apply {
                            label = "<$tag>"
                            // 自闭合标签不加 </tag>
                            if (voidTags.contains(tag)) {
                                insertText = "<$tag>\$0"
                            } else {
                                insertText = "<$tag>\$0</$tag>"
                            }
                            kind = CompletionItemKind.Snippet
                            sortText = "0020-$tag"
                            textEdit = Either.forLeft(TextEdit(replaceRange, insertText))
                            insertTextFormat = InsertTextFormat.Snippet
                        })

                        // 2. 也允许单纯的闭合标签 (防止用户只想手写闭合)
                        items.add(CompletionItem().apply {
                            label = "</$tag>"
                            insertText = "</$tag>"
                            kind = CompletionItemKind.Class
                            sortText = "0030-$tag" // 优先级低一点
                            detail = "Close Tag"
                            textEdit = Either.forLeft(TextEdit(replaceRange, insertText))
                        })
                    }
                }

                // ==========================================
                // 🔤 3. JS/CSS 属性 (非标签模式)
                // ==========================================
                if (!currentToken.startsWith("<") && !currentToken.startsWith("/")) {
                    val keywords = listOf("function", "const", "let", "var", "return", "class", "import", "color", "background", "width", "height", "display", "margin", "padding")
                    keywords.forEach { word ->
                        if (word.startsWith(currentToken, true)) {
                            items.add(CompletionItem().apply {
                                label = word
                                insertText = word
                                kind = CompletionItemKind.Keyword
                                textEdit = Either.forLeft(TextEdit(replaceRange, insertText))
                            })
                        }
                    }
                }

            } catch (e: Exception) {
                // Ignore
            }
            Either.forLeft(items)
        }
    }

    // 宽松匹配逻辑
    private fun isTokenMatch(token: String, tagName: String): Boolean {
        // 去掉 < 和 / 剩下的单词部分
        val cleanToken = token.replace("<", "").replace("/", "")
        if (cleanToken.isEmpty()) return true
        return tagName.startsWith(cleanToken, ignoreCase = true)
    }

    /**
     * 核心上下文解析
     * 往回找，直到遇到空格或 >，确定 Token 的起始位置
     */
    private fun getAnalysisContext(content: String, position: Position): Triple<String, String, Int> {
        val lines = content.split("\n")
        val lineText = if (position.line < lines.size) lines[position.line] else ""
        val col = position.character.coerceIn(0, lineText.length)
        val lineTextBefore = lineText.substring(0, col)

        // 全文 offset 计算
        var offset = 0
        for (i in 0 until position.line) {
            if (i < lines.size) offset += lines[i].length + 1
        }
        offset += col
        val fullTextBefore = if (offset <= content.length) content.substring(0, offset) else content

        // 寻找 Token Start
        var startIndex = col - 1
        while (startIndex >= 0) {
            val c = lineTextBefore[startIndex]
            // Token 边界是：空格 或 >
            // 注意：< 和 / 是 Token 的一部分，不能在这里 break
            if (c.isWhitespace() || c == '>') {
                startIndex++
                break
            }
            if (startIndex == 0) break
            startIndex--
        }
        val tokenStart = startIndex.coerceAtLeast(0)

        return Triple(fullTextBefore, lineTextBefore, tokenStart)
    }

    private fun findLastUnclosedTag(text: String): String? {
        val stack = ArrayList<String>()
        val matcher = Pattern.compile("<(/?)(\\w+)[^>]*>").matcher(text)
        while (matcher.find()) {
            val isClosing = matcher.group(1) == "/"
            val tagName = matcher.group(2).lowercase()
            if (voidTags.contains(tagName)) continue

            if (isClosing) {
                val index = stack.lastIndexOf(tagName)
                if (index != -1) {
                    while (stack.size > index) stack.removeAt(stack.size - 1)
                }
            } else {
                stack.add(tagName)
            }
        }
        return if (stack.isNotEmpty()) stack.last() else null
    }

    override fun didOpen(params: DidOpenTextDocumentParams) { documentCache[params.textDocument.uri] = params.textDocument.text }
    override fun didChange(params: DidChangeTextDocumentParams) { if (params.contentChanges.isNotEmpty()) documentCache[params.textDocument.uri] = params.contentChanges[0].text }
    override fun didClose(params: DidCloseTextDocumentParams) { documentCache.remove(params.textDocument.uri) }
    override fun didSave(params: DidSaveTextDocumentParams) {}
}