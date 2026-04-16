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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import com.web.webide.R

sealed class MarkdownNode {
    data class Text(val content: String) : MarkdownNode()
    data class CodeBlock(val language: String, val content: String) : MarkdownNode()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val nodes = remember(markdown) { parseMarkdown(markdown) }

    Column(modifier = modifier) {
        nodes.forEachIndexed { index, node ->
            when (node) {
                is MarkdownNode.Text -> {
                    if (node.content.isNotBlank()) {
                        val annotatedString = parseInlineMarkdown(node.content)
                        SelectionContainer {
                            Text(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = color,
                                    fontSize = fontSize,
                                    lineHeight = 20.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                is MarkdownNode.CodeBlock -> {
                    key(index) {
                        CodeBlockView(language = node.language, code = node.content)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

fun parseMarkdown(text: String): List<MarkdownNode> {
    val nodes = mutableListOf<MarkdownNode>()
    val regex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    regex.findAll(text).forEach { matchResult ->
        // Add text before the code block
        if (matchResult.range.first > lastIndex) {
            nodes.add(MarkdownNode.Text(text.substring(lastIndex, matchResult.range.first)))
        }

        // Add the code block
        val language = matchResult.groupValues[1].trim()
        val code = matchResult.groupValues[2].trim()
        nodes.add(MarkdownNode.CodeBlock(language, code))

        lastIndex = matchResult.range.last + 1
    }

    // Add remaining text
    if (lastIndex < text.length) {
        nodes.add(MarkdownNode.Text(text.substring(lastIndex)))
    }

    return nodes
}

// Simple inline parser for bold and monospace
fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Matches **bold** or `code`
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(`(.*?)`)")
        
        regex.findAll(text).forEach { match ->
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }
            
            val boldContent = match.groupValues[2]
            val codeContent = match.groupValues[4]
            
            if (boldContent.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldContent)
                }
            } else if (codeContent.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFFE0E0E0).copy(alpha = 0.5f) // Light gray bg for inline code
                    )
                ) {
                    append(" $codeContent ")
                }
            }
            
            currentIndex = match.range.last + 1
        }
        
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

@Composable
fun CodeBlockView(language: String, code: String) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E), // Dark background for code
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse/Expand Icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(if (isExpanded) R.string.content_desc_collapse else R.string.content_desc_expand),
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.size(8.dp))
                
                Text(
                    text = language.ifBlank { stringResource(R.string.markdown_code_default) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFAAAAAA),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))

                // Copy Button
                Row(
                    modifier = Modifier
                        .clickable {
                            copyToClipboard(context, code)
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.action_copy),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
            
            // Code Content
            if (isExpanded) {
                // SelectionContainer is handled by parent composable (AICodingPanel)
                SelectionContainer {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFFD4D4D4), // Light gray text
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // Collapsed state hint
                Text(
                    text = stringResource(R.string.markdown_lines_hidden, code.lines().size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(context.getString(R.string.markdown_copied_code), text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.markdown_code_copied_toast), Toast.LENGTH_SHORT).show()
}
