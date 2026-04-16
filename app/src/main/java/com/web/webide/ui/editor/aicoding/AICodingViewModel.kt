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
package com.web.webide.ui.editor.aicoding

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.web.webide.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

import java.io.File
import java.io.FileReader
import java.io.FileWriter

import java.util.UUID

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val reasoningContent: String? = null,
    val isError: Boolean = false,
    val id: String = UUID.randomUUID().toString()
)

data class ChatSession(
    val id: String,
    var title: String,
    val messages: List<ChatMessage>,
    val timestamp: Long
)

class AICodingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val DEFAULT_CHAT_TITLE_MARKER = "__ai_default_chat_title__"
        const val INITIAL_ASSISTANT_MESSAGE_MARKER = "__ai_initial_assistant_message__"
        const val LEGACY_DEFAULT_CHAT_TITLE = "New Chat"
        const val LEGACY_INITIAL_ASSISTANT_MESSAGE =
            "Hello! I am your AI coding assistant. Please configure your API Key in settings to start."
    }

    private class AiLocalizedException(val localizedContent: String) : IOException(localizedContent)

    private val prefs = application.getSharedPreferences("ai_coding_settings", Context.MODE_PRIVATE)
    private val sessionsFile = File(application.filesDir, "ai_chat_sessions.json")
    private fun string(@androidx.annotation.StringRes resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun localizedContent(@StringRes resId: Int, vararg args: Any): String {
        return AICodingLocalizedText.encode(getApplication<Application>(), resId, *args)
    }

    private fun localizedMessage(
        role: String,
        @StringRes resId: Int,
        vararg args: Any,
        isError: Boolean = false
    ): ChatMessage {
        return ChatMessage(role = role, content = localizedContent(resId, *args), isError = isError)
    }

    private fun resolveContent(content: String): String {
        return AICodingLocalizedText.resolve(getApplication<Application>(), content) ?: content
    }

    var apiKey by mutableStateOf(prefs.getString("api_key", "") ?: "")
    var baseUrl by mutableStateOf(prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1")
    var model by mutableStateOf(prefs.getString("model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo")
    
    // Session Management
    val sessions = mutableStateListOf<ChatSession>()
    var currentSessionId by mutableStateOf<String?>(null)

    // Provider Logic
    enum class ApiProvider(
        @StringRes val displayNameRes: Int,
        val defaultBaseUrl: String,
        val defaultModel: String
    ) {
        OPENAI(R.string.ai_provider_openai, "https://api.openai.com/v1", "gpt-4o"),
        DEEPSEEK(R.string.ai_provider_deepseek, "https://api.deepseek.com", "deepseek-chat"),
        ANTHROPIC(R.string.ai_provider_anthropic, "https://api.anthropic.com/v1", "claude-3-opus-20240229"),
        GOOGLE(R.string.ai_provider_google, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"),
        ZHIPU(R.string.ai_provider_zhipu, "https://open.bigmodel.cn/api/paas/v4", "glm-4.5"),
        MOONSHOT(R.string.ai_provider_moonshot, "https://api.moonshot.cn/v1", "moonshot-v1-128k"),
        ALIYUN(R.string.ai_provider_aliyun, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max"),
        BAIDU(R.string.ai_provider_baidu, "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop", "ernie-bot-4"),
        DOUBAO(R.string.ai_provider_doubao, "https://ark.cn-beijing.volces.com/api/v3", "Doubao-pro-4k"),
        MISTRAL(R.string.ai_provider_mistral, "https://codestral.mistral.ai/v1", "codestral-latest"),
        SILICONFLOW(R.string.ai_provider_siliconflow, "https://api.siliconflow.cn/v1", "yi-1.5-34b"),
        OPENROUTER(R.string.ai_provider_openrouter, "https://openrouter.ai/api/v1", "google/gemini-pro"),
        LMSTUDIO(R.string.ai_provider_lmstudio, "http://localhost:1234/v1", "meta-llama-3.1-8b-instruct"),
        CUSTOM(R.string.ai_provider_custom, "", "");

        companion object {
            fun fromUrl(url: String): ApiProvider {
                return entries.find { it.defaultBaseUrl.isNotEmpty() && url.startsWith(it.defaultBaseUrl) } ?: CUSTOM
            }
        }
    }

    val availableModels = mutableStateListOf<String>()
    var isFetchingModels by mutableStateOf(false)

    val messages = mutableStateListOf<ChatMessage>()
    var isLoading by mutableStateOf(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        loadSessions()
        if (sessions.isEmpty()) {
            createNewSession()
        } else {
            // Load the most recent session
            loadSession(sessions.first().id)
        }
    }
    
    // --- Session Management ---

    private fun isDefaultChatTitle(title: String): Boolean {
        return title == DEFAULT_CHAT_TITLE_MARKER || title == LEGACY_DEFAULT_CHAT_TITLE
    }

    private fun isLegacyInitialAssistantMessage(content: String): Boolean {
        return content == LEGACY_INITIAL_ASSISTANT_MESSAGE
    }

    private fun shouldIncludeInChatHistory(message: ChatMessage): Boolean {
        return !message.isError &&
            message.role != "system" &&
            message.content != INITIAL_ASSISTANT_MESSAGE_MARKER &&
            !AICodingLocalizedText.isEncoded(message.content)
    }

    fun createNewSession() {
        val newSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = DEFAULT_CHAT_TITLE_MARKER,
            messages = listOf(
                ChatMessage("assistant", INITIAL_ASSISTANT_MESSAGE_MARKER)
            ),
            timestamp = System.currentTimeMillis()
        )
        sessions.add(0, newSession)
        loadSession(newSession.id)
        saveSessions()
    }

    fun loadSession(sessionId: String) {
        val session = sessions.find { it.id == sessionId } ?: return
        currentSessionId = sessionId
        messages.clear()
        messages.addAll(session.messages)
    }

    fun deleteSession(sessionId: String) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            sessions.removeAt(index)
            saveSessions()
            
            if (currentSessionId == sessionId) {
                if (sessions.isNotEmpty()) {
                    loadSession(sessions.first().id)
                } else {
                    createNewSession()
                }
            }
        }
    }
    
    private fun updateCurrentSession() {
        val currentId = currentSessionId ?: return
        val index = sessions.indexOfFirst { it.id == currentId }
        if (index != -1) {
            val updatedSession = sessions[index].copy(
                messages = messages.toList(),
                timestamp = System.currentTimeMillis()
            )
            // Auto-title based on first user message if title is still the default one.
            if (isDefaultChatTitle(updatedSession.title)) {
                val firstUserMsg = messages.firstOrNull { it.role == "user" }
                if (firstUserMsg != null) {
                    updatedSession.title = firstUserMsg.content.take(30) + if (firstUserMsg.content.length > 30) "..." else ""
                }
            }
            
            sessions[index] = updatedSession
            // Move to top
            sessions.removeAt(index)
            sessions.add(0, updatedSession)
            saveSessions()
        }
    }

    private fun loadSessions() {
        if (!sessionsFile.exists()) return
        try {
            val jsonStr = FileReader(sessionsFile).use { it.readText() }
            val jsonArray = JSONArray(jsonStr)
            
            val loadedSessions = mutableListOf<ChatSession>()
            var migrated = false
            for (i in 0 until jsonArray.length()) {
                val sessionObj = jsonArray.getJSONObject(i)
                val id = sessionObj.getString("id")
                val title = sessionObj.getString("title")
                val timestamp = sessionObj.getLong("timestamp")
                val msgsArray = sessionObj.getJSONArray("messages")
                
                val msgs = mutableListOf<ChatMessage>()
                for (j in 0 until msgsArray.length()) {
                    val msgObj = msgsArray.getJSONObject(j)
                    val msgId = if (msgObj.has("id")) msgObj.getString("id") else UUID.randomUUID().toString()
                    val role = msgObj.getString("role")
                    val content = msgObj.getString("content")
                    val normalizedContent = when {
                        role == "assistant" && isLegacyInitialAssistantMessage(content) -> INITIAL_ASSISTANT_MESSAGE_MARKER
                        else -> AICodingLocalizedText.migrate(getApplication<Application>(), content)
                    }
                    if (normalizedContent != content) {
                        migrated = true
                    }
                    msgs.add(ChatMessage(
                        role = role,
                        content = normalizedContent,
                        reasoningContent = if (msgObj.has("reasoningContent")) msgObj.getString("reasoningContent") else null,
                        isError = msgObj.optBoolean("isError", false),
                        id = msgId
                    ))
                }
                loadedSessions.add(
                    ChatSession(
                        id,
                        if (isDefaultChatTitle(title)) DEFAULT_CHAT_TITLE_MARKER else title,
                        msgs,
                        timestamp
                    )
                )
            }
            
            sessions.clear()
            sessions.addAll(loadedSessions.sortedByDescending { it.timestamp })
            if (migrated) {
                saveSessions()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                for (session in sessions) {
                    val sessionObj = JSONObject()
                    sessionObj.put("id", session.id)
                    sessionObj.put("title", session.title)
                    sessionObj.put("timestamp", session.timestamp)
                    
                    val msgsArray = JSONArray()
                    for (msg in session.messages) {
                        val msgObj = JSONObject()
                        msgObj.put("role", msg.role)
                        msgObj.put("content", msg.content)
                        if (msg.reasoningContent != null) {
                            msgObj.put("reasoningContent", msg.reasoningContent)
                        }
                        msgObj.put("isError", msg.isError)
                        msgObj.put("id", msg.id)
                        msgsArray.put(msgObj)
                    }
                    sessionObj.put("messages", msgsArray)
                    jsonArray.put(sessionObj)
                }
                
                FileWriter(sessionsFile).use { it.write(jsonArray.toString()) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSettings(newApiKey: String, newBaseUrl: String, newModel: String) {
        apiKey = newApiKey.trim()
        baseUrl = newBaseUrl.trim().removeSuffix("/")
        model = newModel.trim()
        
        prefs.edit {
            putString("api_key", apiKey)
                .putString("base_url", baseUrl)
                .putString("model", model)
        }
            
        messages.add(localizedMessage("system", R.string.ai_status_settings_updated))
    }

    fun fetchModels() {
        if (apiKey.isBlank()) {
             messages.add(localizedMessage("assistant", R.string.ai_error_set_api_key_before_fetch, isError = true))
             return
        }
        
        isFetchingModels = true
        viewModelScope.launch(Dispatchers.IO) {
            var currentBaseUrl = baseUrl.trim().removeSuffix("/")
            
            // Clean up common URL copy-paste errors
            if (currentBaseUrl.endsWith("/chat/completions")) {
                currentBaseUrl = currentBaseUrl.substringBefore("/chat/completions").removeSuffix("/")
                // Also remove /v1 if it was part of the removed path and we want to test clean first
                // But usually we want to preserve v1 if it was there. 
                // Let's just strip /chat/completions.
            }

            // Define candidate endpoints
            val candidates = mutableListOf<String>()
            // 1. Exact base url + /models
            candidates.add("$currentBaseUrl/models")
            
            // 2. If base url doesn't end in /v1, try adding it
            if (!currentBaseUrl.endsWith("/v1")) {
                candidates.add("$currentBaseUrl/v1/models")
            }

            var success = false

            for (endpoint in candidates) {
                try {
                    withContext(Dispatchers.Main) {
                        messages.add(localizedMessage("system", R.string.ai_status_try_fetch_models_from, endpoint))
                    }

                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.code == 404) {
                        response.close()
                        continue // Try next candidate
                    }
                    
                    if (!response.isSuccessful) {
                        val errorMsg = response.body.string()
                        response.close()
                        throw AiLocalizedException(localizedContent(R.string.ai_error_failed_fetch_models_http, response.code, errorMsg))
                    }
                    
                    val responseBody = response.body.string()
                    val json = JSONObject(responseBody)
                    val data = json.optJSONArray("data")
                    
                    val fetchedModels = mutableListOf<String>()
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val modelObj = data.getJSONObject(i)
                            fetchedModels.add(modelObj.getString("id"))
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (fetchedModels.isNotEmpty()) {
                            availableModels.clear()
                            availableModels.addAll(fetchedModels)
                            messages.add(localizedMessage("system", R.string.ai_status_models_fetched, fetchedModels.size))
                            
                            // Update Base URL if we found a better one (e.g. added /v1)
                            if (endpoint.contains("/v1/models") && !baseUrl.endsWith("/v1")) {
                                val newBaseUrl = "$currentBaseUrl/v1"
                                baseUrl = newBaseUrl
                                prefs.edit { putString("base_url", newBaseUrl) }
                                messages.add(localizedMessage("system", R.string.ai_status_base_url_autocorrected, newBaseUrl))
                            }
                        } else {
                             messages.add(localizedMessage("system", R.string.ai_status_no_models_in_response))
                        }
                        isFetchingModels = false
                    }
                    success = true
                    break // Stop after success
                    
                } catch (e: Exception) {
                    // Only log if this is the last attempt or not a 404 (which we handled above)
                    if (endpoint == candidates.last()) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                             messages.add(
                                 if (e is AiLocalizedException) {
                                     ChatMessage("assistant", e.localizedContent, isError = true)
                                 } else {
                                     localizedMessage("assistant", R.string.ai_error_fetch_models, e.message.orEmpty(), isError = true)
                                 }
                             )
                             isFetchingModels = false
                        }
                    }
                }
            }
            
            if (!success && !isFetchingModels) {
                 // Already handled in catch block for the last item, but if loop finished without success (e.g. all 404s)
                 withContext(Dispatchers.Main) {
                     if (availableModels.isEmpty()) {
                        messages.add(localizedMessage("assistant", R.string.ai_error_fetch_models_404, isError = true))
                        isFetchingModels = false
                     }
                 }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            messages.add(localizedMessage("assistant", R.string.ai_error_set_api_key_first, isError = true))
            return
        }

        messages.add(ChatMessage("user", text))
        updateCurrentSession()
        isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (responseContent, reasoningContent) = callChatCompletionApi()
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage("assistant", responseContent, reasoningContent))
                    isLoading = false
                    updateCurrentSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    messages.add(
                        if (e is AiLocalizedException) {
                            ChatMessage("assistant", e.localizedContent, isError = true)
                        } else {
                            localizedMessage("assistant", R.string.ai_error_chat, e.message.orEmpty(), isError = true)
                        }
                    )
                    isLoading = false
                    updateCurrentSession()
                }
            }
        }
    }
    
    fun clearChat() {
        messages.clear()
        messages.add(localizedMessage("assistant", R.string.ai_status_chat_history_cleared))
        updateCurrentSession()
    }

    private fun callChatCompletionApi(): Pair<String, String?> {
        val jsonBody = JSONObject()
        jsonBody.put("model", model)
        
        val messagesArray = JSONArray()
        // Add system prompt
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", string(R.string.ai_system_prompt))
        messagesArray.put(systemMessage)

        // Add history (limit to last 10 messages to save tokens/context)
        val historyToInclude = messages.takeLast(10).filter(::shouldIncludeInChatHistory)
        for (msg in historyToInclude) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", resolveContent(msg.content))
            messagesArray.put(msgObj)
        }
        
        jsonBody.put("messages", messagesArray)

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        
        // Handle custom base URLs properly with smart retry
        var currentBaseUrl = baseUrl.trim().removeSuffix("/")
        if (currentBaseUrl.endsWith("/chat/completions")) {
            currentBaseUrl = currentBaseUrl.substringBefore("/chat/completions").removeSuffix("/")
        }

        val candidates = mutableListOf<String>()
        candidates.add("$currentBaseUrl/chat/completions")
        if (!currentBaseUrl.endsWith("/v1")) {
            candidates.add("$currentBaseUrl/v1/chat/completions")
        }

        var lastError: Exception? = null
        
        for (endpoint in candidates) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                
                if (response.code == 404) {
                    response.close()
                    continue // Try next candidate
                }

                response.use { 
                    if (!it.isSuccessful) {
                        val errorBody = it.body.string()
                        throw AiLocalizedException(localizedContent(R.string.ai_error_api_call_failed, it.code, errorBody))
                    }

                    val responseBody = it.body.string()
                    val jsonResponse = JSONObject(responseBody)
                    
                    val choices = jsonResponse.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val message = firstChoice.optJSONObject("message")
                        
                        // If we successfully used the fallback URL, update the base URL setting
                        if (endpoint.contains("/v1/chat/completions") && !baseUrl.endsWith("/v1")) {
                            val newBaseUrl = "$currentBaseUrl/v1"
                            // We can't easily update State from here since this is background thread without Main context for Compose State
                            // But we can update SharedPreferences
                            prefs.edit { putString("base_url", newBaseUrl) }
                            // We will update the State variable on Main thread later or next time
                        }
                        
                        val content = message?.optString("content") ?: ""
                        // Try to get reasoning content (common in DeepSeek R1 etc.)
                        val reasoning = message?.optString("reasoning_content")
                        
                        return Pair(content, if (reasoning.isNullOrBlank()) null else reasoning)
                    }
                    return Pair(localizedContent(R.string.ai_error_no_response_content), null)
                }
            } catch (e: Exception) {
                lastError = e
                if (endpoint == candidates.last()) {
                    throw e
                }
            }
        }
        
        throw lastError ?: AiLocalizedException(localizedContent(R.string.ai_error_failed_connect_api))
    }
}
