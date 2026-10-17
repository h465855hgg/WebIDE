# ACP 集成（Agent Client Protocol）

WebIDE 内置一个 **ACP 客户端**，把「外部编码 Agent」接进编辑器：Agent 可以读取/修改你的项目文件、在 Alpine 容器里执行命令、向你申请授权，而所有交互都走标准协议 [Agent Client Protocol](https://agentclientprotocol.com)。

代码位置：`app/src/main/java/com/web/webide/ui/editor/acp/`（取代了旧的 `aicoding` 包）。

> 2.0 的会话持久化、权限分级、token 计量与输入体验改造，见 [ACP-AGENT-2.0.md](ACP-AGENT-2.0.md)。

---

## 1. 快速开始

1. 打开任意项目，进入编辑器。
2. 点右上角 `⋮` → **显示 ACP 助手**（面板默认停在编辑器右下角）。
3. 面板里点 **连接**。若该 Agent 尚未安装，点 **一键安装**。
4. 首次连接会写入设置：Agent 类型、启动命令、自动授权开关。

面板按钮（从左到右）：状态点、标题、设置 ⚙、最大化/还原、收起为悬浮气泡、隐藏。

---

## 2. 架构总览

| 文件 | 职责 |
| --- | --- |
| `AcpProtocol.kt` | 协议常量、`ConfigOption` 解析、JSON 工具扩展 |
| `AcpTransport.kt` | 在 proot Alpine 中拉起 Agent 子进程，stdio 换行分隔 JSON-RPC |
| `AcpConnection.kt` | JSON-RPC 2.0 会话层：请求/响应关联、通知、反向请求分派 |
| `AcpAgent.kt` | 内置 Agent 预设、候选命令、命令分词 |
| `AcpEnvironment.kt` | 环境探测（二进制、npm、版本）、npm 安装、自检 |
| `AcpTerminalManager.kt` | Agent 发起的 `terminal/*` 请求落到真实 PTY |
| `AcpModels.kt` | 时间线与配置项的纯数据模型 |
| `AcpViewModel.kt` | 状态机：连接、会话、配置、权限、安装、自检 |
| `AcpPanel.kt` | Compose UI：面板、配置栏、配置面板、时间线、输入栏、设置 |
| `AcpMarkdown.kt` / `AcpPanelState.kt` | 轻量 Markdown 渲染 / 面板可见性状态 |

**传输**：`AlpineManager.buildProotCommand(context, command, workDir, extraEnv)` 生成 proot 命令行，`ProcessBuilder` 启动，stdout/stdin 按行交换 JSON-RPC。proot 已 bind mount `/storage`、`/data`，因此宿主绝对路径在容器内同路径可访问，工作区路径可直接透传。

---

## 3. 协议一致性

协议版本 **1**。客户端在 `initialize` 里声明：

```json
{
  "protocolVersion": 1,
  "clientCapabilities": {
    "fs": { "readTextFile": true, "writeTextFile": true },
    "terminal": true,
    "session": { "configOptions": { "boolean": {} } }
  },
  "clientInfo": { "name": "webide", "title": "WebIDE", "version": "<appVersion>" }
}
```

### 3.1 客户端 → Agent

| 方法 | 说明 |
| --- | --- |
| `initialize` | 握手，校验 `protocolVersion`，解析 `agentInfo` / `authMethods` |
| `authenticate` | 若 Agent 声明了 `authMethods`，连接时自动尝试第一种；设置里可「重新登录」 |
| `session/new` | 创建会话，解析 `modes` 与 `configOptions` |
| `session/prompt` | 发送用户输入，流式接收更新，直到返回 `stopReason` |
| `session/cancel` | 中断当前回合（**通知**，无需响应） |
| `session/set_config_option` | 切换模型 / 模式 / 思考等级等（见 §4） |
| `session/set_mode` | 兼容仅实现旧式 `modes` 的 Agent |

`stopReason` 处理：`end_turn` 静默；`max_tokens` / `max_turn_requests` / `refusal` / `cancelled` 给出对应提示。

### 3.2 Agent → 客户端（反向请求）

| 方法 | 说明 |
| --- | --- |
| `fs/read_text_file` | 读文件，支持 `line` / `limit` 分片 |
| `fs/write_text_file` | 写文件（自动建父目录） |
| `terminal/create` | 创建 PTY，返回 `terminalId` |
| `terminal/output` | 读取当前输出与截断标记 |
| `terminal/wait_for_exit` | 等待退出，返回 `exitCode` |
| `terminal/kill` / `terminal/release` | 终止 / 释放终端 |
| `session/request_permission` | 请求授权；自动授权开启时直接选择 `allow_*`，否则弹卡片 |

### 3.3 Agent → 客户端（通知）

`session/update`，按 `update.sessionUpdate` 分派：

`user_message_chunk`、`agent_message_chunk`、`agent_thought_chunk`、`tool_call`、`tool_call_update`、`plan`、`available_commands_update`、`config_option_update`、`current_mode_update`、`notice`。

---

## 4. 会话配置（模型 / 模式 / 思考等级）

现代 ACP 用 **`configOptions`** 统一表达会话配置，取代旧的 `modes`。

- `session/new` 结果里带 `configOptions`；运行中通过 `config_option_update` 通知更新。
- 结构：`ConfigOption { id, name, description?, category?, type, currentValue, options? }`
  - `type`：`select`（默认）或 `boolean`
  - `options`：扁平 `ConfigOptionValue[]`，或 `ConfigOptionGroup[]`（`{ group, name, options }`）
  - `category`：`mode`、`model`、`model_config`、`thought_level`（其它值为自定义）
- 切换：`session/set_config_option { sessionId, configId, value }`；布尔型额外带 `type: "boolean"`。Agent 在结果里回传**完整**配置状态。
- 客户端需在 `initialize` 声明 `session.configOptions.boolean` 才能收到布尔型配置项。
- 选择按 Agent 持久化；新会话建立后自动恢复上次的模型 / 模式 / 思考等级（`AcpViewModel.restoreConfigPreferences`）。

### UI 呈现

- **配置栏**（状态行下方）：每个配置项一个胶囊，图标按分类（模型→🤖、模式→🎛、思考等级→⭐、模型参数→⚙），文字为「分类 · 当前值」。
- **配置面板**（底部弹层）：分组/扁平单选 + 布尔开关，当前值高亮并打勾，切换即调用 `set_config_option`。
- 若 Agent 只声明了旧式 `modes`，配置栏会退化为一个「模式 · xxx」胶囊，面板里用 `session/set_mode` 切换。

---

## 5. 内置 Agent 预设

| Agent | 启动命令（按顺序尝试） | npm 包 |
| --- | --- | --- |
| Gemini CLI | `gemini --acp` → `gemini --experimental-acp` | `@google/gemini-cli` |
| Qwen Code | `qwen --acp` → `qwen --experimental-acp` | `@qwen-code/qwen-code` |
| Claude Code | `claude-agent-acp` → `npx -y @zed-industries/claude-agent-acp` | `@zed-industries/claude-agent-acp`, `@anthropic-ai/claude-code` |
| Codex | `codex-acp` → `npx -y @agentclientprotocol/codex-acp` | `@agentclientprotocol/codex-acp`, `@openai/codex` |
| OpenCode | `opencode acp` | `opencode-ai` |
| Cline | `cline --acp` → `cline acp` | `cline` |
| Crush | `crush acp` → `crush --acp` | `@charmland/crush` |
| Custom | 用户自填 | — |

- **候选命令回退**：ACP 的启动参数随 CLI 版本漂移，连接时逐个预检并尝试，进程在握手前退出会自动换下一个。
- **命令分词**：支持单/双引号与反斜杠转义，因此可写 `sh -c "cd app && npm run build"`。

### 版本检测

1. 优先 `npm list -g <pkg> --depth=0 --json`。
2. 查不到（二进制分发，如 opencode）时回退执行 `<命令> --version` 并解析版本号。
3. 最新版从 `registry.npmjs.org` 拉取；Custom 预设不猜版本。
4. 版本按**当前下拉选中的预设**查询，避免张冠李戴。

---

## 6. 交互与动效

- 面板最大化/还原：宽高、边距、圆角统一走 260ms `FastOutSlowInEasing` 动画。
- 收起/展开：`fade + scale`（展开用 `spring` 回弹）。
- 工具调用卡片、思考块可展开折叠。
- 输入以 `/` 开头时，从 Agent 的 `available_commands_update` 弹出斜杠命令补全。
- 断连/出错时给出可执行提示（缺失二进制、安装入口、stderr 摘要）。

---

## 7. 权限与安全

权限分为四档（`AcpPermissionController`）：

| 档位 | 行为 |
| --- | --- |
| 每次都问 | 任何工具调用都弹卡片 |
| 智能放行 | 读文件 / 搜索 / 思考自动放行，写盘与执行仍询问（默认） |
| 全部放行 | 自动同意所有请求（含写盘与命令） |
| 只读模式 | 拒绝写文件、删文件、移动、执行命令 |

- **按工具类型记忆**「总是允许 / 总是拒绝」，持久化在 `acp_permissions`；设置页可清空。
- 旧版 `auto_approve` 布尔值会自动迁移到「全部放行 / 智能放行」。
- 每次决策记录审计（类型、标题、结果、原因）。
- 自动裁决优先选 `allow_once` / `reject_once`，把永久放行留给用户显式选择。
- 会话取消时，挂起的授权请求必须回 `cancelled`。
- 提示：「全部放行」会放行所有文件写与命令执行，请谨慎开启。

---

## 8. 设置项

- **Agent**：下拉选择预设。
- **启动命令**：可编辑，保存后若与已连接配置不同则自动重连。
- **环境变量**：每行一条 `KEY=VALUE`（API Key / Base URL 等），启动 Agent 子进程时注入；保存后需重新连接。
- **版本 / 一键安装**：仅对有 npm 包的预设展示。
- **登录**：Agent 声明了 `authMethods` 且正是当前已连接的那个时展示。
- **工具权限**：四档级别 + 已记住的规则；见 §7。
- **记忆与行为**：项目记忆、协作风格、新会话首轮注入、写入 `AGENTS.md`（见 `docs/ACP-AGENT-2.0.md` §2.6）。
- **MCP 服务器**：本地命令 / HTTP(SSE) 服务器列表，`session/new` 时下发；修改后需新建会话生效（见 `docs/ACP-AGENT-2.0.md` §2.9）。
- **停靠到左侧**：面板停靠位置。
- **环境自检**：输出工作目录可达性、命令解析路径、node/npm/npx 版本，并探测 `models.opencode.ai` / `opencode.ai` / `registry.npmjs.org` 的连通性。
- **Agent 运行日志**：读取 Agent 自身日志（如 `~/.local/share/opencode/log/opencode.log`）的末尾若干行。
- **诊断**：Agent 的 stderr 尾部。

---

## 9. 环境与排错

容器：proot Alpine。`filesDir` 在容器内同路径可访问；Node 位于 `/usr/bin/node`。

- **命令未找到**：面板提示缺失二进制并给出一键安装（有 npm 包时）。
- **启动失败**：自动尝试候选命令；仍失败则展示 stderr 尾部与退出码。
- **工作目录不可达**：自检会提示回退到 `/root`。
- **Agent 卡死**：点中断按钮，客户端会同时发 `session/cancel` 并取消本地等待协程。

### 「一直转圈、没有任何回复」排查顺序

这类问题**几乎都不是客户端协议的问题**——客户端发送的 `session/prompt`（`prompt: [{type:"text",...}]`）
与官方 ACP 一致。真正的原因通常是模型侧或网络侧，而且 Agent 往往**不通过协议回报错误**，只写自己的日志。
按以下顺序排查：

1. **看「Agent 运行日志」**（设置里）。openCode 会把失败写进
   `~/.local/share/opencode/log/opencode.log`，典型两类：
   - `AI_APICallError: Rate limit exceeded` —— 免费模型（`opencode/*-free`、`big-pickle` 等）
     共用一个「zen」额度池，被限流。换用带自己 key 的模型（如 `deepseek/*`），或稍后再试。
   - `Failed to fetch models.dev` / `Unable to connect` —— 容器访问不到 `models.opencode.ai`，
     此时连 `session/new` 都会失败（`Internal error: OpenCode service failure`）。
2. **跑「环境自检」看网络连通性**。若某域名「不可达」，多半是代理 / DNS 问题：
   设备若挂着 VPN / 代理（Clash、sing-box、v2ray 等），容器流量会走代理出口；
   代理出口 IP 若被模型服务限流，就会出现「宿主能连、设备不能连」的现象。
3. **确认已登录**。`opencode auth list` 为 0 时只能用免费额度；带 key 的模型需先 `opencode auth login`
   （面板设置里的「登录」按钮会触发 Agent 声明的 `authenticate`）。
4. **超过 45 秒无输出**时，面板会自动插入一条提示，并顺带刷新 Agent 日志，引导按上面步骤排查。

---

## 10. 构建与测试

```sh
# 编译（注意 arch flavor 必须带架构）
./gradlew :app:compileArm64-v8aDebugKotlin
./gradlew :app:assembleArm64-v8aDebug
```

真机验证（adb）：

- **协议链路**：用自定义预设 + 一个假 Agent（实现 `initialize` / `session/new` / `session/prompt` /
  `fs/*` / `terminal/*` / `session/request_permission` / `configOptions`）即可覆盖全部链路。
- **真实对话**：`opencode acp` 起一个真 Agent，发一句 `reply with exactly PONG`，
  应看到 `agent_message_chunk` 流式输出并以 `stopReason: end_turn` 结束。
  若卡住不返回，按 §9 的排查顺序处理（看 Agent 日志 / 网络连通性 / 登录状态）。