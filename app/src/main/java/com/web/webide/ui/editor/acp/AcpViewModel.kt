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

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.web.webide.R
import com.web.webide.core.utils.FileChangeBus
import com.web.webide.core.utils.WorkspaceWatcher
import com.web.webide.core.utils.WorkspaceManager
import com.web.webide.ui.terminal.AlpineManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ACP 面板的状态与编排中心。
 *
 * 负责连接 Agent 子进程、建立会话、收发 prompt，并把 Agent 推送的
 * `session/update` 通知翻译成面板时间线；同时作为「客户端」响应 Agent 发起的
 * 反向请求（文件读写、终端、授权确认）。
 */
internal class AcpViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "acp_settings"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_COMMAND = "agent_command"
        private const val KEY_AUTO_APPROVE = "auto_approve"
        private const val MAX_STDERR_LINES = 40
        private const val MAX_AGENT_LOG_LINES = 120

        /** 超过该时长仍无任何输出时，提示用户去看 Agent 日志。 */
        private const val PROMPT_SLOW_NOTICE_MILLIS = 45_000L

        /** 单个文件超过该大小则不做内容快照（无法安全还原）。 */
        private const val MAX_SNAPSHOT_FILE_BYTES = 512L * 1024L

        /** 每回合采集的基线内容总预算，避免大项目把内存吃满。 */
        private const val MAX_BASELINE_CONTENT_BYTES = 16 * 1024 * 1024
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    var agentPresetId by mutableStateOf(prefs.getString(KEY_AGENT_ID, AcpAgents.DEFAULT.id) ?: AcpAgents.DEFAULT.id)
        private set

    var agentCommand by mutableStateOf(
        prefs.getString(KEY_COMMAND, AcpAgents.DEFAULT.defaultCommand) ?: AcpAgents.DEFAULT.defaultCommand
    )
        private set

    var autoApprove by mutableStateOf(prefs.getBoolean(KEY_AUTO_APPROVE, false))
        private set

    val preset: AcpAgentPreset get() = AcpAgents.byId(agentPresetId)

    // ------------------------------------------------------------------
    // 运行时状态
    // ------------------------------------------------------------------

    var connectionState by mutableStateOf(AcpConnectionState.DISCONNECTED)
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    var agentInfo by mutableStateOf<String?>(null)
        private set

    var stderrTail by mutableStateOf("")
        private set

    var isPrompting by mutableStateOf(false)
        private set

    var sessionId by mutableStateOf<String?>(null)
        private set

    var currentModeId by mutableStateOf<String?>(null)
        private set

    var sessionModes by mutableStateOf<List<AcpSessionMode>>(emptyList())
        private set

    var availableCommands by mutableStateOf<List<AcpSlashCommand>>(emptyList())
        private set

    /** 会话配置项（模型 / 模式 / 思考等级等），现代 ACP 用它取代旧 modes。 */
    var configOptions by mutableStateOf<List<AcpConfigOption>>(emptyList())
        private set

    /** Agent 声明的认证方式（用于「重新登录」入口）。 */
    var authMethods by mutableStateOf<List<AcpAuthMethod>>(emptyList())
        private set

    /** 正在进行的认证方式（null 表示无）。 */
    var activeAuthMethodId by mutableStateOf<String?>(null)
        private set

    /** 是否已按 Agent 声明的认证方式完成登录。 */
    var isAuthenticated by mutableStateOf(false)
        private set

    var permissionPrompt by mutableStateOf<AcpPermissionPrompt?>(null)
        private set

    /** 环境自检报告（逐行文本）。 */
    var selfCheckReport by mutableStateOf<List<String>>(emptyList())
        private set

    var isSelfChecking by mutableStateOf(false)
        private set

    /**
     * Agent 自身日志的末尾若干行。
     *
     * 模型调用失败（限流 / 网络不通 / 鉴权失败）时，Agent 往往**不**通过 ACP 协议
     * 返回错误，只把它写进自己的日志；「一直转圈」时这是唯一的线索。
     */
    var agentLog by mutableStateOf<List<String>>(emptyList())
        private set

    var isLoadingLog by mutableStateOf(false)
        private set

    /** 可切换的历史会话列表（session/list）。 */
    var sessions by mutableStateOf<List<AcpSessionSummary>>(emptyList())
        private set

    var isLoadingSessions by mutableStateOf(false)
        private set

    /** 当前 Agent 是否支持会话列举（不同 CLI 能力不一）。 */
    var supportsSessionList by mutableStateOf(true)
        private set

    /** 本轮对话中被 Agent 修改过的文件快照，用于「撤销修改」。 */
    var pendingSnapshots by mutableStateOf<List<AcpFileSnapshot>>(emptyList())
        private set

    // ---- 一键安装 / 版本检测 ----
    var isInstalling by mutableStateOf(false)
        private set

    /** 安装过程的实时输出。 */
    var installLog by mutableStateOf("")
        private set

    var isCheckingVersion by mutableStateOf(false)
        private set

    /** 容器内已安装的版本（null = 未安装或读取失败）。 */
    var installedVersion by mutableStateOf<String?>(null)
        private set

    /** npm registry 上的最新版本。 */
    var latestVersion by mutableStateOf<String?>(null)
        private set

    /** 当前失败原因是「命令没装」，界面据此给出安装入口。 */
    var canInstallMissing by mutableStateOf(false)
        private set

    val hasUpdate: Boolean
        get() = installedVersion != null &&
            latestVersion != null &&
            installedVersion != latestVersion

    val timeline = mutableStateListOf<AcpTimelineItem>()

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private var transport: AcpTransport? = null
    private var connection: AcpConnection? = null
    private var terminals: AcpTerminalManager? = null
    private var pendingPermission: CompletableDeferred<String?>? = null
    private var promptJob: Job? = null
    private var promptWatchJob: Job? = null
    private var itemSeq = 0
    private var currentAgentMessageItemId: String? = null
    private var currentThoughtItemId: String? = null
    private val stderrLines = ArrayDeque<String>()

    /** 本轮已记录快照的路径（避免同一文件被多次写时覆盖最初的旧内容）。 */
    private val sessionSnapshots = LinkedHashMap<String, AcpFileSnapshot>()

    /** 回合开始前的工作区基线（签名 + 原始内容），用于兜底记录 Agent 直接改盘的改动。 */
    private var turnBaselineRoot: File? = null
    private var turnBaselineSig: Map<String, Long> = emptyMap()
    private var turnBaselineContent: Map<String, String?> = emptyMap()

    /** 本次连接要依次尝试的候选命令，以及当前下标。 */
    private var candidateCommands: List<String> = emptyList()
    private var candidateIndex = 0

    private val installBuffer = StringBuilder()
    private var installTicker: Job? = null

    private fun s(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun newId(prefix: String): String = "$prefix-${++itemSeq}"

    // ------------------------------------------------------------------
    // 对外操作
    // ------------------------------------------------------------------

    fun selectAgent(preset: AcpAgentPreset) {
        agentPresetId = preset.id
        if (preset.defaultCommand.isNotEmpty()) {
            agentCommand = preset.defaultCommand
        }
        canInstallMissing = false
        prefs.edit {
            putString(KEY_AGENT_ID, agentPresetId)
            putString(KEY_COMMAND, agentCommand)
        }
        refreshVersions()
    }

    fun updateAgentCommand(command: String) {
        agentCommand = command
        prefs.edit { putString(KEY_COMMAND, command) }
    }

    fun updateAutoApprove(enabled: Boolean) {
        autoApprove = enabled
        prefs.edit { putBoolean(KEY_AUTO_APPROVE, enabled) }
    }

    fun clearTimeline() {
        timeline.clear()
        currentAgentMessageItemId = null
        currentThoughtItemId = null
    }

    /**
     * 启动 Agent 子进程并完成 initialize + session/new。
     *
     * 会依次尝试预设的候选命令：ACP 的启动参数（`--acp` / `--experimental-acp`）
     * 随 CLI 版本漂移，逐个试过去，用户不必自己猜。
     */
    fun connect() {
        if (connectionState == AcpConnectionState.CONNECTING) return
        teardown()

        val candidates = AcpAgents.candidatesFor(preset, agentCommand)
        if (candidates.isEmpty()) {
            connectionState = AcpConnectionState.ERROR
            statusMessage = s(R.string.acp_error_empty_command)
            addNotice(s(R.string.acp_error_empty_command), isError = true)
            return
        }

        candidateCommands = candidates
        candidateIndex = 0
        canInstallMissing = false
        connectionState = AcpConnectionState.CONNECTING
        statusMessage = s(R.string.acp_status_checking_env)
        viewModelScope.launch { startNextCandidate() }
    }

    /**
     * 逐个候选做环境预检并启动。
     * 预检本身失败（超时等）时不拦，直接尝试启动，避免误伤可用的环境。
     */
    private suspend fun startNextCandidate() {
        val ctx = getApplication<Application>()
        var lastMissing: String? = null

        while (candidateIndex < candidateCommands.size) {
            val commandText = candidateCommands[candidateIndex]
            val command = splitCommand(commandText)
            if (command.isEmpty()) {
                candidateIndex++
                continue
            }
            val binary = command.first()

            when (val probe = withContext(Dispatchers.IO) {
                AcpEnvironment.probeBinary(ctx, binary)
            }) {
                is BinaryProbe.Found -> {
                    if (candidateIndex > 0) {
                        addNotice(s(R.string.acp_status_trying_alternative, commandText))
                    }
                    startAgent(ctx, command)
                    return
                }

                is BinaryProbe.Missing -> {
                    lastMissing = binary
                    candidateIndex++
                }

                is BinaryProbe.Failed -> {
                    appendStderr("环境预检未完成：${probe.reason}（继续尝试启动）")
                    startAgent(ctx, command)
                    return
                }
            }
        }

        connectionState = AcpConnectionState.ERROR
        val message = s(R.string.acp_error_binary_missing, lastMissing ?: agentCommand)
        statusMessage = message
        addNotice(message, isError = true)
        canInstallMissing = preset.supportsInstall
    }

    /**
     * 当前候选启动失败时，换下一个候选再试。
     * @return 是否已经安排了重试
     */
    private fun retryWithNextCandidate(reason: String): Boolean {
        if (candidateIndex + 1 >= candidateCommands.size) return false
        candidateIndex++
        val next = candidateCommands[candidateIndex]

        promptJob?.cancel()
        promptJob = null
        connection?.failAllPending(AcpConnectionLostException(reason))
        terminals?.shutdown()
        terminals = null
        transport?.close()
        transport = null
        connection = null
        sessionId = null

        appendStderr("$reason；改用候选命令重试：$next")
        connectionState = AcpConnectionState.CONNECTING
        statusMessage = s(R.string.acp_status_trying_alternative, next)
        viewModelScope.launch { startNextCandidate() }
        return true
    }

    private fun startAgent(context: Application, command: List<String>) {
        statusMessage = s(R.string.acp_status_starting)
        stderrLines.clear()
        stderrTail = ""

        val terminalManager = AcpTerminalManager(context)
        terminals = terminalManager

        val acpTransport = AcpTransport(
            context = context,
            command = command,
            workDir = resolveWorkDir(),
            extraEnv = emptyMap(),
            listener = object : AcpTransport.Listener {
                override fun onMessage(message: JSONObject) {
                    viewModelScope.launch { connection?.handleIncoming(message) }
                }

                override fun onStderr(line: String) {
                    viewModelScope.launch { appendStderr(line) }
                }

                override fun onExit(exitCode: Int) {
                    viewModelScope.launch { onAgentExited(exitCode) }
                }
            }
        )
        transport = acpTransport

        val acpConnection = AcpConnection(viewModelScope) { message -> acpTransport.send(message) }
        acpConnection.requestHandler = { method, params -> handleAgentRequest(method, params) }
        acpConnection.notificationHandler = { method, params -> handleNotification(method, params) }
        acpConnection.onFatalError = { error -> viewModelScope.launch { failConnection(error) } }
        connection = acpConnection

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { acpTransport.start() }
                initializeSession()
            } catch (e: Throwable) {
                failConnection(e)
            }
        }
    }

    /** 当前是否已经用给定配置连接成功（用于避免无谓的重连）。 */
    fun isConnectedWith(presetId: String, command: String): Boolean =
        connectionState == AcpConnectionState.READY &&
            agentPresetId == presetId &&
            agentCommand == command

    /**
     * 读取 Agent 自身日志（模型 / 网络错误只写在这里，协议层看不到）。
     */
    fun refreshAgentLog() {
        if (isLoadingLog) return
        isLoadingLog = true
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { AcpEnvironment.readAgentLog(ctx) }
            agentLog = lines.takeLast(MAX_AGENT_LOG_LINES)
            isLoadingLog = false
        }
    }

    /** 用于提示文案的 Agent 名称。 */
    private fun agentDisplayName(): String = agentInfo ?: preset.displayName


    /** 运行环境自检并把结果写入 [selfCheckReport]。 */
    fun runSelfCheck(target: AcpAgentPreset = preset, command: String = agentCommand) {
        if (isSelfChecking) return
        isSelfChecking = true
        val ctx = getApplication<Application>()
        val binary = splitCommand(command).firstOrNull().orEmpty()
        val workDir = resolveWorkDir()
        viewModelScope.launch {
            selfCheckReport = withContext(Dispatchers.IO) {
                AcpEnvironment.selfCheck(ctx, binary, workDir)
            }
            isSelfChecking = false
        }
    }

    // ------------------------------------------------------------------
    // 一键安装 / 版本检测
    // ------------------------------------------------------------------

    /**
     * 查询「容器内已安装版本 / npm 上最新版本」。
     *
     * @param target 要查询的 Agent 预设，默认取当前已保存的预设。
     *        设置界面里用户可能刚切换下拉框还没保存，这里显式传入可避免张冠李戴。
     * @param command 用于二进制版回退探测的启动命令。
     */
    fun refreshVersions(target: AcpAgentPreset = preset, command: String = agentCommand) {
        val packageName = target.primaryPackage
        installedVersion = null
        latestVersion = null

        val ctx = getApplication<Application>()
        viewModelScope.launch {
            isCheckingVersion = true
            val installed = withContext(Dispatchers.IO) {
                if (packageName != null) {
                    // npm 包优先；二进制分发的 CLI（如 opencode）npm list 查不到，退回到 --version 探测
                    AcpEnvironment.installedNpmVersion(ctx, packageName)
                        ?: AcpEnvironment.installedBinaryVersion(ctx, command)
                } else {
                    // 自定义预设的命令由用户填写，不猜测版本，避免把解释器版本当成 Agent 版本
                    null
                }
            }
            val latest = if (packageName != null) {
                withContext(Dispatchers.IO) { AcpEnvironment.npmLatestVersion(packageName) }
            } else {
                null
            }
            installedVersion = installed
            latestVersion = latest
            isCheckingVersion = false
        }
    }

    /** 一键安装到最新版；已装过则等同于更新。 */
    fun installSelectedAgent(target: AcpAgentPreset = preset) {
        val current = target
        if (!current.supportsInstall || isInstalling) return

        val ctx = getApplication<Application>()
        isInstalling = true
        installLog = ""
        synchronized(installBuffer) { installBuffer.setLength(0) }

        // 节流刷新，避免每一行输出都切一次主线程
        installTicker = viewModelScope.launch {
            while (isActive) {
                installLog = synchronized(installBuffer) { installBuffer.toString() }.takeLast(20_000)
                delay(400)
            }
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AcpEnvironment.installNpmPackages(ctx, current.npmPackages) { line ->
                    synchronized(installBuffer) { installBuffer.append(line).append('\n') }
                }
            }

            installTicker?.cancel()
            installTicker = null
            installLog = synchronized(installBuffer) { installBuffer.toString() }.takeLast(20_000)
            isInstalling = false

            if (result.isSuccess) {
                installLog += "\n" + s(R.string.acp_status_install_done)
                addNotice(s(R.string.acp_status_install_done))
                canInstallMissing = false
                refreshVersions(current, current.defaultCommand)
                // 装完顺手连一次，省掉用户再点一下
                if (connectionState != AcpConnectionState.READY) connect()
            } else {
                val reason = if (result.timedOut) {
                    s(R.string.acp_install_timeout)
                } else {
                    "exit ${result.exitCode}"
                }
                val message = s(R.string.acp_error_install_failed, reason)
                installLog += "\n$message"
                addNotice(message, isError = true)
            }
        }
    }

    /** 断开连接并清理资源。 */
    fun disconnect() {
        teardown()
        connectionState = AcpConnectionState.DISCONNECTED
        statusMessage = null
        addNotice(s(R.string.acp_status_disconnected))
    }

    /** 发送一轮用户消息。 */
    fun sendPrompt(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return

        val acpConnection = connection
        val sid = sessionId
        if (acpConnection == null || sid == null || connectionState != AcpConnectionState.READY) {
            addNotice(s(R.string.acp_error_not_connected), isError = true)
            return
        }
        if (isPrompting) {
            addNotice(s(R.string.acp_error_busy), isError = true)
            return
        }

        timeline.add(AcpUserMessage(newId("user"), text))
        currentAgentMessageItemId = null
        currentThoughtItemId = null
        sessionSnapshots.clear()
        pendingSnapshots = emptyList()
        turnBaselineRoot = null
        turnBaselineSig = emptyMap()
        turnBaselineContent = emptyMap()
        isPrompting = true

        // 若长时间没有任何输出，提示用户去看 Agent 日志（Agent 出错时常常只写日志、不回协议）
        promptWatchJob?.cancel()
        promptWatchJob = viewModelScope.launch {
            delay(PROMPT_SLOW_NOTICE_MILLIS)
            if (isPrompting) {
                addNotice(s(R.string.acp_status_prompt_slow, agentDisplayName()))
                refreshAgentLog()
            }
        }

        promptJob = viewModelScope.launch {
            captureTurnBaseline()
            try {
                val result = acpConnection.request(
                    Acp.M_SESSION_PROMPT,
                    JSONObject().apply {
                        put("sessionId", sid)
                        put(
                            "prompt",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("type", Acp.C_TEXT)
                                    put("text", text)
                                }
                            )
                        )
                    },
                    timeoutMillis = AcpConnection.PROMPT_TIMEOUT_MILLIS
                )
                when (result.strOrNull("stopReason")) {
                    Acp.STOP_CANCELLED -> addNotice(s(R.string.acp_status_cancelled))
                    Acp.STOP_MAX_TOKENS -> addNotice(s(R.string.acp_status_max_tokens))
                    Acp.STOP_MAX_TURN_REQUESTS -> addNotice(s(R.string.acp_status_max_turns))
                    Acp.STOP_REFUSAL -> addNotice(s(R.string.acp_status_refusal), isError = true)
                    else -> Unit
                }
            } catch (e: CancellationException) {
                // 用户中断：提示由 cancel() 负责，这里只需向上传播
                throw e
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_prompt, e.message ?: e.toString()), isError = true)
                refreshAgentLog()
            } finally {
                isPrompting = false
                promptJob = null
                promptWatchJob?.cancel()
                promptWatchJob = null
                recordTurnSnapshots()
                publishSnapshots()
            }
        }
    }

    /**
     * 中断当前回合。
     *
     * 先发 `session/cancel` 通知，再直接取消本地等待的协程 —— 有些 Agent 在
     * 内部重试（如限流）时不会主动回 stopReason，只发通知会让界面一直转圈。
     */
    fun cancel() {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return
        pendingPermission?.complete(null)
        pendingPermission = null
        permissionPrompt = null
        runCatching {
            acpConnection.notify(Acp.M_SESSION_CANCEL, JSONObject().put("sessionId", sid))
        }
        promptWatchJob?.cancel()
        promptWatchJob = null
        if (isPrompting) {
            promptJob?.cancel()
            promptJob = null
            isPrompting = false
            addNotice(s(R.string.acp_status_cancelled))
        }
    }

    /** 回应用户授权请求；optionId 为 null 表示取消。 */
    fun respondPermission(optionId: String?) {
        val deferred = pendingPermission ?: return
        pendingPermission = null
        permissionPrompt = null
        deferred.complete(optionId)
    }

    /**
     * 设置一个会话配置项（模型 / 模式 / 思考等级等）。
     *
     * 现代 ACP 统一走 `session/set_config_option`，Agent 会回传完整配置状态。
     */
    fun setConfigOption(configId: String, value: Any) {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            try {
                val result = acpConnection.request(
                    Acp.M_SESSION_SET_CONFIG_OPTION,
                    JSONObject().apply {
                        put("sessionId", sid)
                        put("configId", configId)
                        if (value is Boolean) {
                            put("type", Acp.CTYPE_BOOLEAN)
                            put("value", value)
                        } else {
                            put("value", value.toString())
                        }
                    }
                )
                result.arrOrNull("configOptions")?.let { applyConfigOptions(Acp.parseConfigOptions(it)) }
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_set_config, e.message ?: e.toString()), isError = true)
            }
        }
    }

    /** 切换会话模式（兼容仅实现旧 `session/set_mode` 的 Agent）。 */
    fun setMode(modeId: String) {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            try {
                acpConnection.request(
                    Acp.M_SESSION_SET_MODE,
                    JSONObject().apply {
                        put("sessionId", sid)
                        put("modeId", modeId)
                    }
                )
                currentModeId = modeId
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_set_mode, e.message ?: e.toString()), isError = true)
            }
        }
    }

    /** 重新执行认证（Agent 声明了 authMethods 时可用）。 */
    fun authenticate(methodId: String) {
        val acpConnection = connection ?: return
        viewModelScope.launch {
            try {
                acpConnection.request(Acp.M_AUTHENTICATE, JSONObject().put("methodId", methodId))
                activeAuthMethodId = methodId
                isAuthenticated = true
                addNotice(s(R.string.acp_status_authenticated))
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_auth, e.message ?: e.toString()), isError = true)
            }
        }
    }

    // ------------------------------------------------------------------
    // 会话管理（session/list、session/new、session/load、session/fork、session/close）
    // ------------------------------------------------------------------

    /** 拉取历史会话列表。Agent 不支持时静默降级（隐藏会话入口）。 */
    fun refreshSessions() {
        val acpConnection = connection ?: return
        if (connectionState != AcpConnectionState.READY) return
        isLoadingSessions = true
        viewModelScope.launch {
            try {
                val result = acpConnection.request(
                    Acp.M_SESSION_LIST,
                    JSONObject().put("cwd", resolveWorkDir())
                )
                val array = result.arrOrNull("sessions")
                sessions = if (array == null) emptyList() else (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { item ->
                        val id = item.strOrNull("sessionId") ?: return@let null
                        AcpSessionSummary(
                            sessionId = id,
                            title = item.strOrNull("title"),
                            cwd = item.strOrNull("cwd"),
                            updatedAt = item.strOrNull("updatedAt")
                        )
                    }
                }
                supportsSessionList = true
            } catch (e: AcpException) {
                // -32601：Agent 未实现 session/list
                if (e.code == Acp.ERR_METHOD_NOT_FOUND) {
                    supportsSessionList = false
                    sessions = emptyList()
                } else {
                    addNotice(s(R.string.acp_error_session_list, e.message ?: e.toString()), isError = true)
                }
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_session_list, e.message ?: e.toString()), isError = true)
            } finally {
                isLoadingSessions = false
            }
        }
    }

    /** 新建会话（清空时间线）。 */
    fun newSession() {
        val acpConnection = connection ?: return
        if (connectionState != AcpConnectionState.READY) return
        viewModelScope.launch {
            try {
                val result = acpConnection.request(
                    Acp.M_SESSION_NEW,
                    JSONObject().apply {
                        put("cwd", resolveWorkDir())
                        put("mcpServers", JSONArray())
                    }
                )
                applySessionResult(result)
                timeline.clear()
                addNotice(s(R.string.acp_status_new_session))
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_session_new, e.message ?: e.toString()), isError = true)
            }
        }
    }

    /** 切换（加载）到历史会话；Agent 会重放历史，据此重建时间线。 */
    fun loadSession(id: String) {
        val acpConnection = connection ?: return
        if (connectionState != AcpConnectionState.READY) return
        if (id == sessionId) return
        viewModelScope.launch {
            try {
                timeline.clear()
                currentAgentMessageItemId = null
                currentThoughtItemId = null
                val result = acpConnection.request(
                    Acp.M_SESSION_LOAD,
                    JSONObject().apply {
                        put("sessionId", id)
                        put("cwd", resolveWorkDir())
                        put("mcpServers", JSONArray())
                    },
                    timeoutMillis = AcpConnection.DEFAULT_TIMEOUT_MILLIS
                )
                applySessionResult(result, forcedId = id)
                addNotice(s(R.string.acp_status_session_loaded))
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_session_load, e.message ?: e.toString()), isError = true)
            }
        }
    }

    /** 从当前会话分叉出一个新会话。 */
    fun forkSession() {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return
        if (connectionState != AcpConnectionState.READY) return
        viewModelScope.launch {
            try {
                val result = acpConnection.request(
                    Acp.M_SESSION_FORK,
                    JSONObject().apply {
                        put("sessionId", sid)
                        put("cwd", resolveWorkDir())
                        put("mcpServers", JSONArray())
                    }
                )
                applySessionResult(result)
                addNotice(s(R.string.acp_status_session_forked))
            } catch (e: Throwable) {
                addNotice(s(R.string.acp_error_session_fork, e.message ?: e.toString()), isError = true)
            }
        }
    }

    private fun applySessionResult(result: JSONObject, forcedId: String? = null) {
        val sid = forcedId ?: result.strOrNull("sessionId")
        if (sid != null) sessionId = sid
        applyModes(result.objOrNull("modes"))
        if (result.has("configOptions")) {
            applyConfigOptions(Acp.parseConfigOptions(result.arrOrNull("configOptions")))
        }
        connectionState = AcpConnectionState.READY
        statusMessage = null
    }

    // ------------------------------------------------------------------
    // 撤销 Agent 对文件的修改（客户端快照）
    // ------------------------------------------------------------------

    /** 回合开始前拍一份工作区基线（签名 + 小文件原始内容），供兜底撤销使用。 */
    private suspend fun captureTurnBaseline() {
        val root = File(resolveWorkDir())
        if (!root.isDirectory) return
        turnBaselineRoot = root
        withContext(Dispatchers.IO) {
            turnBaselineSig = WorkspaceWatcher.snapshot(root)
            val contents = HashMap<String, String?>()
            var budget = MAX_BASELINE_CONTENT_BYTES
            turnBaselineSig.keys.forEach { path ->
                if (budget <= 0) return@forEach
                val f = File(path)
                if (f.length() > MAX_SNAPSHOT_FILE_BYTES) {
                    contents[path] = null
                    return@forEach
                }
                val read = runCatching { f.readText() }.getOrNull()
                contents[path] = read
                if (read != null) budget -= read.length
            }
            turnBaselineContent = contents
        }
    }

    /**
     * 回合结束后，对比基线与现状，把 Agent 直接改盘造成的改动也纳入「撤销」范围。
     *
     * Agent 通过 `fs/write_text_file` 写的文件已在 [writeTextFile] 记录过精确快照，
     * 这里只补记那些绕开协议、直接在容器内改动的文件。
     */
    private suspend fun recordTurnSnapshots() {
        val root = turnBaselineRoot ?: return
        val baseline = turnBaselineSig
        if (baseline.isEmpty()) return
        withContext(Dispatchers.IO) {
            val current = WorkspaceWatcher.snapshot(root)
            val changed = HashSet<String>()
            current.forEach { (path, signature) ->
                if (baseline[path] != signature) changed += path
            }
            baseline.keys.forEach { path -> if (path !in current) changed += path }
            changed.forEach { path ->
                if (sessionSnapshots.containsKey(path)) return@forEach
                val existedBefore = baseline.containsKey(path)
                val previous = if (existedBefore) turnBaselineContent[path] else null
                // 存在过但内容未采集（文件过大）时无法安全还原，跳过
                if (existedBefore && previous == null) return@forEach
                sessionSnapshots[path] = AcpFileSnapshot(path, existedBefore, previous)
            }
        }
        turnBaselineRoot = null
    }

    private fun publishSnapshots() {
        pendingSnapshots = sessionSnapshots.values.toList()
    }

    /** 隐藏「撤销修改」提示（保留磁盘现状）。 */
    fun dismissSnapshots() {
        pendingSnapshots = emptyList()
        sessionSnapshots.clear()
    }

    /**
     * 把本轮 Agent 修改过的文件回退到修改前的状态，并通知 UI 刷新。
     * 新建的文件会被删除。
     */
    fun undoLastEdits() {
        val snapshots = pendingSnapshots
        if (snapshots.isEmpty()) return
        viewModelScope.launch {
            val touched = withContext(Dispatchers.IO) {
                val paths = mutableListOf<String>()
                snapshots.forEach { snap ->
                    try {
                        val file = File(snap.path)
                        if (snap.existedBefore) {
                            file.parentFile?.mkdirs()
                            file.writeText(snap.previousContent.orEmpty())
                        } else {
                            if (file.exists()) file.delete()
                        }
                        paths += snap.path
                    } catch (_: Exception) {}
                }
                paths
            }
            FileChangeBus.notifyChanged(touched)
            addNotice(s(R.string.acp_status_undo_done, touched.size))
            dismissSnapshots()
        }
    }

    override fun onCleared() {
        teardown()
        super.onCleared()
    }

    // ------------------------------------------------------------------
    // 连接生命周期
    // ------------------------------------------------------------------

    private fun teardown() {
        promptJob?.cancel()
        promptJob = null
        pendingPermission?.complete(null)
        pendingPermission = null
        permissionPrompt = null
        isPrompting = false
        connection?.failAllPending(AcpConnectionLostException("连接已断开"))
        terminals?.shutdown()
        terminals = null
        transport?.close()
        transport = null
        connection = null
        sessionId = null
        currentModeId = null
        sessionModes = emptyList()
        availableCommands = emptyList()
        configOptions = emptyList()
        authMethods = emptyList()
        activeAuthMethodId = null
        isAuthenticated = false
        agentInfo = null
        sessions = emptyList()
        sessionSnapshots.clear()
        pendingSnapshots = emptyList()
    }

    private suspend fun initializeSession() {
        val acpConnection = connection ?: return

        statusMessage = s(R.string.acp_status_initializing)
        val initResult = acpConnection.request(
            Acp.M_INITIALIZE,
            JSONObject().apply {
                put("protocolVersion", Acp.PROTOCOL_VERSION)
                put(
                    "clientCapabilities",
                    JSONObject().apply {
                        put(
                            "fs",
                            JSONObject().apply {
                                put("readTextFile", true)
                                put("writeTextFile", true)
                            }
                        )
                        put("terminal", true)
                        // 声明支持布尔型配置项（session.configOptions.boolean）
                        put(
                            "session",
                            JSONObject().apply {
                                put(
                                    "configOptions",
                                    JSONObject().apply {
                                        put("boolean", JSONObject())
                                    }
                                )
                            }
                        )
                    }
                )
                put(
                    "clientInfo",
                    JSONObject().apply {
                        put("name", "webide")
                        put("title", "WebIDE")
                        put("version", appVersion())
                    }
                )
            }
        )

        val negotiated = initResult.optInt("protocolVersion", 0)
        if (negotiated != Acp.PROTOCOL_VERSION) {
            throw AcpException(
                Acp.ERR_INVALID_REQUEST,
                s(R.string.acp_error_version_mismatch, negotiated)
            )
        }

        agentInfo = initResult.objOrNull("agentInfo")?.let { info ->
            val title = info.strOrNull("title") ?: info.strOrNull("name")
            listOfNotNull(title, info.strOrNull("version")?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { null }
        }

        // 记录 Agent 声明的认证方式，供「重新登录」入口使用；有则自动尝试第一种
        authMethods = initResult.arrOrNull("authMethods")?.let { methods ->
            (0 until methods.length()).mapNotNull { index ->
                methods.optJSONObject(index)?.let { method ->
                    val methodId = method.strOrNull("id") ?: return@let null
                    AcpAuthMethod(
                        id = methodId,
                        name = method.strOrNull("name") ?: methodId,
                        description = method.strOrNull("description")
                    )
                }
            }
        } ?: emptyList()

        authMethods.firstOrNull()?.let { method ->
            runCatching {
                acpConnection.request(
                    Acp.M_AUTHENTICATE,
                    JSONObject().put("methodId", method.id)
                )
                isAuthenticated = true
                activeAuthMethodId = method.id
            }
        }

        statusMessage = s(R.string.acp_status_creating_session)
        val newSession = acpConnection.request(
            Acp.M_SESSION_NEW,
            JSONObject().apply {
                put("cwd", resolveWorkDir())
                put("mcpServers", JSONArray())
            }
        )

        val sid = newSession.strOrNull("sessionId")
            ?: throw AcpException(Acp.ERR_INTERNAL, s(R.string.acp_error_no_session))
        sessionId = sid
        applyModes(newSession.objOrNull("modes"))
        applyConfigOptions(Acp.parseConfigOptions(newSession.arrOrNull("configOptions")))

        connectionState = AcpConnectionState.READY
        statusMessage = null
        addNotice(s(R.string.acp_status_connected, agentInfo ?: preset.displayName))
    }

    private fun onAgentExited(exitCode: Int) {
        // 还没握手成功就退出 —— 多半是启动参数不对，换候选命令再试一次
        if (sessionId == null &&
            retryWithNextCandidate("Agent 进程退出（退出码 $exitCode）")
        ) {
            return
        }

        val wasStarting = connectionState == AcpConnectionState.CONNECTING
        pendingPermission?.complete(null)
        pendingPermission = null
        permissionPrompt = null
        isPrompting = false
        connection?.failAllPending(AcpConnectionLostException("Agent 进程已退出"))
        terminals?.shutdown()

        val detail = stderrTail.trim().lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(6)
            .joinToString("\n")

        val message = if (wasStarting && detail.isNotBlank()) {
            s(R.string.acp_error_agent_start_failed_detail, exitCode, detail)
        } else {
            s(R.string.acp_error_agent_exited, exitCode)
        }
        connectionState = AcpConnectionState.ERROR
        statusMessage = message
        addNotice(message, isError = true)
    }

    private fun failConnection(error: Throwable) {
        if (sessionId == null &&
            retryWithNextCandidate(error.message ?: error.toString())
        ) {
            return
        }

        val wasStarting = connectionState == AcpConnectionState.CONNECTING
        connection?.failAllPending(error)
        pendingPermission?.complete(null)
        pendingPermission = null
        permissionPrompt = null
        isPrompting = false

        val base = error.message ?: error.toString()
        val detail = if (wasStarting) {
            stderrTail.trim().lineSequence()
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(6)
                .joinToString("\n")
        } else {
            ""
        }
        val message = if (detail.isNotBlank()) "$base\n$detail" else base

        connectionState = AcpConnectionState.ERROR
        statusMessage = message
        addNotice(message, isError = true)
    }

    private fun appendStderr(line: String) {
        stderrLines.addLast(line)
        while (stderrLines.size > MAX_STDERR_LINES) stderrLines.removeFirst()
        stderrTail = stderrLines.joinToString("\n")
    }

    private fun appVersion(): String = try {
        val ctx = getApplication<Application>()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    private fun resolveWorkDir(): String {
        val ctx = getApplication<Application>()
        val project = AlpineManager.currentProject
        if (!project.isNullOrBlank() && File(project).isDirectory) return project
        val workspace = WorkspaceManager.getWorkspacePath(ctx)
        return if (File(workspace).isDirectory) workspace else "/root"
    }

    // ------------------------------------------------------------------
    // Agent -> Client 请求
    // ------------------------------------------------------------------

    private suspend fun handleAgentRequest(method: String, params: JSONObject): JSONObject =
        when (method) {
            Acp.M_REQUEST_PERMISSION -> handlePermissionRequest(params)
            Acp.M_FS_READ_TEXT_FILE -> withContext(Dispatchers.IO) { readTextFile(params) }
            Acp.M_FS_WRITE_TEXT_FILE -> withContext(Dispatchers.IO) { writeTextFile(params) }
            Acp.M_TERMINAL_CREATE -> withContext(Dispatchers.IO) {
                terminals?.create(params) ?: JSONObject()
            }
            Acp.M_TERMINAL_OUTPUT -> terminals?.output(params) ?: JSONObject()
            Acp.M_TERMINAL_WAIT_FOR_EXIT -> terminals?.waitForExit(params) ?: JSONObject()
            Acp.M_TERMINAL_KILL -> {
                terminals?.kill(params)
                JSONObject()
            }
            Acp.M_TERMINAL_RELEASE -> {
                terminals?.release(params)
                JSONObject()
            }
            else -> throw AcpException(Acp.ERR_METHOD_NOT_FOUND, "未实现的方法: $method")
        }

    private suspend fun handlePermissionRequest(params: JSONObject): JSONObject {
        val toolCall = params.objOrNull("toolCall") ?: JSONObject()
        val options = params.arrOrEmpty("options").let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { option ->
                    AcpPermissionOption(
                        optionId = option.strOrEmpty("optionId"),
                        name = option.strOrEmpty("name"),
                        kind = option.strOrEmpty("kind")
                    )
                }
            }
        }
        if (options.isEmpty()) return Acp.permissionCancelledResult()

        if (autoApprove) {
            val allow = options.firstOrNull {
                it.kind == Acp.PERM_ALLOW_ONCE || it.kind == Acp.PERM_ALLOW_ALWAYS
            } ?: options.first()
            return Acp.permissionSelectedResult(allow.optionId)
        }

        val deferred = CompletableDeferred<String?>()
        pendingPermission = deferred
        permissionPrompt = AcpPermissionPrompt(
            title = toolCall.strOrNull("title").orEmpty().ifEmpty { s(R.string.acp_permission_default_title) },
            kind = toolCall.strOrEmpty("kind"),
            toolCallId = toolCall.strOrEmpty("toolCallId"),
            options = options,
            rawInput = jsonValueToDisplay(toolCall.opt("rawInput"))
        )

        val chosen = deferred.await()
        if (pendingPermission === deferred) pendingPermission = null
        permissionPrompt = null
        return if (chosen == null) {
            Acp.permissionCancelledResult()
        } else {
            Acp.permissionSelectedResult(chosen)
        }
    }

    private fun readTextFile(params: JSONObject): JSONObject {
        val path = params.strOrNull("path")
            ?: throw AcpException(Acp.ERR_INVALID_PARAMS, "缺少 path")
        val file = File(path)
        if (!file.isFile) throw AcpException(Acp.ERR_INVALID_PARAMS, "文件不存在: $path")

        var content = file.readText()
        val startLine = params.intOrNull("line")
        val limit = params.intOrNull("limit")
        if (startLine != null || limit != null) {
            val all = content.split("\n")
            val from = ((startLine ?: 1) - 1).coerceIn(0, all.size)
            val to = if (limit != null) (from + limit).coerceIn(from, all.size) else all.size
            content = all.subList(from, to).joinToString("\n")
        }
        return JSONObject().put("content", content)
    }

    private fun writeTextFile(params: JSONObject): JSONObject {
        val path = params.strOrNull("path")
            ?: throw AcpException(Acp.ERR_INVALID_PARAMS, "缺少 path")
        val file = File(path)
        // 记录写前快照，供「撤销 Agent 修改」使用（同一路径只记第一次）
        val key = file.absolutePath
        if (!sessionSnapshots.containsKey(key)) {
            val existed = file.isFile
            val previous = if (existed) runCatching { file.readText() }.getOrNull() else null
            sessionSnapshots[key] = AcpFileSnapshot(key, existed, previous)
        }
        file.parentFile?.mkdirs()
        file.writeText(params.strOrEmpty("content"))
        // 通知文件树 / 已打开标签页刷新，无需用户手动重开
        FileChangeBus.notifyChanged(key)
        return JSONObject()
    }

    // ------------------------------------------------------------------
    // Agent -> Client 通知
    // ------------------------------------------------------------------

    private fun handleNotification(method: String, params: JSONObject) {
        if (method != Acp.N_SESSION_UPDATE) return
        val update = params.objOrNull("update") ?: return
        when (update.strOrNull("sessionUpdate")) {
            Acp.U_AGENT_MESSAGE_CHUNK -> appendChunk(update, agentMessage = true)
            Acp.U_AGENT_THOUGHT_CHUNK -> appendChunk(update, agentMessage = false)
            Acp.U_USER_MESSAGE_CHUNK -> Unit
            Acp.U_TOOL_CALL -> upsertToolCall(update)
            Acp.U_TOOL_CALL_UPDATE -> upsertToolCall(update)
            Acp.U_PLAN -> applyPlan(update)
            Acp.U_AVAILABLE_COMMANDS_UPDATE -> applyCommands(update)
            Acp.U_CONFIG_OPTION_UPDATE -> applyConfigOptions(Acp.parseConfigOptions(update.arrOrNull("configOptions")))
            Acp.U_CURRENT_MODE_UPDATE -> currentModeId = update.strOrNull("currentModeId")
            Acp.U_NOTICE -> {
                val text = update.strOrNull("message")
                    ?: update.strOrNull("text")
                    ?: update.strOrNull("title")
                if (!text.isNullOrBlank()) addNotice(text)
            }
            else -> Unit
        }
    }

    private fun appendChunk(update: JSONObject, agentMessage: Boolean) {
        val text = contentBlockToText(update.objOrNull("content")) ?: return
        if (text.isEmpty()) return
        val messageId = update.strOrNull("messageId")

        if (agentMessage) {
            val key = messageId?.let { "agent:$it" } ?: currentAgentMessageItemId
            val index = key?.let { id -> timeline.indexOfFirst { it.id == id } } ?: -1
            val existing = if (index >= 0) timeline[index] else null
            if (existing is AcpAgentMessage) {
                timeline[index] = existing.copy(text = existing.text + text)
            } else {
                val itemId = key ?: newId("agent")
                timeline.add(AcpAgentMessage(itemId, text))
                currentAgentMessageItemId = itemId
            }
        } else {
            val key = messageId?.let { "thought:$it" } ?: currentThoughtItemId
            val index = key?.let { id -> timeline.indexOfFirst { it.id == id } } ?: -1
            val existing = if (index >= 0) timeline[index] else null
            if (existing is AcpThoughtMessage) {
                timeline[index] = existing.copy(text = existing.text + text)
            } else {
                val itemId = key ?: newId("thought")
                timeline.add(AcpThoughtMessage(itemId, text))
                currentThoughtItemId = itemId
            }
        }
    }

    private fun upsertToolCall(update: JSONObject) {
        val toolCallId = update.strOrNull("toolCallId") ?: return
        val contents = parseToolContents(update.arrOrNull("content"))
        val locations = parseLocations(update.arrOrNull("locations"))
        val rawInput = jsonValueToDisplay(update.opt("rawInput"))
        val rawOutput = jsonValueToDisplay(update.opt("rawOutput"))

        val index = timeline.indexOfFirst { it is AcpToolCallItem && it.toolCallId == toolCallId }
        if (index >= 0) {
            val old = timeline[index] as AcpToolCallItem
            timeline[index] = old.copy(
                title = update.strOrNull("title") ?: old.title,
                kind = update.strOrNull("kind") ?: old.kind,
                status = update.strOrNull("status") ?: old.status,
                contents = if (contents.isEmpty()) old.contents else contents,
                locations = if (locations.isEmpty()) old.locations else locations,
                rawInput = rawInput ?: old.rawInput,
                rawOutput = rawOutput ?: old.rawOutput
            )
        } else {
            timeline.add(
                AcpToolCallItem(
                    id = newId("tool"),
                    toolCallId = toolCallId,
                    title = update.strOrNull("title").orEmpty(),
                    kind = update.strOrEmpty("kind").ifEmpty { Acp.KIND_OTHER },
                    status = update.strOrEmpty("status").ifEmpty { Acp.STATUS_PENDING },
                    contents = contents,
                    locations = locations,
                    rawInput = rawInput,
                    rawOutput = rawOutput
                )
            )
        }
    }

    private fun parseToolContents(array: JSONArray?): List<AcpToolContent> {
        if (array == null) return emptyList()
        val result = mutableListOf<AcpToolContent>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            when (item.optString("type")) {
                Acp.TC_DIFF -> result.add(
                    AcpToolContent.Diff(
                        path = item.strOrEmpty("path"),
                        oldText = item.strOrNull("oldText"),
                        newText = item.strOrEmpty("newText")
                    )
                )

                Acp.TC_TERMINAL -> result.add(AcpToolContent.TerminalRef(item.strOrEmpty("terminalId")))

                else -> contentBlockToText(item.objOrNull("content"))
                    ?.let { result.add(AcpToolContent.Text(it)) }
            }
        }
        return result
    }

    private fun parseLocations(array: JSONArray?): List<AcpToolLocation> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { location ->
                AcpToolLocation(
                    path = location.strOrEmpty("path"),
                    line = location.intOrNull("line")
                )
            }
        }
    }

    private fun applyPlan(update: JSONObject) {
        val entries = update.arrOrEmpty("entries").let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { entry ->
                    AcpPlanEntry(
                        content = entry.strOrEmpty("content"),
                        priority = entry.strOrEmpty("priority"),
                        status = entry.strOrEmpty("status")
                    )
                }
            }
        }
        val index = timeline.indexOfFirst { it is AcpPlanItem }
        if (index >= 0) {
            timeline[index] = AcpPlanItem(timeline[index].id, entries)
        } else {
            timeline.add(AcpPlanItem(newId("plan"), entries))
        }
    }

    private fun applyCommands(update: JSONObject) {
        availableCommands = update.arrOrEmpty("availableCommands").let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { command ->
                    AcpSlashCommand(
                        name = command.strOrEmpty("name"),
                        description = command.strOrEmpty("description")
                    )
                }
            }
        }
    }

    private fun applyConfigOptions(options: List<AcpConfigOption>) {
        configOptions = options
        // 同步旧字段：部分 Agent 仅通过 configOptions 暴露模式
        options.firstOrNull { it.category == Acp.CAT_MODE }?.let { mode ->
            currentModeId = mode.currentValueString
        }
    }

    private fun applyModes(modes: JSONObject?) {
        if (modes == null) return
        currentModeId = modes.strOrNull("currentModeId")
        sessionModes = modes.arrOrEmpty("availableModes").let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { mode ->
                    AcpSessionMode(
                        id = mode.strOrEmpty("id"),
                        name = mode.strOrEmpty("name"),
                        description = mode.strOrEmpty("description")
                    )
                }
            }
        }
    }

    private fun addNotice(text: String, isError: Boolean = false) {
        timeline.add(AcpNoticeMessage(newId("notice"), text, isError))
    }
}
