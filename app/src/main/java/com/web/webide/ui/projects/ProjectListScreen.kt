package com.web.webide.ui.projects

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.web.webide.core.utils.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- 常量定义 ---
private const val PREFS_NAME = "project_prefs"
private const val KEY_PINNED_PROJECTS = "pinned_projects"
private const val KEY_SORT_ORDER = "sort_order"
private const val KEY_SEARCH_HISTORY = "search_history" // 新增：搜索记录Key

// --- 数据模型 ---
data class ProjectItem(
    val name: String,
    val lastModified: Long
)

// --- 排序规则枚举 ---
enum class SortOrder(val displayName: String) {
    NAME_ASC("名称 (A-Z)"),
    NAME_DESC("名称 (Z-A)"),
    DATE_NEWEST("修改时间 (最新)"),
    DATE_OLDEST("修改时间 (最早)");

    companion object {
        fun fromOrdinal(ordinal: Int): SortOrder = entries.getOrElse(ordinal) { NAME_ASC }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val projectDirPath = WorkspaceManager.getWorkspacePath(context)
    val projectDir = File(projectDirPath)

    // --- 核心数据状态 ---
    var projectList by remember { mutableStateOf<List<ProjectItem>>(emptyList()) }
    var pinnedProjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentSortOrder by remember { mutableStateOf(SortOrder.NAME_ASC) }

    // --- 搜索相关状态 ---
    var isSearchActive by remember { mutableStateOf(false) } // 是否处于搜索模式
    var searchQuery by remember { mutableStateOf("") }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }

    // --- UI 交互状态 ---
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // FAB 逻辑：搜索时不显示，非搜索模式下根据滑动显示
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // 按下物理返回键时，如果是搜索模式，则退出搜索模式
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    // --- 持久化工具函数 ---
    fun saveSearchHistory(query: String) {
        if (query.isBlank()) return
        val newHistory = (listOf(query) + searchHistory)
            .distinct() // 去重
            .take(10)   // 只保留最近10条

        searchHistory = newHistory
        // 简单持久化：用换行符分隔保存 (假设项目名不含换行符)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEARCH_HISTORY, newHistory.joinToString("\n")).apply()
    }

    fun deleteHistoryItem(item: String) {
        val newHistory = searchHistory - item
        searchHistory = newHistory
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEARCH_HISTORY, newHistory.joinToString("\n")).apply()
    }

    fun loadHistory() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (historyStr.isNotEmpty()) {
            searchHistory = historyStr.split("\n")
        }
    }

    // --- 列表刷新逻辑 ---
    fun refreshList() {
        scope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPinned = prefs.getStringSet(KEY_PINNED_PROJECTS, emptySet()) ?: emptySet()
            val sortOrdinal = prefs.getInt(KEY_SORT_ORDER, SortOrder.NAME_ASC.ordinal)
            val sortOrder = SortOrder.fromOrdinal(sortOrdinal)

            withContext(Dispatchers.Main) {
                pinnedProjects = savedPinned
                currentSortOrder = sortOrder
            }

            if (projectDir.exists() && projectDir.isDirectory) {
                val rawFiles = projectDir.listFiles { file ->
                    file.isDirectory && file.name != "logs"
                } ?: emptyArray()

                val items = rawFiles.map {
                    ProjectItem(name = it.name, lastModified = it.lastModified())
                }

                // 先排序
                val sortedList = items.sortedWith(
                    compareByDescending<ProjectItem> { it.name in savedPinned }
                        .then(
                            when (sortOrder) {
                                SortOrder.NAME_ASC -> compareBy { it.name.lowercase() }
                                SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
                                SortOrder.DATE_NEWEST -> compareByDescending { it.lastModified }
                                SortOrder.DATE_OLDEST -> compareBy { it.lastModified }
                            }
                        )
                )
                withContext(Dispatchers.Main) { projectList = sortedList }
            } else {
                withContext(Dispatchers.Main) { projectList = emptyList() }
            }
        }
    }

    // --- 操作逻辑 ---
    fun togglePin(folderName: String) {
        val newPinned = pinnedProjects.toMutableSet()
        if (newPinned.contains(folderName)) newPinned.remove(folderName) else newPinned.add(folderName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_PINNED_PROJECTS, newPinned).apply()
        refreshList()
    }

    fun changeSortOrder(newOrder: SortOrder) {
        currentSortOrder = newOrder
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SORT_ORDER, newOrder.ordinal).apply()
        refreshList()
    }

    fun deleteProject(folderName: String) {
        scope.launch(Dispatchers.IO) {
            val targetDir = File(projectDir, folderName)
            val success = targetDir.deleteRecursively()
            withContext(Dispatchers.Main) {
                if (success) {
                    if (pinnedProjects.contains(folderName)) togglePin(folderName) else refreshList()
                    snackbarHostState.showSnackbar("项目已删除")
                } else {
                    snackbarHostState.showSnackbar("删除失败，请检查权限")
                }
            }
        }
    }

    // 初始化
    LaunchedEffect(projectDir) {
        refreshList()
        loadHistory()
    }

    // --- 计算当前显示的列表 ---
    // 如果在搜索模式且有输入，过滤列表；否则显示完整排序列表
    val displayList = remember(projectList, isSearchActive, searchQuery) {
        if (isSearchActive && searchQuery.isNotEmpty()) {
            projectList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            projectList
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // 根据状态切换 TopBar
            if (isSearchActive) {
                // --- 搜索模式 TopBar ---
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索项目...", style = MaterialTheme.typography.bodyLarge) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                saveSearchHistory(searchQuery)
                                focusManager.clearFocus()
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出搜索")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    }
                )
            } else {
                // --- 常规模式 TopBar ---
                LargeTopAppBar(
                    title = { Text("Web Projects") },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // 搜索按钮
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        // 排序按钮
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "排序")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (order == currentSortOrder) {
                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                } else {
                                                    Spacer(Modifier.width(24.dp))
                                                }
                                                Text(order.displayName)
                                            }
                                        },
                                        onClick = {
                                            changeSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, "设置")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // 搜索时不显示 FAB
            AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate("new_project") },
                    icon = { Icon(Icons.Default.Add, "新建项目") },
                    text = { Text("新建项目") },
                    expanded = isFabExpanded
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // --- 场景 1：搜索模式且无输入 -> 显示历史记录 ---
            if (isSearchActive && searchQuery.isEmpty()) {
                if (searchHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无搜索记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Text(
                                "搜索历史",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(searchHistory) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = historyItem // 填充搜索
                                        // 可选：点击历史立即触发搜索并移动到历史首位
                                        saveSearchHistory(historyItem)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(historyItem, style = MaterialTheme.typography.bodyLarge)
                                }
                                IconButton(
                                    onClick = { deleteHistoryItem(historyItem) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除记录",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        item {
                            TextButton(
                                onClick = {
                                    searchHistory = emptyList()
                                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().remove(KEY_SEARCH_HISTORY).apply()
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("清空历史记录", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
            // --- 场景 2：显示项目列表 (常规 或 搜索过滤中) ---
            else {
                if (displayList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isSearchActive) "没有找到匹配的项目" else "没有找到项目",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = if (isSearchActive) 16.dp else 100.dp // 搜索时不需要底部留白给 FAB
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayList, key = { it.name }) { item ->
                            ProjectCard(
                                modifier = Modifier.animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ),
                                folderName = item.name,
                                isPinned = pinnedProjects.contains(item.name),
                                onClick = {
                                    // 点击项目时，如果需要保存此次搜索记录，可以在这里调用 saveSearchHistory(searchQuery)
                                    navController.navigate("code_edit/${item.name}")
                                },
                                onTogglePin = { togglePin(item.name) },
                                onDelete = {
                                    projectToDelete = item.name
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // ... (弹窗代码保持不变) ...
        if (showDeleteDialog && projectToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除项目") },
                text = { Text("确定要删除 \"$projectToDelete\" 吗？此操作无法撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteProject(projectToDelete!!)
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

// ... (ProjectCard 组件代码与上一次回复完全相同，无需更改) ...
@Composable
fun ProjectCard(
    modifier: Modifier = Modifier,
    folderName: String,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(85.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "更多选项")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isPinned) "取消置顶" else "置顶项目") },
                            leadingIcon = {
                                Icon(if (isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignTop, null)
                            },
                            onClick = {
                                menuExpanded = false
                                onTogglePin()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除项目", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(16.dp)
                        .rotate(45f)
                )
            }
        }
    }
}