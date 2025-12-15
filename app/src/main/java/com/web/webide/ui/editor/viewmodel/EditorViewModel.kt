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

    // 权限检查
    private var hasPermissions = false
    private lateinit var appContext: Context

    fun initializePermissions(context: Context) {
        appContext = context.applicationContext
        hasPermissions = PermissionManager.hasRequiredPermissions(appContext)
    }

    private fun checkPermissions(operation: String): Boolean {
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

    suspend fun saveAllModifiedFiles(context: Context, snackbarHostState: SnackbarHostState) {
        withContext(Dispatchers.IO) {
            val modifiedFiles = openFiles.filter { it.isModified }
            if (modifiedFiles.isEmpty()) return@withContext

            if (!checkPermissions("保存文件")) {
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
                    } catch (e: Exception) {
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
                val startLine = editor.cursor.leftLine
                val startColumn = editor.cursor.leftColumn
                val processedSymbol = if (symbol == "Tab") "\t" else symbol
                editor.text.insert(startLine, startColumn, processedSymbol)
                editor.setSelection(startLine, startColumn + processedSymbol.length)
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