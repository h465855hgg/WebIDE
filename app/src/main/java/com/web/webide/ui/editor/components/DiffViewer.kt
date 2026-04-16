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

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.web.webide.R
import com.web.webide.ui.editor.EditorColorSchemeManager
import com.web.webide.ui.editor.viewmodel.DiffEditorState
import com.web.webide.ui.editor.viewmodel.DiffViewMode
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.event.TextSizeChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import java.util.LinkedList
import kotlin.math.max

// ==========================================
// 1. 核心逻辑：Wake + Lock 同步器 (Ultimate Fix)
// ==========================================
// 核心修正版 Synchronizer
// ==========================================
// 1. 核心逻辑：纯净事件镜像 (Butter Smooth Version)
// ==========================================

class ScrollSynchronizer {
    private var leftEditor: CodeEditor? = null
    private var rightEditor: CodeEditor? = null
    private val receipts = ArrayList<SubscriptionReceipt<*>>()

    // 0 = 无/未知, 1 = 左控右, 2 = 右控左
    // 默认为 0，只有当用户触摸某一边时，该边才成为 Driver
    private var activeDriver = 0

    fun setEditors(left: CodeEditor?, right: CodeEditor?) {
        if (this.leftEditor === left && this.rightEditor === right) return
        unbind()
        this.leftEditor = left
        this.rightEditor = right
        bindEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindEvents() {
        val left = leftEditor ?: return
        val right = rightEditor ?: return

        // 开启缩放功能
        left.isScalable = true
        right.isScalable = true

        // 1. Touch 监听：确立 Driver 身份，并终止对方的惯性
        val touchListener = { id: Int, other: CodeEditor ->
            android.view.View.OnTouchListener { _, event ->
                // ACTION_DOWN 确立主控方
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    activeDriver = id
                    // 立即停止对方的 Scroller，防止“两个物理引擎打架”
                    // 使用 eventHandler.scroller 以确保访问到正确的内部滚动器
                    val scroller = other.eventHandler.scroller
                    if (!scroller.isFinished) {
                        scroller.forceFinished(true)
                    }
                }
                false // 不消费事件，交给 Editor 内部处理
            }
        }

        left.setOnTouchListener(touchListener(1, right))
        right.setOnTouchListener(touchListener(2, left))

        // 2. Scroll 监听：Master -> Slave 绝对坐标同步
        // 修正：使用 scroller.startScroll() 而非简单的 scrollTo()
        // 原因：用户反馈“editor本身在移动”，这通常是因为直接调用 View.scrollTo() 导致
        // View 的视口位置与 Editor 内部 Scroller (OverScroller) 的状态不同步。
        // 当用户随后触摸 Slave 编辑器时，Scroller 会从旧位置（通常是 0）开始，导致跳变。
        // 使用 startScroll(x, y, 0, 0, 0) 可以同时更新 View 位置和 Scroller 状态，
        // 实现真正的“内部滚动操作”同步。
        val scrollListener = { id: Int, target: CodeEditor ->
            android.view.View.OnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                if (activeDriver == id) {
                    val scroller = target.eventHandler.scroller
                    // 只有当位置真正改变时才同步，避免循环调用（虽然 activeDriver 已防护）
                    // 注意：这里我们强制同步 Scroller 的状态
                    if (scroller.currX != scrollX || scroller.currY != scrollY) {
                        // duration = 0 表示瞬时跳转，但更新了 Scroller 内部状态
                        scroller.startScroll(scrollX, scrollY, 0, 0, 0)
                        // 通知编辑器已滚动，触发滚动条绘制等副作用
                        target.eventHandler.notifyScrolled()
                    }
                }
            }
        }

        left.setOnScrollChangeListener(scrollListener(1, right))
        right.setOnScrollChangeListener(scrollListener(2, left))

        // 3. Zoom 监听：同步缩放比例
        val zoomListener = { id: Int, target: CodeEditor ->
            io.github.rosemoe.sora.event.EventReceiver<TextSizeChangeEvent> { event, _ ->
                if (activeDriver == id) {
                    if (target.textSizePx != event.newTextSize) {
                        target.setTextSizePx(event.newTextSize)
                    }
                }
            }
        }

        receipts.add(left.subscribeEvent(TextSizeChangeEvent::class.java, zoomListener(1, right)))
        receipts.add(right.subscribeEvent(TextSizeChangeEvent::class.java, zoomListener(2, left)))
    }

    private fun unbind() {
        leftEditor?.setOnTouchListener(null)
        leftEditor?.setOnScrollChangeListener(null)
        rightEditor?.setOnTouchListener(null)
        rightEditor?.setOnScrollChangeListener(null)
        
        receipts.forEach { it.unsubscribe() }
        receipts.clear()
        
        // SoraEditor 的事件订阅系统没有直接的 "unsubscribeAll"，但我们重新创建实例时会丢弃旧对象
        // 如果要严谨，应该保存 SubscriptionReceipt 并取消订阅，但这里我们简化处理，
        // 依赖 Garbage Collection，因为 SubscriptionReceipt 是强引用
        // 更好的做法是保存 receipts 列表并在 unbind 时 unsubscribe
        // 但在这个简单的 Synchronizer 生命周期中，直接置空引用通常足够，
        // 除非 CodeEditor 也是长生命周期的（在这个 Compose 场景中是每次重组可能变化）
    }
}

// ==========================================
// 2. 数据结构
// ==========================================
data class AlignedDiffResult(
    val leftContent: String,
    val rightContent: String,
    val leftHighlights: HighlightTextContainer,
    val rightHighlights: HighlightTextContainer,
    val adds: Int,
    val deletes: Int
)

// ==========================================
// 3. UI 组件
// ==========================================

@Composable
fun DiffViewer(
    viewModel: EditorViewModel,
    state: DiffEditorState,
    modifier: Modifier = Modifier
) {
    // 异步计算差异
    // 使用 state 作为 key，确保切换文件时重置，但修改内容时不重置 (防止闪烁)
    var diffData by remember(state) {
        mutableStateOf<AlignedDiffResult?>(null)
    }

    LaunchedEffect(state.originalContent, state.currentContent) {
        // 防抖：避免每次击键都重新计算差异，导致左侧编辑器频繁跳动
        // 首次加载不需要防抖
        if (diffData != null) {
            kotlinx.coroutines.delay(500)
        }
        
        withContext(Dispatchers.Default) {
            val newData = DiffAligner.align(state.originalContent, state.currentContent)
            // 只有当差异真正变化时才更新 UI
            if (diffData != newData) {
                diffData = newData
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        DiffToolbar(state, diffData)

        if (diffData == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text(stringResource(R.string.diff_calculating), modifier = Modifier.padding(top = 48.dp))
            }
        } else {
            val data = diffData!!
            if (state.viewMode == DiffViewMode.UNIFIED) {
                UnifiedDiffView(state, viewModel, data)
            } else {
                SplitDiffView(state, viewModel, data)
            }
        }
    }
}

@Composable
fun DiffToolbar(state: DiffEditorState, data: AlignedDiffResult?) {
    val added = data?.adds ?: 0
    val deleted = data?.deletes ?: 0

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().height(42.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = state.file.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.width(16.dp))
            if (added > 0) {
                Text("+$added", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
            }
            if (deleted > 0) {
                Text("-$deleted", color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            
            IconButton(onClick = { state.viewMode = DiffViewMode.SPLIT }) {
                Icon(
                    Icons.Default.ViewColumn, stringResource(R.string.diff_side_by_side),
                    tint = if (state.viewMode == DiffViewMode.SPLIT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { state.viewMode = DiffViewMode.SPLIT_VERTICAL }) {
                Icon(
                    Icons.Filled.ViewAgenda, stringResource(R.string.diff_top_bottom),
                    tint = if (state.viewMode == DiffViewMode.SPLIT_VERTICAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { state.viewMode = DiffViewMode.UNIFIED }) {
                Icon(
                    Icons.AutoMirrored.Filled.ViewList, stringResource(R.string.diff_unified),
                    tint = if (state.viewMode == DiffViewMode.UNIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SplitDiffView(
    state: DiffEditorState,
    viewModel: EditorViewModel,
    data: AlignedDiffResult
) {
    // 保持 Synchronizer 实例
    val synchronizer = remember { ScrollSynchronizer() }

    // 使用 ref 引用编辑器，避免 Compose 重组导致对象丢失
    var leftEditorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var rightEditorRef by remember { mutableStateOf<CodeEditor?>(null) }

    // 【重要】当编辑器实例变化时，重新绑定
    DisposableEffect(leftEditorRef, rightEditorRef) {
        if (leftEditorRef != null && rightEditorRef != null) {
            synchronizer.setEditors(leftEditorRef, rightEditorRef)
        }
        onDispose {
            // 组件销毁时可以在这里做清理，或者由 Synchronizer 内部处理
            // synchronizer.unbind()
        }
    }

    if (state.viewMode == DiffViewMode.SPLIT_VERTICAL) {
        // 上下分栏模式
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                DiffHeader(stringResource(R.string.diff_head), Color(0xFFD32F2F))
                DiffEditorInstance(
                    content = data.leftContent,
                    highlights = data.leftHighlights,
                    fileName = state.file.name,
                    viewModel = viewModel,
                    readOnly = true,
                    onEditorCreated = { leftEditorRef = it }
                )
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                DiffHeader(stringResource(R.string.diff_working), Color(0xFF388E3C))
                DiffEditorInstance(
                    content = data.rightContent,
                    highlights = data.rightHighlights,
                    fileName = state.file.name,
                    viewModel = viewModel,
                    readOnly = false,
                    onEditorCreated = { 
                        rightEditorRef = it
                        state.activeDiffEditor = it
                    },
                    onContentChanged = { newContent ->
                        viewModel.updateDiffContent(state, newContent)
                    }
                )
            }
        }
    } else {
        // 左右分栏模式
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                DiffHeader(stringResource(R.string.diff_head), Color(0xFFD32F2F))
                DiffEditorInstance(
                    content = data.leftContent,
                    highlights = data.leftHighlights,
                    fileName = state.file.name,
                    viewModel = viewModel,
                    readOnly = true,
                    onEditorCreated = { leftEditorRef = it }
                )
            }
            VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                DiffHeader(stringResource(R.string.diff_working), Color(0xFF388E3C))
                DiffEditorInstance(
                    content = data.rightContent,
                    highlights = data.rightHighlights,
                    fileName = state.file.name,
                    viewModel = viewModel,
                    readOnly = false,
                    onEditorCreated = { 
                        rightEditorRef = it
                        state.activeDiffEditor = it
                    },
                    onContentChanged = { newContent ->
                        viewModel.updateDiffContent(state, newContent)
                    }
                )
            }
        }
    }
}

@Composable
fun UnifiedDiffView(state: DiffEditorState, viewModel: EditorViewModel, data: AlignedDiffResult) {
    Column(modifier = Modifier.fillMaxSize()) {
        DiffEditorInstance(
            content = data.rightContent,
            highlights = data.rightHighlights,
            fileName = state.file.name,
            viewModel = viewModel,
            readOnly = false,
            onEditorCreated = { state.activeDiffEditor = it },
            onContentChanged = { newContent ->
                viewModel.updateDiffContent(state, newContent)
            }
        )
    }
}

@Composable
fun DiffHeader(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, MaterialTheme.shapes.small))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiffEditorInstance(
    content: String,
    highlights: HighlightTextContainer,
    fileName: String,
    viewModel: EditorViewModel,
    readOnly: Boolean,
    onEditorCreated: (CodeEditor) -> Unit,
    onContentChanged: ((String) -> Unit)? = null
) {
    val editorConfig = viewModel.editorConfig

    // 状态标志，防止无限循环
    // 使用 remember 保存一个 MutableState，在 AndroidView 内部访问
    val isUpdatingRef = remember { mutableStateOf(false) }
    // 记录上一次用户输入的时间，用于防抖
    val lastUserInputTime = remember { mutableLongStateOf(0L) }

    // 修复：使用 rememberUpdatedState 确保回调始终是最新的
    val currentOnContentChanged by rememberUpdatedState(onContentChanged)

    val currentColorScheme = MaterialTheme.colorScheme
    AndroidView(
        factory = { ctx ->
            CodeEditor(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // --- 基础 ---
                isEditable = !readOnly
                isFocusable = true // 必须开启
                isHighlightCurrentLine = true // 始终显示当前行高亮，即使在只读模式下

                // Remove zoom limits
                setScaleTextSizes(2f, 100f)


                colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, android.graphics.Color.TRANSPARENT) // 去掉背景遮罩
                colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, android.graphics.Color.TRANSPARENT)     // 去掉外框


                // --- 核心优化 ---
                // 1. 关闭换行：保证每一行的“高度”绝对一致，防止左右行高错位
                isWordwrap = false

                // 2. 移除边缘光晕：防止同步时一边闪蓝光一边不闪，影响视觉流畅度
                overScrollMode = android.view.View.OVER_SCROLL_NEVER

                // 3. 字体强制等宽：这是对齐的基础
                typefaceText = android.graphics.Typeface.MONOSPACE
                typefaceLineNumber = android.graphics.Typeface.MONOSPACE

                // 4. 设置字号和Tab：必须完全一致
                tabWidth = editorConfig.tabWidth

                // 初始语言设置
                try {
                    viewModel.applyLanguageToEditor(this, java.io.File(fileName).extension)
                } catch (_: Exception) {}

                EditorColorSchemeManager.applyThemeColors(colorScheme, currentColorScheme)
                
                // 5. 监听内容变更
                text.addContentListener(object : ContentListener {
                    override fun beforeReplace(content: Content) {}
                    override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, inserted: CharSequence) {
                        if (!isUpdatingRef.value) {
                            lastUserInputTime.longValue = System.currentTimeMillis()
                            // 过滤掉 Diff 对齐用的占位符 (\u200B)
                            // 1. 先移除整行占位符 (防止产生空行)
                            // 2. 再移除残留的占位符 (防止用户编辑了占位行)
                            val cleanText = text.toString()
                                .replace("\u200B\n", "")
                                .replace("\u200B", "")
                            currentOnContentChanged?.invoke(cleanText)
                        }
                    }
                    override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deleted: CharSequence) {
                        if (!isUpdatingRef.value) {
                            lastUserInputTime.longValue = System.currentTimeMillis()
                            // 过滤掉 Diff 对齐用的占位符 (\u200B)
                            val cleanText = text.toString()
                                .replace("\u200B\n", "")
                                .replace("\u200B", "")
                            currentOnContentChanged?.invoke(cleanText)
                        }
                    }
                })

                onEditorCreated(this)
            }
        },
        update = { editor ->
            // 确保每次重组时都更新主题色，以响应系统主题变化
            EditorColorSchemeManager.applyThemeColors(editor.colorScheme, currentColorScheme)

            // 确保语言设置正确 (Fix: 防止重组后语言丢失或未更新)
            try {
                val currentExt = java.io.File(fileName).extension
                // 这里可以优化：检查当前 editorLanguage 是否匹配，不匹配再设置
                // 但 EditorViewModel.applyLanguageToEditor 内部有缓存/检查机制，直接调用通常安全
                viewModel.applyLanguageToEditor(editor, currentExt)
            } catch (_: Exception) {}

            if (editor.isEditable != !readOnly) {
                editor.isEditable = !readOnly
            }

            // 确保 WordWrap 始终关闭 (Fix: 防止左侧自动换行)
            if (editor.isWordwrap) {
                editor.isWordwrap = false
            }

            // 只有内容真变了才 Set，防止重置位置
            val contentChanged = editor.text.toString() != content
            // 防抖：如果用户最近在输入（1000ms内），则不要强制覆盖内容，除非内容差异巨大（这里简化为只看时间）
            // 注意：这会导致对齐暂时失效，但能保证输入流畅和保存成功
            val isUserTyping = (System.currentTimeMillis() - lastUserInputTime.longValue) < 1000
            
            // 如果是只读模式，或者是第一次加载，或者不是用户正在输入，则更新
            if (contentChanged && (readOnly || !isUserTyping)) {
                isUpdatingRef.value = true
                try {
                    val cursor = editor.cursor
                    val line = cursor.leftLine
                    val column = cursor.leftColumn
                    val scroller = editor.eventHandler.scroller
                    val scrollX = scroller.currX
                    val scrollY = scroller.currY
                    
                    editor.setText(content)
                    
                    // 尝试恢复光标位置
                    if (line < editor.text.lineCount) {
                         editor.setSelection(line, column.coerceAtMost(editor.text.getColumnCount(line)))
                    }
                    
                    // 恢复滚动位置 (Fix: 防止左侧编辑器跳动)
                    if (!scroller.isFinished) scroller.forceFinished(true)
                    scroller.startScroll(scrollX, scrollY, 0, 0, 0)
                    editor.eventHandler.notifyScrolled()
                } finally {
                    isUpdatingRef.value = false
                }
            }
            
            // 确保高亮始终同步，修复切换模式后的高亮偏移和丢失问题
            if (contentChanged || editor.highlightTexts != highlights) {
                editor.highlightTexts = highlights
                editor.postInvalidate()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ==========================================
// 4. Diff 算法逻辑 (LCS + Padding)
// ==========================================
object DiffAligner {
    private const val PHANTOM_TEXT = "\u200B\n"

    private data class DiffLine(
        val text: String,
        val type: LineType,
        val originalText: String? = null
    )

    enum class LineType { NORMAL, ADD, DELETE, MODIFY }

    private object AddColor : ResolvableColor {
        override fun resolve(colorScheme: EditorColorScheme) = EditorColorSchemeManager.getDiffAddColor(colorScheme)
    }

    private object DeleteColor : ResolvableColor {
        override fun resolve(colorScheme: EditorColorScheme) = EditorColorSchemeManager.getDiffDeleteColor(colorScheme)
    }

    private object AddWordColor : ResolvableColor {
        override fun resolve(colorScheme: EditorColorScheme) = EditorColorSchemeManager.getDiffAddWordColor(colorScheme)
    }

    private object DeleteWordColor : ResolvableColor {
        override fun resolve(colorScheme: EditorColorScheme) = EditorColorSchemeManager.getDiffDeleteWordColor(colorScheme)
    }

    fun align(oldText: String, newText: String): AlignedDiffResult {
        val oldSafe = oldText.ifEmpty { "" }
        val newSafe = newText.ifEmpty { "" }

        val oldLines = oldSafe.lines()
        val newLines = newSafe.lines()

        val m = oldLines.size
        val n = newLines.size

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val leftStack = LinkedList<DiffLine>()
        val rightStack = LinkedList<DiffLine>()

        var i = m
        var j = n
        var adds = 0
        var deletes = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1]) {
                // Exact match
                leftStack.push(DiffLine(oldLines[i - 1] + "\n", LineType.NORMAL))
                rightStack.push(DiffLine(newLines[j - 1] + "\n", LineType.NORMAL))
                i--
                j--
            } else if (i > 0 && j > 0 && isSimilar(oldLines[i - 1], newLines[j - 1])) {
                // Similar -> Modify (Align them)
                leftStack.push(DiffLine(oldLines[i - 1] + "\n", LineType.MODIFY, newLines[j - 1]))
                rightStack.push(DiffLine(newLines[j - 1] + "\n", LineType.MODIFY, oldLines[i - 1]))
                deletes++
                adds++
                i--
                j--
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                // Add (in Right) -> Phantom in Left
                leftStack.push(DiffLine(PHANTOM_TEXT, LineType.NORMAL))
                rightStack.push(DiffLine(newLines[j - 1] + "\n", LineType.ADD))
                adds++
                j--
            } else {
                // Delete (in Left) -> Phantom in Right
                leftStack.push(DiffLine(oldLines[i - 1] + "\n", LineType.DELETE))
                rightStack.push(DiffLine(PHANTOM_TEXT, LineType.NORMAL))
                deletes++
                i--
            }
        }

        val leftBuilder = StringBuilder()
        val rightBuilder = StringBuilder()
        val leftContainer = HighlightTextContainer()
        val rightContainer = HighlightTextContainer()

        processStack(leftStack, leftBuilder, leftContainer, true)
        processStack(rightStack, rightBuilder, rightContainer, false)

        // Trim last newline if necessary
        if (leftBuilder.isNotEmpty() && leftBuilder.last() == '\n') leftBuilder.deleteCharAt(leftBuilder.length - 1)
        if (rightBuilder.isNotEmpty() && rightBuilder.last() == '\n') rightBuilder.deleteCharAt(rightBuilder.length - 1)

        return AlignedDiffResult(
            leftBuilder.toString(),
            rightBuilder.toString(),
            leftContainer,
            rightContainer,
            adds,
            deletes
        )
    }

    private fun isSimilar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return true
        
        val prefix = commonPrefixLength(a, b)
        val suffix = commonSuffixLength(a, b, prefix)
        
        // If more than 40% matches, consider it a modification
        return (prefix + suffix).toFloat() / maxLen > 0.4f
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val n = kotlin.math.min(a.length, b.length)
        for (i in 0 until n) {
            if (a[i] != b[i]) return i
        }
        return n
    }

    private fun commonSuffixLength(a: String, b: String, offset: Int): Int {
        val n = kotlin.math.min(a.length, b.length) - offset
        if (n <= 0) return 0
        for (i in 1..n) {
            if (a[a.length - i] != b[b.length - i]) return i - 1
        }
        return n
    }

    private fun processStack(
        stack: LinkedList<DiffLine>,
        builder: StringBuilder,
        container: HighlightTextContainer,
        isLeft: Boolean
    ) {
        var currentLine = 0
        stack.forEach { line ->
            val text = line.text
            builder.append(text)
            
            if (line.type != LineType.NORMAL) {
                val color = when (line.type) {
                    LineType.ADD -> AddColor
                    LineType.DELETE -> DeleteColor
                    LineType.MODIFY -> if (isLeft) DeleteColor else AddColor
                    else -> null
                }
                
                if (color != null) {
                    // Highlight the entire line
                    container.add(
                        HighlightTextContainer.HighlightText(
                            currentLine, 0,
                            currentLine, text.length,
                            color
                        )
                    )
                }

                // Highlight Word Diff for Modify
                if (line.type == LineType.MODIFY && line.originalText != null) {
                    val otherStr = line.originalText
                    val thisStr = text.removeSuffix("\n")
                    
                    val prefix = commonPrefixLength(thisStr, otherStr)
                    val suffix = commonSuffixLength(thisStr, otherStr, prefix)
                    
                    val start = prefix
                    val end = thisStr.length - suffix
                    
                    if (end > start) {
                        val wordColor = if (isLeft) DeleteWordColor else AddWordColor
                        container.add(
                            HighlightTextContainer.HighlightText(
                                currentLine, start,
                                currentLine, end,
                                wordColor
                            )
                        )
                    }
                }
            }

            if (text.endsWith("\n")) {
                currentLine++
            }
        }
    }
}

