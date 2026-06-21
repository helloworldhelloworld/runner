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
| `OpenClawClient`（interface, SPI） | 讲 OpenClaw Gateway/ACP 协议：`Flux<OpenClawEvent> chat(req)` + `void cancel(runId)`。藏在接口后便于 fake。|
| `OpenClawEvent`（sealed） | 内部事件：`RunStarted/Token/ToolUse/ToolResult/RunEnd/ErrorEvent`。**仅模块内可见，绝不外泄为 StreamEvent 枚举**（守 ADR-005）。|
| `OpenClawChatRequest` | `{sessionId, agentId, message}`。|
| `OpenClawChatHandler implements ChatHandler` | 核心适配器：事件映射 + `activeRuns` + barge-in。|
| `OpenClawRun` | `InterruptibleRun` 等价：持 `openClawRunId`/`subscription`/phase；`interrupt()` = `client.cancel` + `dispose`。|
| `ws.WebSocketOpenClawClient` | 真实 WS 实现（仿 agent-mcp transport + ADR-011 连接韧性）。|

## 事件映射（`OpenClawEvent → StreamEvent`）

| OpenClawEvent | StreamEvent |
|---|---|
| `RunStarted(runId)` | —（记 `OpenClawRun.openClawRunId`，供取消用，不外发） |
| `Token(text)` | `StreamEvent.textDelta(text)` → 下游 `SpeakableChunkProcessor` |
| `RunEnd` | `StreamEvent.llmComplete(...)`（触发末块 flush + `stream_end`） |
| `ToolUse`/`ToolResult` | 默认隐藏；`forwardToolTrace` 时转既有 `toolCallStart/toolResult`（仅 trace） |
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

## 开放问题（全在 OpenClaw 侧）

1. ACP 是否支持「无新输入纯取消在途 run」→ barge-in 真打断 vs 假停（续烧 token）。
2. OpenClaw 事件流 / 线格式 → 定 `OpenClawEvent` 与 `WebSocketOpenClawClient`。
3. OpenClaw MCP 挂 Pi 设备 server + agent 配置 API。
