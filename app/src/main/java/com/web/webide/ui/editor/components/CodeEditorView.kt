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


package com.web.webide.ui.editor.components

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.web.webide.R
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.ThemeViewModelFactory
import com.web.webide.ui.editor.TextMateInitializer
import com.web.webide.ui.editor.viewmodel.CodeEditorState
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import java.io.File

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

@Composable
fun CodeEditorView(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    viewModel: EditorViewModel,
    onShowSearch: () -> Unit = {},
    onRun: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onShowJumpLine: () -> Unit = {},
    onShowCreate: () -> Unit = {},
    onShowColorPicker: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadEditorConfig(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var isEditorReady by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }

    val editorConfig = viewModel.editorConfig

    // === 字体加载逻辑 ===
    val editorTypeface = remember(editorConfig.fontPath) {
        if (editorConfig.fontPath.isBlank()) {
            Typeface.MONOSPACE
        } else {
            try {
                val file = File(editorConfig.fontPath)
                if (file.exists() && file.isFile && file.canRead()) {
                    Typeface.createFromFile(file)
                } else {
                    Typeface.createFromAsset(context.assets, editorConfig.fontPath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Typeface.MONOSPACE
            }
        }
    }

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val themeState by themeViewModel.themeState.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeState.selectedModeIndex) {
        0 -> systemDark
        1 -> false
        2 -> true
        else -> systemDark
    }
    val seedColor = if (themeState.isCustomTheme) themeState.customColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme

    val editor = remember(state.file.absolutePath) { viewModel.getOrCreateEditor(context, state) }

    // 添加自定义文本操作按钮 (在双击/长按选区后的弹窗中)
    DisposableEffect(editor) {
        val textActionWindow = editor.getComponent(EditorTextActionWindow::class.java)
        val provider = object : EditorTextActionWindow.ExtraButtonProvider {
            override fun getIconResource(): Int = R.drawable.ic_command // 使用 Command 图标
            override fun getContentDescription(): String = context.getString(R.string.content_desc_command)
            override fun shouldShowButton(editor: CodeEditor): Boolean = true
            override fun onButtonClick(editor: CodeEditor) {
                showCommandDialog = true
            }
        }
        textActionWindow.addExtraButtonProvider(provider)
        onDispose {
            textActionWindow.removeExtraButtonProvider(provider)
        }
    }

    // 初始化 TextMate
    LaunchedEffect(Unit) {
        if (!TextMateInitializer.isReady()) {
            TextMateInitializer.initialize(context) {
                isEditorReady = true
            }
        } else {
            isEditorReady = true
        }
    }

    // 当 TextMate 准备好时，重新加载编辑器语言（从 EmptyLanguage 切换到 TextMateLanguage）
    LaunchedEffect(isEditorReady) {
        if (isEditorReady) {
            viewModel.reloadAllEditors(context)
        }
    }

    // 监听深色模式变化
    LaunchedEffect(seedColor, isDark, isEditorReady, colorScheme) {
        if (isEditorReady) {
            try {
                // 1. 设置 TextMate 主题 (语法高亮)
                val targetTheme = if (isDark) TextMateInitializer.THEME_DARK else TextMateInitializer.THEME_LIGHT
                ThemeRegistry.getInstance().setTheme(targetTheme)
                
                // 2. 重新创建配色方案 (只针对使用 TextMate 的编辑器)
                if (editor.editorLanguage is TextMateLanguage) {
                    val newScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                    editor.colorScheme = newScheme
                }
                
                // 3. 应用 Material 主题颜色覆盖 (背景色、行号等)
                viewModel.updateEditorTheme(colorScheme)
                
                // 4. 强制刷新
                editor.invalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showCommandDialog) {
        val focusRequester = remember { FocusRequester() }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCommandDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.85f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 保持输入法焦点的隐形输入框
                        TextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .size(1.dp)
                                .alpha(0f)
                        )

                        val actions = remember {
                            listOf(
                                Triple(context.getString(R.string.toolbar_cut), Icons.Filled.ContentCut) { editor.cutText() },
                                Triple(context.getString(R.string.toolbar_copy), Icons.Filled.ContentCopy) { editor.copyText() },
                                Triple(context.getString(R.string.toolbar_paste), Icons.Filled.ContentPaste) { editor.pasteText() },
                                Triple(context.getString(R.string.toolbar_select_all), Icons.Filled.SelectAll) { editor.selectAll() },
                                Triple(context.getString(R.string.toolbar_select_line), Icons.AutoMirrored.Filled.Segment) {
                                    val cursor = editor.cursor
                                    val line = cursor.leftLine
                                    if (line < editor.text.lineCount) {
                                        try {
                                            editor.setSelectionRegion(line, 0, line, editor.text.getColumnCount(line))
                                        } catch (_: Exception) {
                                            editor.setSelection(line, 0)
                                        }
                                    }
                                },
                                Triple(context.getString(R.string.action_save), Icons.Filled.Save) {
                                    viewModel.onContentChanged(state.file, editor.text.toString(), saveToFile = true)
                                    state.onContentSaved()
                                },
                                Triple(context.getString(R.string.action_save_all), Icons.Filled.Save) {
                                viewModel.openFiles.filterIsInstance<CodeEditorState>().filter { it.isModified }.forEach { s ->
                                    viewModel.onContentChanged(s.file, s.content, saveToFile = true)
                                    s.onContentSaved()
                                }
                            },
                            Triple(context.getString(R.string.editor_undo), Icons.AutoMirrored.Filled.Undo) { editor.undo() },
                            Triple(context.getString(R.string.editor_redo), Icons.AutoMirrored.Filled.Redo) { editor.redo() },
                            Triple(context.getString(R.string.toolbar_jump_line), Icons.Filled.SwapVert) { onShowJumpLine() },
                            Triple(context.getString(R.string.toolbar_format), Icons.Filled.Menu) { viewModel.formatCode() },
                            Triple(context.getString(R.string.action_new), Icons.Filled.Add) { onShowCreate() },
                            Triple(context.getString(R.string.toolbar_palette), Icons.Filled.ColorLens) { onShowColorPicker() },
                            Triple(context.getString(R.string.action_terminal), Icons.Filled.Dns) { onNavigateToTerminal() },
                            Triple(context.getString(R.string.action_run), Icons.Filled.PlayArrow) {
                                onRun()
                            },
                                Triple(context.getString(R.string.toolbar_readonly), Icons.Filled.Lock) {
                                    editor.isEditable = !editor.isEditable
                                },
                                Triple(context.getString(R.string.toolbar_search_replace), Icons.Filled.Search) {
                                    onShowSearch()
                                },
                                Triple(context.getString(R.string.toolbar_reload), Icons.Filled.Refresh) {
                                    viewModel.reloadAllEditors(context)
                                }
                            )
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(actions) { (label, icon, action) ->
                                FilledTonalButton(
                                    onClick = {
                                        action()
                                        showCommandDialog = false
                                    },
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 始终显示编辑器，即使未完全初始化
        AndroidView(
            factory = { _ ->
                (editor.parent as? ViewGroup)?.removeView(editor)
                editor
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.props.singleDirectionDragging = false
                view.typefaceText = editorTypeface
                view.typefaceLineNumber = editorTypeface
                view.isWordwrap = editorConfig.wordWrap
                view.tabWidth = editorConfig.tabWidth
                view.setFoldingEnabled(editorConfig.codeFolding)
                view.setScaleTextSizes(2f, 300f)
                editor.setHighlightBracketPair(true)

                if (editorConfig.showInvisibles) {
                    view.nonPrintablePaintingFlags =
                        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                                CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
                                CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
                                CodeEditor.FLAG_DRAW_LINE_SEPARATOR
                } else {
                    view.nonPrintablePaintingFlags = 0
                }

                if (view.text.toString() != state.content) {
                    val cursor = view.cursor
                    val cursorLine = cursor.leftLine
                    val cursorColumn = cursor.leftColumn
                    view.setText(state.content)
                    try {
                        val lineCount = view.text.lineCount
                        val targetLine = cursorLine.coerceIn(0, lineCount - 1)
                        val lineLength = if (targetLine < view.text.lineCount) view.text.getColumnCount(targetLine) else 0
                        val targetColumn = cursorColumn.coerceIn(0, lineLength)
                        view.setSelection(targetLine, targetColumn)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                view.isEnabled = true
                view.visibility = android.view.View.VISIBLE
                view.requestLayout()
            }
        )
    }
}
