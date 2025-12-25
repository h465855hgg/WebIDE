/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

class WebTextDocumentService : TextDocumentService {
    private var client: LanguageClient? = null
    // 1. 新增：用于在内存中缓存文件内容，以便服务端分析上下文
    private val documentCache = ConcurrentHashMap<String, String>()

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync {
            val items = ArrayList<CompletionItem>()
            val uri = position.textDocument.uri
            val content = documentCache[uri] ?: ""

            // 2. 获取光标位置的前文信息
            val (triggerChar, prefix) = getContextAt(content, position.position)

            // 3. 定义 HTML 标签列表
            val htmlTags = listOf("div", "span", "p", "a", "button", "input", "script", "style", "html", "body", "head", "ul", "li", "h1", "h2", "h3", "form", "img")

            htmlTags.forEach { tag ->
                // 简单的过滤：如果用户输入了 "ht"，只显示 html 等相关标签
                if (prefix.isNotEmpty() && !tag.startsWith(prefix)) return@forEach

                items.add(CompletionItem().apply {
                    label = tag
                    kind = CompletionItemKind.Snippet
                    detail = "HTML Tag"

                    // 4. 核心修复逻辑：根据触发字符动态调整 insertText
                    if (triggerChar == "<") {
                        // 场景：用户输入了 "<" -> 补全内容不应包含开头的 "<"
                        insertText = "$tag>\$0</$tag>"
                        // 强制标签显示为 "<tag>" 让用户看起来更直观，或者保持 "tag"
                        label = tag
                    } else if (triggerChar == "/") {
                        // 场景：用户输入了 "</" -> 补全闭合标签
                        insertText = "$tag>"
                        label = "$tag>"
                        detail = "Close Tag"
                    } else {
                        // 场景：空格或其他位置 -> 补全完整的 "<tag>...</tag>"
                        insertText = "<$tag>\$0</$tag>"
                        label = "<$tag>"
                    }

                    insertTextFormat = InsertTextFormat.Snippet
                })
            }

            // 只有在非标签触发的情况下才推荐关键字（避免在 < 后面推荐 function）
            if (triggerChar != "<" && triggerChar != "/") {
                val keywords = listOf("function", "const", "let", "var", "return", "class", "import", "color", "background", "width", "height")
                keywords.forEach { word ->
                    if (prefix.isNotEmpty() && !word.startsWith(prefix)) return@forEach

                    items.add(CompletionItem().apply {
                        label = word
                        kind = CompletionItemKind.Keyword
                        detail = "Keyword"
                        insertText = word
                    })
                }
            }

            Either.forLeft(items)
        }
    }

    /**
     * 辅助方法：获取光标前的触发字符和当前正在输入的单词前缀
     * 返回 Pair(触发字符, 单词前缀)
     * 例如输入 "<ht|" -> 返回 ("<", "ht")
     * 例如输入 "<|"   -> 返回 ("<", "")
     */
    private fun getContextAt(content: String, position: Position): Pair<String, String> {
        if (content.isEmpty()) return Pair("", "")

        val lines = content.split("\n")
        if (position.line >= lines.size) return Pair("", "")

        val lineText = lines[position.line]
        val col = position.character
        if (col <= 0) return Pair("", "")

        // 截取光标所在行，光标之前的内容
        val textBefore = lineText.substring(0, col)

        // 1. 检查是否刚好在 < 或 / 后面
        if (textBefore.endsWith("<")) return Pair("<", "")
        if (textBefore.endsWith("</")) return Pair("/", "")

        // 2. 尝试向前查找最近的触发字符，并提取前缀
        // 例如 "... <ht" -> 找到 <，前缀是 ht
        val lastOpenAngle = textBefore.lastIndexOf('<')
        val lastSpace = textBefore.lastIndexOf(' ')
        val lastSeparator = maxOf(lastOpenAngle, lastSpace)

        if (lastSeparator != -1 && lastOpenAngle > lastSpace) {
            // 说明是在 < 之后输入的内容
            val prefix = textBefore.substring(lastSeparator + 1)
            // 检查是不是 </
            if (lastSeparator > 0 && textBefore[lastSeparator - 1] == '<' && textBefore[lastSeparator] == '/') {
                return Pair("/", prefix)
            }
            return Pair("<", prefix)
        }

        // 普通单词前缀
        if (lastSeparator != -1) {
            return Pair("", textBefore.substring(lastSeparator + 1))
        }

        return Pair("", textBefore)
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        client?.logMessage(MessageParams(MessageType.Info, "LSP: Opened ${params.textDocument.uri}"))
        // 缓存文件内容
        documentCache[params.textDocument.uri] = params.textDocument.text
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // 必须实现：更新文件内容缓存
        // 注意：这里假设使用的是 Full Sync (在 SimpleWebLanguageServer.kt 中配置了 TextDocumentSyncKind.Full)
        if (params.contentChanges.isNotEmpty()) {
            documentCache[params.textDocument.uri] = params.contentChanges[0].text
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentCache.remove(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }
}