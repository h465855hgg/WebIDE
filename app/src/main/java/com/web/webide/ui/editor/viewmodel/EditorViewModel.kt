/*
 * TreeCompose - A tree-structured file viewer built with Jetpack Compose
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

package com.web.webide.ui.editor.viewmodel

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.web.webide.core.utils.LogCatcher
import com.web.webide.core.utils.PermissionManager
import com.web.webide.lsp.LocalSocketProvider
import com.web.webide.lsp.LspService
import com.web.webide.ui.editor.EditorColorSchemeManager
import com.web.webide.ui.editor.components.TextMateInitializer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CodeEditorState(
    val file: File,
    val languageScopeName: String,
) {
    var content by mutableStateOf("")
    private var savedContent by mutableStateOf("")
    val isModified: Boolean get() = content != savedContent

    fun onContentLoaded(loadedContent: String) {
        content = loadedContent
        savedContent = loadedContent
    }

    fun onContentSaved() {
        savedContent = content
    }
}

data class EditorConfig(
    val fontSize: Float = 14f,
    val tabWidth: Int = 4,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val showInvisibles: Boolean = false,
    val showToolbar: Boolean = true,
    val fontPath: String = "",
    val customSymbols: String = "Tab,<,>,/,=,\",',!,?,;,:,{,},[,],(,),+,-,*,_,&,|"
) {
    fun getSymbolList(): List<String> = customSymbols.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

class EditorViewModel : ViewModel() {
    var hasShownInitialLoader by mutableStateOf(false)
        private set
    var openFiles by mutableStateOf<List<CodeEditorState>>(emptyList())
        private set
    var activeFileIndex by mutableStateOf(-1)
        private set
    var currentProjectPath by mutableStateOf<String?>(null)
        private set

    private val editorInstances = mutableMapOf<String, CodeEditor>()
    private val lspWrappers = mutableMapOf<String, LspEditor>()
    private var lspProject: LspProject? = null

    // 🔴 修复 1：将 source.json 加入支持列表
    private val supportedLanguageScopes = setOf(
        "text.html.basic",
        "source.css",
        "source.js",
        "source.json"
    )

    var editorConfig by mutableStateOf(EditorConfig())
        private set

    private var hasPermissions = false
    private lateinit var appContext: Context

    fun reloadEditorConfig(context: Context) {
        val prefs = context.getSharedPreferences("WebIDE_Editor_Settings", Context.MODE_PRIVATE)
        editorConfig = EditorConfig(
            fontSize = prefs.getFloat("editor_font_size", 14f),
            tabWidth = prefs.getInt("editor_tab_width", 4),
            wordWrap = prefs.getBoolean("editor_word_wrap", false),
            showInvisibles = prefs.getBoolean("editor_show_invisibles", false),
            showToolbar = prefs.getBoolean("editor_show_toolbar", true),
            fontPath = prefs.getString("editor_font_path", "") ?: "",
            customSymbols = prefs.getString("editor_custom_symbols", "Tab,<,>,/,=,\",',!,?,;,:,{,},[,],(,),+,-,*,_,&,|") ?: ""
        )
    }

    fun initializePermissions(context: Context) {
        appContext = context.applicationContext
        hasPermissions = PermissionManager.hasRequiredPermissions(appContext)
    }

    private fun checkPermissions(): Boolean {
        if (!hasPermissions) {
            return false
        }
        return true
    }

    fun onInitialLoaderShown() {
        hasShownInitialLoader = true
    }

    fun updateEditorTheme(seedColor: Color, isDark: Boolean) {
        editorInstances.values.forEach { editor ->
            val currentScheme = editor.colorScheme
            EditorColorSchemeManager.applyThemeColors(currentScheme, seedColor, isDark)
            editor.invalidate()
        }
    }

    private fun ensureLspProject(context: Context, projectRoot: String) {
        if (lspProject != null && currentProjectPath == projectRoot) return

        // 启动服务
        context.startService(Intent(context, LspService::class.java))

        lspProject?.dispose()
        lspProject = LspProject(projectRoot)

        // 注册各种后缀都使用同一个 Socket 连接
        val extensions = listOf("html", "css", "js", "json")
        extensions.forEach { ext ->
            val webDefinition = object : CustomLanguageServerDefinition(
                ext,
                {
                    // 这里连接到我们 Service 中开启的 LocalServerSocket
                    LocalSocketProvider("web-lsp-socket")
                }
            ) {}
            lspProject?.addServerDefinition(webDefinition)
        }
        LogCatcher.d("LSP", "LspProject initialized for $projectRoot")
    }

    @Synchronized
    fun getOrCreateEditor(context: Context, state: CodeEditorState): CodeEditor {
        val filePath = state.file.absolutePath

        // 1. 确保 LSP Project 环境已准备好
        currentProjectPath?.let { root ->
            ensureLspProject(context, root)
        }

        // 2. 如果 View 缓存里已有，直接复用
        editorInstances[filePath]?.let {
            if (it.context == context) return it
            else editorInstances.remove(filePath)
        }

        // 3. 初始化 TextMate 资源
        if (!TextMateInitializer.isReady()) {
            TextMateInitializer.initialize(context)
        }

        // 4. 创建编辑器并配置
        val editor = CodeEditor(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setText(state.content)
            colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

            getComponent(EditorAutoCompletion::class.java).apply {
                isEnabled = true
                setEnabledAnimation(true)
            }

            try {
                setEditorLanguage(TextMateLanguage.create(state.languageScopeName, true))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. 连接 LSP
        val project = lspProject
        if (project != null) {
            val lspEditor = project.createEditor(filePath)
            lspEditor.editor = editor
            lspEditor.wrapperLanguage = TextMateLanguage.create(state.languageScopeName, true)

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    LogCatcher.d("LSP_Client", "Connecting to server...")

                    // --- 修正点开始 ---
                    lspEditor.connectWithTimeout() // 无参数，挂起等待

                    LogCatcher.d("LSP_Client", "Connected successfully!")

                    lspEditor.requestManager?.didOpen(
                        org.eclipse.lsp4j.DidOpenTextDocumentParams(
                            org.eclipse.lsp4j.TextDocumentItem(
                                filePath,
                                state.languageScopeName,
                                1,
                                state.content
                            )
                        )
                    )
                    // --- 修正点结束 ---

                } catch (e: Exception) {
                    LogCatcher.e("LSP", "Connection failed", e)
                }
            }
            lspWrappers[filePath] = lspEditor
        }

        editorInstances[filePath] = editor
        return editor
    }

    // ... 其他方法保持不变 (onCleared, loadInitialFile 等) ...
    // 为了节省篇幅，这里省略了未修改的辅助方法
    // 请保留 search, save, fileOps 等相关方法的原有代码
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            lspWrappers.values.forEach { it.dispose() }
            lspWrappers.clear()
            lspProject?.dispose()
            lspProject = null
        }
        editorInstances.values.forEach {
            try { it.release() } catch (e: Exception) { e.printStackTrace() }
        }
        editorInstances.clear()
    }

    fun loadInitialFile(projectPath: String) {
        if (projectPath != currentProjectPath) {
            closeAllFiles()
            currentProjectPath = projectPath
            ensureLspProject(appContext, projectPath)

            val indexFile = File(projectPath, "index.html")
            if (indexFile.exists() && indexFile.isFile && indexFile.canRead()) {
                openFile(indexFile)
            }
        }
    }

    private var lastSearchQuery = ""
    private var isIgnoreCase = true

    fun getActiveEditor(): CodeEditor? {
        val activeFile = openFiles.getOrNull(activeFileIndex) ?: return null
        return editorInstances[activeFile.file.absolutePath]
    }

    fun searchText(query: String, ignoreCase: Boolean = isIgnoreCase) {
        lastSearchQuery = query
        isIgnoreCase = ignoreCase
        val editor = getActiveEditor() ?: return

        if (query.isNotEmpty()) {
            editor.searcher.search(query, EditorSearcher.SearchOptions(ignoreCase, false))
        } else {
            editor.searcher.stopSearch()
        }
    }

    fun searchNext() {
        val editor = getActiveEditor() ?: return
        if (editor.searcher.hasQuery()) {
            try {
                editor.searcher.gotoNext()
            } catch (e: Exception) {
                LogCatcher.e("Search", "Next failed", e)
            }
        }
    }

    fun searchPrev() {
        val editor = getActiveEditor() ?: return
        if (editor.searcher.hasQuery()) {
            try {
                editor.searcher.gotoPrevious()
            } catch (e: Exception) {
                LogCatcher.e("Search", "Prev failed", e)
            }
        }
    }

    fun replaceCurrent(replaceText: String) {
        try {
            getActiveEditor()?.searcher?.replaceCurrentMatch(replaceText)
        } catch (e: Exception) {
            LogCatcher.e("Search", "Replace failed", e)
        }
    }

    fun replaceAll(replaceText: String) {
        try {
            getActiveEditor()?.searcher?.replaceAll(replaceText)
        } catch (e: Exception) {
            LogCatcher.e("Search", "Replace all failed", e)
        }
    }

    fun stopSearch() {
        getActiveEditor()?.searcher?.stopSearch()
    }

    private var isFormatting = false
    fun formatCode() {
        if (isFormatting) return
        isFormatting = true
        val activeFile = openFiles.getOrNull(activeFileIndex) ?: return
        val filePath = activeFile.file.absolutePath
        val editor = editorInstances[filePath] ?: return
        val extension = activeFile.file.extension

        viewModelScope.launch(Dispatchers.Default) {
            val originalCode = editor.text.toString()
            val formattedCode = com.web.webide.core.utils.CodeFormatter.format(originalCode, extension, editorConfig.tabWidth)

            if (formattedCode != originalCode) {
                withContext(Dispatchers.Main) {
                    val text = editor.text
                    val lastLine = text.lineCount - 1
                    val lastColumn = if(lastLine >= 0) text.getColumnCount(lastLine) else 0
                    text.replace(0, 0, lastLine, lastColumn, formattedCode)
                    activeFile.content = formattedCode
                }
            }
            isFormatting = false
        }
    }

    fun jumpToLine(lineStr: String) {
        val line = lineStr.toIntOrNull() ?: return
        val editor = getActiveEditor() ?: return
        val totalLines = editor.text.lineCount
        val targetLine = (line - 1).coerceIn(0, totalLines - 1)
        editor.setSelection(targetLine, 0)
        editor.ensureSelectionVisible()
    }

    fun insertText(text: String) {
        val editor = getActiveEditor() ?: return
        val cursor = editor.cursor
        editor.text.insert(cursor.leftLine, cursor.leftColumn, text)
    }

    fun createNewItem(parentPath: String, name: String, isFile: Boolean, onSuccess: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newItem = File(parentPath, name)
                if (newItem.exists()) return@launch

                val success = if (isFile) {
                    newItem.createNewFile()
                } else {
                    newItem.mkdirs()
                }

                if (success) {
                    withContext(Dispatchers.Main) {
                        onSuccess(newItem)
                    }
                }
            } catch (e: Exception) {
                LogCatcher.e("FileOps", "创建失败", e)
            }
        }
    }

    suspend fun saveAllModifiedFiles(snackbarHostState: SnackbarHostState) {
        withContext(Dispatchers.IO) {
            val modifiedFiles = openFiles.filter { it.isModified }
            if (modifiedFiles.isEmpty()) return@withContext

            if (!checkPermissions()) {
                withContext(Dispatchers.Main) {
                    viewModelScope.launch { snackbarHostState.showSnackbar("需要存储权限才能保存文件") }
                }
                return@withContext
            }

            var successCount = 0
            modifiedFiles.forEach { state ->
                try {
                    state.file.outputStream().use { output ->
                        output.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(state.content)
                        }
                    }
                    state.onContentSaved()
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                if (successCount > 0) {
                    viewModelScope.launch { snackbarHostState.showSnackbar("已保存 $successCount 个文件") }
                }
            }
        }
    }

    fun openFile(file: File) {
        if (file.isDirectory || !file.exists() || !file.canRead()) return
        viewModelScope.launch {
            val existingIndex = openFiles.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                activeFileIndex = existingIndex
            } else {
                val content = withContext(Dispatchers.IO) {
                    try {
                        file.readText(Charsets.UTF_8)
                    } catch (_: Exception) {
                        ""
                    }
                }
                val language = getLanguageScope(file.extension)
                val newState = CodeEditorState(file = file, languageScopeName = language)
                newState.onContentLoaded(content)
                openFiles = openFiles + newState
                activeFileIndex = openFiles.lastIndex
            }
        }
    }

    fun undo() {
        getActiveEditor()?.undo()
    }

    fun redo() {
        getActiveEditor()?.redo()
    }

    fun insertSymbol(symbol: String) {
        getActiveEditor()?.let { editor ->
            val processedSymbol = if (symbol == "Tab") "\t" else symbol
            editor.insertText(processedSymbol, processedSymbol.length)
        }
    }

    fun changeActiveFileIndex(index: Int) {
        if (index in openFiles.indices) activeFileIndex = index
    }

    fun closeAllFiles() {
        openFiles.forEach { state ->
            val path = state.file.absolutePath
            lspWrappers.remove(path)?.dispose()
            editorInstances.remove(path)?.release()
        }
        openFiles = emptyList()
        activeFileIndex = -1
    }

    fun closeOtherFiles(indexToKeep: Int) {
        if (indexToKeep !in openFiles.indices) return
        openFiles.forEachIndexed { index, state ->
            if (index != indexToKeep) {
                val path = state.file.absolutePath
                lspWrappers.remove(path)?.dispose()
                editorInstances.remove(path)?.release()
            }
        }
        openFiles = listOf(openFiles[indexToKeep])
        activeFileIndex = 0
    }

    fun closeFile(indexToClose: Int) {
        if (indexToClose !in openFiles.indices) return
        openFiles.getOrNull(indexToClose)?.file?.absolutePath?.let { path ->
            lspWrappers.remove(path)?.dispose()
            editorInstances.remove(path)?.release()
        }
        openFiles = openFiles.toMutableList().also { it.removeAt(indexToClose) }
        if (openFiles.isEmpty()) {
            activeFileIndex = -1
        } else if (activeFileIndex >= indexToClose) {
            activeFileIndex = (activeFileIndex - 1).coerceAtLeast(0)
        }
    }

    // 🔴 修复 4：修正 Scope 映射
    private fun getLanguageScope(extension: String): String = when (extension.lowercase()) {
        "html", "htm" -> "text.html.basic"
        "css" -> "source.css"
        "js" -> "source.js"
        "json" -> "source.json" // 使用标准的 source.json
        else -> "text.plain"
    }
}