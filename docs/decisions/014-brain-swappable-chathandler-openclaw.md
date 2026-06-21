# ADR-014：脑 = 可切换 ChatHandler SPI + OpenClaw 完整大脑适配

状态：Accepted（2026-06-21）
关联：[ADR-005](005-streamevent-closed-protocol.md)（StreamEvent 闭合枚举）、
[ADR-006 D2](006-minion-embodiment-architecture.md)（媒体/大脑双平面）、
[ADR-008](008-mcp-transport-cloud-to-pi.md)（cloud↔Pi MCP transport）、
[ADR-012](012-voice-text-plane-contract.md)（语音文本契约）、
提案 [architecture-todos/2026-06-19-openclaw-brain-swap.md](../../architecture-todos/2026-06-19-openclaw-brain-swap.md)。

## Context

runner 的"脑"目前只有自研 `Orchestrator`。需要能把脑换成外部 **OpenClaw**（TS agent runtime，自带
loop / 工具 / compaction / subagent / feature-flag，见 [architecture-todos/2026-04-08.md](../../architecture-todos/2026-04-08.md)），
并 runner ⟷ OpenClaw **配置可切换**。

ADR-006 D2 + ADR-012 已把系统切成**媒体平面**（二进制音频，绕开 JVM）与**大脑平面**（Voice Gateway ⟷ runner，纯文本 + 事件）。
大脑平面对外的耦合点只有 `ChatHandler.chatStreamReactive(GatewayRequest)` 产出的 `Flux<StreamEvent>` 与
`ChatHandler.interrupt(sessionId)`；而**语音平面**（可说块切分 R2 + 逐块 emotion R3、`StreamEventSerializer`、WS handler、
post-process 管道、barge-in 入口 R4）对 `ChatHandler` 的**具体实现泛型无关** —— 它只消费 `StreamEvent` 流。
因此换脑的天然缝就是 `ChatHandler`。

## Decision

1. **把 `ChatHandler` 正式确立为"脑"的 SPI。** 两实现：`Orchestrator`（native）/ `OpenClawChatHandler`（openclaw）。

2. **完整大脑模式**：OpenClaw 自跑 agent loop + 工具 + compaction；runner 保留语音平面 + barge-in 入口契约 + 安全前置检测。
   （对比"OpenClaw 仅当裸补全后端"被否，见 Alternatives。）

3. **适配器置于新模块 `agent-openclaw`**（仿 `agent-mcp` 结构），依赖 `agent-kernel`；Spring 装配在 `agent-web` 的
   `BrainConfig`，由 `app.brain.type=native|openclaw`（默认 `native`）+ `@Primary` 选择。
   依赖方向单向：`agent-kernel ← agent-openclaw ← agent-web`（禁止 `agent-kernel` 反向依赖）。

4. **不新增 StreamEvent 枚举**（守 ADR-005）：OpenClaw 内部事件在适配器内映射为既有 `TEXT_DELTA` / `LLM_COMPLETE`
   （以及可选的既有 tool 生命周期事件 / `TRACE`）。OpenClaw 的内部事件类型 `OpenClawEvent` 仅模块内可见，绝不外泄为 `StreamEvent` 枚举。

5. **工具 / persona 让位 OpenClaw**：minion persona 落成 OpenClaw agent workspace 的引导文件 `SOUL.md`/`AGENTS.md`
   （OpenClaw 无 per-agent `systemPrompt` 键，见开放问题③）；Pi 设备 MCP（ADR-008 streamable_http：舵机/眼睛/摄像头）
   配 `mcp.servers.<name>.{url, transport:"streamable-http"}`。openclaw 模式下 runner 的 `ScopedToolRegistry` / risk policy 让位。

6. **会话历史 / compaction 归 OpenClaw**：openclaw 模式下 runner 聊天环不写 `MemoryProvider`；runner 仅以 `sessionId` 映射
   OpenClaw session（`agent:minion:main:<sessionId>` ↔ OpenClaw session）。

7. **安全前置不变**：`crisisDetector` 在 `SpringChatWebSocketHandler.handleChat`、即**脑之前**触发，故 openclaw 模式同样受保护，零改动。

## Consequences

- ✅ config 一键切；语音平面 / 序列化 / WS / barge-in 入口 / 安全检测**零改动**；拿到 OpenClaw runtime 红利
  （compaction / subagent / feature-flag / 多渠道 inbox）。
- ✅ 适配器可在**不连真 OpenClaw** 时用 fake client 完整测通（缝 + 映射 + barge-in + 切换），与外部依赖解耦推进。
- ✅ barge-in 真打断成立：OpenClaw `chat.abort`/`sessions.abort` 协作式取消在途 run、不拆 session（开放问题①已查证）。
- ⚠️ openclaw 模式下 runner 的工具治理 / risk policy 让位，移交 OpenClaw（取舍：通用度 ↔ 专用治理）。
- ⚠️ 两脑契约对齐靠**假对端接缝测试**防漂；run-end/工具事件的确切名待读 OpenClaw chat schema 模块坐实（开放问题②残留）。
- ⚠️ 历史归 OpenClaw → runner 侧记忆 / 学习链在 openclaw 模式下不积累（如需，另设单向同步，超本 ADR）。

## Alternatives rejected

- **LLMProvider 缝（OpenClaw 当裸补全后端）**：runner 续跑 loop / 工具，OpenClaw 仅供 token —— 拿不到 OpenClaw loop / 工具红利，
  且 OpenClaw 是 runtime 非补全端点，浪费其价值。否。
- **standalone bridge 重写 R2–R4**：在 OpenClaw 栈里重写可说块 / emotion / barge-in / 契约，失复用，工作量数倍。否。

（两方案对比详见 [architecture-todos/2026-06-19-openclaw-brain-swap.md](../../architecture-todos/2026-06-19-openclaw-brain-swap.md) §4。）

## 开放问题（已查官方文档 docs.openclaw.ai，2026-06-21）

OpenClaw Gateway 是 **JSON-RPC over WebSocket**（`wss://host:18789`），帧 `req`/`res`/`event`；
连接走 `connect`（protocol v3/4 + `auth.token` + scopes `operator.read/write` + 设备签名）。

1. **✅ 已解：barge-in 真打断成立。** ACP `cancel` → Gateway `chat.abort {sessionKey}` / `sessions.abort {key, runId?}`，
   **协作式、非破坏**：取消在途 run、把挂起 prompt 解析为 cancelled，session 不拆。无需发新消息。
   → `OpenClawClient.cancel(runId)` 映射 `chat.abort`/`sessions.abort`，barge-in 是真打断不是假停。
2. **✅ 已解（语音路径）：run 生命周期由 `chat` 事件的 `state` 判别承载。** 起 run = `chat.send {sessionKey, text, agentId}`；
   `event` 帧 `event:"chat"` payload 带 `state`（源：`gateway-protocol/src/schema/logs-chat.ts`）：
   - `state:"delta"` → `{deltaText, message?, replace?, usage?}` → `Token` → **TEXT_DELTA**
   - `state:"final"` → `{message?, usage?, stopReason?}` → `RunEnd` → **LLM_COMPLETE**
   - `state:"error"` → `{errorMessage?, errorKind: refusal|timeout|rate_limit|context_length|unknown}` → `ErrorEvent`
   - `state:"aborted"` → 打断后的终态
   打断：`chat.abort {sessionKey, agentId?, runId?}`（**runId 可选** → 可仅凭 sessionKey 打断）。`session.operation` 仅 `compact` 的 start/end（compaction，非 run 生命周期，无关）。
   **残留（非阻塞语音）**：`session.tool`（tool_use/tool_result）payload 定义在别处 —— 仅影响可选 `forwardToolTrace` 观测，语音路径默认隐藏工具事件,不阻塞。
3. **✅ 已解：Pi MCP + persona 路径清楚。** MCP 挂载 = `mcp.servers.<name>.{url, transport:"streamable-http", timeout, toolFilter}`
   —— Pi 设备 server（ADR-008 streamable_http）直接对上。agent 配置 = `agents.list[].{id, model, name, identity, tools.allow/deny/profile, skills}`；
   **persona 不是 config 里的 systemPrompt 键**，而是 agent workspace 的引导文件 **`SOUL.md`/`AGENTS.md`** —— minion persona 要落成这两个文件（影响 §5/阶段4）。
   agent 选择走 `bindings`（channel/account/peer 匹配）或 `chat.send` 的 `agentId`。
