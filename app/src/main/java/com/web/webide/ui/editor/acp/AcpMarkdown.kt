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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown 渲染：只处理 ACP Agent 输出里最常见的两类结构——
 * 围栏代码块与行内 `code` / **粗体**。刻意保持简单，避免引入额外依赖。
 */
@Composable
internal fun AcpMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val segments = remember(text) { splitFencedBlocks(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is AcpMdSegment.Code -> AcpCodeBlock(
                    language = segment.language,
                    content = segment.content,
                    background = codeBackground,
                    contentColor = codeColor
                )

                is AcpMdSegment.Prose -> Text(
                    text = annotateInline(segment.text, codeColor, codeBackground),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AcpCodeBlock(
    language: String,
    content: String,
    background: Color,
    contentColor: Color
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            if (language.isNotBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Text(
                text = content,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = contentColor
            )
        }
    }
}

internal sealed interface AcpMdSegment {
    data class Prose(val text: String) : AcpMdSegment
    data class Code(val language: String, val content: String) : AcpMdSegment
}

private val inlinePattern = Regex("`([^`]+)`|\\*\\*([^*]+)\\*\\*")

internal fun splitFencedBlocks(raw: String): List<AcpMdSegment> {
    if (raw.isEmpty()) return emptyList()
    val segments = mutableListOf<AcpMdSegment>()
    val lines = raw.split("\n")
    val prose = StringBuilder()
    var index = 0

    fun flushProse() {
        val text = prose.toString().trim('\n')
        if (text.isNotBlank()) segments.add(AcpMdSegment.Prose(text))
        prose.setLength(0)
    }

    while (index < lines.size) {
        val line = lines[index]
        if (line.trimStart().startsWith("```")) {
            flushProse()
            val language = line.trim().removePrefix("```").trim()
            index++
            val code = StringBuilder()
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code.append(lines[index]).append('\n')
                index++
            }
            if (index < lines.size) index++
            segments.add(AcpMdSegment.Code(language, code.toString().trimEnd('\n')))
        } else {
            prose.append(line).append('\n')
            index++
        }
    }
    flushProse()
    return segments
}

private fun annotateInline(text: String, codeColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        inlinePattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val code = match.groups[1]
            val bold = match.groups[2]
            when {
                code != null -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        color = codeColor
                    )
                ) { append(code.value) }

                bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                    append(bold.value)
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
