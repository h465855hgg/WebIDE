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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 工作区变更轮询器。
 *
 * 为什么不用 [android.os.FileObserver]（inotify）：Android 的 `/storage` 多为 FUSE
 * 挂载，inotify 事件常常不触发；同时 ACP Agent 也可能绕开 `fs/write_text_file`、
 * 直接在容器内改盘。轮询「路径 -> 修改时间 + 大小」快照可以覆盖**一切**外部改动来源，
 * 命中差异后交给 [FileChangeBus] 广播，文件树与编辑器标签据此自动同步。
 */
object WorkspaceWatcher {

    /** 这些目录体积大、变更噪音多，扫描时直接跳过。 */
    private val IGNORED_DIRS = setOf(
        "node_modules", ".git", "build", ".gradle", ".idea", "dist", ".next", "out", ".dart_tool"
    )

    /** 单次扫描的文件数上限，防止超大项目拖慢轮询。 */
    private const val MAX_ENTRIES = 8000

    /** 采集一份「绝对路径 -> 修改时间+大小」快照。 */
    fun snapshot(root: File): Map<String, Long> {
        val result = HashMap<String, Long>()
        val stack = ArrayDeque<File>()
        if (root.isDirectory) stack.addLast(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            result[dir.absolutePath] =
                (children.size.toLong() shl 40) xor dir.lastModified()
            if (result.size >= MAX_ENTRIES) break
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name in IGNORED_DIRS) continue
                    stack.addLast(child)
                } else {
                    result[child.absolutePath] = (child.lastModified() shl 20) xor child.length()
                    if (result.size >= MAX_ENTRIES) break
                }
            }
        }
        return result
    }

    /**
     * 持续对比快照，发生变化时发出受影响路径集合。
     *
     * 首次调用会立即建立基线，之后每 [intervalMillis] 扫描一次；仅在确有差异时 emit。
     */
    fun changes(root: File, intervalMillis: Long = 1200L): Flow<Set<String>> = flow {
        if (!root.isDirectory) return@flow
        var previous = snapshot(root)
        while (true) {
            delay(intervalMillis)
            val current = snapshot(root)
            if (current != previous) {
                val changed = HashSet<String>()
                current.forEach { (path, signature) ->
                    if (previous[path] != signature) changed += path
                }
                previous.keys.forEach { path -> if (path !in current) changed += path }
                if (changed.isNotEmpty()) emit(changed)
            }
            previous = current
        }
    }.flowOn(Dispatchers.IO)
}
