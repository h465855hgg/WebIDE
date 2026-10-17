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
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * 实现 ACP 的 `terminal` 系列能力：Agent 可以通过它在客户端环境里执行命令，
 * 并拿到流式输出与退出码。
 *
 * 命令同样通过 proot Alpine 执行，因此 Agent 能复用容器内的 node / npm / git 等工具链。
 */
internal class AcpTerminalManager(private val context: Context) {

    companion object {
        private const val TAG = "ACP-Terminal"
        private const val DEFAULT_OUTPUT_LIMIT = 1_000_000
    }

    /** 带字节上限的输出缓冲，超限时从头部截断（按字符边界）。 */
    private class OutputBuffer(private val limit: Int) {
        private val builder = StringBuilder()

        @Volatile
        var truncated: Boolean = false
            private set

        @Synchronized
        fun append(text: String) {
            builder.append(text)
            if (builder.length > limit) {
                builder.delete(0, builder.length - limit)
                truncated = true
            }
        }

        @Synchronized
        fun snapshot(): String = builder.toString()
    }

    private class Terminal(
        val process: Process,
        val output: OutputBuffer,
        val exit: CompletableDeferred<Int>
    ) {
        /** 进程退出码；为 null 表示仍在运行。 */
        @Volatile
        var exitCode: Int? = null
    }

    private val terminals = ConcurrentHashMap<String, Terminal>()
    private val nextId = AtomicLong(1)

    /** `terminal/create`：立即返回 terminalId，命令在后台运行。 */
    fun create(params: JSONObject): JSONObject {
        val command = params.strOrEmpty("command")
        if (command.isBlank()) {
            throw AcpException(Acp.ERR_INVALID_PARAMS, "terminal/create 缺少 command")
        }
        val args = params.arrOrNull("args")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        } ?: emptyList()

        val env = params.arrOrNull("env")?.let { array ->
            buildMap {
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i) ?: continue
                    val name = entry.strOrNull("name") ?: continue
                    put(name, entry.strOrEmpty("value"))
                }
            }
        } ?: emptyMap()

        val cwd = params.strOrNull("cwd")
        val workDir = if (cwd != null && File(cwd).isDirectory) cwd else "/root"
        val limit = params.intOrNull("outputByteLimit") ?: DEFAULT_OUTPUT_LIMIT

        val fullCommand = AlpineManager.buildProotCommand(
            context = context,
            command = (listOf(command) + args).toTypedArray(),
            workDir = workDir,
            extraEnv = env
        )

        val builder = ProcessBuilder(fullCommand)
        builder.environment().putAll(AlpineManager.getProotEnv(context))
        builder.directory(context.filesDir)

        val process = try {
            builder.start()
        } catch (e: Exception) {
            Log.e(TAG, "启动命令失败: $fullCommand", e)
            throw AcpException(Acp.ERR_INTERNAL, "启动命令失败: ${e.message}")
        }

        val terminalId = "term_${nextId.getAndIncrement()}"
        val buffer = OutputBuffer(limit)
        val exit = CompletableDeferred<Int>()
        val terminal = Terminal(process, buffer, exit)
        terminals[terminalId] = terminal

        pump(terminalId, process.inputStream.bufferedReader(Charsets.UTF_8), buffer)
        pump(terminalId, process.errorStream.bufferedReader(Charsets.UTF_8), buffer)

        thread(name = "acp-term-wait-$terminalId") {
            val code = try {
                process.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            terminal.exitCode = code
            exit.complete(code)
        }

        Log.d(TAG, "创建终端 $terminalId: $fullCommand")
        return JSONObject().put("terminalId", terminalId)
    }

    private fun pump(terminalId: String, reader: BufferedReader, buffer: OutputBuffer) {
        thread(name = "acp-term-out-$terminalId") {
            try {
                reader.use { input ->
                    val chunk = CharArray(2048)
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        buffer.append(String(chunk, 0, read))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "终端 $terminalId 输出流结束: ${e.message}")
            }
        }
    }

    /** `terminal/output`：返回当前输出与（若已结束）退出状态。 */
    fun output(params: JSONObject): JSONObject {
        val terminal = require(params)
        val result = JSONObject().apply {
            put("output", terminal.output.snapshot())
            put("truncated", terminal.output.truncated)
        }
        val exitCode = terminal.exitCode
        if (exitCode != null) {
            result.put(
                "exitStatus",
                JSONObject().apply {
                    put("exitCode", exitCode)
                    put("signal", JSONObject.NULL)
                }
            )
        }
        return result
    }

    /** `terminal/wait_for_exit`：等待命令结束并返回退出码。 */
    suspend fun waitForExit(params: JSONObject): JSONObject {
        val terminal = require(params)
        val exitCode = terminal.exit.await()
        return JSONObject().apply {
            put("exitCode", exitCode)
            put("signal", JSONObject.NULL)
        }
    }

    /** `terminal/kill`：终止命令但不释放终端。 */
    fun kill(params: JSONObject) {
        val terminal = terminals[requireId(params)] ?: return
        destroy(terminal)
    }

    /** `terminal/release`：终止并释放终端资源。 */
    fun release(params: JSONObject) {
        val id = requireId(params)
        val terminal = terminals.remove(id) ?: return
        destroy(terminal)
        Log.d(TAG, "释放终端 $id")
    }

    /** 断开连接时清理全部终端。 */
    fun shutdown() {
        terminals.values.forEach { destroy(it) }
        terminals.clear()
    }

    private fun destroy(terminal: Terminal) {
        try {
            if (terminal.process.isAlive) {
                terminal.process.destroy()
            }
        } catch (_: Exception) {
        }
    }

    private fun requireId(params: JSONObject): String {
        return params.strOrNull("terminalId")
            ?: throw AcpException(Acp.ERR_INVALID_PARAMS, "缺少 terminalId")
    }

    private fun require(params: JSONObject): Terminal {
        val id = requireId(params)
        return terminals[id] ?: throw AcpException(Acp.ERR_INVALID_PARAMS, "未知的终端: $id")
    }
}
