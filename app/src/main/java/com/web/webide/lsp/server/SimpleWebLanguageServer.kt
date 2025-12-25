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