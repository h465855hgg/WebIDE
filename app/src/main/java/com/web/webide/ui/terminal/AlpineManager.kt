




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
// 文件: java/com/example/sorarunrun/terminal/AlpineManager.kt

package com.web.webide.ui.terminal

import android.content.Context
import com.rk.terminal.ui.screens.terminal.stat
import com.rk.terminal.ui.screens.terminal.vmstat
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.web.webide.core.utils.WorkspaceManager
import java.io.File
import java.io.FileOutputStream

object AlpineManager {
    var currentProject: String? = null
    private fun getPrefixDir(context: Context): File = context.filesDir.parentFile!!
    private fun getLocalDir(context: Context): File = File(getPrefixDir(context), "local").apply { mkdirs() }
    private fun getBinDir(context: Context): File = File(getLocalDir(context), "bin").apply { mkdirs() }
    private fun getLibDir(context: Context): File = File(getLocalDir(context), "lib").apply { mkdirs() }

    /**
     * 构建 Proot 命令列表
     * 用于启动 LSP 后台进程 (ProotStreamConnectionProvider)
     *
     * @param context 安卓上下文
     * @param command 要在 Alpine 内部执行的命令数组，例如 ["vscode-html-language-server", "--stdio"]
     */
    fun buildProotCommand(context: Context, command: Array<String>): List<String> {
        val prefixDir = getPrefixDir(context)
        val alpineDir = File(prefixDir, "local/alpine")

        // [🔥 修复点 1] 获取有执行权限的 proot 路径
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val libProot = File(nativeLibDir, "libproot.so")

        // 如果文件没搬对，这里会找不到，所以第一步一定要做
        val prootExec = if (libProot.exists()) libProot.absolutePath else File(getBinDir(context), "proot").absolutePath

        val args = mutableListOf<String>()
        args.add(prootExec)
        args.add("--kill-on-exit")
        args.add("--link2symlink")
        args.add("--sysvipc")
        args.add("-L")
        args.add("-0")

        // [🔥 修复点 2] 挂载点
        val mounts = listOf(
            "/proc", "/sys", "/dev", "/data", "/storage",
            "/system"
        )
        mounts.forEach {
            if (File(it).exists()) {
                args.add("-b")
                args.add(it)
            }
        }

        // 绑定共享内存 (Node.js 需要)
        val tmpDir = File(alpineDir, "tmp").apply { mkdirs() }
        args.add("-b")
        args.add("${tmpDir.absolutePath}:/dev/shm")

        // [🔥 修复点 3] 必须将 filesDir 挂载到绝对路径，方便 LSP 找文件

        val rootHome = File(alpineDir, "root")
        if (!rootHome.exists()) {
            rootHome.mkdirs()
        }
        // 显式绑定宿主的目录到容器的 /root
        args.add("-b")
        args.add("${rootHome.absolutePath}:/root")

        args.add("-b")
        args.add(context.filesDir.absolutePath)
        args.add("-r")
        args.add(alpineDir.absolutePath)

        args.add("-w")
        args.add("/root")

        args.add("/usr/bin/env")
        args.add("-i")
        args.add("HOME=/root")
        // 确保 PATH 包含 npm 的 bin 目录
        args.add("NODE_PATH=/root/lsp/node_modules")

        // 🔥🔥🔥 修改点 2: 把 /root/lsp/node_modules/.bin 加入 PATH (虽然我们打算用绝对路径，但这能防止内部调用出错)
        args.add("PATH=/root/lsp/node_modules/.bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")

        args.add("LANG=C.UTF-8")
        args.add("TERM=xterm-256color")
        args.add("TMPDIR=/tmp")










        args.addAll(command)

        return args
    }

    /**
     * 获取运行 Proot 所需的宿主环境变量
     * 主要是为了注入 libproot-loader.so 来绕过 Android 的 Seccomp 限制
     */
    fun getProotEnv(context: Context): Map<String, String> {
        val env = mutableMapOf<String, String>()
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        env["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath

        // [🔥 核心修复 🔥]
        // 告诉 Linker 去哪里找 libtalloc.so.2
        // SetupWorker 会把 libtalloc.so.2 复制到 filesDir 和 filesDir/local/lib
        // 我们把这两个路径都加到 LD_LIBRARY_PATH 里
        val libPath = "${context.filesDir.absolutePath}:${context.filesDir.absolutePath}/local/lib:$nativeLibDir"
        env["LD_LIBRARY_PATH"] = libPath

        if (File(nativeLibDir, "libproot-loader.so").exists()) {
            env["PROOT_LOADER"] = "$nativeLibDir/libproot-loader.so"
        }
        if (File(nativeLibDir, "libproot-loader32.so").exists()) {
            env["PROOT_LOADER32"] = "$nativeLibDir/libproot-loader32.so"
        }
        return env
    }

    // --- 创建 Terminal Session (用于 UI 终端) ---
    fun createSession(context: Context, client: TerminalSessionClient, projectPath: String? = null): TerminalSession {
        val binDir = getBinDir(context)
        val libDir = getLibDir(context)
        val prefixDir = getPrefixDir(context)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        // 1. 每次都覆盖脚本，确保使用最新版本（修复旧版本残留导致的问题）
        val initHostScript = File(binDir, "init-host")
        copyAsset(context, "init-host.sh", initHostScript)
        copyAsset(context, "init.sh", File(binDir, "init"))
        initHostScript.setExecutable(true)
        File(binDir, "init").setExecutable(true)
        val workspacePath = WorkspaceManager.getWorkspacePath(context)
        var versionName = "Unknown"
        var versionCode = 0L
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pInfo.versionName ?: "Unknown"
            versionCode =
                pInfo.longVersionCode
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val targetProjectPath = projectPath ?: currentProject ?: ""
        // 2. 环境变量 (宿主环境)
        val env = mutableListOf(
            "PATH=${System.getenv("PATH")}:/sbin:${binDir.absolutePath}",
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PREFIX=${prefixDir.absolutePath}",
            "LD_LIBRARY_PATH=${libDir.absolutePath}",
            // 尝试适配不同架构的 linker
            "LINKER=${if(File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"}",
            "NATIVE_LIB_DIR=$nativeLibDir",
            "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
            "TMPDIR=${context.cacheDir.absolutePath}",

            //自定义环境变量
            "WEBIDE_VERSION_NAME=$versionName",
            "WEBIDE_VERSION_CODE=$versionCode",
            "WEBIDE_WORKSPACE=$workspacePath",
            "WEBIDE_PROJECT_DIR=$targetProjectPath"
        )

        // 注入 Loader
        if (File(nativeLibDir, "libproot-loader.so").exists()) {
            env.add("PROOT_LOADER=$nativeLibDir/libproot-loader.so")
        }
        if (File(nativeLibDir, "libproot-loader32.so").exists()) {
            env.add("PROOT_LOADER32=$nativeLibDir/libproot-loader32.so")
        }

        // 3. 伪造系统文件
        val statFile = File(getLocalDir(context), "stat")
        if (!statFile.exists()) statFile.writeText(stat)
        val vmstatFile = File(getLocalDir(context), "vmstat")
        if (!vmstatFile.exists()) vmstatFile.writeText(vmstat)

        // 4. 启动 Shell
        // Android SELinux 禁止直接 exec /data 目录下的脚本（Permission denied），
        // 也不能用 "-cpp"（不是 /system/bin/sh 的有效参数，会导致语法错误）。
        // 正确做法：用 /system/bin/sh 读取脚本文件并执行。
        // 脚本内的 $LINKER/proot 调用会在 proot 容器内用 /usr/bin/bash 执行 init.sh。
        val shell = "/system/bin/sh"
        val args = arrayOf(initHostScript.absolutePath)

        return TerminalSession(
            shell,
            context.filesDir.absolutePath,
            args,
            env.toTypedArray(),
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            client
        )
    }

    private fun copyAsset(context: Context, assetName: String, destFile: File) {
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