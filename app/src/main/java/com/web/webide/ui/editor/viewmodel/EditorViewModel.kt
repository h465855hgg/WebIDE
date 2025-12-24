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

package com.web.webide.ui.editor.viewmodel

import android.content.Context
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
import com.web.webide.ui.editor.EditorColorSchemeManager
import com.web.webide.ui.editor.components.TextMateInitializer
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor
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
// 1. 定义配置数据类
// 1. 修改配置数据类，增加 fontPath
data class EditorConfig(
    val fontSize: Float = 14f,
    val tabWidth: Int = 4,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val showInvisibles: Boolean = false,
    val showToolbar: Boolean = true,
    val fontPath: String = "", // 空字符串代表系统默认，否则填文件名如 "JetBrainsMono-Regular.ttf"
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
    private val supportedLanguageScopes = setOf("text.html.basic", "source.css", "source.js")
    var editorConfig by mutableStateOf(EditorConfig())
        private set
    // 权限检查
    private var hasPermissions = false
    private lateinit var appContext: Context

    // 2. 更新加载逻辑
    fun reloadEditorConfig(context: Context) {
        val prefs = context.getSharedPreferences("WebIDE_Editor_Settings", Context.MODE_PRIVATE)
        editorConfig = EditorConfig(
            fontSize = prefs.getFloat("editor_font_size", 14f),
            tabWidth = prefs.getInt("editor_tab_width", 4),
            wordWrap = prefs.getBoolean("editor_word_wrap", false),
            showInvisibles = prefs.getBoolean("editor_show_invisibles", false),
            showToolbar = prefs.getBoolean("editor_show_toolbar", true),
            fontPath = prefs.getString("editor_font_path", "") ?: "", // 加载字体路径
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

    // 🔥 修复 1：更新主题时强制重绘，防止第一个文件光标因颜色加载滞后而不显示
    fun updateEditorTheme(seedColor: Color, isDark: Boolean) {
        editorInstances.values.forEach { editor ->
            val currentScheme = editor.colorScheme
            EditorColorSchemeManager.applyThemeColors(currentScheme, seedColor, isDark)
            editor.invalidate() // 强制重绘
        }
    }

    @Synchronized
    fun getOrCreateEditor(context: Context, state: CodeEditorState): CodeEditor {
        val filePath = state.file.absolutePath



        // 检查缓存
        editorInstances[filePath]?.let { existingEditor ->
            // 🔥 必须检查：如果 Context 变了（比如屏幕旋转、退出了页面重进），必须销毁重建！
            // 否则 View 会持有旧 Activity 的引用，导致键盘弹不出来
            if (existingEditor.context != context) {
                try {
                    (existingEditor.parent as? ViewGroup)?.removeView(existingEditor)
                    existingEditor.release()
                } catch (e: Exception) { e.printStackTrace() }
                editorInstances.remove(filePath)
                // 让代码继续往下走，创建新的实例
            } else {
                (existingEditor.parent as? ViewGroup)?.removeView(existingEditor)
                return existingEditor
            }
        }

        // 2. 确保 TextMate 初始化
        if (!TextMateInitializer.isReady()) {
            TextMateInitializer.initialize(context)
        }

        // 3. 创建新实例
        val editor = CodeEditor(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )



            isFocusable = true
            isFocusableInTouchMode = true
            isEnabled = true

            setText(state.content)

            // 初始化配色
            colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

            // 初始化语言
            if (state.languageScopeName in supportedLanguageScopes) {
                try {
                    val language = TextMateLanguage.create(state.languageScopeName, true)
                    setEditorLanguage(language)
                } catch (e: Exception) {
                    LogCatcher.e("EditorViewModel", "设置语言失败", e)
                }
            }

            // 初始化光标
            setSelection(0, 0)
            ensureSelectionVisible()

            // 监听内容变化
            text.addContentListener(object : ContentListener {
                override fun beforeReplace(content: Content) {}
                override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, inserted: CharSequence) {
                    val newText = content.toString()
                    if (state.content != newText) state.content = newText
                }
                override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deleted: CharSequence) {
                    val newText = content.toString()
                    if (state.content != newText) state.content = newText
                }
            })
        }

        // 存入缓存
        editorInstances[filePath] = editor
        return editor
    }

    override fun onCleared() {
        super.onCleared()
        editorInstances.values.forEach {
            try { it.release() } catch (e: Exception) { e.printStackTrace() }
        }
        editorInstances.clear()
    }

    fun loadInitialFile(projectPath: String) {
        if (projectPath != currentProjectPath) {
            closeAllFiles()
            currentProjectPath = projectPath
            val indexFile = File(projectPath, "index.html")
            if (indexFile.exists() && indexFile.isFile && indexFile.canRead()) {
                openFile(indexFile)
            }
        }
    }

    private var lastSearchQuery = ""
    private var isIgnoreCase = true // 默认忽略大小写
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
    // EditorViewModel.kt 中的修改
    fun searchNext() {
        val editor = getActiveEditor() ?: return
        // 关键：只有在已经有查询词且搜索结果不为空时才跳转
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
            // 传入当前配置的缩进宽度
            val formattedCode = com.web.webide.core.utils.CodeFormatter.format(originalCode, extension, editorConfig.tabWidth)

            if (formattedCode != originalCode) {
                withContext(Dispatchers.Main) {
                    val text = editor.text
                    // ...
                    val lastLine = text.lineCount - 1
                    // 修复 getColumnCount 可能越界的问题
                    val lastColumn = if(lastLine >= 0) text.getColumnCount(lastLine) else 0
                    text.replace(0, 0, lastLine, lastColumn, formattedCode)
                    activeFile.content = formattedCode
                }
            }
            isFormatting = false // 别忘了重置标志位
        }
    }

    fun jumpToLine(lineStr: String) {
        val line = lineStr.toIntOrNull() ?: return
        val editor = getActiveEditor() ?: return
        val totalLines = editor.text.lineCount

        // 限制范围
        val targetLine = (line - 1).coerceIn(0, totalLines - 1)

        // 执行跳转
        editor.setSelection(targetLine, 0)
        editor.ensureSelectionVisible()

    }

    // 2. 插入文本 (用于调色板)
    fun insertText(text: String) {
        val editor = getActiveEditor() ?: return
        val cursor = editor.cursor
        editor.text.insert(cursor.leftLine, cursor.leftColumn, text)
    }

    // 3. 创建文件或文件夹
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
        openFiles.getOrNull(activeFileIndex)?.let { state ->
            editorInstances[state.file.absolutePath]?.undo()
        }
    }

    fun redo() {
        openFiles.getOrNull(activeFileIndex)?.let { state ->
            editorInstances[state.file.absolutePath]?.redo()
        }
    }

    fun insertSymbol(symbol: String) {
        openFiles.getOrNull(activeFileIndex)?.let { state ->
            editorInstances[state.file.absolutePath]?.let { editor ->
                val processedSymbol = if (symbol == "Tab") "\t" else symbol

                // 修改点：使用 editor.insertText 而不是 editor.text.insert
                // 1. 自动处理选中状态：如果有选中内容，会先被替换
                // 2. 第二个参数是光标移动的偏移量，传入 length 表示光标停在插入符号的后面
                editor.insertText(processedSymbol, processedSymbol.length)
            }
        }
    }

    fun changeActiveFileIndex(index: Int) {
        if (index in openFiles.indices) activeFileIndex = index
    }

    fun closeAllFiles() {
        openFiles.forEach { state -> editorInstances.remove(state.file.absolutePath)?.release() }
        openFiles = emptyList()
        activeFileIndex = -1
    }

    fun closeOtherFiles(indexToKeep: Int) {
        if (indexToKeep !in openFiles.indices) return
        openFiles.forEachIndexed { index, state ->
            if (index != indexToKeep) editorInstances.remove(state.file.absolutePath)?.release()
        }
        openFiles = listOf(openFiles[indexToKeep])
        activeFileIndex = 0
    }

    fun closeFile(indexToClose: Int) {
        if (indexToClose !in openFiles.indices) return
        openFiles.getOrNull(indexToClose)?.file?.absolutePath?.let { path ->
            editorInstances.remove(path)?.release()
        }
        openFiles = openFiles.toMutableList().also { it.removeAt(indexToClose) }
        if (openFiles.isEmpty()) {
            activeFileIndex = -1
        } else if (activeFileIndex >= indexToClose) {
            activeFileIndex = (activeFileIndex - 1).coerceAtLeast(0)
        }
    }


    private fun getLanguageScope(extension: String): String = when (extension.lowercase()) {
        "html", "htm" -> "text.html.basic"  //text.html.basic
        "css" -> "source.css"
        "js" -> "source.js"
        "json" , "JSON" -> "source.js"
        else -> "text.plain"
    }
}