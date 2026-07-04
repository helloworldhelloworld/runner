# agent-openclaw

OpenClaw 大脑适配模块。把外部 **OpenClaw**（TS agent runtime）作为小黄人的"脑"接入大脑平面，
与自研 `Orchestrator`（native）经 `app.brain.type` 配置切换。决策见
[ADR-014](../decisions/014-brain-swappable-chathandler-openclaw.md)。

## 职责边界

- **做**：实现 `ChatHandler`（大脑平面 SPI），把 OpenClaw 的事件流映射成 runner 的 `Flux<StreamEvent>`
  （`TEXT_DELTA` / `LLM_COMPLETE`），并实现 barge-in 的 `interrupt(sessionId)` 契约。
- **不做**：可说块切分 / emotion / 序列化 / WS / 安全检测 —— 这些是**语音平面**，对 `ChatHandler` 泛型无关、保持在
  agent-web / agent-kernel postprocess，本模块不碰。
- **让位**：openclaw 模式下工具治理 / MCP / 会话历史 / compaction 归 OpenClaw（minion persona = OpenClaw agent 配置，
  Pi 设备 MCP 挂 OpenClaw toolset）。

依赖方向：`agent-kernel ← agent-openclaw ← agent-web`（仿 `agent-mcp`，不反向依赖 kernel）。

## 关键类型（`com.lightweightai.openclaw`）

| 类型 | 角色 |
|---|---|
| `OpenClawClient`（interface, SPI） | 讲 OpenClaw Gateway/ACP 协议：`Flux<OpenClawEvent> chat(req)` + `void cancel(sessionId)`（OpenClaw 按 sessionKey 打断,runId 可选）。藏在接口后便于 fake。|
| `OpenClawEvent`（sealed） | 内部事件：`RunStarted/Token/ToolUse/ToolResult/RunEnd/ErrorEvent`。**仅模块内可见，绝不外泄为 StreamEvent 枚举**（守 ADR-005）。|
| `OpenClawChatRequest` | `{sessionId, agentId, message}`。|
| `OpenClawChatHandler implements ChatHandler` | 核心适配器：事件映射 + `activeRuns` + barge-in。|
| `OpenClawRun` | `InterruptibleRun` 等价：持 `subscription`/外层 `sink`/phase；`interrupt()` = `client.cancel(sessionId)` + `dispose` + 自终止外层流。|
| `ws.WebSocketOpenClawClient` | 真实 WS 实现（仿 agent-mcp transport + ADR-011 连接韧性）。|

## 事件映射（`OpenClawEvent → StreamEvent`）

| OpenClawEvent | StreamEvent |
|---|---|
| `RunStarted(runId)` | —（记 `OpenClawRun.openClawRunId`，供停播帧 runId 用,不外发） |
| `Token(text)` | `StreamEvent.textDelta(text)` → 下游 `SpeakableChunkProcessor` |
| `RunEnd` | `StreamEvent.llmComplete(...)`（触发末块 flush + `stream_end`） |
| `ToolUse`/`ToolResult` | 默认隐藏；`forwardToolTrace` 时映射为既有 `TRACE` 事件（phase=`openclaw.tool`，非 toolCallStart/toolResult） |
| `ErrorEvent` | `Flux.error` |

## barge-in

`OpenClawChatHandler.interrupt(sessionId)` 查 `activeRuns` → `OpenClawRun.interrupt(client)`：
`client.cancel(openClawRunId)`（ACP 取消）+ `subscription.dispose()`（停转发）+ 返回
`StreamEvent.speechInterrupted(runId, "barge-in")`。形状与 `Orchestrator.interrupt` 一致，故
`SpringChatWebSocketHandler.handleInterrupt` 零改动。下游取消（WS 断）经 `doOnCancel` 也会 `client.cancel`，省 token。

## 配置（agent-web `BrainConfig`）

```yaml
app:
  brain:
    type: native            # native | openclaw（默认 native）
  openclaw:
    url: ws://127.0.0.1:18789
    agents: { minion: "<openclaw-agent-id>" }   # agentId → OpenClaw agent
```

`@ConditionalOnProperty(app.brain.type)` + `@Primary` 选 `ChatHandler` Bean；其余 Gateway/post-process/WS Bean 不动。

## 测试策略（守 CLAUDE.md 集成接缝规约）

- 适配器层：**fake `OpenClawClient`**（可编排事件 + 记录 cancel，仿 `CapturingLLMProvider`）跑通映射 + barge-in + 切换，
  **不依赖真 OpenClaw**。
- 真 client：**faithful fake WS 对端**（RFC6455，仿 agent-mcp `MiniWebSocketServer`），真 client 端到端往返 + 失败时序；
  **不 mock 自己层**。

## 已查证的 OpenClaw 协议（docs.openclaw.ai，2026-06-21）

JSON-RPC over WS（`wss://host:18789`，帧 `req`/`res`/`event`）：
- 连接 `connect`（v3/4 + `auth.token` + scopes `operator.read/write` + 设备签名）。
- 起 run：`chat.send {sessionKey, text, agentId}`。
- token 流：`event` 帧 `event:"chat"` payload `{deltaText, message}`（v4）→ TEXT_DELTA。
- **barge-in 真打断**：`chat.abort {sessionKey}` / `sessions.abort {key, runId?}`，协作式不拆 session（开放问题①✅）。
- MCP 挂 Pi：`mcp.servers.<name>.{url, transport:"streamable-http"}`（ADR-008 直接对上，开放问题③✅）。
- persona：agent workspace 引导文件 `SOUL.md`/`AGENTS.md`（无 per-agent `systemPrompt` 键）；agent 配置 `agents.list[].{id,model,tools.allow/deny/profile,skills}`。

run 生命周期 = `event:"chat"` 的 `state`（`logs-chat.ts`）：`delta{deltaText}`→Token、`final{stopReason?}`→RunEnd、
`error{errorKind}`→ErrorEvent、`aborted`→终态。编解码见 `ws/OpenClawProtocol`。

## 实现状态（阶段3，2026-06-21）

- ✅ `OpenClawProtocol`（codec，Jackson，纯函数单测）+ `WebSocketOpenClawClient`（JDK `java.net.http.WebSocket`，
  一轮一连接：connect→chat.send→chat state 流→RunEnd 完成；cancel 按 sessionKey 发 chat.abort）。
- ✅ 接缝测试 `WebSocketOpenClawRoundTripTest`：真客户端 ⟷ 假对端 `FakeOpenClawServer`（真 RFC-6455 + chat 协议）——
  往返、barge-in（含经 handler 端到端）、连接失败快失败。
- ⏳ **真用待补**（非本仓代码）：① 真 OpenClaw Gateway 实例；② `connect` 的 auth 握手（challenge + 设备签名，当前简化）；
  ③ `session.tool` payload（仅 `forwardToolTrace` 观测用）；④ 持久连接复用 + ADR-011 重连韧性（当前一轮一连接）；
  ⑤ persona `SOUL.md`/`AGENTS.md` + Pi MCP 挂载（OpenClaw 仓）。建议保留一个对真网关的周期 smoke 作 backstop。
