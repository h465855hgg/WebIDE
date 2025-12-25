package com.web.webide.lsp.server

import android.util.Log
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class SimpleWebLanguageServer : LanguageServer, LanguageClientAware {
    private var client: LanguageClient? = null
    private val textDocumentService = WebTextDocumentService()
    private val workspaceService = WebWorkspaceService()

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        Log.d("LSP_Server", "Received initialize request!") // <--- 关键日志 1

        val capabilities = ServerCapabilities().apply {
            // 1. 声明支持全量文本同步（必须，否则补全时服务端不知道文件内容）
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

            // 2. 详细配置补全能力
            completionProvider = CompletionOptions().apply {
                resolveProvider = false
                // 关键：声明触发字符。当用户输入这些符号时，编辑器会自动请求补全
                triggerCharacters = listOf(".", "<", "/", ":", " ", "\n")
            }
        }

        Log.d("LSP_Server", "Returning capabilities...") // <--- 关键日志 2
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        // 退出逻辑
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    override fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.connect(client)
    }
}