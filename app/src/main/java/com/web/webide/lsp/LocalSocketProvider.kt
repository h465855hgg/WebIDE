package com.web.webide.lsp

import android.net.LocalSocket
import android.net.LocalSocketAddress
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream

/**
 * 自定义的 LocalSocket 连接提供者
 * 用于适配 Android 的 IPC 通信机制
 */
class LocalSocketProvider(private val socketName: String) : StreamConnectionProvider {
    private var socket: LocalSocket? = null

    override fun start() {
        // 创建并连接到服务端
        socket = LocalSocket().apply {
            connect(LocalSocketAddress(socketName))
        }
    }

    override val inputStream: InputStream
        get() = socket?.inputStream ?: throw IllegalStateException("Socket not connected")

    override val outputStream: OutputStream
        get() = socket?.outputStream ?: throw IllegalStateException("Socket not connected")

    override fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}