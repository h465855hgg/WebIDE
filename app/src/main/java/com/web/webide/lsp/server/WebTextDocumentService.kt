package com.web.webide.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class WebTextDocumentService : TextDocumentService {
    private var client: LanguageClient? = null

    fun connect(client: LanguageClient) {
        this.client = client
    }

    // 当用户请求补全时调用
    override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync {
            val items = ArrayList<CompletionItem>()


            // 添加 HTML 常见标签补全
            val htmlTags = listOf("div", "span", "p", "a", "button", "input", "script", "style", "html", "body", "head")
            htmlTags.forEach { tag ->
                items.add(CompletionItem().apply {
                    label = tag
                    kind = CompletionItemKind.Snippet
                    detail = "HTML Tag"
                    insertText = "<$tag>\$0</$tag>" // 支持 Snippet 格式，$0 是光标最终位置
                    insertTextFormat = InsertTextFormat.Snippet
                })
            }

            // 添加 CSS/JS 常见关键字
            val keywords = listOf("function", "const", "let", "var", "return", "class", "import", "color", "background")
            keywords.forEach { word ->
                items.add(CompletionItem().apply {
                    label = word
                    kind = CompletionItemKind.Keyword
                    detail = "Keyword"
                    insertText = word
                })
            }

            Either.forLeft(items)
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        // 文件打开时触发，可以在这里建立索引
        client?.logMessage(MessageParams(MessageType.Info, "LSP: Opened ${params.textDocument.uri}"))
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // 文件内容变化时触发，真正的 LSP 会在这里更新 AST 语法树
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }
}