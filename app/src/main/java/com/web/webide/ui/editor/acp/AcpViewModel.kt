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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    /** 记住每个 Agent 上次选择的配置项（模型 / 模式 / 思考等级）。 */
    private val configPrefs = application.getSharedPreferences("acp_config", Context.MODE_PRIVATE)

    /** 每个 Agent 的环境变量（API Key / Base URL 等），启动子进程时注入。 */
    private val envPrefs = application.getSharedPreferences("acp_env", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    var agentPresetId by mutableStateOf(prefs.getString(KEY_AGENT_ID, AcpAgents.DEFAULT.id) ?: AcpAgents.DEFAULT.id)
        private set

    var agentCommand by mutableStateOf(
        prefs.getString(KEY_COMMAND, AcpAgents.DEFAULT.defaultCommand) ?: AcpAgents.DEFAULT.defaultCommand
    )
        private set

    /** 当前 Agent 的环境变量（明文保存在私有 prefs，仅本机可见）。 */
    var agentEnv by mutableStateOf(loadAgentEnv(agentPresetId))
        private set

    /** 启动子进程时实际使用的环境变量快照，用于判断「设置改了但没重连」。 */
    private var connectedEnv: Map<String, String> = emptyMap()

    /** 兼容旧面板里的「自动授权」开关；真实策略由 [permissions] 管。 */
    val autoApprove: Boolean get() = permissions.autoApprove

    /** 工具权限策略（级别 + 记住的允许/拒绝类型 + 审计记录）。 */
    val permissions = AcpPermissionController(application)

    /** 当前会话的 token / 上下文统计。 */
    val usage = AcpUsageTracker()

    /** 项目记忆与行为指令（文件 + 首轮注入）。 */
    val memory = AcpMemoryController(application)

    /** 项目技能（.webide/skills 下的 Markdown）与管理状态。 */
    val skills = AcpSkillsController(application)

    /** MCP 服务器配置；新建/加载会话时随 `mcpServers` 下发给 Agent。 */
    val mcp = AcpMcpController(application)

    private val sessionStore = AcpSessionStore(application)

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

    /** Agent 是否正在重放历史（session/load 期间）。 */
    var isRestoringSession by mutableStateOf(false)
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

    /** 是否已按 Agent 声明的认证方式完成登录。客户端无法真正确认，仅作展示。 */
    var isAuthenticated by mutableStateOf(false)
        private set

    /** 已向 Agent 发起过登录请求；不代表登录真的完成。 */
    var authRequested by mutableStateOf(false)
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

    // ---- 本地会话（不依赖 Agent，离线可见） ----

    /** 当前工作区的本地会话索引。 */
    var localSessions by mutableStateOf<List<AcpSessionMeta>>(emptyList())
        private set

    var isLoadingLocalSessions by mutableStateOf(false)
        private set

    /** 当前工作区本地会话占用的字节数。 */
    var storageBytes by mutableStateOf(0L)
        private set

    /** 当前会话的本地元数据。 */
    var currentSessionMeta by mutableStateOf<AcpSessionMeta?>(null)
        private set

    /** 时间线来自本地回放、Agent 尚未接管这段上下文时为 true。 */
    var isReadOnlyReplay by mutableStateOf(false)
        private set

    /** 本地会话搜索框内容（防抖后写入 [localSessions]）。 */
    var localSearchQuery by mutableStateOf("")
        private set

    /** 当前解析出的工作区路径。 */
    var workspacePath by mutableStateOf<String?>(null)
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

    /** 重连时优先尝试恢复的 Agent 会话 id（上次打开的对话）。 */
    private var resumeSessionId: String? = null

    private val installBuffer = StringBuilder()
    private var installTicker: Job? = null

    /** 会话落盘的防抖任务。 */
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var workspacePrepared = false

    /** Agent 正在重放历史（session/load），此时不要落盘半成品。 */
    private var isReplaying = false

    /** 重放用户消息时的前缀裁剪状态。 */
    private var replayUserBuffer: StringBuilder? = null
    private var replayUserItemId: String? = null
    private var replayPrefixStripped = false

    init {
        viewModelScope.launch {
            runCatching { prepareWorkspace() }
        }
    }

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
        agentEnv = loadAgentEnv(agentPresetId)
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

    /**
     * 应用设置页里的「Agent + 启动命令」。
     *
     * 与 [selectAgent] 的区别：不会用预设默认命令覆盖用户手改的命令。
     * 环境变量由调用方先通过 [saveAgentEnv] 落盘，这里只负责切换到对应 Agent 的环境。
     */
    fun applyAgentConfig(preset: AcpAgentPreset, command: String) {
        val presetChanged = preset.id != agentPresetId
        agentPresetId = preset.id
        val trimmed = command.trim()
        agentCommand = trimmed.ifEmpty { preset.defaultCommand }
        if (presetChanged) {
            agentEnv = loadAgentEnv(preset.id)
            canInstallMissing = false
        }
        prefs.edit {
            putString(KEY_AGENT_ID, agentPresetId)
            putString(KEY_COMMAND, agentCommand)
        }
        if (presetChanged) refreshVersions(preset, agentCommand)
    }

    // ---- 环境变量（API Key / Base URL） ----

    fun envTextFor(presetId: String): String = envToText(loadAgentEnv(presetId))

    fun saveAgentEnv(presetId: String, text: String) {
        val parsed = parseEnvText(text)
        saveEnvMap(presetId, parsed)
        if (presetId == agentPresetId) agentEnv = parsed
    }

    private fun loadAgentEnv(agentId: String): Map<String, String> {
        val raw = envPrefs.getString("env_$agentId", null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveEnvMap(agentId: String, env: Map<String, String>) {
        val json = JSONObject().apply { env.forEach { (key, value) -> put(key, value) } }
        envPrefs.edit { putString("env_$agentId", json.toString()) }
    }

    private fun envToText(env: Map<String, String>): String =
        env.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun parseEnvText(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            if (!key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return@forEach
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 &&
                ((value.startsWith('"') && value.endsWith('"')) ||
                    (value.startsWith('\'') && value.endsWith('\'')))
            ) {
                value = value.substring(1, value.length - 1)
            }
            result[key] = value
        }
        return result
    }

    fun updateAutoApprove(enabled: Boolean) {
        permissions.setAutoApprove(enabled)
    }

    fun updateApprovalMode(mode: AcpApprovalMode) {
        permissions.applyMode(mode)
    }

    fun clearTimeline() {
        timeline.clear()
        currentAgentMessageItemId = null
        currentThoughtItemId = null
        usage.reset()
        isReadOnlyReplay = false
    }

    /**
     * 启动 Agent 子进程并完成 initialize + session/new。
     *
     * 会依次尝试预设的候选命令：ACP 的启动参数（`--acp` / `--experimental-acp`）
     * 随 CLI 版本漂移，逐个试过去，用户不必自己猜。
     */
    fun connect() {
        if (connectionState == AcpConnectionState.CONNECTING) return
        // 重连时优先恢复上次的会话，而不是无脑开新会话
        resumeSessionId = (currentSessionMeta?.id ?: sessionId)
            ?.takeIf { timeline.isNotEmpty() && currentSessionMeta?.agentId == agentPresetId }
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

        connectedEnv = agentEnv
        val acpTransport = AcpTransport(
            context = context,
            command = command,
            workDir = resolveWorkDir(),
            extraEnv = connectedEnv,
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
            agentCommand == command &&
            agentEnv == connectedEnv

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

    /** 诊断文本：自检 + Agent 日志 + stderr + 安装日志，供复制/保存。 */
    fun diagnosticsText(): String = buildString {
        appendLine("WebIDE ACP diagnostics")
        appendLine("time: ${System.currentTimeMillis()}")
        appendLine("agent: ${agentInfo ?: preset.displayName} (${agentPresetId})")
        appendLine("command: $agentCommand")
        appendLine("connection: $connectionState")
        appendLine("session: ${sessionId ?: "-"}")
        appendLine()
        appendLine("=== self check ===")
        if (selfCheckReport.isEmpty()) appendLine("(empty)") else selfCheckReport.forEach { appendLine(it) }
        appendLine()
        appendLine("=== agent log ===")
        if (agentLog.isEmpty()) appendLine("(empty)") else agentLog.forEach { appendLine(it) }
        appendLine()
        appendLine("=== stderr ===")
        appendLine(stderrTail.ifBlank { "(empty)" })
        appendLine()
        appendLine("=== install log ===")
        appendLine(installLog.ifBlank { "(empty)" })
    }

    /** 把诊断信息写到项目 `.webide/logs/`，方便直接分享。 */
    fun exportDiagnostics() {
        val text = diagnosticsText()
        val ws = workspacePath ?: resolveWorkDir()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(ws, ".webide/logs").apply { mkdirs() }
                    File(dir, "acp-diagnostics-${System.currentTimeMillis()}.txt").apply {
                        writeText(text)
                    }
                }.getOrNull()
            }
            if (file != null) {
                addNotice(s(R.string.acp_diag_saved, file.absolutePath))
            } else {
                addNotice(s(R.string.acp_export_failed, "IO"), isError = true)
            }
        }
    }


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
        if (timeline.isNotEmpty()) isReadOnlyReplay = true
        addNotice(s(R.string.acp_status_disconnected))
    }

    /** 发送一轮用户消息。 */
    fun sendPrompt(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || !canPrompt()) return

        // 首轮注入项目技能清单；UI 只显示用户原文（注入块在历史重放时会被裁掉）
        val isFirstUserTurn = timeline.none { it is AcpUserMessage }
        val preamble = if (isFirstUserTurn) skills.buildPreamble() else null
        val promptText = if (preamble.isNullOrBlank()) text else "$preamble$text"

        timeline.add(AcpUserMessage(newId("user"), text))
        usage.onPrompt(promptText)
        beginTurn(promptText)
    }

    /**
     * 重试最后一个没有产出的回合（Agent 报错 / 超时 / 被中断后）。
     *
     * ACP 没有「回滚 Agent 上下文」的能力，所以这里只重发最后一条用户消息，
     * 且仅当它后面还没有 Agent 产出时才允许，避免把已有回答搞乱。
     */
    fun retryLastTurn() {
        if (!canPrompt()) return
        val lastUserIndex = timeline.indexOfLast { it is AcpUserMessage }
        if (lastUserIndex < 0) return
        val tail = timeline.drop(lastUserIndex + 1)
        if (tail.any { it is AcpAgentMessage || it is AcpToolCallItem }) return

        while (timeline.size > lastUserIndex + 1) {
            timeline.removeAt(timeline.lastIndex)
        }
        val userText = (timeline[lastUserIndex] as AcpUserMessage).text
        val isFirstUserTurn = timeline.count { it is AcpUserMessage } == 1
        val preamble = if (isFirstUserTurn) skills.buildPreamble() else null
        val promptText = if (preamble.isNullOrBlank()) userText else "$preamble$userText"
        usage.onPrompt(promptText)
        beginTurn(promptText)
    }

    /** 用某条历史提问开一个新会话（不带过去的上下文，等价于「干净地重开」）。 */
    fun startNewSessionWithText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() ||
            connection == null ||
            connectionState != AcpConnectionState.READY ||
            isPrompting
        ) {
            return
        }
        viewModelScope.launch {
            persistCurrentNow()
            if (createNewSession()) {
                sendPrompt(text)
            }
        }
    }

    /** 发送前的统一检查；失败时给出可读提示。 */
    private fun canPrompt(): Boolean {
        if (connection == null || sessionId == null || connectionState != AcpConnectionState.READY) {
            addNotice(s(R.string.acp_error_not_connected), isError = true)
            return false
        }
        if (isPrompting) {
            addNotice(s(R.string.acp_error_busy), isError = true)
            return false
        }
        if (isReadOnlyReplay) {
            addNotice(s(R.string.acp_error_replay_readonly), isError = true)
            return false
        }
        return true
    }

    /** 真正发起一轮 prompt（用户消息已由调用方写入时间线）。 */
    private fun beginTurn(promptText: String) {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return

        sessionSnapshots.clear()
        currentAgentMessageItemId = null
        currentThoughtItemId = null
        pendingSnapshots = emptyList()
        turnBaselineRoot = null
        turnBaselineSig = emptyMap()
        turnBaselineContent = emptyMap()
        isPrompting = true
        schedulePersist()

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
                                    put("text", promptText)
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
                usage.applyReported(usageFrom(result))
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
                schedulePersist()
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
        val prompt = permissionPrompt
        pendingPermission = null
        permissionPrompt = null
        if (prompt != null) {
            val selected = prompt.options.firstOrNull { it.optionId == optionId }
            val decision = when {
                optionId == null -> "cancelled"
                selected?.kind?.startsWith("allow") == true -> "allowed"
                else -> "denied"
            }
            permissions.record(
                kind = prompt.kind,
                title = prompt.title,
                decision = decision,
                detail = selected?.name.orEmpty()
            )
        }
        deferred.complete(optionId)
    }

    /** 记住某类工具的授权偏好（授权卡片上的「总是允许 / 总是拒绝」）。 */
    fun rememberPermission(kind: String, allow: Boolean) {
        permissions.remember(kind, allow)
        addNotice(
            s(
                if (allow) R.string.acp_permission_rule_allow else R.string.acp_permission_rule_deny,
                AcpPermissionController.kindLabel(kind)
            )
        )
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
                saveConfigSelection(agentPresetId, configId, value)
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
                // ACP 的 authenticate 只代表「请求已送达」，是否真的登录成功只有 Agent 自己知道。
                // 不要再乐观地标记 isAuthenticated=true，否则会出现「点了登录、聊天里说已登录、
                // 实际还是未登录」的假象。
                isAuthenticated = false
                authRequested = true
                addNotice(s(R.string.acp_status_auth_requested))
                refreshAgentLog()
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
        if (connection == null || connectionState != AcpConnectionState.READY) return
        viewModelScope.launch {
            persistCurrentNow()
            if (createNewSession()) {
                addNotice(s(R.string.acp_status_new_session))
            }
        }
    }

    /** 建立新会话并清空本地时间线；失败时保留现场并提示。 */
    private suspend fun createNewSession(): Boolean {
        val acpConnection = connection ?: return false
        return try {
            val result = acpConnection.request(
                Acp.M_SESSION_NEW,
                JSONObject().apply {
                    put("cwd", resolveWorkDir())
                    put("mcpServers", mcp.toJsonArray())
                }
            )
            timeline.clear()
            currentAgentMessageItemId = null
            currentThoughtItemId = null
            usage.reset()
            isReadOnlyReplay = false
            applySessionResult(result)
            schedulePersist()
            true
        } catch (e: Throwable) {
            addNotice(s(R.string.acp_error_session_new, e.message ?: e.toString()), isError = true)
            false
        }
    }

    /** 切换（加载）到历史会话；Agent 会重放历史，据此重建时间线。 */
    fun loadSession(id: String) {
        if (connectionState != AcpConnectionState.READY) return
        viewModelScope.launch { loadSessionInternal(id, force = false) }
    }

    /** 让 Agent 接管当前本地回放的会话（「继续此会话」）。 */
    fun resumeLocalSession() {
        val id = sessionId ?: return
        if (connectionState != AcpConnectionState.READY) {
            addNotice(s(R.string.acp_error_not_connected), isError = true)
            return
        }
        viewModelScope.launch { loadSessionInternal(id, force = true) }
    }

    private suspend fun loadSessionInternal(id: String, force: Boolean) {
        val acpConnection = connection ?: return
        if (!force && id == sessionId && !isReadOnlyReplay) return
        persistCurrentNow()
        timeline.clear()
        currentAgentMessageItemId = null
        currentThoughtItemId = null
        usage.reset()
        isReplaying = true
        isRestoringSession = true
        replayUserBuffer = null
        replayUserItemId = null
        replayPrefixStripped = false
        try {
            val result = acpConnection.request(
                Acp.M_SESSION_LOAD,
                JSONObject().apply {
                    put("sessionId", id)
                    put("cwd", resolveWorkDir())
                    put("mcpServers", mcp.toJsonArray())
                },
                timeoutMillis = AcpConnection.DEFAULT_TIMEOUT_MILLIS
            )
            applySessionResult(result, forcedId = id)
            isReadOnlyReplay = false
            currentSessionMeta?.let { meta ->
                usage.restore(
                    input = meta.inputTokens,
                    output = meta.outputTokens,
                    turns = meta.turns,
                    toolCalls = timeline.count { it is AcpToolCallItem },
                    items = timeline.toList()
                )
            }
            addNotice(s(R.string.acp_status_session_loaded))
        } catch (e: Throwable) {
            addNotice(s(R.string.acp_error_session_load, e.message ?: e.toString()), isError = true)
            // Agent 不认这个 session：退回本地副本，至少对话内容还在
            restoreLocalInto(id)
        } finally {
            isReplaying = false
            isRestoringSession = false
            schedulePersist()
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
                        put("mcpServers", mcp.toJsonArray())
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
        if (sid != null) {
            sessionId = sid
            bindSessionMeta(sid)
        }
        applyModes(result.objOrNull("modes"))
        if (result.has("configOptions")) {
            applyConfigOptions(Acp.parseConfigOptions(result.arrOrNull("configOptions")))
        }
        connectionState = AcpConnectionState.READY
        statusMessage = null
        // 恢复上次选择的模型 / 模式（不同 Agent 支持程度不一，失败就静默忽略）
        viewModelScope.launch { restoreConfigPreferences() }
    }

    /** 把一个 Agent sessionId 绑定到本地会话元数据上。 */
    private fun bindSessionMeta(sid: String) {
        val existing = localSessions.firstOrNull { it.id == sid }
        currentSessionMeta = existing ?: AcpSessionMeta(
            id = sid,
            workspace = workspacePath ?: resolveWorkDir(),
            agentId = agentPresetId,
            agentName = agentInfo ?: preset.displayName,
            title = "",
            preview = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        if (existing?.model != null) usage.applyModel(existing.model)
        isReadOnlyReplay = false
    }

    // ------------------------------------------------------------------
    // 本地会话（落盘 / 搜索 / 恢复）
    // ------------------------------------------------------------------

    /** 面板第一次组合时调用；重复调用无副作用。 */
    fun ensureWorkspaceLoaded() {
        if (workspacePrepared) return
        viewModelScope.launch { prepareWorkspace() }
    }

    private suspend fun prepareWorkspace() {
        if (workspacePrepared) return
        workspacePrepared = true
        val ws = resolveWorkDir()
        workspacePath = ws
        localSessions = sessionStore.list(ws)
        storageBytes = sessionStore.totalBytes(ws)
        skills.load(ws)
        // 面板一打开先恢复上次的对话，即使 Agent 还没连
        if (timeline.isEmpty() && currentSessionMeta == null) {
            sessionStore.lastSessionId(ws)?.let { openLocalSessionInternal(it) }
        }
    }

    fun refreshLocalSessions() {
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        isLoadingLocalSessions = true
        viewModelScope.launch {
            try {
                localSessions = sessionStore.list(ws)
                storageBytes = sessionStore.totalBytes(ws)
            } finally {
                isLoadingLocalSessions = false
            }
        }
    }

    // ---- 记忆与行为 ----

    fun refreshMemory() {
        viewModelScope.launch { memory.load(workspacePath ?: resolveWorkDir()) }
    }

    fun saveMemoryText(text: String) {
        viewModelScope.launch {
            memory.updateMemoryText(text)
        }
    }

    fun setBehaviorPreset(preset: AcpBehaviorPreset) {
        memory.applyBehavior(preset)
    }

    fun setMemoryInjection(enabled: Boolean) {
        memory.applyInjectOnNewSession(enabled)
    }

    /** 把记忆 + 行为写进项目 `AGENTS.md` 的受管区块。 */
    fun writeMemoryToAgentsMd() {
        viewModelScope.launch {
            val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
            runCatching { memory.writeAgentsMd(ws) }
                .onSuccess { addNotice(s(R.string.acp_memory_agents_written, it.absolutePath)) }
                .onFailure { addNotice(s(R.string.acp_export_failed, it.message ?: ""), isError = true) }
        }
    }

    // ---- 技能 ----

    fun refreshSkills() {
        viewModelScope.launch {
            skills.load(workspacePath ?: resolveWorkDir().also { workspacePath = it })
        }
    }

    fun saveSkill(skill: AcpSkill) {
        viewModelScope.launch {
            val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
            runCatching { skills.save(ws, skill) }
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
            runCatching { skills.delete(ws, id) }
        }
    }

    fun setSkillsInjection(enabled: Boolean) {
        skills.applyInjectSkills(enabled)
    }

    /** 搜索本地会话（标题 / 摘要 / 正文），带 220ms 防抖。 */
    fun updateLocalSearchQuery(query: String) {        localSearchQuery = query
        searchJob?.cancel()
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        searchJob = viewModelScope.launch {
            delay(220)
            localSessions = sessionStore.search(ws, query)
        }
    }

    /** 打开一条本地会话；即使离线也能查看全文。 */
    fun openLocalSession(id: String) {
        if (id == sessionId && timeline.isNotEmpty()) return
        viewModelScope.launch { openLocalSessionInternal(id) }
    }

    private suspend fun openLocalSessionInternal(id: String) {
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        persistCurrentNow()
        val stored = sessionStore.load(ws, id) ?: return
        timeline.clear()
        timeline.addAll(stored.items)
        sessionId = id
        currentSessionMeta = stored.meta
        usage.reset()
        usage.applyModel(stored.meta.model)
        usage.restore(
            input = stored.meta.inputTokens,
            output = stored.meta.outputTokens,
            turns = stored.meta.turns,
            toolCalls = stored.items.count { it is AcpToolCallItem },
            items = stored.items
        )
        isReadOnlyReplay = true
    }

    /** 从本地存储恢复某个会话到时间线（Agent 拒绝 session/load 时兜底）。 */
    private suspend fun restoreLocalInto(id: String) {
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        val stored = sessionStore.load(ws, id) ?: return
        timeline.clear()
        timeline.addAll(stored.items)
        sessionId = id
        currentSessionMeta = stored.meta
        usage.reset()
        usage.applyModel(stored.meta.model)
        usage.restore(
            input = stored.meta.inputTokens,
            output = stored.meta.outputTokens,
            turns = stored.meta.turns,
            toolCalls = stored.items.count { it is AcpToolCallItem },
            items = stored.items
        )
        isReadOnlyReplay = true
    }

    fun renameLocalSession(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        viewModelScope.launch {
            sessionStore.rename(ws, id, clean)
            localSessions = sessionStore.list(ws)
            if (currentSessionMeta?.id == id) {
                currentSessionMeta = currentSessionMeta?.copy(title = clean)
            }
        }
    }

    fun toggleLocalSessionPin(id: String) {
        val target = localSessions.firstOrNull { it.id == id } ?: return
        val ws = workspacePath ?: return
        viewModelScope.launch {
            sessionStore.setPinned(ws, id, !target.pinned)
            localSessions = sessionStore.list(ws)
        }
    }

    fun deleteLocalSession(id: String) {
        val ws = workspacePath ?: return
        viewModelScope.launch {
            sessionStore.delete(ws, id)
            localSessions = sessionStore.list(ws)
            if (currentSessionMeta?.id == id) {
                // 当前会话被删掉：如果 Agent 还连着，直接开一条新会话，避免「连接着却没有 session」
                val wasReady = connectionState == AcpConnectionState.READY
                timeline.clear()
                currentSessionMeta = null
                sessionId = null
                usage.reset()
                isReadOnlyReplay = false
                if (wasReady) newSession()
            }
        }
    }

    /** 导出为 Markdown，写进项目的 `.webide/exports/`，文件树里能直接看到。 */
    fun exportLocalSession(id: String) {
        val ws = workspacePath ?: return
        viewModelScope.launch {
            val stored = sessionStore.load(ws, id) ?: return@launch
            val dir = File(ws, ".webide/exports")
            val safe = stored.meta.title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .take(40)
                .ifBlank { "acp-session" }
            val file = File(dir, "$safe-${stored.meta.updatedAt}.md")
            runCatching { sessionStore.exportMarkdown(stored, file) }
                .onSuccess { addNotice(s(R.string.acp_export_done, it.absolutePath)) }
                .onFailure { addNotice(s(R.string.acp_export_failed, it.message ?: ""), isError = true) }
        }
    }

    // ---- 落盘 ----

    /** 防抖保存：流式输出期间最多每 900ms 写一次盘。 */
    private fun schedulePersist() {
        if (sessionId == null || isReplaying) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(900)
            persistCurrentNow()
        }
    }

    private suspend fun persistCurrentNow() {
        val sid = sessionId ?: return
        val ws = workspacePath ?: resolveWorkDir().also { workspacePath = it }
        val base = currentSessionMeta
            ?.takeIf { it.id == sid }
            ?: AcpSessionMeta(
                id = sid,
                workspace = ws,
                agentId = agentPresetId,
                agentName = agentInfo ?: preset.displayName,
                title = "",
                preview = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        val modelName = usage.model
            ?: configOptions.firstOrNull { it.category == Acp.CAT_MODEL }?.currentDisplayName
        val saved = sessionStore.save(
            base.copy(
                workspace = ws,
                agentId = agentPresetId,
                agentName = agentInfo ?: preset.displayName,
                model = modelName,
                turns = usage.turns,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens
            ),
            timeline.toList()
        )
        currentSessionMeta = saved
        localSessions = sessionStore.list(ws)
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
        saveJob?.cancel()
        // onCleared 之后 viewModelScope 会被取消，最后一份快照用独立协程落盘
        val sid = sessionId
        val meta = currentSessionMeta
        if (sid != null && meta != null && timeline.isNotEmpty()) {
            val items = timeline.toList()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                sessionStore.save(meta, items)
            }
        }
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
        authRequested = false
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
                isAuthenticated = false
                authRequested = true
                activeAuthMethodId = method.id
            }
        }

        statusMessage = s(R.string.acp_status_creating_session)

        val resume = resumeSessionId
        resumeSessionId = null
        var resumeError: String? = null
        if (resume != null) {
            val previousMeta = currentSessionMeta
            isReplaying = true
            isRestoringSession = true
            replayUserBuffer = null
            replayUserItemId = null
            replayPrefixStripped = false
            try {
                timeline.clear()
                val loaded = acpConnection.request(
                    Acp.M_SESSION_LOAD,
                    JSONObject().apply {
                        put("sessionId", resume)
                        put("cwd", resolveWorkDir())
                        put("mcpServers", mcp.toJsonArray())
                    },
                    timeoutMillis = AcpConnection.DEFAULT_TIMEOUT_MILLIS
                )
                applySessionResult(loaded, forcedId = resume)
                usage.restore(
                    input = previousMeta?.inputTokens ?: 0L,
                    output = previousMeta?.outputTokens ?: 0L,
                    turns = previousMeta?.turns ?: 0,
                    toolCalls = timeline.count { it is AcpToolCallItem },
                    items = timeline.toList()
                )
                isReadOnlyReplay = false
                addNotice(s(R.string.acp_status_session_resumed))
                return
            } catch (e: Throwable) {
                // Agent 不支持 session/load 或会话已失效：本地记录仍在，开一个新会话继续
                resumeError = s(R.string.acp_error_session_resume, e.message ?: e.toString())
                timeline.clear()
                usage.reset()
            } finally {
                isReplaying = false
                isRestoringSession = false
            }
        }

        val newSession = acpConnection.request(
            Acp.M_SESSION_NEW,
            JSONObject().apply {
                put("cwd", resolveWorkDir())
                put("mcpServers", mcp.toJsonArray())
            }
        )

        val sid = newSession.strOrNull("sessionId")
            ?: throw AcpException(Acp.ERR_INTERNAL, s(R.string.acp_error_no_session))
        sessionId = sid
        currentSessionMeta = null
        bindSessionMeta(sid)
        applyModes(newSession.objOrNull("modes"))
        applyConfigOptions(Acp.parseConfigOptions(newSession.arrOrNull("configOptions")))

        connectionState = AcpConnectionState.READY
        statusMessage = null
        resumeError?.let { addNotice(it, isError = true) }
        addNotice(s(R.string.acp_status_connected, agentInfo ?: preset.displayName))
        restoreConfigPreferences()
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

    /** 从 `session/prompt` 响应里尽量挖出用量对象（不同 Agent 放的位置不一）。 */
    private fun usageFrom(result: JSONObject): JSONObject? {
        result.objOrNull("usage")?.let { return it }
        result.objOrNull("_meta")?.let { meta ->
            meta.objOrNull("usage")?.let { return it }
            if (meta.has("inputTokens") || meta.has("outputTokens") || meta.has("totalTokens")) {
                return meta
            }
        }
        return null
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

        val title = toolCall.strOrNull("title").orEmpty()
            .ifEmpty { s(R.string.acp_permission_default_title) }
        val kind = toolCall.strOrEmpty("kind")

        when (val verdict = permissions.decide(kind, options)) {
            is AcpPermissionVerdict.Select -> {
                permissions.record(
                    kind = kind,
                    title = title,
                    decision = if (verdict.allowed) "allowed" else "denied",
                    detail = verdict.reason
                )
                return Acp.permissionSelectedResult(verdict.optionId)
            }

            is AcpPermissionVerdict.Ask -> Unit
        }

        val deferred = CompletableDeferred<String?>()
        pendingPermission = deferred
        permissionPrompt = AcpPermissionPrompt(
            title = title,
            kind = kind,
            toolCallId = toolCall.strOrEmpty("toolCallId"),
            options = options,
            rawInput = jsonValueToDisplay(toolCall.opt("rawInput"))
        )
        permissions.record(kind, title, "asked", "等待用户确认")

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
            Acp.U_USER_MESSAGE_CHUNK -> if (isReplaying) appendReplayedUserChunk(update)
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
        update.objOrNull("_meta")?.let { usage.applyReported(it.objOrNull("usage") ?: it) }
        schedulePersist()
    }

    /** `session/load` 重放历史时，Agent 也会重放用户消息；把它们补回时间线。 */
    private fun appendReplayedUserChunk(update: JSONObject) {
        val text = contentBlockToText(update.objOrNull("content")) ?: return
        if (text.isEmpty()) return

        // 新的用户消息（中间夹着 Agent 消息）重新开始一段缓冲
        if (replayUserItemId == null || timeline.lastOrNull()?.id != replayUserItemId) {
            replayUserBuffer = StringBuilder()
            replayPrefixStripped = false
            replayUserItemId = null
        }
        val buffer = replayUserBuffer ?: StringBuilder().also { replayUserBuffer = it }
        buffer.append(text)

        if (!replayPrefixStripped) {
            val markerIndex = buffer.indexOf(AcpMemoryController.USER_PROMPT_MARKER)
            when {
                markerIndex >= 0 -> {
                    buffer.delete(0, markerIndex + AcpMemoryController.USER_PROMPT_MARKER.length)
                    replayPrefixStripped = true
                }

                buffer.contains(AcpMemoryController.PREAMBLE_START) -> return // 前缀还没收全，继续等

                buffer.length < AcpMemoryController.PREAMBLE_START.length &&
                    AcpMemoryController.PREAMBLE_START.startsWith(buffer.toString()) -> return

                buffer.length > 32 -> replayPrefixStripped = true // 不是注入消息，按原文显示
                else -> return
            }
        }

        val visible = buffer.toString().trimStart('\n')
        val existingId = replayUserItemId
        if (existingId == null) {
            val itemId = newId("user")
            replayUserItemId = itemId
            timeline.add(AcpUserMessage(itemId, visible))
        } else {
            val index = timeline.indexOfFirst { it.id == existingId }
            if (index >= 0) {
                timeline[index] = AcpUserMessage(existingId, visible)
            }
        }
    }

    private fun appendChunk(update: JSONObject, agentMessage: Boolean) {
        val text = contentBlockToText(update.objOrNull("content")) ?: return
        if (text.isEmpty()) return
        val messageId = update.strOrNull("messageId")
        usage.onAgentText(text)

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
        val now = System.currentTimeMillis()
        if (index >= 0) {
            val old = timeline[index] as AcpToolCallItem
            val newStatus = update.strOrNull("status") ?: old.status
            val terminal = newStatus == Acp.STATUS_COMPLETED || newStatus == Acp.STATUS_FAILED
            timeline[index] = old.copy(
                title = update.strOrNull("title") ?: old.title,
                kind = update.strOrNull("kind") ?: old.kind,
                status = newStatus,
                contents = if (contents.isEmpty()) old.contents else contents,
                locations = if (locations.isEmpty()) old.locations else locations,
                rawInput = rawInput ?: old.rawInput,
                rawOutput = rawOutput ?: old.rawOutput,
                startedAt = old.startedAt.takeIf { it > 0L } ?: now,
                finishedAt = old.finishedAt ?: now.takeIf { terminal }
            )
        } else {
            val title = update.strOrNull("title").orEmpty()
            val status = update.strOrEmpty("status").ifEmpty { Acp.STATUS_PENDING }
            val terminal = status == Acp.STATUS_COMPLETED || status == Acp.STATUS_FAILED
            timeline.add(
                AcpToolCallItem(
                    id = newId("tool"),
                    toolCallId = toolCallId,
                    title = title,
                    kind = update.strOrEmpty("kind").ifEmpty { Acp.KIND_OTHER },
                    status = status,
                    contents = contents,
                    locations = locations,
                    rawInput = rawInput,
                    rawOutput = rawOutput,
                    startedAt = now,
                    finishedAt = now.takeIf { terminal }
                )
            )
            usage.onToolCall(
                title = title,
                detail = contents.filterIsInstance<AcpToolContent.Text>().joinToString("\n") { it.text }
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
        val modelName = options.firstOrNull { it.category == Acp.CAT_MODEL }?.currentDisplayName
        if (!modelName.isNullOrBlank()) usage.applyModel(modelName)
    }

    // ---- 配置项记忆（模型 / 模式 / 思考等级） ----

    private fun loadConfigSelections(agentId: String): Map<String, String> {
        val raw = configPrefs.getString("selections_$agentId", null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveConfigSelection(agentId: String, configId: String, value: Any) {
        val current = loadConfigSelections(agentId).toMutableMap()
        current[configId] = value.toString()
        val json = JSONObject().apply { current.forEach { (key, item) -> put(key, item) } }
        configPrefs.edit { putString("selections_$agentId", json.toString()) }
    }

    /** 新会话建立后，把上次选择的模型 / 模式恢复回去。 */
    private suspend fun restoreConfigPreferences() {
        val acpConnection = connection ?: return
        val sid = sessionId ?: return
        if (connectionState != AcpConnectionState.READY) return
        val stored = loadConfigSelections(agentPresetId)
        if (stored.isEmpty()) return

        configOptions.forEach { option ->
            if (option.type != Acp.CTYPE_SELECT && option.type != Acp.CTYPE_BOOLEAN) return@forEach
            val desired = stored[option.id] ?: return@forEach
            if (option.currentValueString == desired) return@forEach

            val payload = JSONObject().apply {
                put("sessionId", sid)
                put("configId", option.id)
                if (option.type == Acp.CTYPE_BOOLEAN) {
                    put("type", Acp.CTYPE_BOOLEAN)
                    put("value", desired.toBoolean())
                } else {
                    put("value", desired)
                }
            }
            runCatching {
                val result = acpConnection.request(Acp.M_SESSION_SET_CONFIG_OPTION, payload)
                result.arrOrNull("configOptions")
                    ?.let { applyConfigOptions(Acp.parseConfigOptions(it)) }
            }
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
