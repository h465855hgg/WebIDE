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

import android.app.Service
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.IBinder
import android.util.Log
import com.web.webide.lsp.server.SimpleWebLanguageServer
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LspService : Service() {
    private lateinit var serverSocket: LocalServerSocket
    private var isRunning = false
    // 建议使用线程池来处理并发连接
    private val cachedPool = Executors.newCachedThreadPool()

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY
        isRunning = true

        thread {
            try {
                // 必须与 Client 端连接的名字一致
                serverSocket = LocalServerSocket("web-lsp-socket")
                Log.d("LspService", "LSP Server started on local socket: web-lsp-socket")

                while (isRunning) {
                    val clientSocket = serverSocket.accept()
                    Log.d("LspService", "Client connected")
                    // 将连接交给线程池处理
                    cachedPool.execute { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e("LspService", "Server error", e)
            }
        }
        return START_STICKY
    }

    private fun handleClient(socket: LocalSocket) {
        Log.d("LSP_Service", "handleClient started, preparing launcher...")
        try {
            // 1. 创建我们自定义的语言服务器实例
            val server = SimpleWebLanguageServer()

            // 2. 使用 LSP4J 创建 Launcher
            // 它会自动对接 Socket 的输入输出流，解析 JSON-RPC
            val launcher = Launcher.createLauncher(
                server,
                LanguageClient::class.java,
                socket.inputStream,
                socket.outputStream
            )

            // 3. 将 Client 的代理对象传给 Server（以便 Server 给 Client 发消息，如诊断报错）
            server.connect(launcher.remoteProxy)

            // 4. 开始监听 (这是一个阻塞操作，直到连接断开)
            Log.d("LspService", "LSP Launcher starting listening...")
            val listeningFuture = launcher.startListening()

            // 等待直到结束
            listeningFuture.get()

        } catch (e: Exception) {
            Log.e("LspService", "Error in LSP session", e)
        } finally {
            try {
                socket.close()
                Log.d("LspService", "Client disconnected")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        try {
            serverSocket.close()
            cachedPool.shutdownNow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}