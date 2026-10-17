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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 命令探测结果。区分「确实没装」和「探测本身失败」很重要：
 * 前者应当拦住用户并给出安装提示，后者不该误伤可用的环境。
 */
internal sealed interface BinaryProbe {
    data class Found(val path: String) : BinaryProbe
    data object Missing : BinaryProbe
    data class Failed(val reason: String) : BinaryProbe
}

/**
 * ACP Agent 的运行环境探测。
 *
 * 连接失败最常见的原因是「命令根本没装进 Alpine 容器」，或「工作目录在容器内不可达」。
 * 这里在真正拉起 Agent 之前先做一次轻量探测，把模糊的启动失败换成可执行的提示。
 */
internal object AcpEnvironment {

    private const val TAG = "ACP-Env"
    private const val MARKER_MISSING = "__ACP_MISSING__"
    private const val MARKER_OK = "__ACP_OK__"

    /** 自检报告 / 安装日志会直接显示在 UI 上，按应用语言输出。 */
    private fun t(zhText: String, enText: String): String =
        if (java.util.Locale.getDefault().language.startsWith("zh")) zhText else enText

    /** 探测类命令的默认超时。proot 首次启动要解压/挂载，给得宽松一些。 */
    const val PROBE_TIMEOUT_MILLIS = 20_000L

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean
    ) {
        val isSuccess: Boolean get() = !timedOut && exitCode == 0
    }

    /** 在 proot 容器内执行一条 shell 命令并收集 stdout（stderr 合并）。 */
    fun exec(
        context: Context,
        shellCommand: String,
        workDir: String,
        timeoutMillis: Long = 10_000L
    ): CommandResult {
        val resolvedWorkDir = if (File(workDir).isDirectory) workDir else "/root"
        val fullCommand = AlpineManager.buildProotCommand(
            context = context,
            command = arrayOf("sh", "-c", shellCommand),
            workDir = resolvedWorkDir
        )
        val builder = ProcessBuilder(fullCommand)
        builder.environment().putAll(AlpineManager.getProotEnv(context))
        builder.directory(context.filesDir)

        val process = try {
            builder.start()
        } catch (e: Exception) {
            Log.e(TAG, "探测进程启动失败", e)
            return CommandResult(-1, e.message ?: "启动失败", timedOut = false)
        }

        val sink = StringBuilder()
        val reader = thread(name = "acp-probe-out") {
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { stream ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        synchronized(sink) { sink.append(buffer, 0, read) }
                    }
                }
            } catch (_: Exception) {
            }
        }
        // stderr 必须一起抽干，否则命令写满管道会阻塞（npm 的进度就打在 stderr）
        val errReader = thread(name = "acp-probe-err") {
            try {
                process.errorStream.bufferedReader(Charsets.UTF_8).use { stream ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        synchronized(sink) { sink.append(buffer, 0, read) }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Process.waitFor(long, TimeUnit) 需要 API 26，这里用 CountDownLatch 兼容低版本
        val latch = CountDownLatch(1)
        thread(name = "acp-probe-wait") {
            try {
                process.waitFor()
            } catch (_: InterruptedException) {
            } finally {
                latch.countDown()
            }
        }

        val finished = try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }

        if (!finished) {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
            reader.interrupt()
            errReader.interrupt()
            return CommandResult(-1, synchronized(sink) { sink.toString() }, timedOut = true)
        }

        reader.join(800)
        errReader.join(800)
        val exitCode = try {
            process.exitValue()
        } catch (_: Exception) {
            -1
        }
        return CommandResult(exitCode, synchronized(sink) { sink.toString() }, timedOut = false)
    }

    /**
     * 与 [exec] 相同，但逐行回调输出，用于安装这类需要实时展示进度的长任务。
     *
     * @param timeoutMillis <= 0 表示不超时
     */
    fun execStreaming(
        context: Context,
        shellCommand: String,
        workDir: String,
        timeoutMillis: Long = 0L,
        onLine: (String) -> Unit
    ): CommandResult {
        val resolvedWorkDir = if (File(workDir).isDirectory) workDir else "/root"
        val fullCommand = AlpineManager.buildProotCommand(
            context = context,
            command = arrayOf("sh", "-c", shellCommand),
            workDir = resolvedWorkDir
        )
        val builder = ProcessBuilder(fullCommand)
        builder.environment().putAll(AlpineManager.getProotEnv(context))
        builder.directory(context.filesDir)

        val process = try {
            builder.start()
        } catch (e: Exception) {
            Log.e(TAG, "流式进程启动失败", e)
            return CommandResult(-1, e.message ?: "启动失败", timedOut = false)
        }

        val sink = StringBuilder()

        fun pump(name: String, stream: java.io.InputStream) {
            thread(name = name) {
                try {
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            synchronized(sink) { sink.append(line).append('\n') }
                            try {
                                onLine(line)
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        pump("acp-exec-out", process.inputStream)
        pump("acp-exec-err", process.errorStream)

        val latch = CountDownLatch(1)
        thread(name = "acp-exec-wait") {
            try {
                process.waitFor()
            } catch (_: InterruptedException) {
            } finally {
                latch.countDown()
            }
        }

        val finished = try {
            if (timeoutMillis > 0) {
                latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } else {
                latch.await()
                true
            }
        } catch (_: InterruptedException) {
            false
        }

        if (!finished) {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
            return CommandResult(-1, synchronized(sink) { sink.toString() }, timedOut = true)
        }

        val exitCode = try {
            process.exitValue()
        } catch (_: Exception) {
            -1
        }
        return CommandResult(exitCode, synchronized(sink) { sink.toString() }, timedOut = false)
    }

    // ------------------------------------------------------------------
    // 版本查询与一键安装
    // ------------------------------------------------------------------

    /**
     * 查询 npm registry 上的最新版本。网络请求，必须在 IO 线程调用。
     * @return 版本号；查询失败返回 null（不区分「包不存在」与「网络不通」）
     */
    fun npmLatestVersion(packageName: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(packageName, "UTF-8").replace("+", "%20")
            connection = (URL("https://registry.npmjs.org/$encoded/latest").openConnection()
                    as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("version").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "查询 $packageName 最新版本失败: ${e.message}")
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** 读取容器内已安装的 npm 包版本。 */
    fun installedNpmVersion(context: Context, packageName: String): String? {
        val result = exec(
            context = context,
            shellCommand = "npm list -g ${shellQuote(packageName)} --depth=0 --json 2>/dev/null",
            workDir = "/root",
            timeoutMillis = 30_000L
        )
        val output = result.output.trim()
        if (output.isEmpty()) return null
        val json = try {
            JSONObject(output)
        } catch (_: Exception) {
            return null
        }
        val dependencies = json.optJSONObject("dependencies") ?: return null
        return dependencies.optJSONObject(packageName)?.optString("version")?.takeIf { it.isNotBlank() }
    }

    private val VERSION_REGEX = Regex("""\d+\.\d+\.\d+([\w.\-+]*)?|\d+\.\d+""")

    /**
     * 通过可执行文件本身查询版本，覆盖「非 npm 安装」（二进制 / 单文件分发）的 Agent。
     *
     * 不少 ACP CLI 以二进制形式发布（例如 opencode），npm list -g 查不到，
     * 于是界面上会误报「未安装」。这里退而求其次，直接执行第一个 token 并解析 --version。
     */
    fun installedBinaryVersion(
        context: Context,
        command: String,
        timeoutMillis: Long = PROBE_TIMEOUT_MILLIS
    ): String? {
        val tokens = splitCommand(command)
        val binary = tokens.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val quoted = shellQuote(binary)
        val exists = if (binary.contains('/')) {
            "test -x $quoted"
        } else {
            "command -v $quoted >/dev/null 2>&1"
        }
        val result = exec(
            context = context,
            shellCommand = "$exists && $quoted --version 2>&1 | head -n 1 || echo $MARKER_MISSING",
            workDir = "/root",
            timeoutMillis = timeoutMillis
        )
        if (result.timedOut) return null
        val first = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (first.isEmpty() || first.contains(MARKER_MISSING)) return null
        return VERSION_REGEX.find(first)?.value
    }

    /** 确保容器里有 npm；没有就用 apk 装 Node.js。 */
    fun ensureNpm(context: Context, onLine: (String) -> Unit): Boolean {
        val probe = exec(
            context = context,
            shellCommand = "command -v npm >/dev/null 2>&1 && echo __ACP_OK__",
            workDir = "/root",
            timeoutMillis = PROBE_TIMEOUT_MILLIS
        )
        if (probe.output.contains(MARKER_OK)) return true

        onLine(t("未检测到 npm，正在通过 apk 安装 Node.js…", "npm not found, installing Node.js via apk…"))
        val install = execStreaming(
            context = context,
            shellCommand = "apk add --no-cache nodejs npm 2>&1",
            workDir = "/root",
            timeoutMillis = 600_000L,
            onLine = onLine
        )
        if (install.exitCode != 0) {
            onLine(
                t(
                    "安装 Node.js 失败（退出码 ${install.exitCode}）",
                    "Node.js install failed (exit ${install.exitCode})"
                )
            )
            return false
        }
        val verify = exec(
            context = context,
            shellCommand = "command -v npm >/dev/null 2>&1 && echo __ACP_OK__",
            workDir = "/root",
            timeoutMillis = PROBE_TIMEOUT_MILLIS
        )
        return verify.output.contains(MARKER_OK)
    }

    /** 全局安装一组 npm 包。 */
    fun installNpmPackages(
        context: Context,
        packages: List<String>,
        onLine: (String) -> Unit
    ): CommandResult {
        if (packages.isEmpty()) return CommandResult(0, "", timedOut = false)
        if (!ensureNpm(context, onLine)) {
            return CommandResult(-1, t("npm 不可用", "npm unavailable"), timedOut = false)
        }
        val list = packages.joinToString(" ") { shellQuote(it) }
        onLine("$ npm install -g $list")
        return execStreaming(
            context = context,
            shellCommand = "npm install -g --no-fund --no-audit $list 2>&1",
            workDir = "/root",
            timeoutMillis = 900_000L,
            onLine = onLine
        )
    }

    /**
     * 检查可执行文件是否在容器内可见。
     *
     * - 裸命令名（如 `gemini`）走 `command -v`
     * - 绝对路径走 `test -x`
     */
    fun probeBinary(
        context: Context,
        binary: String,
        timeoutMillis: Long = PROBE_TIMEOUT_MILLIS
    ): BinaryProbe {
        val quoted = shellQuote(binary)
        val isPath = binary.contains('/')
        val result = exec(
            context = context,
            shellCommand = if (isPath) {
                "test -x $quoted && echo $MARKER_OK"
            } else {
                "command -v $quoted 2>/dev/null || echo $MARKER_MISSING"
            },
            workDir = "/root",
            timeoutMillis = timeoutMillis
        )

        if (result.timedOut) return BinaryProbe.Failed(t("探测超时", "probe timed out"))
        val output = result.output.trim()

        if (isPath) {
            return if (output.contains(MARKER_OK)) BinaryProbe.Found(binary) else BinaryProbe.Missing
        }
        if (output.isEmpty() || output.contains(MARKER_MISSING)) return BinaryProbe.Missing
        val path = output.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return BinaryProbe.Missing
        return BinaryProbe.Found(path)
    }

    /**
     * 读取 Agent 自身日志文件的末尾若干行。
     *
     * ACP Agent（如 opencode）在模型调用失败时**不会**通过协议返回错误，而是把
     * 原因写进自己的日志（例如 AI_APICallError: Rate limit exceeded）。当界面
     * 一直转圈、stderr 又为空时，这份日志是唯一能说明问题的线索。
     *
     * 常见的日志位置都会试一遍（XDG 数据目录 / 常见缓存目录）。
     */
    fun readAgentLog(context: Context, maxLines: Int = 60): List<String> {
        val candidates = listOf(
            "/root/.local/share/opencode/log/opencode.log",
            "/root/.local/state/opencode/log/opencode.log",
            "/root/.cache/opencode/log/opencode.log",
            "/root/.opencode/log/opencode.log"
        ).joinToString(" ") { shellQuote(it) }
        val command = "for f in " + candidates + "; do [ -f \"\$f\" ] && tail -n " + maxLines + " \"\$f\"; done"
        val result = exec(context, command, "/root", timeoutMillis = 15_000L)
        if (result.timedOut) return listOf(t("读取日志超时", "read log timed out"))
        return result.output.lineSequence().filter { it.isNotBlank() }.toList()
    }

    /** 便捷包装：只关心路径。 */
    fun which(context: Context, binary: String): String? =
        (probeBinary(context, binary) as? BinaryProbe.Found)?.path

    /**
     * 组装一份可读的自检报告，覆盖最常见的启动失败原因。
     * @param binary 启动命令的第一个 token
     * @param workDir 会话工作目录
     */
    fun selfCheck(context: Context, binary: String, workDir: String): List<String> {
        val lines = mutableListOf<String>()

        val resolvedWorkDir = if (File(workDir).isDirectory) workDir else "/root"
        lines += t("工作目录: $workDir", "Working dir: $workDir")
        if (resolvedWorkDir != workDir) {
            lines += t("  ⚠ 宿主机上不存在，将回退到 /root", "  ⚠ not present on host, falling back to /root")
        } else {
            val pwd = exec(context, "pwd", resolvedWorkDir, timeoutMillis = 15_000L)
            if (pwd.timedOut) {
                lines += t("  ⚠ 容器内进入该目录超时", "  ⚠ entering this directory in container timed out")
            } else if (pwd.isSuccess) {
                lines += t(
                    "  容器内可访问: ${pwd.output.trim()}",
                    "  accessible in container: ${pwd.output.trim()}"
                )
            } else {
                lines += t(
                    "  ⚠ 容器内不可访问（退出码 ${pwd.exitCode}）：${pwd.output.trim().take(160)}",
                    "  ⚠ not accessible in container (exit ${pwd.exitCode}): ${pwd.output.trim().take(160)}"
                )
            }
        }

        if (binary.isNotBlank() && !binary.contains('/')) {
            val path = which(context, binary)
            lines += if (path != null) {
                t("命令 $binary: $path", "Command $binary: $path")
            } else {
                t(
                    "命令 $binary: 未找到 —— 需要先在终端里安装",
                    "Command $binary: not found — install it in the terminal first"
                )
            }
        } else if (binary.isNotBlank()) {
            lines += t(
                "命令路径: $binary（${if (File(binary).exists()) "存在" else "不存在"}）",
                "Command path: $binary (${if (File(binary).exists()) "exists" else "missing"})"
            )
        }

        listOf("node", "npm", "npx").forEach { tool ->
            val result = exec(context, "$tool -v 2>&1 || echo $MARKER_MISSING", "/root", timeoutMillis = 15_000L)
            val output = result.output.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            lines += when {
                result.timedOut -> t("$tool: 检测超时", "$tool: probe timed out")
                output.isEmpty() || output.contains(MARKER_MISSING) -> t("$tool: 未安装", "$tool: not installed")
                else -> "$tool: $output"
            }
        }

        // 网络连通性：ACP Agent 大多要访问模型服务，容器内 DNS / 代理不通会导致
        // 会话创建失败或对话一直无响应（Agent 往往只写自己的日志，不回报协议错误）。
        lines += t("网络连通性：", "Network connectivity:")
        listOf(
            "models.opencode.ai" to "https://models.opencode.ai/api.json",
            "opencode.ai" to "https://opencode.ai/",
            "registry.npmjs.org" to "https://registry.npmjs.org/"
        ).forEach { (name, url) ->
            val probe = exec(
                context,
                "wget -q -T 8 -O /dev/null " + shellQuote(url) + " 2>/dev/null; echo EXIT=$?",
                "/root",
                timeoutMillis = 15_000L
            )
            val code = Regex("EXIT=(\\d+)").find(probe.output)?.groupValues?.get(1)
            lines += when {
                probe.timedOut -> t("  $name: 连接超时", "  $name: timed out")
                code == "0" -> t("  $name: 可达", "  $name: reachable")
                else -> t(
                    "  $name: 不可达（wget exit ${code ?: "?"}）—— 检查代理 / DNS",
                    "  $name: unreachable (wget exit ${code ?: "?"}) — check proxy / DNS"
                )
            }
        }

        return lines
    }
}