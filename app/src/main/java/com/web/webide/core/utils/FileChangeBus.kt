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
package com.web.webide.core.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * 文件变更总线。
 *
 * 编辑器 / 文件树并不会感知「外部进程直接改磁盘」——例如 ACP Agent 通过
 * `fs/write_text_file` 写文件、或终端命令 `echo > x` 之后，文件树与已打开的
 * 标签页都还是旧内容，必须手动关闭重开。
 *
 * 任何「非编辑器 UI 自身发起」的落盘动作都应在这里广播一次，UI 层（文件树 +
 * 编辑器标签）订阅后自动刷新，无需用户手动操作。
 *
 * 广播的是**绝对路径**：可以是文件（需要同步内容）或目录（只需刷新树、并关闭
 * 已被删除的标签）。
 */
object FileChangeBus {

    private val _events = MutableSharedFlow<Set<String>>(extraBufferCapacity = 32)

    /** 订阅变更事件。每个事件是一批绝对路径（文件或目录）。 */
    val events = _events.asSharedFlow()

    /** 广播一批路径变更（自动去重、规范化为绝对路径）。 */
    fun notifyChanged(paths: Collection<String>) {
        val normalized = paths.asSequence()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .map { File(it).absolutePath }
            .toSet()
        if (normalized.isEmpty()) return
        _events.tryEmit(normalized)
    }

    /** 广播单个路径变更。 */
    fun notifyChanged(path: String) = notifyChanged(listOf(path))

    /** 广播整个目录变更（树刷新 + 清理被删标签）。 */
    fun notifyDirectoryChanged(dir: String) = notifyChanged(listOf(dir))
}
