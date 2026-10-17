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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * Agent 回复的 Markdown 渲染。
 *
 * 之前的自研渲染器只处理代码块和粗体，标题、列表、表格、引用全会退化成纯文本。
 * 这里直接用项目里已经引入的 multiplatform-markdown-renderer（M3 主题），
 * 段落/列表/表格/链接/代码都由它处理，字号压到面板可读的区间。
 */
@Composable
internal fun AcpRichText(
    text: String,
    modifier: Modifier = Modifier
) {
    val body = MaterialTheme.typography.bodyMedium
    Markdown(
        content = text,
        colors = markdownColor(),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.titleMedium,
            h2 = MaterialTheme.typography.titleSmall,
            h3 = MaterialTheme.typography.titleSmall,
            h4 = MaterialTheme.typography.titleSmall,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.titleSmall,
            text = body,
            paragraph = body,
            code = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            ),
            inlineCode = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            quote = body,
            ordered = body,
            bullet = body,
            list = body,
            table = body
        ),
        modifier = modifier.fillMaxWidth()
    )
}
