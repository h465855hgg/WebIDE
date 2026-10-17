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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * ACP 连接的 JSON-RPC 2.0 会话层。
 *
 * 负责三件事：
 * 1. 出站请求的 id 分配与响应关联（挂起等待 result / error）
 * 2. 出站通知（session/cancel）
 * 3. 入站消息分派：Agent 的响应、Agent 发起的反向请求（fs 与 terminal 系列方法、
 *    session/request_permission）、以及 session/update 通知
 */
internal class AcpConnection(
    private val scope: CoroutineScope,
    private val sendRaw: (JSONObject) -> Unit
) {

    companion object {
        /** initialize / session/new 等控制类请求的默认超时。 */
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L

        /** 一轮对话最长等待时间（10 分钟），期间用户可随时中断。 */
        const val PROMPT_TIMEOUT_MILLIS = 600_000L
    }

    /** Agent -> Client 的请求处理。返回 result，或抛 [AcpException] 回传错误。 */
    var requestHandler: (suspend (method: String, params: JSONObject) -> JSONObject)? = null

    /** Agent -> Client 的通知处理。 */
    var notificationHandler: ((method: String, params: JSONObject) -> Unit)? = null

    /** 连接层发生不可恢复错误（如写入失败）。 */
    var onFatalError: ((Throwable) -> Unit)? = null

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()

    private fun loc(zh: String, en: String): String =
        if (java.util.Locale.getDefault().language.startsWith("zh")) zh else en

    /**
     * 发送请求并挂起等待结果。
     *
     * @param timeoutMillis 超时毫秒数；<= 0 表示不超时（长任务如 session/prompt 用）。
     *        没有超时的话，Agent 若静默不响应，界面会永远停在「连接中」。
     */
    suspend fun request(
        method: String,
        params: JSONObject,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): JSONObject {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        try {
            sendRaw(payload)
        } catch (e: Throwable) {
            pending.remove(id)
            throw e
        }
        try {
            return if (timeoutMillis > 0) {
                withTimeout(timeoutMillis) { deferred.await() }
            } else {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw AcpException(
                Acp.ERR_INTERNAL,
                loc(
                    "请求超时（${timeoutMillis / 1000}s）：$method",
                    "Request timeout (${timeoutMillis / 1000}s): $method"
                )
            )
        } finally {
            pending.remove(id)
        }
    }

    /** 发送通知（无需响应）。 */
    fun notify(method: String, params: JSONObject) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        sendRaw(payload)
    }

    /** 分派一条入站消息。 */
    fun handleIncoming(message: JSONObject) {
        val idValue = message.opt("id")
        val hasId = message.has("id") && idValue != null && idValue != JSONObject.NULL
        val method = message.strOrNull("method")
        val params = message.objOrNull("params") ?: JSONObject()

        when {
            method != null && hasId -> dispatchIncomingRequest(idValue, method, params)
            method != null -> notificationHandler?.invoke(method, params)
            hasId -> completePending(idValue, message)
            else -> Unit
        }
    }

    private fun completePending(idValue: Any?, message: JSONObject) {
        val id = when (idValue) {
            is Number -> idValue.toLong()
            is String -> idValue.toLongOrNull()
            else -> null
        } ?: return
        val deferred = pending.remove(id) ?: return

        val error = message.objOrNull("error")
        if (error != null) {
            deferred.completeExceptionally(
                AcpException(
                    code = error.optInt("code", Acp.ERR_INTERNAL),
                    message = error.strOrNull("message").orEmpty().ifEmpty { loc("Agent 返回错误", "Agent returned an error") },
                    data = error.opt("data")
                )
            )
        } else {
            deferred.complete(message.objOrNull("result") ?: JSONObject())
        }
    }

    private fun dispatchIncomingRequest(idValue: Any?, method: String, params: JSONObject) {
        android.util.Log.d("ACP-Connection", "<-- request $method")
        scope.launch {
            try {
                val handler = requestHandler
                val result = if (handler != null) {
                    handler(method, params)
                } else {
                    throw AcpException(Acp.ERR_METHOD_NOT_FOUND, loc("未实现的方法: $method", "Unsupported method: $method"))
                }
                safeSend(resultResponse(idValue, result))
            } catch (e: AcpException) {
                safeSend(errorResponse(idValue, e.code, e.message ?: "error", e.data))
            } catch (e: Throwable) {
                safeSend(errorResponse(idValue, Acp.ERR_INTERNAL, e.message ?: e.toString(), null))
            }
        }
    }

    private fun resultResponse(idValue: Any?, result: JSONObject?): JSONObject = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", idValue)
        put("result", result ?: JSONObject())
    }

    private fun errorResponse(idValue: Any?, code: Int, message: String, data: Any?): JSONObject =
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", idValue)
            put(
                "error",
                JSONObject().apply {
                    put("code", code)
                    put("message", message)
                    if (data != null && data != JSONObject.NULL) put("data", data)
                }
            )
        }

    private fun safeSend(message: JSONObject) {
        try {
            sendRaw(message)
        } catch (e: Throwable) {
            onFatalError?.invoke(e)
        }
    }

    /** 连接关闭时让所有挂起请求失败，避免协程泄漏。 */
    fun failAllPending(cause: Throwable) {
        val ids = pending.keys.toList()
        ids.forEach { id ->
            pending.remove(id)?.completeExceptionally(cause)
        }
    }

    fun hasPending(): Boolean = pending.isNotEmpty()
}
