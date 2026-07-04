# OpenClaw 换脑设计：把"脑"做成可切换 SPI —— 2026-06-19

> **已采纳：ChatHandler 缝 / 完整大脑（方案 B 的 ChatHandler 变体）。** 决策与落地见
> [ADR-014](../docs/decisions/014-brain-swappable-chathandler-openclaw.md)、模块文档
> [docs/modules/agent-openclaw.md](../docs/modules/agent-openclaw.md)。本文保留为设计对比 / 取舍记录。

> 承接 [2026-04-08 OpenClaw/Claude Code 对标](2026-04-08.md)。问题：能否把小黄人的"脑"从
> **runner（自研 Java）** 换成 **OpenClaw（外部 TS agent runtime, 2026.4）**，且两者方便切换？
> 依据：[ADR-006 D2 双平面](../docs/decisions/006-minion-embodiment-architecture.md)、
> [ADR-012 语音文本契约](../docs/decisions/012-voice-text-plane-contract.md)、
> 契约 `contract/minion-voice-text/messages.schema.json`。

## 0. 一句话结论
**换得了、但不是平移；"方便切换"是可设计的。** 关键看在哪条缝上换：传输缝已是干净 SPI（契约），
协议缝要写 adapter，**能力缝（R1–R5）才是真成本**——小黄人的价值全在 runner，OpenClaw 是通用 runtime，不带这些。
推荐 **方案 B1**（OpenClaw 当 loop/tool 引擎，runner 保留语音/切块/emotion/barge-in/契约），用 config 切换。

## 1. 缝的三层（换在哪一层，成本天差地别）

```
voice-gateway (Python) ──WS(契约)──▶ [ 脑 ]          媒体平面(音频) 绕开 JVM
                                       ├─ A) runner(Java)        ：原生实现契约（今天）
                                       └─ B) openclaw-bridge     ：契约前脸 ⇄ OpenClaw Gateway/ACP 后端
```

- **传输缝 ✅ 已是 SPI**：voice-gateway 只认契约 5 出站 + 2 入站帧，不认脑的内部。任何"会说这几帧"的脑即插即用。
- **协议缝 ⚠️ 要 adapter**：OpenClaw 自己的 Gateway 是另一套 WS（`127.0.0.1:18789` + ACP dispatch），不原生讲本契约。
- **能力缝 🔴 真成本**：R1–R5 是 runner 专属，OpenClaw 没有（见 §3）。

## 2. 逐帧映射（契约 ⟷ OpenClaw）

| 契约帧 | 向 | OpenClaw 原生来源 | adapter 工作 | 缺口 |
|---|---|---|---|---|
| `inbound.chat{message,sessionId,agentId}` | gw→脑 | inbound message / ACP user turn | sessionId↔OpenClaw session/channel；agentId→选 OpenClaw agent | persona 路由需映射到 OpenClaw agent 配置 |
| `inbound.interrupt`/`barge_in` | gw→脑 | ACP cancel 在途 run | 转 ACP 取消；**必须 pre-STT**（VAD 即发）→ 立即取消 | ❓OpenClaw 是否支持"无新输入纯取消" |
| `outbound.token{data}` | 脑→gw | token/delta 事件 | 文本增量透传 | 低 |
| `outbound.speakable_chunk{text,index,emotion}` | 脑→gw | **无**（OpenClaw 只流 token，不切句、无 emotion） | adapter 自跑切块+emotion（= 复用 R2/R3） | **最大缺口** |
| `outbound.speech_interrupted{runId,reason}` | 脑→gw | run-cancelled 事件 | cancel ack → 该帧；合成 runId/reason | 依赖 R4 取消语义 |
| `outbound.stream_end{meta.emotion}` | 脑→gw | turn/run complete | run 结束发；算整轮 emotion | 次要，依赖 R3 |

## 3. 能力缺口 R1–R5（OpenClaw 不带，必须移植或代理）

| 能力 | runner 现状 | OpenClaw | 缺口/做法 |
|---|---|---|---|
| **R1 视觉回灌链** | frame→ImageContent→下一轮多模态（ADR-010） | 文本/工具导向 | ❓OpenClaw 是否支持 vision 上下文；要把设备帧喂进去。中–高 |
| **R2 可说块切分** | `SpeakableChunkProcessor` | 无 | 必移植（含 [ADR-013](../docs/decisions/013-first-chunk-timed-flush.md) 首响杠杆）。高 |
| **R3 逐块 emotion** | `EmotionClassifier` | 无 | 随 R2 一起。高 |
| **R4 barge-in 独立打断** | `Orchestrator.interrupt()`（ADR-012） | ACP cancel | 需"纯 interrupt 取消在途 run"；否则 adapter 假装停转发但 OpenClaw 仍在烧 token。中（协议风险） |
| **R5 persona + MCP 设备平面** | minion persona + 到 Pi 的 MCP（ADR-008） | **自带强 MCP/多工具** | 这是 OpenClaw *帮忙* 处：Pi 设备 MCP server 可直接挂到 OpenClaw；persona=OpenClaw agent 配置。低–中 |

**关键洞察**：R2/R3 + 首响杠杆是 `agent-kernel/core/postprocess` 的我方代码。最便宜的 adapter **不是另起 TS 进程重写**，
而是**把 OpenClaw 当 token/loop 来源塞进 runner 现有缝**，复用 postprocess + 契约序列化 + barge-in 入口。
于是"换成 OpenClaw"不等于"扔掉 runner"，而是"**给 runner 加一个 OpenClaw 后端**"——成本骤降、R1–R5 保住。

## 4. 两个方案

### 方案 A：完整替换（standalone openclaw-bridge）
独立进程实现契约前脸，在 OpenClaw 栈里重写 R2–R5。**扔掉 runner**，换取 OpenClaw runtime 的红利
（compaction / subagent 隔离 / feature flag / 多渠道 inbox，见 [2026-04-08](2026-04-08.md)）。
代价：R2/R3/契约/barge-in 全要在 TS 重做，失去复用。**仅当你决心全面标准化到 OpenClaw 才选。**

### 方案 B1：OpenClaw 当 loop/tool 引擎，runner 保留语音平面（推荐）
- OpenClaw 负责：跑 agent loop、调 model、执行 tool（含挂 Pi 设备 MCP）、persona。
- runner 负责：契约 WS 前脸、R2/R3 切块+emotion、R4 barge-in 代理、stream_end、首响杠杆。
- runner 内新增 `BrainEngine`/`AgentSource` SPI，两实现：`NativeAgentLoopBrain`（今天）/ `OpenClawBrain`（adapter）。
  **复用 todo P0#4 已规划的 `LLMProviderFactory→ServiceLoader` SPI 模式**——换脑与换 provider 同一套机制。
- 切换 = 一个 `@ConditionalOnProperty` / 工厂 config。**这就是"方便切换"。**

> B1 的边界：OpenClaw 拥有 loop+tools，runner 的 orchestrator/ToolRegistry 在该模式下让位（工具走 OpenClaw 的 MCP）。
> 若想 runner 仍管自己的 loop/tools、只把 OpenClaw 当"裸 completion 后端"（记 B2）——不划算：OpenClaw 是 runtime 不是 LLM 端点，浪费其价值，且未必暴露裸补全 API。**不推荐 B2。**

## 5. 工作量粗估（仅 adapter，不含 OpenClaw 自身部署）

| 方案 | 主要工作 | 量级 |
|---|---|---|
| **B1** | OpenClaw 事件流→runner `TEXT_DELTA/LLM_COMPLETE` 桥；barge-in→ACP cancel + speech_interrupted；persona→OpenClaw agent + Pi MCP 挂载；R1 帧喂图 | 数个工程周，瓶颈在 **ACP 取消语义** 与 **OpenClaw vision** 两个未知 |
| **A** | B1 + 在 TS 重写 R2/R3 + 契约 server + barge-in，失复用 | B1 的数倍 |

复用项（B1 ≈ 0 成本）：`SpeakableChunkProcessor`、`EmotionClassifier`、`FirstChunkPolicy`、StreamEvent→帧序列化。

## 6. 决策线
- **留 runner**：只要语音平面、暂不急要 compaction/subagent/flags → 不动。
- **B1（推荐）**：想要 OpenClaw runtime 成熟度，又不丢语音/emotion/barge-in；config 可切换、可 A/B。
- **A**：决心弃 runner、全面 OpenClaw，接受语音平面移植成本。

## 7. 待验证（动手前必答）
1. **ACP 是否支持"无新输入的纯取消在途 run"**？决定 R4 barge-in 是真打断还是假停（烧 token）。
2. **OpenClaw 是否支持 vision 上下文**？决定 R1 视觉回灌可否平移。
3. **OpenClaw 事件流是否暴露稳定的 token 增量 + run 开始/结束/取消事件**？决定逐帧映射可行性。
4. Pi 设备 MCP（ADR-008 streamable_http）能否直接挂 OpenClaw 的 MCP toolset？决定 R5 是"帮忙"还是"又一处适配"。

> 三个未知都在 OpenClaw 侧（`docs.openclaw.ai` / ACP runtime）。建议先做 §7 的 spike，再定 A/B1。
