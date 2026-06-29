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

    sealed class PrepareResult {
        object Success : PrepareResult()
        object Timeout : PrepareResult()
        data class Error(val message: String) : PrepareResult()
    }

    /**
     * 准备终端环境，最多等待 [timeoutMs] 毫秒。
     * 超时或出错都不抛异常，返回结果让 UI 决定怎么提示。
     */
    suspend fun prepareEnvironment(
        context: Context,
        timeoutMs: Long = 180_000L
    ): PrepareResult {
        startTimestamp = System.currentTimeMillis()
        emit(Progress.Phase.COPYING_PROOT)
        val result = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                doPrepare(context)
            }
        }
        return result ?: PrepareResult.Timeout.also {
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
            val rootfsTar = File(filesDir, "alpine.tar.gz")
            if (!rootfsTar.exists()) {
                emit(Progress.Phase.EXTRACTING_ROOTFS, 0)
                copyAsset(context, "rootfs.bin", rootfsTar)
            }

            val etcDir = File(alpineDir, "etc")
            if (!etcDir.exists()) {
                alpineDir.mkdirs()
                // 启动进度监控线程
                val monitor = startExtractProgressMonitor(rootfsTar.length(), alpineDir)
                monitor.start()
                try {
                    val cmd = "tar -zxf ${rootfsTar.absolutePath} -C ${alpineDir.absolutePath}"
                    val process = Runtime.getRuntime().exec(cmd)
                    process.waitFor()
                    if (process.exitValue() != 0) {
                        Runtime.getRuntime()
                            .exec("tar -xf ${rootfsTar.absolutePath} -C ${alpineDir.absolutePath}")
                            .waitFor()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    monitor.interrupt()
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
