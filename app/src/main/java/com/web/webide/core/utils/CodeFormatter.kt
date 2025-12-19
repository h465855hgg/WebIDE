package com.web.webide.core.utils

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

object CodeFormatter {

    // 增加 indentSize 参数，默认为 4
    fun format(code: String, extension: String, indentSize: Int = 2): String {
        if (code.isBlank()) return ""

        return try {
            when (extension.lowercase()) {
                "html", "htm" -> formatHtml(code, indentSize)
                "json" -> formatJson(code, indentSize)
                "css" -> formatCss(code, indentSize) // CSS 简单处理暂时略过动态缩进
                "js" -> formatJs(code, indentSize)
                else -> code
            }
        } catch (e: Exception) {
            e.printStackTrace()
            code
        }
    }

    private fun formatHtml(code: String, indentSize: Int): String {
        val doc = Jsoup.parse(code)
        doc.outputSettings()
            .indentAmount(indentSize)
            .prettyPrint(true)
        return doc.html()
    }

    private fun formatJson(code: String, indentSize: Int): String {
        val trimmed = code.trim()
        return if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toString(indentSize)
        } else {
            JSONObject(trimmed).toString(indentSize)
        }
    }

    private fun formatCss(code: String, indentSize: Int = 4): String {
        // 构建缩进字符串
        val indent = " ".repeat(indentSize)
        return code
            .replace(Regex("\\s*\\{\\s*"), " {\n$indent")
            .replace(Regex("\\s*;\\s*"), ";\n$indent")
            .replace(Regex("\\s*\\}\\s*"), "\n}\n")
            .replace(Regex("(?m)^\\s+$"), "")
            .replace(Regex("\\n\\s*\\n"), "\n")
            .trim()
    }

    private fun formatJs(code: String, indentSize: Int): String {
        val lines = code.split("\n")
        val result = StringBuilder()
        var indentLevel = 0
        val indentStr = " ".repeat(indentSize)

        for (line in lines) {
            var trimmed = line.trim()
            if (trimmed.startsWith("}")) indentLevel--
            repeat(maxOf(0, indentLevel)) { result.append(indentStr) }
            result.append(trimmed).append("\n")
            if (trimmed.endsWith("{") || trimmed.endsWith("[")) indentLevel++
        }
        return result.toString().trim()
    }
}