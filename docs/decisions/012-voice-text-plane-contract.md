# ADR-012：语音文本平面契约 + 入站 barge-in（Voice Gateway ⟷ runner WS）

状态：Accepted（2026-06-16）
关联：[ADR-006 D2 双平面分离](006-minion-embodiment-architecture.md)、[ADR-005 StreamEvent 闭合协议](005-streamevent-closed-protocol.md)

## Context

ADR-006 D2 把系统拆成两条平面：媒体平面（二进制音频，绕开 JVM）与**大脑平面**（Voice Gateway ⟷ runner，纯文本 + 工具 + 事件）。耦合点只有文本：STT 转写 → runner 输入；runner 可说块/打断 → Voice Gateway。

巡检（M-2 / C3）发现两处缺口：

1. **大脑平面文本契约 runner 侧无跨仓接缝测试**，且三仓间是**隐式 JSON 约定**、无共享 schema。runner 只对「设备工具平面（MCP）」做了 fake 锁定（`RunnerToMinionBodyMcpSmokeTest` 等），文本平面对 Python 仓基本裸奔——只有单元级 `WsChatRequestsTest` 验证入站 `agentId` 路由。
2. **入站 barge-in 是真空缺**：kernel 的打断（`InterruptibleRun.interrupt()`）目前只在**新 chat 消息到达**时由 `Orchestrator.chatStreamReactive` 隐式触发。Voice Gateway 需要的是：用户一开口（VAD 命中）就立即停 TTS，**此时新转写还没来**。WS 两个 handler 的 `switch(type)` 里都没有 `interrupt` case，没有独立打断入口。

## Decision

### D1. 固化大脑平面文本契约为共享 JSON Schema

新增 `contract/minion-voice-text/messages.schema.json`（JSON Schema draft 2020-12），按消息类型分 `$defs`，三仓共享：

- 入站：`inbound.chat`（STT 文本）、`inbound.interrupt`（barge-in 信号）
- 出站：`outbound.token`、`outbound.speakable_chunk`（`{text,index,emotion}`）、
  `outbound.speech_interrupted`（`{runId,reason}`）、`outbound.stream_end`（`{meta:{emotion}}`）

形状取自 runner 既有实现（`StreamEventSerializer`），即把已有隐式约定写成可机器校验的契约，而非新发明格式。runner 侧接缝测试**用此 schema 校验**出/入站帧，schema 与线格式漂移即测试红。

### D2. 入站 barge-in：独立打断入口（additive）

- `ChatHandler` 加 `default StreamEvent interrupt(String sessionId)`（默认返回 `null`，非交互式 handler 无需改动）。
- `Orchestrator` override `interrupt(sessionId)`：查 `activeRuns`，若在途则 `existing.interrupt()` 并返回
  `StreamEvent.speechInterrupted(runId, "barge-in")`；无在途 run 返回 `null`。这把原先内嵌在
  `chatStreamReactive` 的打断逻辑提成可独立调用的入口，语义一致。
- `Gateway` 加 `interrupt(sessionId)` 委托 `chatHandler.interrupt(...)`。
- `SpringChatWebSocketHandler` 加入站 `interrupt` / `barge_in` case：调 `gateway.interrupt(sessionId)`，
  dispose 该 session 的在途订阅，并把返回的 `SPEECH_INTERRUPTED` 经 `StreamEventSerializer` 下发。

均为新增成员/默认方法/新枚举值之外的纯增量，符合 Public API Stability（`Gateway`/`ChatHandler` 为对外契约，加 `default` 方法不破坏既有实现）。

### D3. runner 侧跨仓接缝测试（fake 远端对端）

`MinionVoiceTextSeamAcceptanceTest`：真 `SpringChatWebSocketHandler` + 真 `Gateway`（接真
`SpeakableChunkProcessor` 管道）+ 真 `StreamEventSerializer`，对端用记录出站帧的假
`WebSocketSession`（= 假 Voice Gateway），驱动：

- **STT 文本入 → 可说块出**：送 `inbound.chat`（多句）→ 断言出站帧含 `speakable_chunk` + `stream_end`，且**逐帧用 schema 校验**；
- **barge-in 入 → 停播出**：送 `inbound.interrupt` → 断言下发 `speech_interrupted`（schema 校验）且在途订阅被取消。

kernel 侧另有 `OrchestratorInterruptAcceptanceTest` 确定性验证 `Orchestrator.interrupt(sessionId)` 的打断语义（gated agent，无 sleep）。

## Consequences

- 大脑平面文本契约从隐式约定升级为三仓可引用、可机器校验的 schema；runner 侧接缝测试守住它。
- Voice Gateway 获得独立 barge-in 入口，可在 STT 完成前停 TTS。
- 媒体平面仍不进 runner（D2 不变）。schema 暂只覆盖文本平面核心消息，其余 WS 消息类型（skill creator/assessment 等）不属此契约、不在此 schema。
