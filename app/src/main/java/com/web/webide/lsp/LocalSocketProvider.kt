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