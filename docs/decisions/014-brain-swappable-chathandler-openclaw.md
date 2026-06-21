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

5. **工具 / persona 让位 OpenClaw**：minion persona = OpenClaw agent 配置；Pi 设备 MCP（ADR-008 streamable_http：舵机/眼睛/摄像头）
   挂 OpenClaw 的 MCP toolset。openclaw 模式下 runner 的 `ScopedToolRegistry` / risk policy 让位。

6. **会话历史 / compaction 归 OpenClaw**：openclaw 模式下 runner 聊天环不写 `MemoryProvider`；runner 仅以 `sessionId` 映射
   OpenClaw session（`agent:minion:main:<sessionId>` ↔ OpenClaw session）。

7. **安全前置不变**：`crisisDetector` 在 `SpringChatWebSocketHandler.handleChat`、即**脑之前**触发，故 openclaw 模式同样受保护，零改动。

## Consequences

- ✅ config 一键切；语音平面 / 序列化 / WS / barge-in 入口 / 安全检测**零改动**；拿到 OpenClaw runtime 红利
  （compaction / subagent / feature-flag / 多渠道 inbox）。
- ✅ 适配器可在**不连真 OpenClaw** 时用 fake client 完整测通（缝 + 映射 + barge-in + 切换），与外部依赖解耦推进。
- ⚠️ barge-in 真打断依赖 OpenClaw ACP「无新输入纯取消在途 run」；未证实则**降级**为停转发但 OpenClaw 续烧 token（开放问题①）。
- ⚠️ openclaw 模式下 runner 的工具治理 / risk policy 让位，移交 OpenClaw（取舍：通用度 ↔ 专用治理）。
- ⚠️ 两脑契约对齐靠**假对端接缝测试**防漂；OpenClaw 事件流 / 取消语义是外部依赖（开放问题①②）。
- ⚠️ 历史归 OpenClaw → runner 侧记忆 / 学习链在 openclaw 模式下不积累（如需，另设单向同步，超本 ADR）。

## Alternatives rejected

- **LLMProvider 缝（OpenClaw 当裸补全后端）**：runner 续跑 loop / 工具，OpenClaw 仅供 token —— 拿不到 OpenClaw loop / 工具红利，
  且 OpenClaw 是 runtime 非补全端点，浪费其价值。否。
- **standalone bridge 重写 R2–R4**：在 OpenClaw 栈里重写可说块 / emotion / barge-in / 契约，失复用，工作量数倍。否。

（两方案对比详见 [architecture-todos/2026-06-19-openclaw-brain-swap.md](../../architecture-todos/2026-06-19-openclaw-brain-swap.md) §4。）

## 开放问题（全在 OpenClaw 侧，阻塞真实现阶段，不阻塞适配器 + 假对端）

1. OpenClaw ACP 是否支持「无新输入纯取消在途 run」→ 决定 barge-in 真打断 vs 假停。
2. OpenClaw 事件流 / 线格式（token 增量 + run 开始 / 结束 / 取消）→ 定 `OpenClawEvent` 与 `WebSocketOpenClawClient`。
3. OpenClaw MCP 挂 Pi 设备 server + agent 配置 API → 决定 §5 端到端。
