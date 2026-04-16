package com.web.webide.ui.editor.aicoding

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.web.webide.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object AICodingLocalizedText {
    private const val PREFIX = "__ai_localized_text__:"
    private val placeholderRegex = Regex("%(\\d+)\\$([sd])")
    private val migratableResIds = intArrayOf(
        R.string.ai_status_settings_updated,
        R.string.ai_error_set_api_key_before_fetch,
        R.string.ai_status_try_fetch_models_from,
        R.string.ai_status_models_fetched,
        R.string.ai_status_base_url_autocorrected,
        R.string.ai_status_no_models_in_response,
        R.string.ai_error_fetch_models,
        R.string.ai_error_fetch_models_404,
        R.string.ai_error_set_api_key_first,
        R.string.ai_error_chat,
        R.string.ai_status_chat_history_cleared,
        R.string.ai_error_no_response_content,
        R.string.ai_error_failed_connect_api,
        R.string.ai_error_failed_fetch_models_http,
        R.string.ai_error_api_call_failed
    )

    data class Payload(
        val key: String,
        val args: List<String>
    )

    fun encode(context: Context, @StringRes resId: Int, vararg args: Any): String {
        val payload = JSONObject().apply {
            put("key", context.resources.getResourceEntryName(resId))
            put("args", JSONArray().apply {
                args.forEach { put(it.toString()) }
            })
        }
        return PREFIX + payload.toString()
    }

    fun isEncoded(content: String): Boolean = content.startsWith(PREFIX)

    fun decode(content: String): Payload? {
        if (!isEncoded(content)) return null
        return runCatching {
            val payload = JSONObject(content.removePrefix(PREFIX))
            val argsArray = payload.optJSONArray("args") ?: JSONArray()
            Payload(
                key = payload.getString("key"),
                args = buildList(argsArray.length()) {
                    for (index in 0 until argsArray.length()) {
                        add(argsArray.optString(index))
                    }
                }
            )
        }.getOrNull()
    }

    fun resolve(context: Context, content: String): String? {
        val payload = decode(content) ?: return null
        return when (payload.key) {
            "ai_status_settings_updated" -> context.getString(R.string.ai_status_settings_updated)
            "ai_error_set_api_key_before_fetch" -> context.getString(R.string.ai_error_set_api_key_before_fetch)
            "ai_status_try_fetch_models_from" -> context.getString(
                R.string.ai_status_try_fetch_models_from,
                payload.args.getOrElse(0) { "" }
            )
            "ai_status_models_fetched" -> context.getString(
                R.string.ai_status_models_fetched,
                payload.args.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            )
            "ai_status_base_url_autocorrected" -> context.getString(
                R.string.ai_status_base_url_autocorrected,
                payload.args.getOrElse(0) { "" }
            )
            "ai_status_no_models_in_response" -> context.getString(R.string.ai_status_no_models_in_response)
            "ai_error_fetch_models" -> context.getString(
                R.string.ai_error_fetch_models,
                payload.args.getOrElse(0) { "" }
            )
            "ai_error_fetch_models_404" -> context.getString(R.string.ai_error_fetch_models_404)
            "ai_error_set_api_key_first" -> context.getString(R.string.ai_error_set_api_key_first)
            "ai_error_chat" -> context.getString(
                R.string.ai_error_chat,
                payload.args.getOrElse(0) { "" }
            )
            "ai_status_chat_history_cleared" -> context.getString(R.string.ai_status_chat_history_cleared)
            "ai_error_no_response_content" -> context.getString(R.string.ai_error_no_response_content)
            "ai_error_failed_connect_api" -> context.getString(R.string.ai_error_failed_connect_api)
            "ai_error_failed_fetch_models_http" -> context.getString(
                R.string.ai_error_failed_fetch_models_http,
                payload.args.getOrElse(0) { "0" }.toIntOrNull() ?: 0,
                payload.args.getOrElse(1) { "" }
            )
            "ai_error_api_call_failed" -> context.getString(
                R.string.ai_error_api_call_failed,
                payload.args.getOrElse(0) { "0" }.toIntOrNull() ?: 0,
                payload.args.getOrElse(1) { "" }
            )
            else -> null
        }
    }

    fun migrate(context: Context, content: String): String {
        if (isEncoded(content)) return content

        val locales = listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)
        for (resId in migratableResIds) {
            for (locale in locales) {
                val template = getLocalizedTemplate(context, locale, resId)
                val args = extractArgs(template, content) ?: continue
                return encode(context, resId, *args.toTypedArray())
            }
        }
        return content
    }

    private fun getLocalizedTemplate(context: Context, locale: Locale, @StringRes resId: Int): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).getString(resId)
    }

    private fun extractArgs(template: String, content: String): List<String>? {
        val placeholders = placeholderRegex.findAll(template).toList()
        if (placeholders.isEmpty()) {
            return if (content == template) emptyList() else null
        }

        val pattern = buildString {
            append("^")
            var lastIndex = 0
            for (placeholder in placeholders) {
                append(Regex.escape(template.substring(lastIndex, placeholder.range.first)))
                val type = placeholder.groupValues[2]
                append(if (type == "d") "(-?\\d+)" else "([\\s\\S]*?)")
                lastIndex = placeholder.range.last + 1
            }
            append(Regex.escape(template.substring(lastIndex)))
            append("$")
        }

        val match = Regex(pattern).matchEntire(content) ?: return null
        return match.groupValues.drop(1)
    }
}
