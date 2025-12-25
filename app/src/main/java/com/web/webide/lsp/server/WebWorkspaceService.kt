package com.web.webide.lsp.server

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class WebWorkspaceService : WorkspaceService {
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
    }
}