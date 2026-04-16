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

package com.web.webide.ui.editor.doc

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.launch
import com.web.webide.R
import java.io.BufferedReader
import java.io.InputStreamReader

data class DocSection(val id: Int, val title: String, val content: String)

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsInterfaceDocScreen(navController: NavController) {
    val context = LocalContext.current
    var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFile by remember { mutableStateOf("") }
    var docContent by remember { mutableStateOf("") }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Load file list
    remember {
        try {
            val files = context.assets.list("docs")?.toList() ?: emptyList()
            fileList = files
            if (files.isNotEmpty()) {
                selectedFile = files.find { it.contains("js", ignoreCase = true) } ?: files.first()
            }
        } catch (_: Exception) {
            fileList = emptyList()
        }
    }

    // Load content when selection changes
    remember(selectedFile) {
        if (selectedFile.isNotEmpty()) {
            try {
                val inputStream = context.assets.open("docs/$selectedFile")
                val reader = BufferedReader(InputStreamReader(inputStream))
                docContent = reader.use { it.readText() }
            } catch (e: Exception) {
                docContent = context.getString(R.string.doc_load_error, e.message ?: "")
            }
        }
    }

    // Parse sections
    val sections = remember(docContent) {
        if (docContent.isBlank()) emptyList()
        else {
            // Split by headers (#, ##, etc.) but keep the delimiter
            val regex = Regex("(?m)^(?=#{1,6}\\s)")
            val parts = docContent.split(regex).filter { it.isNotBlank() }
            parts.mapIndexed { index, part ->
                val firstLine = part.lines().firstOrNull { it.isNotBlank() } ?: ""
                val title = firstLine.trimStart('#').trim().ifEmpty {
                    context.getString(R.string.doc_section_fallback, index + 1)
                }
                DocSection(index, title, part)
            }
        }
    }

    // Filtered results
    val searchResults = remember(searchQuery, sections) {
        if (searchQuery.isBlank()) emptyList()
        else sections.filter {
            it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.ime, // 处理输入法遮挡
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, stringResource(R.string.action_clear))
                                        }
                                    }
                                }
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text(stringResource(R.string.js_interface_docs_title))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                            }
                        }
                    }
                )
                if (fileList.isNotEmpty() && !isSearchActive) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = fileList.indexOf(selectedFile).coerceAtLeast(0),
                            edgePadding = 0.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {},
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            fileList.forEach { fileName ->
                                Tab(
                                    selected = selectedFile == fileName,
                                    onClick = { selectedFile = fileName },
                                    text = { Text(fileName.removeSuffix(".md").removeSuffix(".txt")) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val isDarkTheme = isSystemInDarkTheme()
        val highlightsBuilder = remember(isDarkTheme) {
            Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDarkTheme))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Content
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(sections, key = { it.id }) { section ->
                        Markdown(
                            markdownState = rememberMarkdownState(section.content) { section.content },
                            components = markdownComponents(
                                codeBlock = {
                                    MarkdownHighlightedCodeBlock(
                                        content = it.content,
                                        node = it.node,
                                        highlightsBuilder = highlightsBuilder,
                                        showHeader = true,
                                    )
                                },
                                codeFence = {
                                    MarkdownHighlightedCodeFence(
                                        content = it.content,
                                        node = it.node,
                                        highlightsBuilder = highlightsBuilder,
                                        showHeader = true,
                                    )
                                },
                                checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) }
                            ),
                            imageTransformer = Coil2ImageTransformerImpl,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Search Results Overlay
            if (isSearchActive && searchQuery.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(searchResults) { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        scope.launch {
                                            isSearchActive = false
                                            searchQuery = ""
                                            listState.scrollToItem(result.id)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Show a snippet of content containing the query
                                    val snippet = remember(result.content, searchQuery) {
                                        val index = result.content.indexOf(searchQuery, ignoreCase = true)
                                        if (index != -1) {
                                            val start = (index - 20).coerceAtLeast(0)
                                            val end = (index + searchQuery.length + 50).coerceAtMost(result.content.length)
                                            "..." + result.content.substring(start, end).replace("\n", " ") + "..."
                                        } else {
                                            result.content.take(100).replace("\n", " ") + "..."
                                        }
                                    }
                                    Text(
                                        text = snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
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
