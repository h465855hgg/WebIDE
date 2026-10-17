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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.web.webide.R

/** 一条搜索命中：时间线下标 + 类型 + 带上下文的摘要。 */
private data class AcpSearchMatch(
    val index: Int,
    val kindRes: Int,
    val text: String
)

/**
 * 对话内搜索。
 *
 * 只做「找到并跳回时间线」：结果按时间线顺序列出，点击关闭面板并滚动到对应
 * 条目，同时让该条高亮 2 秒。不做全文替换，也不改动 Agent 上下文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AcpConversationSearchSheet(
    viewModel: AcpViewModel,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val matches = remember(query, viewModel.timeline.size) {
        searchTimeline(viewModel.timeline, query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.acp_search_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.acp_action_hide),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.acp_search_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                query.isBlank() -> EmptyHint(stringResource(R.string.acp_search_hint_short))
                matches.isEmpty() -> EmptyHint(stringResource(R.string.acp_search_empty))
                else -> {
                    Text(
                        text = stringResource(R.string.acp_search_results, matches.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(matches) { _, match ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onJumpTo(match.index) }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = stringResource(match.kindRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = highlight(match.text, query),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun searchTimeline(items: List<AcpTimelineItem>, rawQuery: String): List<AcpSearchMatch> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return emptyList()
    val result = mutableListOf<AcpSearchMatch>()
    items.forEachIndexed { index, item ->
        val (kindRes, text) = when (item) {
            is AcpUserMessage -> R.string.acp_search_kind_user to item.text
            is AcpAgentMessage -> R.string.acp_search_kind_agent to item.text
            is AcpThoughtMessage -> R.string.acp_search_kind_thought to item.text
            is AcpNoticeMessage -> R.string.acp_search_kind_notice to item.text
            is AcpPlanItem -> R.string.acp_search_kind_plan to item.entries.joinToString("\n") { it.content }
            is AcpToolCallItem -> R.string.acp_search_kind_tool to buildString {
                append(item.title)
                item.contents.forEach { content ->
                    when (content) {
                        is AcpToolContent.Text -> append('\n').append(content.text)
                        is AcpToolContent.Diff -> append('\n').append(content.path)
                        is AcpToolContent.TerminalRef -> Unit
                    }
                }
            }
        }
        val position = text.indexOf(query, ignoreCase = true)
        if (position < 0) return@forEachIndexed
        val start = (position - 36).coerceAtLeast(0)
        val end = (position + query.length + 60).coerceAtMost(text.length)
        val snippet = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        result += AcpSearchMatch(index, kindRes, snippet)
    }
    return result
}

private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val keyword = query.trim()
    if (keyword.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor <= text.length) {
        val found = text.indexOf(keyword, cursor, ignoreCase = true)
        if (found < 0) {
            append(text.substring(cursor))
            break
        }
        if (found > cursor) append(text.substring(cursor, found))
        withStyle(
            SpanStyle(
                background = androidx.compose.ui.graphics.Color(0xFFFFF176),
                fontWeight = FontWeight.Medium
            )
        ) {
            append(text.substring(found, found + keyword.length))
        }
        cursor = found + keyword.length
    }
}
