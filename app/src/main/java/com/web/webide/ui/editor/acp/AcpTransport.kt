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
package com.web.webide.ui.editor.acp

import android.content.Context
import android.util.Log
import com.web.webide.ui.terminal.AlpineManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * ACP 传输层：在 proot Alpine 环境中拉起 Agent 子进程，并通过 stdio 交换
 * 换行分隔的 JSON-RPC 消息。
 *
 * 复用 [AlpineManager.buildProotCommand]，与 LSP 的
 * `ProotStreamConnectionProvider` 走同一套 proot 启动方式。
 */
internal class AcpTransport(
    private val context: Context,
    private val command: List<String>,
    private val workDir: String,
    private val extraEnv: Map<String, String>,
    private val listener: Listener
) {

    interface Listener {
        /** 收到一条完整的 JSON-RPC 消息。 */
        fun onMessage(message: JSONObject)

        /** Agent 写到 stderr 的一行（用于诊断展示）。 */
        fun onStderr(line: String)

        /** Agent 进程结束。 */
        fun onExit(exitCode: Int)
    }

    companion object {
        private const val TAG = "ACP-Transport"
    }

    private var process: Process? = null
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    @Volatile
    private var lastStartError: String? = null

    /** 启动 Agent 子进程。失败时抛异常。 */
    fun start() {
        val resolvedWorkDir = if (java.io.File(workDir).isDirectory) workDir else "/root"
        val fullCommand = AlpineManager.buildProotCommand(
            context = context,
            command = command.toTypedArray(),
            workDir = resolvedWorkDir,
            extraEnv = extraEnv
        )
        val hostEnv = AlpineManager.getProotEnv(context)

        Log.d(TAG, "启动 Agent: $fullCommand (cwd=$resolvedWorkDir)")

        val builder = ProcessBuilder(fullCommand)
        builder.environment().putAll(hostEnv)
        builder.directory(context.filesDir)

        val started = try {
            builder.start()
        } catch (e: Exception) {
            lastStartError = e.message
            Log.e(TAG, "Agent 启动失败", e)
            throw e
        }
        process = started

        thread(name = "acp-stdout") { readStdout(started) }
        thread(name = "acp-stderr") { readStderr(started) }
        thread(name = "acp-wait") {
            val code = try {
                started.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            Log.d(TAG, "Agent 进程退出，exitCode=$code")
            if (!closed.get()) listener.onExit(code)
        }
    }

    private fun readStdout(proc: Process) {
        try {
            BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val parsed = runCatching { JSONObject(line) }.getOrNull()
                    if (parsed != null) {
                        listener.onMessage(parsed)
                    } else {
                        Log.w(TAG, "无法解析的消息: ${line.take(200)}")
                        listener.onStderr("无法解析的消息: ${line.take(200)}")
                    }
                }
            }
        } catch (e: IOException) {
            if (!closed.get()) listener.onStderr("读取 Agent 输出失败: ${e.message}")
        } catch (e: Exception) {
            if (!closed.get()) listener.onStderr("读取 Agent 输出异常: ${e.message}")
        }
    }

    private fun readStderr(proc: Process) {
        try {
            BufferedReader(InputStreamReader(proc.errorStream, Charsets.UTF_8)).use { reader ->
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    Log.d(TAG, "stderr: $line")
                    listener.onStderr(line)
                }
            }
        } catch (_: IOException) {
            // 进程被销毁时流关闭，属正常现象
        } catch (_: Exception) {
        }
    }

    /** 发送一条 JSON-RPC 消息（追加换行作为帧分隔）。 */
    fun send(message: JSONObject) {
        val proc = process ?: throw AcpConnectionLostException("Agent 进程未启动")
        if (closed.get()) throw AcpConnectionLostException("连接已关闭")
        try {
            synchronized(writeLock) {
                val stream = proc.outputStream
                stream.write(message.toString().toByteArray(Charsets.UTF_8))
                stream.write('\n'.code)
                stream.flush()
            }
        } catch (e: IOException) {
            throw AcpConnectionLostException("写入 Agent 失败: ${e.message}")
        }
    }

    /** Agent 是否仍在运行。 */
    fun isAlive(): Boolean = process?.isAlive == true && !closed.get()

    fun startErrorMessage(): String? = lastStartError

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val proc = process
        process = null
        try {
            proc?.outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            proc?.destroy()
        } catch (_: Exception) {
        }
    }
}
