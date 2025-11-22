package com.web.webide.ui.editor.components

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.web.webide.ui.ThemeViewModel
import com.web.webide.ui.ThemeViewModelFactory
import com.web.webide.ui.editor.viewmodel.CodeEditorState
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource
import kotlinx.coroutines.launch

@Composable
fun CodeEditorView(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    viewModel: EditorViewModel
) {
    val context = LocalContext.current
    var isEditorReady by remember { mutableStateOf(false) }
    
    // ✅ 获取主题 ViewModel
    val themeViewModel: ThemeViewModel = viewModel(
        factory = ThemeViewModelFactory(context)
    )
    val themeState by themeViewModel.themeState.collectAsState()
    
    // ✅ 确定最终的暗色模式状态
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeState.selectedModeIndex) {
        0 -> systemDark  // 跟随系统
        1 -> false       // 强制浅色
        2 -> true        // 强制深色
        else -> systemDark
    }
    
    // ✅ 确定主题色
    val seedColor = if (themeState.isCustomTheme) {
        themeState.customColor
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    // ✅ 当主题色或明暗模式变化时，更新编辑器配色
    LaunchedEffect(seedColor, isDark, isEditorReady) {
        if (isEditorReady) {
            viewModel.updateEditorTheme(seedColor, isDark)
        }
    }

    // 获取编辑器实例
    val editor = remember(state.file.absolutePath) {
        viewModel.getOrCreateEditor(context, state)
    }

    // 等待 TextMate 初始化完成并编辑器准备好
    LaunchedEffect(state.file.absolutePath) {
        if (!TextMateInitializer.isReady()) {
            TextMateInitializer.initialize(context) {
                isEditorReady = true
                viewModel.updateEditorTheme(seedColor, isDark)
            }
        } else {
            isEditorReady = true
            viewModel.updateEditorTheme(seedColor, isDark)
        }
    }

    DisposableEffect(state.file.absolutePath) {
        onDispose {
            // ViewModel 管理编辑器生命周期
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isEditorReady) {
            AndroidView(
                factory = { factoryContext ->
                    (editor.parent as? ViewGroup)?.removeView(editor)
                    editor
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.text.toString() != state.content) {
                        val cursor = view.cursor
                        val cursorLine = cursor.leftLine
                        val cursorColumn = cursor.leftColumn
                        view.setText(state.content)
                        try {
                            val lineCount = view.text.lineCount
                            val targetLine = cursorLine.coerceIn(0, lineCount - 1)
                            val lineLength = view.text.getColumnCount(targetLine)
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
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "正在初始化编辑器...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

object TextMateInitializer {
    private var isInitialized = false
    private var isInitializing = false
    private val callbacks = mutableListOf<() -> Unit>()
    
    @Synchronized
    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        if (isInitializing) {
            onComplete?.let { callbacks.add(it) }
            return
        }
        isInitializing = true
        onComplete?.let { callbacks.add(it) }
        
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val appContext = context.applicationContext
                val assetsFileResolver = AssetsFileResolver(appContext.assets)
                FileProviderRegistry.getInstance().addFileProvider(assetsFileResolver)
                
                val themeRegistry = ThemeRegistry.getInstance()
                val themeName = "quietlight"
                val themePath = "textmate/$themeName.json"
                
                FileProviderRegistry.getInstance().tryGetInputStream(themePath)?.use { inputStream ->
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(inputStream, themePath, null),
                            themeName
                        )
                    )
                    themeRegistry.setTheme(themeName)
                }
                
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
                
                synchronized(this) {
                    isInitialized = true
                    isInitializing = false
                    callbacks.forEach { it.invoke() }
                    callbacks.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                synchronized(this) {
                    isInitializing = false
                    callbacks.clear()
                }
            }
        }
    }
    
    fun isReady() = isInitialized
    
    fun preloadCommonLanguages(context: Context) {
        if (!isInitialized && !isInitializing) {
            initialize(context)
        }
    }
}