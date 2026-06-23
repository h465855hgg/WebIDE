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
package com.web.webide.ui.terminal

import android.annotation.SuppressLint
import android.app.Application
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.navigation.NavController
import com.rk.libcommons.application
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.web.webide.R
import com.web.webide.ui.terminal.TerminalConfig.VIRTUAL_KEYS_JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

// 🔥 全局 VirtualKeysView 引用，用于 TerminalBackEnd 读取 Ctrl/Alt 按键状态
var virtualKeysView: WeakReference<VirtualKeysView>? = null

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController) {
    val context = LocalContext.current
    var isEnvironmentReady by remember { mutableStateOf(false) }
    val isSystemDark = isSystemInDarkTheme()

    // === 初始化逻辑 ===
    LaunchedEffect(Unit) {
        if (application == null) application = context.applicationContext as Application
        withContext(Dispatchers.IO) {
            SetupWorker.prepareEnvironment(context)
        }
        isEnvironmentReady = true
        if (SessionManager.sessions.isEmpty()) {
            SessionManager.addNewSession(context)
        }
    }

    if (!isEnvironmentReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentSession = SessionManager.currentSession
    var terminalViewRef by remember { mutableStateOf<WeakReference<TerminalView>?>(null) }

    val buttonTextColor = TerminalConfig.getButtonTextColor(isSystemDark)
    val buttonBgColor = TerminalConfig.getButtonBarBgColor(isSystemDark)
    val buttonActiveTextColor = TerminalConfig.getButtonActiveTextColor(isSystemDark)
    val buttonActiveBgColor = TerminalConfig.getButtonActiveBgColor(isSystemDark)



    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            // 仅处理左右的安全区域，顶部和底部交给 topBar/bottomBar 处理
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),

        // === 顶部区域 ===
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding() // 1. 确保避开状态栏
            ) {
                // 1. 标题栏
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.terminal_title),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                )

                // 2. Tabs + Add Button
                // 🔥 按照您的要求：
                // 1. 设定固定高度 (45dp)，防止太大占地
                // 2. 移除 CenterVertically，改为 Bottom (底对齐)，紧贴分割线
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp),
                    verticalAlignment = Alignment.Bottom // 🔥 核心修改：底对齐
                ) {
                    if (SessionManager.sessions.isNotEmpty()) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = SessionManager.currentSessionIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            edgePadding = 0.dp,
                            divider = {},
                            modifier = Modifier.weight(1f),
                            indicator = {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(SessionManager.currentSessionIndex),
                                    height = 3.dp, // 指示条稍厚一点，更清晰
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            SessionManager.sessions.forEachIndexed { index, session ->
                                val isSelected = SessionManager.currentSessionIndex == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { SessionManager.switchTo(index) },
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    // Tab 内容
                                    Row(
                                        // 🔥 移除垂直居中，让文字自然落下
                                        // 稍微加一点 padding 调整左右和底部间距
                                        modifier = Modifier.padding(
                                            start = 12.dp,
                                            end = 12.dp,
                                            bottom = 10.dp
                                        )
                                    ) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.action_close),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        SessionManager.removeSession(
                                                            session
                                                        )
                                                    },
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // 垂直分割线
                    VerticalDivider(
                        modifier = Modifier
                            .padding(vertical = 10.dp) // 上下留白
                            .height(20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Add 按钮
                    // 因为 Row 是底对齐，按钮可能会沉底。
                    // 这里我们还是让按钮稍微居中一点点看起来舒服，或者就让它沉底
                    Box(
                        modifier = Modifier
                            .size(45.dp) // 宽度和高度填满 Row
                            .clickable { SessionManager.addNewSession(context) },
                        contentAlignment = Alignment.Center // 按钮图标在格子里居中
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.terminal_new_session),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 3. 底部分割线 (Tabs 就在这根线上面)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        },

        // === 底部虚拟按键 ===
        bottomBar = {
            Surface(
                color = Color(buttonBgColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding() // 🔥 抬高至导航栏之上（经典三按键导航）
                    .imePadding() // 确保被输入法顶起
            ) {
                val pagerState = rememberPagerState(pageCount = { 2 })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.height(75.dp),
                    userScrollEnabled = true
                ) { page ->
                    when (page) {
                        0 -> {
                            AndroidView(
                                factory = { ctx ->
                                    VirtualKeysView(ctx, null).apply {
                                        // 🔥 设置全局引用
                                        virtualKeysView = WeakReference(this)

                                        // 🔥 使用 TerminalSession 而不是 TerminalView
                                        virtualKeysViewClient = currentSession?.let { VirtualKeysListener(it) }

                                        setButtonTextAllCaps(true)
                                        // 🔥 必须在 reload() 之前设置颜色，否则按钮创建时使用默认白色文字
                                        setButtonColors(
                                            buttonTextColor,
                                            buttonActiveTextColor,
                                            0x00000000,
                                            buttonActiveBgColor
                                        )
                                        reload(
                                            VirtualKeysInfo(
                                                VIRTUAL_KEYS_JSON,
                                                "",
                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    view.setButtonColors(
                                        buttonTextColor,
                                        buttonActiveTextColor,
                                        0x00000000,
                                        buttonActiveBgColor
                                    )
                                    // 🔥 更新已创建按钮的实际文字颜色（不只是字段值）
                                    view.updateAllButtonColors()
                                }
                            )
                        }

                        1 -> {
                            var text by rememberSaveable { mutableStateOf("") }
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        EditText(ctx).apply {
                                            maxLines = 1; isSingleLine = true; imeOptions =
                                            EditorInfo.IME_ACTION_DONE
                                            background = null; hint = context.getString(R.string.terminal_input_hint)
                                            setHintTextColor(if (isSystemDark) 0xFF888888.toInt() else 0xFF757575.toInt())
                                            setTextColor(TerminalConfig.getButtonTextColor(isSystemDark))
                                            doOnTextChanged { t, _, _, _ ->
                                                val inputChar = t.toString()
                                                if (inputChar.isNotEmpty()) {
                                                    val session = SessionManager.currentSession
                                                    session?.write(inputChar)
                                                }
                                                text = "" // 清空输入框，准备下一次输入
                                            }
                                            setOnEditorActionListener { _, actionId, _ ->
                                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                                    val term = terminalViewRef?.get()
                                                    if (text.isEmpty()) term?.dispatchKeyEvent(
                                                        KeyEvent(
                                                            KeyEvent.ACTION_DOWN,
                                                            KeyEvent.KEYCODE_ENTER
                                                        )
                                                    )
                                                    else {
                                                        term?.mTermSession?.write(text); setText("")
                                                    }
                                                    true
                                                } else false
                                            }
                                        }
                                    },
                                    update = {
                                        if (it.text.toString() != text) it.setText(text); it.setTextColor(
                                        TerminalConfig.getButtonTextColor(isSystemDark)
                                    )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // === 终端内容区域 ===
        // 🔥🔥🔥 这里的 innerPadding 非常关键！它包含了 TopBar 的高度。
        // 如果这里没加 padding，TopBar 就会直接盖在终端上面。
        if (currentSession != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // 必须应用 Scaffold 传递的 Padding
                    .background(Color(TerminalConfig.getBackgroundColor(isSystemDark)))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            terminalViewRef = WeakReference(this)
                            setTextSize(42)
                            setTypeface(TerminalFontManager.getTypeface(ctx))
                            keepScreenOn = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            attachSession(currentSession)
                            val client = TerminalBackEnd(this, ctx)
                            setTerminalViewClient(client)
                            currentSession.updateTerminalSessionClient(client)
                        }
                    },
                    update = { view ->
                        view.setTypeface(TerminalFontManager.getTypeface(context))
                        view.setBackgroundColor(TerminalConfig.getBackgroundColor(isSystemDark))
                        // 🔥 更新终端模拟器的颜色方案（前景色、背景色、光标色）
                        view.mEmulator?.let { emulator ->
                            val colors = emulator.mColors.mCurrentColors
                            colors[TextStyle.COLOR_INDEX_FOREGROUND] = TerminalConfig.getForegroundColor(isSystemDark)
                            colors[TextStyle.COLOR_INDEX_BACKGROUND] = TerminalConfig.getBackgroundColor(isSystemDark)
                            colors[TextStyle.COLOR_INDEX_CURSOR] = TerminalConfig.getCursorColor(isSystemDark)
                        }
                        if (view.currentSession != currentSession) {
                            view.attachSession(currentSession)
                            val client = TerminalBackEnd(view, context)
                            view.setTerminalViewClient(client)
                            currentSession.updateTerminalSessionClient(client)
                            view.onScreenUpdated()
                        }
                    }
                )
            }
        }
    }
}
