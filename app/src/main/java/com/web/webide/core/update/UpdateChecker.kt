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
package com.web.webide.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub Release 更新检测。
 *
 * 通过 GitHub API 查询最新 release，与本地 [currentVersion] 比较版本号，
 * 返回 [UpdateResult] 供 UI 决定是否提示用户跳转浏览器下载。
 *
 * 版本号比较采用“去前缀 v + 按点分段数值比较”，例如 0.3.7 < 0.3.10。
 */
object UpdateChecker {

    private const val OWNER = "h465855hgg"
    private const val REPO = "WebIDE"

    /** GitHub releases 页面（检测到新版本时跳转此地址）。 */
    val releasesPageUrl: String = "https://github.com/$OWNER/$REPO/releases"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    sealed class UpdateResult {
        /** 有新版本，[latestVersion] 为最新版本号（如 0.3.8）。 */
        data class UpdateAvailable(val latestVersion: String) : UpdateResult()
        /** 已是最新版本。 */
        object UpToDate : UpdateResult()
        /** 检测失败（网络/解析/API 限流等），[message] 为错误摘要。 */
        data class Error(val message: String) : UpdateResult()
    }

    /**
     * 查询最新版本。必须在协程中调用（内部切到 IO 线程）。
     *
     * @param currentVersion 当前已安装版本号（如 0.3.7），不应带 v 前缀。
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WebIDE-UpdateChecker")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("HTTP ${response.code}")
                }
                val body = response.body.string()
                if (body.isEmpty()) return@withContext UpdateResult.Error("Empty response")
                val json = JSONObject(body)
                // tag_name 形如 "0.3.8" 或 "v0.3.8"
                val tagName = json.optString("tag_name", "").trim()
                val latest = tagName.removePrefix("v").removePrefix("V").trim()
                if (latest.isEmpty()) {
                    return@withContext UpdateResult.Error("No tag_name")
                }
                if (compareVersions(latest, currentVersion) > 0) {
                    UpdateResult.UpdateAvailable(latest)
                } else {
                    UpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 语义版本比较。返回值：正数表示 [a] 更新，负数表示 [b] 更新，0 表示相等。
     * 非数字段按 0 处理，例如 0.3.7 与 0.3.7.0 相等。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
