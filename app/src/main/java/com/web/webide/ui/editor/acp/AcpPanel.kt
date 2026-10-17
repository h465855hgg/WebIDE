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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.web.webide.R
import com.web.webide.ui.editor.viewmodel.EditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * ACP 面板：替代原先的 AI 聊天面板，改为驱动外部 ACP Agent
 * （Claude Code / Gemini CLI / Qwen Code 等）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AcpPanel(
    onHide: () -> Unit,
    editorViewModel: EditorViewModel? = null,
    state: AcpPanelState = rememberAcpPanelState(),
    viewModel: AcpViewModel = viewModel()
) {
    var showSettings by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableIntStateOf(0) }
    var showSessions by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSkills by remember { mutableStateOf(false) }
    var showTasks by remember { mutableStateOf(false) }
    var showMcp by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var configMenu by remember { mutableStateOf<AcpConfigMenu?>(null) }
    var highlightedId by remember { mutableStateOf<String?>(null) }
    var fullDiff by remember { mutableStateOf<AcpToolContent.Diff?>(null) }
    var input by rememberSaveable(viewModel.sessionId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var inputFullscreen by remember { mutableStateOf(false) }
    var contextWarningDismissed by remember(viewModel.sessionId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 最后一条用户消息之后还没有 Agent 产出时，允许「重试」
    val retryableUserId = remember(viewModel.timeline.size, viewModel.timeline.lastOrNull()?.id) {
        val lastUserIndex = viewModel.timeline.indexOfLast { it is AcpUserMessage }
        if (lastUserIndex >= 0 &&
            viewModel.timeline.drop(lastUserIndex + 1)
                .none { it is AcpAgentMessage || it is AcpToolCallItem }
        ) {
            viewModel.timeline[lastUserIndex].id
        } else {
            null
        }
    }
    val canStartNewSession = viewModel.connectionState == AcpConnectionState.READY &&
        !viewModel.isPrompting

    LaunchedEffect(Unit) {
        viewModel.ensureWorkspaceLoaded()
    }

    LaunchedEffect(viewModel.timeline.size, viewModel.isPrompting) {
        if (viewModel.timeline.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.timeline.lastIndex)
        }
    }

    // 搜索跳转后高亮 2.4 秒，帮助定位
    LaunchedEffect(highlightedId) {
        val target = highlightedId ?: return@LaunchedEffect
        delay(2_400)
        if (highlightedId == target) highlightedId = null
    }

    // 连上后拉一次会话列表；切换/新建/分叉后也会刷新。
    LaunchedEffect(viewModel.connectionState) {
        if (viewModel.connectionState == AcpConnectionState.READY) {
            viewModel.refreshSessions()
        }
    }

    // 软键盘弹出时把面板顶到键盘上方并临时展开，否则浮层会被输入法盖住。
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val expanded = state.isMaximized || imeVisible
    val localContext = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.panelWidth, state.panelHeight, state.dockSide, state.fabSide, state.fabYFraction) {
        state.persist(localContext)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .imePadding(),
        contentAlignment = if (state.dockSide == AcpDockSide.RIGHT) {
            Alignment.BottomEnd
        } else {
            Alignment.BottomStart
        }
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight

        // 边距 / 尺寸 / 圆角统一走动画，避免最大化时生硬跳变
        val resizeSpec: AnimationSpec<Dp> = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        val margin by animateDpAsState(
            targetValue = if (expanded) 0.dp else 10.dp,
            animationSpec = resizeSpec,
            label = "acpMargin"
        )
        val panelW by animateDpAsState(
            targetValue = if (expanded) availableWidth else state.panelWidth.coerceAtMost(availableWidth - 20.dp),
            animationSpec = resizeSpec,
            label = "acpWidth"
        )
        val panelH by animateDpAsState(
            targetValue = if (expanded) availableHeight else state.panelHeight.coerceAtMost(availableHeight - 20.dp),
            animationSpec = resizeSpec,
            label = "acpHeight"
        )
        val corner by animateDpAsState(
            targetValue = if (expanded) 0.dp else 16.dp,
            animationSpec = resizeSpec,
            label = "acpCorner"
        )

        val fabOrigin = TransformOrigin(
            if (state.fabSide == AcpDockSide.RIGHT) 1f else 0f,
            state.fabYFraction.coerceIn(0.08f, 0.92f)
        )

        AnimatedVisibility(
            visible = !state.isCollapsed,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.5f,
                transformOrigin = fabOrigin,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.55f,
                transformOrigin = fabOrigin,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
        ) {
            Surface(
                modifier = Modifier
                    .padding(margin)
                    .width(panelW)
                    .height(panelH),
                shape = RoundedCornerShape(corner),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 10.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AcpHeader(
                        viewModel = viewModel,
                        state = state,
                        onNewSession = { viewModel.newSession() },
                        onOpenSessions = { showSessions = true },
                        onOpenSearch = { showSearch = true },
                        onOpenSettings = {
                            settingsTab = 0
                            showSettings = true
                        },
                        onOpenSkills = { showSkills = true },
                        onOpenMcp = { showMcp = true },
                        onHide = onHide
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    AcpTopStatusBar(
                        viewModel = viewModel,
                        onOpenStats = { showStats = true },
                        onOpenPermissions = {
                            settingsTab = 1
                            showSettings = true
                        }
                    )
                    if (viewModel.connectionState != AcpConnectionState.READY || viewModel.statusMessage != null) {
                        AcpStatusRow(viewModel)
                    }
                    if (viewModel.isReadOnlyReplay) {
                        AcpReplayBanner(
                            onResume = { viewModel.resumeLocalSession() },
                            onNewSession = { viewModel.newSession() },
                            canResume = viewModel.connectionState == AcpConnectionState.READY
                        )
                    }

                    AcpUndoBanner(viewModel)
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.timeline.isEmpty()) {
                            AcpEmptyState(
                                enabled = viewModel.connectionState == AcpConnectionState.READY,
                                onSuggestion = { text ->
                                    input = TextFieldValue(text)
                                }
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 12.dp,
                                    bottom = 12.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(viewModel.timeline, key = { it.id }) { item ->
                                    val highlighted = item.id == highlightedId
                                    val turnTop = when (item) {
                                        is AcpUserMessage -> 10.dp
                                        is AcpAgentMessage -> 8.dp
                                        else -> 0.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = turnTop)
                                            .then(
                                                if (highlighted) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            MaterialTheme.colorScheme.primaryContainer
                                                                .copy(alpha = 0.28f)
                                                        )
                                                        .padding(4.dp)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        AcpTimelineRow(
                                            item = item,
                                            retryableUserId = retryableUserId,
                                            canStartNewSession = canStartNewSession,
                                            onRetry = { viewModel.retryLastTurn() },
                                            onNewSessionFrom = { viewModel.startNewSessionWithText(it) },
                                            onOpenDiff = { fullDiff = it },
                                            onOpenFile = { path, line ->
                                                editorViewModel?.openFileAt(File(path), line)
                                                state.isCollapsed = true
                                            }
                                        )
                                    }
                                }
                                if (viewModel.isPrompting) {
                                    item(key = "acp-running") {
                                        AcpRunningRow()
                                    }
                                }
                            }

                            if (listState.canScrollForward) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            scope.launch {
                                                if (viewModel.timeline.isNotEmpty()) {
                                                    listState.animateScrollToItem(viewModel.timeline.lastIndex)
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.acp_action_scroll_bottom),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    viewModel.permissionPrompt?.let { prompt ->
                        AcpPermissionCard(
                            prompt = prompt,
                            approvalMode = viewModel.permissions.mode,
                            onSelect = { viewModel.respondPermission(it) },
                            onRememberAllow = {
                                viewModel.rememberPermission(prompt.kind, allow = true)
                                prompt.options.firstOrNull { it.kind.startsWith("allow_") }
                                    ?.let { viewModel.respondPermission(it.optionId) }
                            },
                            onRememberDeny = {
                                viewModel.rememberPermission(prompt.kind, allow = false)
                                prompt.options.firstOrNull { it.kind.startsWith("reject_") }
                                    ?.let { viewModel.respondPermission(it.optionId) }
                                    ?: viewModel.respondPermission(null)
                            }
                        )
                    }

                    if (!contextWarningDismissed &&
                        viewModel.usage.contextFraction >= 0.85f &&
                        viewModel.timeline.isNotEmpty()
                    ) {
                        AcpContextWarning(
                            percent = (viewModel.usage.contextFraction * 100).toInt(),
                            onNewSession = {
                                contextWarningDismissed = true
                                viewModel.newSession()
                            },
                            onDismiss = { contextWarningDismissed = true }
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    AcpComposerToolbar(
                        viewModel = viewModel,
                        onOpenModel = { configMenu = AcpConfigMenu.MODEL },
                        onOpenMode = { configMenu = AcpConfigMenu.MODE },
                        onOpenTasks = { showTasks = true },
                        onOpenFullscreen = { inputFullscreen = true }
                    )

                    AcpComposer(
                        value = input,
                        onValueChange = { input = it },
                        isPrompting = viewModel.isPrompting,
                        connected = viewModel.connectionState == AcpConnectionState.READY,
                        readOnlyReplay = viewModel.isReadOnlyReplay,
                        commands = viewModel.availableCommands,
                        onSend = {
                            val text = input.text
                            input = TextFieldValue("")
                            viewModel.sendPrompt(text)
                        },
                        onCancel = { viewModel.cancel() }
                    )
                }
                configMenu?.let { menu ->
                    AcpConfigOverlay(
                        viewModel = viewModel,
                        menu = menu,
                        onDismiss = { configMenu = null }
                    )
                }
            }
            }
        }

        AnimatedVisibility(
            visible = state.isCollapsed,
            enter = fadeIn(tween(120)) + scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut(tween(120)) + scaleOut(
                targetScale = 0.4f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AcpDraggableFab(
                    containerWidth = availableWidth,
                    containerHeight = availableHeight,
                    side = state.fabSide,
                    yFraction = state.fabYFraction,
                    connectionState = viewModel.connectionState,
                    isPrompting = viewModel.isPrompting,
                    hasPendingPermission = viewModel.permissionPrompt != null,
                    onSideChange = { state.fabSide = it },
                    onYFractionChange = { state.fabYFraction = it },
                    onClick = { state.isCollapsed = false }
                )
            }
        }
    }

    if (showSettings) {
        AcpAgentSettingsDialog(
            viewModel = viewModel,
            state = state,
            initialTab = settingsTab,
            onDismiss = { showSettings = false }
        )
    }

    if (showSessions) {
        AcpHistorySheet(
            viewModel = viewModel,
            onDismiss = { showSessions = false }
        )
    }

    if (showStats) {
        AcpStatsSheet(
            viewModel = viewModel,
            onDismiss = { showStats = false }
        )
    }

    if (showSkills) {
        AcpSkillsSheet(
            viewModel = viewModel,
            onDismiss = { showSkills = false }
        )
    }

    if (inputFullscreen) {
        AcpFullscreenInputDialog(
            initial = input,
            isPrompting = viewModel.isPrompting,
            onDismiss = { value ->
                input = value
                inputFullscreen = false
            },
            onSend = { text ->
                input = TextFieldValue("")
                inputFullscreen = false
                viewModel.sendPrompt(text)
            }
        )
    }

    if (showTasks) {
        AcpTasksSheet(
            viewModel = viewModel,
            onDismiss = { showTasks = false }
        )
    }

    if (showMcp) {
        AcpMcpSheet(
            viewModel = viewModel,
            onDismiss = { showMcp = false }
        )
    }

    if (showSearch) {
        AcpConversationSearchSheet(
            viewModel = viewModel,
            onJumpTo = { index ->
                showSearch = false
                viewModel.timeline.getOrNull(index)?.let { target ->
                    highlightedId = target.id
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
            onDismiss = { showSearch = false }
        )
    }

    fullDiff?.let { diff ->
        if (editorViewModel != null) {
            AcpDiffDialog(
                diff = diff,
                editorViewModel = editorViewModel,
                onDismiss = { fullDiff = null }
            )
        }
    }
}

/** 收起后的悬浮气泡：点一下展开，连接保持不断。 */
@Composable
private fun AcpCollapsedBubble(
    connectionState: AcpConnectionState,
    isPrompting: Boolean,
    hasPendingPermission: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        hasPendingPermission -> MaterialTheme.colorScheme.tertiaryContainer
        connectionState == AcpConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
        connectionState == AcpConnectionState.READY -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        hasPendingPermission -> MaterialTheme.colorScheme.onTertiaryContainer
        connectionState == AcpConnectionState.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        connectionState == AcpConnectionState.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .padding(14.dp)
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = container,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isPrompting -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = content
                )

                hasPendingPermission -> Icon(
                    Icons.Default.Warning,
                    contentDescription = stringResource(R.string.acp_action_expand),
                    modifier = Modifier.size(22.dp),
                    tint = content
                )

                else -> Icon(
                    Icons.Default.SmartToy,
                    contentDescription = stringResource(R.string.acp_action_expand),
                    modifier = Modifier.size(24.dp),
                    tint = content
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 头部
// ---------------------------------------------------------------------------

@Composable
private fun AcpHeader(
    viewModel: AcpViewModel,
    state: AcpPanelState,
    onNewSession: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMcp: () -> Unit,
    onHide: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val name = viewModel.agentInfo ?: viewModel.preset.displayName
    val statusText = when (viewModel.connectionState) {
        AcpConnectionState.READY -> stringResource(R.string.acp_state_ready)
        AcpConnectionState.CONNECTING -> stringResource(R.string.acp_state_connecting)
        AcpConnectionState.ERROR -> stringResource(R.string.acp_state_error)
        AcpConnectionState.DISCONNECTED -> stringResource(R.string.acp_state_disconnected)
    }
    val statusColor = when (viewModel.connectionState) {
        AcpConnectionState.READY -> Color(0xFF2E7D32)
        AcpConnectionState.ERROR -> Color(0xFFC62828)
        AcpConnectionState.CONNECTING -> Color(0xFFF9A825)
        AcpConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 第一层：窗口管理
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { state.isMaximized = !state.isMaximized },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (state.isMaximized) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = stringResource(
                        if (state.isMaximized) R.string.acp_restore else R.string.acp_maximize
                    ),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { state.isCollapsed = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Minimize,
                    contentDescription = stringResource(R.string.acp_action_minimize),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onHide,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.acp_action_hide),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 第二层：会话管理
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (viewModel.isPrompting) {
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                }
            }
            IconButton(onClick = onOpenSearch, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.acp_search_menu),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSessions, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.History,
                    contentDescription = stringResource(R.string.acp_action_sessions),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onNewSession,
                enabled = viewModel.connectionState == AcpConnectionState.READY && !viewModel.isPrompting,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.acp_action_new_session),
                    modifier = Modifier.size(18.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.acp_action_more),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.acp_settings_title)) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.acp_skills_open)) },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenSkills()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.acp_mcp_menu)) },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenMcp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot

(state: AcpConnectionState, modifier: Modifier = Modifier) {
    val color = when (state) {
        AcpConnectionState.READY -> MaterialTheme.colorScheme.primary
        AcpConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
        AcpConnectionState.ERROR -> MaterialTheme.colorScheme.error
        AcpConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun AcpContextWarning(
    percent: Int,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.acp_context_warning, percent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.acp_action_dismiss), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onNewSession) {
                Text(stringResource(R.string.acp_action_new_session), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 本地回放提示：可以继续会话，或干脆开新的。 */@Composable
private fun AcpReplayBanner(
    onResume: () -> Unit,
    onNewSession: () -> Unit,
    canResume: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.acp_readonly_banner),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onResume, enabled = canResume) {
                Text(stringResource(R.string.acp_resume_session), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onNewSession, enabled = canResume) {
                Text(stringResource(R.string.acp_action_new_session), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 空状态：给出三个可直接试的任务，而不是一句干巴巴的提示。 */
@Composable
private fun AcpEmptyState(
    enabled: Boolean,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.acp_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(18.dp))
        listOf(
            stringResource(R.string.acp_suggestion_read),
            stringResource(R.string.acp_suggestion_fix),
            stringResource(R.string.acp_suggestion_test)
        ).forEach { suggestion ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled) { onSuggestion(suggestion) }
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun AcpStatusRow(viewModel: AcpViewModel) {
    val label = when (viewModel.connectionState) {
        AcpConnectionState.DISCONNECTED -> stringResource(R.string.acp_state_disconnected)
        AcpConnectionState.CONNECTING -> stringResource(R.string.acp_state_connecting)
        AcpConnectionState.READY -> stringResource(R.string.acp_state_ready)
        AcpConnectionState.ERROR -> stringResource(R.string.acp_state_error)
    }
    val detail = viewModel.statusMessage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (viewModel.connectionState == AcpConnectionState.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.weight(1f))
            if (viewModel.canInstallMissing && viewModel.connectionState == AcpConnectionState.ERROR) {
                TextButton(
                    onClick = { viewModel.installSelectedAgent() },
                    enabled = !viewModel.isInstalling
                ) {
                    Text(stringResource(R.string.acp_action_install))
                }
            }
            if (viewModel.connectionState == AcpConnectionState.DISCONNECTED ||
                viewModel.connectionState == AcpConnectionState.ERROR
            ) {
                TextButton(onClick = { viewModel.connect() }) {
                    Text(stringResource(R.string.acp_action_connect))
                }
            } else {
                TextButton(onClick = { viewModel.disconnect() }) {
                    Text(stringResource(R.string.acp_action_disconnect))
                }
            }
        }
        if (!detail.isNullOrBlank() && viewModel.connectionState == AcpConnectionState.ERROR) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 会话配置（模型 / 模式 / 思考等级 …）
// ---------------------------------------------------------------------------

/** 配置分类 → 图标。 */
private fun configCategoryIcon(category: String?): ImageVector = when (category) {
    Acp.CAT_MODEL -> Icons.Default.SmartToy
    Acp.CAT_MODE -> Icons.Default.Tune
    Acp.CAT_THOUGHT_LEVEL -> Icons.Default.Star
    Acp.CAT_MODEL_CONFIG -> Icons.Default.Settings
    else -> Icons.Default.Tune
}

/** 配置分类 → 显示名；未知分类回退到配置项自身的 name。 */
@Composable
private fun configCategoryLabel(option: AcpConfigOption): String {
    val res = when (option.category) {
        Acp.CAT_MODEL -> R.string.acp_config_model
        Acp.CAT_MODE -> R.string.acp_config_mode
        Acp.CAT_THOUGHT_LEVEL -> R.string.acp_config_thought_level
        Acp.CAT_MODEL_CONFIG -> R.string.acp_config_model_config
        else -> null
    }
    return if (res != null) stringResource(res) else option.name
}

/** 旧式 Agent（只有 session/modes）在配置面板里使用的虚拟配置项 id。 */
private const val LEGACY_MODE_OPTION_ID = "__legacy_mode__"

/**
 * 配置栏：把 Agent 暴露的会话配置渲染成一排可点的胶囊。
 * 点任意胶囊打开配置面板；没有配置项或未连接时整条隐藏，避免占地方。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcpConfigBar(
    viewModel: AcpViewModel,
    onOpenConfig: (String?) -> Unit
) {
    val options = viewModel.configOptions
    val hasModeOption = options.any { it.category == Acp.CAT_MODE }
    val legacyModes = if (hasModeOption) emptyList() else viewModel.sessionModes
    if (viewModel.connectionState != AcpConnectionState.READY) return
    if (options.isEmpty() && legacyModes.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            AcpConfigChip(option = option, onClick = { onOpenConfig(option.id) })
        }
        // 旧式 Agent：只有 modes、没有 configOptions，这里给一个兼容入口
        if (legacyModes.isNotEmpty()) {
            val current = legacyModes.firstOrNull { it.id == viewModel.currentModeId }
            FilterChip(
                selected = false,
                onClick = { onOpenConfig(LEGACY_MODE_OPTION_ID) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = "${stringResource(R.string.acp_config_mode)} · " +
                            (current?.name ?: viewModel.currentModeId ?: "-"),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun AcpConfigChip(option: AcpConfigOption, onClick: () -> Unit) {
    val isBoolean = option.type == Acp.CTYPE_BOOLEAN
    val selected = if (isBoolean) option.currentValueBoolean == true else true
    val valueText = if (isBoolean) {
        if (option.currentValueBoolean == true) {
            stringResource(R.string.acp_config_on)
        } else {
            stringResource(R.string.acp_config_off)
        }
    } else {
        option.currentDisplayName
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = configCategoryIcon(option.category),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = "${configCategoryLabel(option)} · $valueText",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 配置面板：分组/扁平的单选项 + 布尔开关，当前值高亮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcpConfigSheet(
    viewModel: AcpViewModel,
    focusOptionId: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = viewModel.configOptions
    val hasModeOption = options.any { it.category == Acp.CAT_MODE }
    val legacyModes = if (hasModeOption) emptyList() else viewModel.sessionModes

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.acp_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))

            if (options.isEmpty() && legacyModes.isEmpty()) {
                Text(
                    text = stringResource(R.string.acp_config_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        AcpConfigOptionRow(
                            option = option,
                            highlighted = option.id == focusOptionId,
                            onSelect = { value -> viewModel.setConfigOption(option.id, value) }
                        )
                    }
                    if (legacyModes.isNotEmpty()) {
                        if (options.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.acp_config_mode),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        legacyModes.forEach { mode ->
                            val selected = mode.id == viewModel.currentModeId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setMode(mode.id) }
                                    .padding(start = 22.dp, top = 2.dp, bottom = 2.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { viewModel.setMode(mode.id) })
                                Spacer(Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.name.ifBlank { mode.id },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    mode.description.takeIf { it.isNotBlank() }?.let { desc ->
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
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
private fun AcpConfigOptionRow(
    option: AcpConfigOption,
    highlighted: Boolean,
    onSelect: (Any) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = configCategoryIcon(option.category),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = configCategoryLabel(option),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                option.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (option.type == Acp.CTYPE_BOOLEAN) {
                Switch(
                    checked = option.currentValueBoolean == true,
                    onCheckedChange = { onSelect(it) }
                )
            }
        }

        if (option.type != Acp.CTYPE_BOOLEAN) {
            Spacer(Modifier.height(4.dp))
            if (option.groups.isNotEmpty()) {
                option.groups.forEach { group ->
                    val groupLabel = group.name.ifBlank { group.group }
                    if (groupLabel.isNotBlank()) {
                        Text(
                            text = groupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 26.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }
                    group.options.forEach { value ->
                        AcpConfigValueRow(option, value, onSelect)
                    }
                }
            } else {
                option.values.forEach { value ->
                    AcpConfigValueRow(option, value, onSelect)
                }
            }
        }
    }
}

@Composable
private fun AcpConfigValueRow(
    option: AcpConfigOption,
    value: AcpConfigOptionValue,
    onSelect: (Any) -> Unit
) {
    val selected = option.currentValueString == value.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelect(value.value) }
            .padding(start = 22.dp, top = 2.dp, bottom = 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = { onSelect(value.value) })
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            value.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 撤销 AI 修改 / 会话管理
// ---------------------------------------------------------------------------

/** 本轮 Agent 写过的文件集合，一键回退到修改前状态。 */
@Composable
private fun AcpUndoBanner(viewModel: AcpViewModel) {
    val snapshots = viewModel.pendingSnapshots
    AnimatedVisibility(
        visible = snapshots.isNotEmpty(),
        enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.acp_undo_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.acp_undo_desc, snapshots.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                snapshots.take(6).forEach { snap ->
                    Text(
                        text = snap.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (snapshots.size > 6) {
                    Text(
                        text = "+" + (snapshots.size - 6),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.dismissSnapshots() }) {
                        Text(stringResource(R.string.acp_action_dismiss))
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { viewModel.undoLastEdits() }) {
                        Text(stringResource(R.string.acp_action_undo))
                    }
                }
            }
        }
    }
}

/** 会话管理面板：列出历史会话，可切换 / 新建 / 分叉。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcpSessionsSheet(
    viewModel: AcpViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { viewModel.refreshSessions() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.acp_sessions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.refreshSessions() },
                    enabled = !viewModel.isLoadingSessions
                ) {
                    if (viewModel.isLoadingSessions) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.acp_action_refresh_sessions)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.newSession(); onDismiss() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.acp_action_new_session))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { viewModel.forkSession(); onDismiss() },
                    enabled = viewModel.sessionId != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.acp_action_fork_session))
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                viewModel.isLoadingSessions && viewModel.sessions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.acp_sessions_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                viewModel.sessions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.acp_sessions_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        viewModel.sessions.forEach { session ->
                            val selected = session.sessionId == viewModel.sessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.loadSession(session.sessionId)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        viewModel.loadSession(session.sessionId)
                                        onDismiss()
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.acp_session_untitled),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    formatSessionTime(session.updatedAt)?.let { time ->
                                        Text(
                                            text = time,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
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

/** 把 ISO 时间戳裁剪成 "yyyy-MM-dd HH:mm"，无法解析时原样返回。 */
private fun formatSessionTime(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return value.replace('T', ' ').take(16)
}

// ---------------------------------------------------------------------------
// 时间线
// ---------------------------------------------------------------------------

@Composable
private fun AcpTimelineRow(
    item: AcpTimelineItem,
    retryableUserId: String?,
    canStartNewSession: Boolean,
    onRetry: () -> Unit,
    onNewSessionFrom: (String) -> Unit,
    onOpenDiff: (AcpToolContent.Diff) -> Unit,
    onOpenFile: (String, Int?) -> Unit
) {
    when (item) {
        is AcpUserMessage -> AcpUserBubble(
            item = item,
            canRetry = item.id == retryableUserId,
            canStartNewSession = canStartNewSession,
            onRetry = onRetry,
            onNewSessionFrom = onNewSessionFrom
        )

        is AcpAgentMessage -> AcpAgentBubble(item)
        is AcpThoughtMessage -> AcpThoughtBlock(item)
        is AcpNoticeMessage -> AcpNoticeRow(item)
        is AcpPlanItem -> AcpPlanCard(item)
        is AcpToolCallItem -> AcpToolCallCard(item, onOpenDiff, onOpenFile)
    }
}

@Composable
private fun AcpRunningRow() {
    val transition = rememberInfiniteTransition(label = "acp-running")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "acp-running-alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.acp_status_working),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun AcpUserBubble(
    item: AcpUserMessage,
    canRetry: Boolean,
    canStartNewSession: Boolean,
    onRetry: () -> Unit,
    onNewSessionFrom: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .pointerInput(item.id) {
                        detectTapGestures(onLongPress = { showMenu = true })
                    }
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acp_action_copy)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(item.text))
                        showMenu = false
                    }
                )
                if (canRetry) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.acp_action_retry)) },
                        onClick = {
                            showMenu = false
                            onRetry()
                        }
                    )
                }
                if (canStartNewSession) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.acp_action_new_session_from)) },
                        onClick = {
                            showMenu = false
                            onNewSessionFrom(item.text)
                        }
                    )
                }
            }
        }
        CopyIconButton(item.text)
    }
}

@Composable
private fun CopyIconButton(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
        modifier = Modifier.size(26.dp)
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.acp_action_copy),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AcpAgentBubble(item: AcpAgentMessage) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.acp_mark),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(item.id) {
                        detectTapGestures(onLongPress = { showMenu = true })
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AcpRichText(text = item.text)
                    CopyIconButton(item.text)
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acp_action_copy)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(item.text))
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AcpThoughtBlock(item: AcpThoughtMessage) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(item.text) {
        item.text.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(90)
            .orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.acp_thinking),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            DotSeparator()
            Spacer(Modifier.width(8.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun AcpNoticeRow(item: AcpNoticeMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AcpPlanCard
(item: AcpPlanItem) {
    val done = item.entries.count { it.status == Acp.STATUS_COMPLETED }
    val total = item.entries.size.coerceAtLeast(1)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.acp_plan_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$done/${item.entries.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (done > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(done.toFloat() / total)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            item.entries.forEach { entry ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val completed = entry.status == Acp.STATUS_COMPLETED
                    val inProgress = entry.status == Acp.STATUS_IN_PROGRESS
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    completed -> MaterialTheme.colorScheme.primary
                                    inProgress -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (completed) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 工具调用
// ---------------------------------------------------------------------------

@Composable
private fun AcpToolCallCard(
    item: AcpToolCallItem,
    onOpenDiff: (AcpToolContent.Diff) -> Unit,
    onOpenFile: (String, Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = item.contents.isNotEmpty() ||
        item.locations.isNotEmpty() ||
        !item.rawInput.isNullOrBlank() ||
        !item.rawOutput.isNullOrBlank()

    // 失败的工具调用直接展开，省得用户还要点一下才知道错在哪
    LaunchedEffect(item.status) {
        if (item.status == Acp.STATUS_FAILED) expanded = true
    }
    val summary = remember(item.toolCallId, item.locations, item.contents, item.rawInput) {
        toolCallSummary(item)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = hasDetail) { expanded = !expanded }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = toolKindIcon(item.kind),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (item.status == Acp.STATUS_FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.title.ifBlank { item.kind },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp)
            )
            Spacer(Modifier.width(8.dp))
            DotSeparator()
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            ToolStateDot(item.status)
            item.finishedAt?.let { finished ->
                if (item.startedAt > 0L && finished >= item.startedAt) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatToolDuration(finished - item.startedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasDetail) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    item.locations.forEach { location ->
                        Text(
                            text = buildString {
                                append(location.path)
                                location.line?.let { append(":").append(it) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { onOpenFile(location.path, location.line) }
                                .padding(vertical = 2.dp)
                        )
                    }
                    item.contents.forEach { content ->
                        when (content) {
                            is AcpToolContent.Text -> {
                                Spacer(Modifier.height(4.dp))
                                AcpMarkdownText(
                                    text = content.text,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is AcpToolContent.Diff -> {
                                Spacer(Modifier.height(4.dp))
                                AcpDiffView(content, onOpenDiff)
                            }

                            is AcpToolContent.TerminalRef -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = content.terminalId,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item.rawInput?.takeIf { it.isNotBlank() }?.let { raw ->
                        Spacer(Modifier.height(4.dp))
                        RawBlock(title = stringResource(R.string.acp_raw_input), body = raw)
                    }
                    item.rawOutput?.takeIf { it.isNotBlank() }?.let { raw ->
                        Spacer(Modifier.height(4.dp))
                        RawBlock(title = stringResource(R.string.acp_raw_output), body = raw)
                    }
                }
            }
        }
    }
}

private fun toolCallSummary(item: AcpToolCallItem): String {
    item.locations.firstOrNull()?.let { location ->
        return buildString {
            append(location.path)
            location.line?.let { append(":").append(it) }
        }
    }
    item.contents.filterIsInstance<AcpToolContent.Diff>().firstOrNull()?.path?.let { return it }
    item.contents.filterIsInstance<AcpToolContent.Text>()
        .firstOrNull { it.text.isNotBlank() }
        ?.text
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(120)
        ?.let { return it }
    item.rawInput?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(120)?.let { return it }
    return ""
}

@Composable
private fun DotSeparator() {
    Box(
        modifier = Modifier
            .size(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun ToolStateDot(status: String) {
    val color = when (status) {
        Acp.STATUS_COMPLETED -> MaterialTheme.colorScheme.primary
        Acp.STATUS_FAILED -> MaterialTheme.colorScheme.error
        Acp.STATUS_IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun RawBlock
(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = body.take(2000),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ToolStatusChip(status: String) {
    val (label, container, content) = when (status) {
        Acp.STATUS_COMPLETED -> Triple(
            stringResource(R.string.acp_tool_status_completed),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )

        Acp.STATUS_IN_PROGRESS -> Triple(
            stringResource(R.string.acp_tool_status_in_progress),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )

        Acp.STATUS_FAILED -> Triple(
            stringResource(R.string.acp_tool_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )

        else -> Triple(
            stringResource(R.string.acp_tool_status_pending),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

private fun formatToolDuration(millis: Long): String = when {
    millis < 1_000L -> "${millis}ms"
    millis < 60_000L -> String.format(java.util.Locale.US, "%.1fs", millis / 1_000.0)
    else -> "${millis / 60_000L}m ${(millis % 60_000L) / 1_000L}s"
}

private fun toolKindIcon(kind: String): ImageVector = when (kind) {
    Acp.KIND_EDIT -> Icons.Default.Edit
    Acp.KIND_DELETE -> Icons.Default.Delete
    Acp.KIND_SEARCH -> Icons.Default.Search
    Acp.KIND_EXECUTE -> Icons.Default.Terminal
    Acp.KIND_THINK -> Icons.Default.Star
    Acp.KIND_FETCH -> Icons.Default.Refresh
    Acp.KIND_MOVE -> Icons.AutoMirrored.Filled.ArrowForward
    Acp.KIND_SWITCH_MODE -> Icons.Default.Settings
    Acp.KIND_READ -> Icons.Default.Info
    else -> Icons.Default.MoreVert
}

// ---------------------------------------------------------------------------
// 差异视图
// ---------------------------------------------------------------------------

private sealed interface AcpDiffLine {
    val text: String

    data class Same(override val text: String) : AcpDiffLine
    data class Removed(override val text: String) : AcpDiffLine
    data class Added(override val text: String) : AcpDiffLine
}

private fun unifiedDiff(oldText: String?, newText: String): List<AcpDiffLine> {
    val oldLines = oldText?.split("\n") ?: emptyList()
    val newLines = newText.split("\n")
    val n = oldLines.size
    val m = newLines.size

    // 超大文件退化为「整块替换」，避免 O(n*m) 内存爆炸
    if (n.toLong() * m.toLong() > 250_000L) {
        return oldLines.map { AcpDiffLine.Removed(it) } + newLines.map { AcpDiffLine.Added(it) }
    }

    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (oldLines[i] == newLines[j]) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
    }

    val result = mutableListOf<AcpDiffLine>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            oldLines[i] == newLines[j] -> {
                result.add(AcpDiffLine.Same(oldLines[i]))
                i++
                j++
            }

            dp[i + 1][j] >= dp[i][j + 1] -> {
                result.add(AcpDiffLine.Removed(oldLines[i]))
                i++
            }

            else -> {
                result.add(AcpDiffLine.Added(newLines[j]))
                j++
            }
        }
    }
    while (i < n) {
        result.add(AcpDiffLine.Removed(oldLines[i]))
        i++
    }
    while (j < m) {
        result.add(AcpDiffLine.Added(newLines[j]))
        j++
    }
    return result
}

@Composable
private fun AcpDiffView(
    diff: AcpToolContent.Diff,
    onOpenDiff: (AcpToolContent.Diff) -> Unit
) {
    val lines = remember(diff.oldText, diff.newText) { unifiedDiff(diff.oldText, diff.newText) }
    val visible = lines.take(400)
    val removedColor = MaterialTheme.colorScheme.errorContainer
    val addedColor = MaterialTheme.colorScheme.primaryContainer

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = diff.path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onOpenDiff(diff) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.acp_action_open_diff),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                visible.forEach { line ->
                    val (prefix, background) = when (line) {
                        is AcpDiffLine.Same -> "  " to Color.Transparent
                        is AcpDiffLine.Removed -> "- " to removedColor
                        is AcpDiffLine.Added -> "+ " to addedColor
                    }
                    Text(
                        text = prefix + line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(background)
                            .padding(horizontal = 4.dp)
                    )
                }
                if (lines.size > visible.size) {
                    Text(
                        text = stringResource(R.string.acp_diff_truncated, lines.size - visible.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 授权卡片
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcpPermissionCard(
    prompt: AcpPermissionPrompt,
    approvalMode: AcpApprovalMode,
    onSelect: (String?) -> Unit,
    onRememberAllow: () -> Unit,
    onRememberDeny: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.acp_permission_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = permissionKindLabel(prompt.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = prompt.title,
                style = MaterialTheme.typography.bodySmall
            )
            prompt.rawInput?.takeIf { it.isNotBlank() }?.let { raw ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = raw.take(800),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                prompt.options.forEach { option ->
                    val isReject = option.kind == Acp.PERM_REJECT_ONCE ||
                        option.kind == Acp.PERM_REJECT_ALWAYS
                    if (isReject) {
                        TextButton(onClick = { onSelect(option.optionId) }) {
                            Text(option.name)
                        }
                    } else {
                        Button(onClick = { onSelect(option.optionId) }) {
                            Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.acp_permission_inline_hint, approvalModeLabel(approvalMode)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onRememberDeny) {
                    Text(
                        text = stringResource(R.string.acp_permission_always_deny),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = onRememberAllow) {
                    Text(
                        text = stringResource(R.string.acp_permission_always_allow),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 输入区
// ---------------------------------------------------------------------------

@Composable
private fun AcpInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isPrompting: Boolean,
    enabled: Boolean,
    commands: List<AcpSlashCommand>,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    // 输入以 / 开头时给出斜杠命令补全（Agent 通过 available_commands 声明）
    val slashQuery = value.takeIf { it.startsWith("/") && !it.contains(' ') && !it.contains('\n') }
    val matches = remember(slashQuery, commands) {
        if (slashQuery == null) {
            emptyList()
        } else {
            commands.filter { it.name.startsWith(slashQuery, ignoreCase = true) }.take(8)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = matches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                matches.forEach { command ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueChange("${command.name} ") }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = command.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (command.description.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = command.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.acp_input_hint), style = MaterialTheme.typography.bodySmall) },
                enabled = enabled,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.width(4.dp))
            if (isPrompting) {
                IconButton(onClick = onCancel, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.acp_action_cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = enabled && value.isNotBlank(),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.acp_action_send)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 设置
// ---------------------------------------------------------------------------

@Composable
private fun AcpSettingsDialog(
    viewModel: AcpViewModel,
    state: AcpPanelState,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf(viewModel.agentCommand) }
    var selected by remember { mutableStateOf(viewModel.preset) }
    var envText by remember(selected.id) { mutableStateOf(viewModel.envTextFor(selected.id)) }

    // 打开设置、或切换预设时查一次版本（只查当前选中的预设，避免串版本号）
    LaunchedEffect(selected.id) { viewModel.refreshVersions(selected, command) }

    // 打开设置时顺便读一次 Agent 日志，方便「一直转圈」时排查
    LaunchedEffect(Unit) { viewModel.refreshAgentLog() }

    // 配置没变就不重连，避免只改个开关也被迫重新拉起 Agent
    val configUnchanged = viewModel.isConnectedWith(selected.id, command)
    val installed = viewModel.installedVersion
    val latest = viewModel.latestVersion

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.acp_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selected.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.acp_agent_label)) },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        AcpAgents.PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.displayName) },
                                onClick = {
                                    selected = preset
                                    expanded = false
                                    if (preset.defaultCommand.isNotEmpty()) {
                                        command = preset.defaultCommand
                                    }
                                }
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(selected.descriptionRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.acp_command_label)) },
                    placeholder = { Text(stringResource(R.string.acp_command_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // ---- 环境变量（API Key / Base URL） ----
                OutlinedTextField(
                    value = envText,
                    onValueChange = { envText = it },
                    label = { Text(stringResource(R.string.acp_env_title)) },
                    placeholder = { Text(stringResource(R.string.acp_env_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 150.dp),
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    minLines = 2,
                    maxLines = 6
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.acp_env_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.saveAgentEnv(selected.id, envText) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                // ---- 版本 / 一键安装 ----
                if (selected.primaryPackage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.acp_version_installed,
                                installed ?: stringResource(R.string.acp_version_none)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when {
                            viewModel.isCheckingVersion -> Text(
                                text = stringResource(R.string.acp_version_checking),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            latest != null && viewModel.hasUpdate -> Text(
                                text = stringResource(R.string.acp_version_update_available, latest),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            latest != null -> Text(
                                text = stringResource(R.string.acp_version_latest, latest),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = { viewModel.refreshVersions(selected, command) },
                        enabled = !viewModel.isCheckingVersion && selected.supportsInstall
                    ) {
                        Text(stringResource(R.string.acp_action_check_update))
                    }
                }
                }

                if (selected.supportsInstall) {
                    Button(
                        onClick = { viewModel.installSelectedAgent(selected) },
                        enabled = !viewModel.isInstalling,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                viewModel.isInstalling -> stringResource(R.string.acp_installing)
                                installed == null -> stringResource(R.string.acp_action_install)
                                viewModel.hasUpdate -> stringResource(R.string.acp_action_update)
                                else -> stringResource(R.string.acp_action_reinstall)
                            }
                        )
                    }
                }

                if (viewModel.installLog.isNotBlank()) {
                    Text(
                        text = viewModel.installLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                // 仅在「当前下拉选中的 Agent 正是已连接的那个」时展示登录入口，
                // 否则会把上一个 Agent 的登录方式张冠李戴
                if (viewModel.authMethods.isNotEmpty() && viewModel.isConnectedWith(selected.id, command)) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.acp_auth_title),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = viewModel.authMethods
                                    .firstOrNull { it.id == viewModel.activeAuthMethodId }?.name
                                    ?: stringResource(R.string.acp_auth_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.authMethods.firstOrNull()?.let { viewModel.authenticate(it.id) }
                            }
                        ) {
                            Text(stringResource(R.string.acp_action_authenticate))
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.acp_permission_mode_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                AcpApprovalMode.entries.forEach { mode ->
                    val selected = viewModel.permissions.mode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.updateApprovalMode(mode) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.updateApprovalMode(mode) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = approvalModeLabel(mode),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                            Text(
                                text = stringResource(approvalModeDescription(mode)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                val savedRules = viewModel.permissions.alwaysAllowKinds +
                    viewModel.permissions.alwaysDenyKinds
                if (savedRules.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.acp_permission_rules) +
                                ": " + savedRules.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.permissions.clearRules() }) {
                            Text(
                                text = stringResource(R.string.acp_permission_clear_rules),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.acp_dock_left_switch),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.dockSide == AcpDockSide.LEFT,
                        onCheckedChange = {
                            state.dockSide = if (it) AcpDockSide.LEFT else AcpDockSide.RIGHT
                        }
                    )
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.acp_self_check),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.runSelfCheck(selected, command) },
                        enabled = !viewModel.isSelfChecking
                    ) {
                        Text(
                            if (viewModel.isSelfChecking) {
                                stringResource(R.string.acp_self_check_running)
                            } else {
                                stringResource(R.string.acp_action_run)
                            }
                        )
                    }
                }

                if (viewModel.selfCheckReport.isNotEmpty()) {
                    Text(
                        text = viewModel.selfCheckReport.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }


                // ---- Agent 运行日志（模型 / 网络错误只写在这里） ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.acp_agent_log),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.acp_agent_log_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.refreshAgentLog() },
                        enabled = !viewModel.isLoadingLog
                    ) {
                        Text(stringResource(R.string.acp_action_refresh_log))
                    }
                }

                if (viewModel.agentLog.isNotEmpty()) {
                    Text(
                        text = viewModel.agentLog.takeLast(60).joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    )
                } else if (!viewModel.isLoadingLog) {
                    Text(
                        text = stringResource(R.string.acp_agent_log_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (viewModel.stderrTail.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.acp_diagnostics),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = viewModel.stderrTail.takeLast(1200),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 110.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val needsReconnect = !viewModel.isConnectedWith(selected.id, command)
                    viewModel.selectAgent(selected)
                    viewModel.updateAgentCommand(command)
                    if (needsReconnect) viewModel.connect()
                    onDismiss()
                }
            ) {
                Text(
                    when {
                        configUnchanged -> stringResource(R.string.action_save)
                        viewModel.connectionState == AcpConnectionState.READY ->
                            stringResource(R.string.acp_action_reconnect)

                        else -> stringResource(R.string.acp_action_connect)
                    }
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.clearTimeline() }) {
                    Text(stringResource(R.string.acp_action_clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}