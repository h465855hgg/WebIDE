package com.web.webide.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class WebTextDocumentService : TextDocumentService {
    private var client: LanguageClient? = null
    // 1. 用于在内存中缓存文件内容 (URI -> Content)
    private val documentBuffer = ConcurrentHashMap<String, String>()

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync {
            val items = ArrayList<CompletionItem>()
            val uri = position.textDocument.uri
            val content = documentBuffer[uri] ?: ""

            // 2. 获取光标位置的上下文
            val lineNum = position.position.line
            val charNum = position.position.character

            // 简单解析：获取当前行的内容
            val lines = content.split("\n")
            val currentLine = if (lineNum < lines.size) lines[lineNum] else ""

            // 获取光标前的一个字符
            // 注意安全检查，防止索引越界
            val charBeforeCursor = if (charNum > 0 && charNum <= currentLine.length) {
                currentLine[charNum - 1]
            } else {
                ' '
            }

            // 判断是否已经输入了 '<'
            val hasOpenBracket = charBeforeCursor == '<'

            // 添加 HTML 常见标签补全
            val htmlTags = listOf("div", "span", "p", "a", "button", "input", "script", "style", "html", "body", "head", "ul", "li")

            htmlTags.forEach { tag ->
                items.add(CompletionItem().apply {
                    label = tag // 标签名显示保持纯净，不要带 <
                    kind = CompletionItemKind.Snippet
                    detail = "HTML Tag"

                    // 3. 动态调整插入文本
                    if (hasOpenBracket) {
                        // 如果前面已经是 '<'，我们只补全剩下的部分
                        // 例如输入了 '<'，补全变成 "div>$0</div>"
                        insertText = "$tag>\$0</$tag>"
                    } else {
                        // 如果前面是空的，补全完整的标签
                        // 例如输入了空，补全变成 "<div>$0</div>"
                        insertText = "<$tag>\$0</$tag>"
                    }

                    insertTextFormat = InsertTextFormat.Snippet
                })
            }

            // 添加 CSS/JS 常见关键字 (仅在没有输入 < 时显示，防止干扰 HTML 编写)
            if (!hasOpenBracket) {
                val keywords = listOf("function", "const", "let", "var", "return", "class", "import", "color", "background", "console")
                keywords.forEach { word ->
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

    override fun didOpen(params: DidOpenTextDocumentParams) {
        // 4. 文件打开时，初始化缓存
        documentBuffer[params.textDocument.uri] = params.textDocument.text
        client?.logMessage(MessageParams(MessageType.Info, "LSP: Opened ${params.textDocument.uri}"))
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // 5. 文件修改时，更新缓存
        // 因为我们在 initialize 中设置了 TextDocumentSyncKind.Full，所以 changes[0].text 是全量文本
        if (params.contentChanges.isNotEmpty()) {
            documentBuffer[params.textDocument.uri] = params.contentChanges[0].text
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        // 关闭文件时清理缓存
        documentBuffer.remove(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }
}