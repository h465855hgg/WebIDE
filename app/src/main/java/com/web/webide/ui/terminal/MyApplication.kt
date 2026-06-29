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

import android.app.Application
import com.rk.libcommons.application
import com.rk.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. 初始化 ReTerminal 的全局 Context
        application = this

        // 2. 初始化资源模块
        Res.application = this

        // 3. 🔥 App 启动即后台解压 rootfs（LSP 进程依赖 Alpine 容器已就绪）
        // 不阻塞主线程；用户在 welcome 阅读协议/做题的几十秒里通常已经解压完
        appScope.launch {
            SetupWorker.prepareEnvironment(this@MyApplication, timeoutMs = 180_000L)
        }
    }

    companion object {
        // 🔥 全局协程作用域，用于后台启动任务（如 rootfs 解压），随进程退出
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
