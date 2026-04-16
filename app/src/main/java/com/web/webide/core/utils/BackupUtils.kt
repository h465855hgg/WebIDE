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

import android.content.Context
import com.web.webide.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupUtils {
    // 保留最近 5 个备份
    private const val MAX_BACKUP_COUNT = 5

    /**
     * 备份项目：打包成 ZIP 存入私有目录
     */
    suspend fun backupProject(context: Context, projectPath: String): String = withContext(Dispatchers.IO) {
        val projectDir = File(projectPath)
        if (!projectDir.exists()) return@withContext context.getString(R.string.backup_project_not_exists)

        val folderName = projectDir.name
        // 私有目录: /data/data/包名/files/project_backups/项目名/
        val backupRootDir = File(context.filesDir, "project_backups/$folderName")
        if (!backupRootDir.exists()) backupRootDir.mkdirs()

        // 命名: 项目名_时间戳.zip
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupRootDir, "${folderName}_$timestamp.zip")

        try {
            zipFolder(projectDir, backupFile) { file ->
                val path = file.absolutePath
                // 过滤 build, .git 和 .gradle 目录
                !path.contains("/build/") && !path.contains("/.git/") && !path.contains("/.gradle/")
            }
            cleanOldBackups(backupRootDir)
            return@withContext backupFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext context.getString(R.string.backup_failed, e.message)
        }
    }

    private fun zipFolder(srcFolder: File, destZipFile: File, filter: (File) -> Boolean) {
        ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
            addFolderToZip(srcFolder, srcFolder, zos, filter)
        }
    }

    private fun addFolderToZip(rootFolder: File, srcFolder: File, zos: ZipOutputStream, filter: (File) -> Boolean) {
        val files = srcFolder.listFiles() ?: return
        for (file in files) {
            if (!filter(file)) continue
            if (file.isDirectory) {
                addFolderToZip(rootFolder, file, zos, filter)
            } else {
                val relPath = file.toRelativeString(rootFolder)
                zos.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun cleanOldBackups(backupDir: File) {
        val files = backupDir.listFiles { _, name -> name.endsWith(".zip") } ?: return
        if (files.size > MAX_BACKUP_COUNT) {
            files.sortBy { it.lastModified() }
            // 删除最旧的几个
            files.take(files.size - MAX_BACKUP_COUNT).forEach { it.delete() }
        }
    }
}
