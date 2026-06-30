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
package com.web.webide.ui.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

object SetupWorker {

    /**
     * 环境准备进度。UI 监听 [progress] 显示提示，避免用户误以为卡死。
     * - phase：当前阶段文案
     * - percent：0..100，-1 表示无法估算
     * - elapsedMs：从开始到现在的耗时
     */
    data class Progress(
        val phase: Phase,
        val percent: Int = -1,
        val elapsedMs: Long = 0L
    ) {
        enum class Phase {
            IDLE,            // 未开始
            COPYING_PROOT,   // 复制 proot 二进制（< 1s）
            EXTRACTING_ROOTFS, // 解压 Alpine rootfs（最耗时，30-90s）
            FINALIZING,      // 复制 lib/配置
            SUCCESS,
            TIMEOUT,
            ERROR
        }
    }

    private val _progress = MutableStateFlow(Progress(Progress.Phase.IDLE))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var startTimestamp: Long = 0L

    // 互斥锁：终端和 LSP 启动路径都可能调用 prepareEnvironment，用锁保证串行，
    // 避免并发解压/删目录导致的文件错乱。
    private val mutex = Mutex()

    sealed class PrepareResult {
        object Success : PrepareResult()
        object Timeout : PrepareResult()
        data class Error(val message: String) : PrepareResult()
    }

    /**
     * 准备终端环境，最多等待 [timeoutMs] 毫秒。
     * 超时或出错都不抛异常，返回结果让 UI 决定怎么提示。
     * 内部用 Mutex 保证并发安全（终端与 LSP 路径可能同时调用）。
     */
    suspend fun prepareEnvironment(
        context: Context,
        timeoutMs: Long = 180_000L
    ): PrepareResult = mutex.withLock {
        startTimestamp = System.currentTimeMillis()
        emit(Progress.Phase.COPYING_PROOT)
        val result = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                doPrepare(context)
            }
        }
        result ?: PrepareResult.Timeout.also {
            emit(Progress.Phase.TIMEOUT, elapsedMs = elapsed())
        }
    }

    private fun elapsed(): Long = System.currentTimeMillis() - startTimestamp

    private fun emit(phase: Progress.Phase, percent: Int = -1) {
        _progress.value = Progress(phase, percent, elapsed())
    }

    /**
     * 后台轮询 rootfs 解压进度：用 alpineDir 已落地字节数 / rootfs.bin 压缩包大小估算。
     * 解压是非阻塞的（在另一个线程跑 tar），这里只负责定时更新 percent。
     */
    private fun startExtractProgressMonitor(rootfsSize: Long, alpineDir: File): Thread {
        return Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val extracted = dirSize(alpineDir)
                    val percent = if (rootfsSize > 0) {
                        ((extracted.toFloat() / rootfsSize.toFloat()).coerceIn(0f, 0.99f) * 100).toInt()
                    } else -1
                    emit(Progress.Phase.EXTRACTING_ROOTFS, percent)
                    Thread.sleep(500)
                }
            } catch (_: InterruptedException) {
                // 正常退出
            }
        }.also { it.isDaemon = true }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        var size = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            cur.listFiles()?.forEach { f ->
                if (f.isDirectory) stack.addLast(f) else size += f.length()
            }
        }
        return size
    }

    /**
     * 校验 Alpine rootfs 是否解压完整。
     *
     * 只检查真实文件 bin/busybox 是否存在，不检查符号链接（usr/bin/env、bin/sh）。
     * 原因：Alpine 里 env/sh 是指向 busybox 的符号链接，File.exists() 会跟随符号链接
     * 解析目标。在宿主 Android 上，符号链接的目标路径解析与 proot 容器内不同
     * （proot 用 -r 做路径翻译，宿主不会），导致符号链接在宿主上"断链"误判为不存在，
     * 进而触发反复重新解压、死循环。proot 运行时能正确解析这些符号链接，所以只要
     * busybox 这个真实文件在，rootfs 就是完整的。
     */
    private fun isRootfsComplete(alpineDir: File): Boolean {
        if (!alpineDir.exists()) {
            android.util.Log.w("LSP-Setup", "rootfs 目录不存在: ${alpineDir.absolutePath}")
            return false
        }
        val busyboxFile = File(alpineDir, "bin/busybox")
        val ok = busyboxFile.exists()
        if (!ok) {
            android.util.Log.w("LSP-Setup", "rootfs 不完整: busybox 不存在, dir=${alpineDir.list()?.joinToString(",") ?: "(empty)"}")
        }
        return ok
    }

    /**
     * 公开的快速环境检查（仅做文件存在性校验，不解压、不阻塞）。
     * LSP 启动路径用它判断 rootfs 是否就绪：就绪则直接连 LSP，不就绪才走 prepareEnvironment。
     */
    fun isEnvironmentReady(context: Context): Boolean {
        val prefixDir = context.filesDir.parentFile!!
        val alpineDir = File(prefixDir, "local/alpine")
        return isRootfsComplete(alpineDir)
    }

    private fun doPrepare(context: Context): PrepareResult {
        return try {
            val filesDir = context.filesDir
            val prefixDir = filesDir.parentFile!!
            val alpineDir = File(prefixDir, "local/alpine")
            val binDir = File(prefixDir, "local/bin")
            val libDir = File(prefixDir, "local/lib")

            // 阶段 1：复制 proot 二进制
            emit(Progress.Phase.COPYING_PROOT, 0)
            copyAsset(context, "proot", File(filesDir, "proot"))
            copyAsset(context, "libtalloc.so.2", File(filesDir, "libtalloc.so.2"))
            File(filesDir, "proot").setExecutable(true)

            // 阶段 2：复制并解压 rootfs（最耗时）
            // 注意：不能用 etc 目录是否存在判断解压是否完成。当应用在后台被系统因内存
            // 压力终止进程时，解压会被中断：etc 已写出，但关键可执行文件
            // （usr/bin/env）尚未落盘。下次启动若仅凭 etc 存在就跳过解压，会导致 proot
            // 启动语言服务器时报 '/usr/bin/env' not found 并陷入重启死循环，表现为
            // LSP 代码补全完全失效、编辑器被拖卡。这里改为校验关键文件完整性。
            val rootfsTar = File(filesDir, "alpine.tar.gz")
            if (!isRootfsComplete(alpineDir)) {
                // 解压不完整（首次安装或上次被中断）：清理残留后重新解压，避免文件错乱
                if (alpineDir.exists()) {
                    alpineDir.deleteRecursively()
                }
                alpineDir.mkdirs()

                // 重试机制：rootfs 仅 40MB，但拷贝/解压可能因进程被系统内存压力杀掉、
                // 或 alpine.tar.gz 上次拷贝被中断导致残缺而失败（表现为概率性解压失败）。
                // 每次重试都重新从 assets 拷贝压缩包，确保源头完整；解压后校验关键文件，
                // 不通过则重试，最多 3 次。避免“解压没完成却报成功”导致 LSP 起不来。
                var extracted = false
                for (attempt in 1..3) {
                    // 首次尝试：若已有 alpine.tar.gz 则复用（可能只是解压被中断，包本身完整）；
                    // 后续重试：强制重新拷贝（旧包可能在上次中断时损坏）
                    if (attempt == 1) {
                        if (!rootfsTar.exists() || rootfsTar.length() == 0L) {
                            emit(Progress.Phase.EXTRACTING_ROOTFS, 0)
                            copyAsset(context, "rootfs.bin", rootfsTar)
                        }
                    } else {
                        rootfsTar.delete()
                        emit(Progress.Phase.EXTRACTING_ROOTFS, 0)
                        copyAsset(context, "rootfs.bin", rootfsTar)
                    }
                    if (!rootfsTar.exists() || rootfsTar.length() == 0L) {
                        // 拷贝失败，直接进入下一轮重试
                        continue
                    }

                    // 启动进度监控线程
                    val monitor = startExtractProgressMonitor(rootfsTar.length(), alpineDir)
                    monitor.start()
                    try {
                        val cmd = "tar -zxf ${rootfsTar.absolutePath} -C ${alpineDir.absolutePath}"
                        val process = Runtime.getRuntime().exec(cmd)
                        process.waitFor()
                        if (process.exitValue() != 0) {
                            // 兜底：gz 解压失败再尝试不带 -z 的纯 tar
                            Runtime.getRuntime()
                                .exec("tar -xf ${rootfsTar.absolutePath} -C ${alpineDir.absolutePath}")
                                .waitFor()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        monitor.interrupt()
                    }

                    if (isRootfsComplete(alpineDir)) {
                        extracted = true
                        break
                    }
                    // 解压仍不完整：清理残留，下一轮重新拷贝 + 解压
                    if (alpineDir.exists()) {
                        alpineDir.deleteRecursively()
                    }
                    alpineDir.mkdirs()
                }

                if (!extracted) {
                    // 解压确实失败：如实上报错误，而不是误报成功让 LSP 在残缺环境里起不来
                    emit(Progress.Phase.ERROR, elapsedMs = elapsed())
                    return PrepareResult.Error("rootfs 解压失败，请重试")
                }
            }

            // 阶段 3：收尾
            emit(Progress.Phase.FINALIZING, 100)
            binDir.mkdirs()
            libDir.mkdirs()
            copyAsset(context, "libtalloc.so.2", File(libDir, "libtalloc.so.2"))
            copyAsset(context, "proot", File(binDir, "proot"))
            File(binDir, "proot").setExecutable(true)

            emit(Progress.Phase.SUCCESS, 100)
            PrepareResult.Success
        } catch (e: Exception) {
            emit(Progress.Phase.ERROR, elapsedMs = elapsed())
            PrepareResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun emit(phase: Progress.Phase, percent: Int = -1, elapsedMs: Long? = null) {
        _progress.value = Progress(phase, percent, elapsedMs ?: elapsed())
    }

    private fun copyAsset(context: Context, assetName: String, destFile: File) {
        if (!destFile.exists() || assetName.contains("so") || assetName == "proot") {
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
