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

    // SimpleWebLanguageServer.kt

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities().apply {
            // 必须确认这里是 Full，否则 didChange 收不到全量文本，documentBuffer 就会出错
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

            completionProvider = CompletionOptions().apply {
                resolveProvider = false
                triggerCharacters = listOf(".", "<", "/", ":", " ", "\n")
            }
        }
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