# agent-web

The assembly and transport module. Spring Boot REST/SSE + Vert.x WebSocket server.

## Responsibility
Wires all modules together, provides HTTP/WebSocket endpoints, Spring configuration.

## Key Packages

| Package | Purpose |
|---|---|
| `.config` | Spring Boot auto-configuration |
| `.controller` | REST controllers (gateway/chat, assessment) |
| `.gateway` | WebSocket gateway adapters |
| `.websocket` | Vert.x WebSocket server |
| `.agent` | Agent configuration and wiring |
| `.observer` | AgentObserver implementations |
| `.postprocess` | Web-specific stream post-processors |
| `.service` | Application services |
| `.skillcreator` | Skill creation tooling |

## Orchestrator agent assembly

`config/OrchestratorConfig` builds the `AgentRegistry` (enabled via `app.orchestrator.enabled=true`).
Registered personas:

| agentId | Persona | Tool policy | maxSpawnDepth |
|---|---|---|---|
| `default` | 默认助手 | all tools | 1 (may spawn) |
| `worker` | 专注执行的工作助手 | all tools | 0 |
| `minion` | 小黄人 — 桌面具身陪伴体（见 [ADR-006](../decisions/006-minion-embodiment-architecture.md) / [minion-body.md](minion-body.md)）| `ToolPolicy.byRiskLevel(SYSTEM)` — 放行 `deviceType="minion"` 的运动/视觉工具（`RiskLevel.SYSTEM`），口语化短回复 | 0 |

The `minion` persona is the runner-side half of R5: its `ToolPolicy.byRiskLevel(SYSTEM)` gate is what
exposes the Pi's motion/vision MCP tools to the LLM once the device's MCP server is connected (real Pi
tool接入仍待硬件). The kernel-side risk-gating wiring it relies on is proven by
`MinionToolWiringAcceptanceTest`; the persona registration here by `MinionPersonaAssemblyTest`.

Both WebSocket handlers (Vert.x + Spring) build the `GatewayRequest` via the shared
`websocket/WsChatRequests.baseBuilder`, which maps an inbound `agentId` field into
`metadata("agentId", …)` so `MetadataAgentRouter` routes the turn to that persona (e.g. voice → minion).
Proven by `WsChatRequestsTest`.

## Stream post-processors (`config/PostProcessorConfig`)

`StreamPostProcessor` beans are auto-collected by `GatewayConfig` into the real Gateway pipeline
(`Gateway.handleStreamReactive` → `stream.transform(pipeline)`). Each is opt-in via a `@ConditionalOn*`
guard so a deployment only pays for what it declares:

| Bean | Guard | Purpose |
|---|---|---|
| `riskControlProcessor` | `@ConditionalOnBean(RiskChecker)` | 风控拦截 |
| `deeplinkProcessor` / `cardAppendProcessor` | `@ConditionalOnBean(resolver/provider)` | 文本增强 |
| `tracing*` | `app.tracing.enabled` (default on) | 调用链追踪 |
| `speakableChunkProcessor` | `app.voice.speakable-chunk.enabled` (default **off**) | Minion R2/R3：在 `LLM_COMPLETE` 前切出带 emotion 的 `SPEAKABLE_CHUNK`，喂下游 Voice Gateway 流式 TTS |

`speakableChunkProcessor` wires the kernel's `SpeakableChunkProcessor` (+ optional `EmotionClassifier`
bean, defaulting to `EmotionClassifier.NEUTRAL`) into production — without it the processor is never
instantiated and the real Gateway never emits `SPEAKABLE_CHUNK`. The flag is **off by default** so
non-voice personas/deployments are unaffected; the voice/minion brain deployment sets it `true`.
End-to-end wiring (real `GatewayConfig` + `PostProcessorConfig` → `SPEAKABLE_CHUNK` reaches output)
is proven by `SpeakableChunkWiringAcceptanceTest`. Per-persona gating within a mixed-persona runner
is a follow-up (requires threading persona context into the post-processor pipeline).

## Dependencies
Depends on ALL other modules — it is the top-level assembly.

## Rules
- Profile and Spring budget settings must reach actual model requests and execution stop
  events; see [ADR-026](../decisions/026-execution-usage-budget-compaction.md).
- Authenticated session and persistent memory assembly follows
  [ADR-025](../decisions/025-authenticated-session-memory.md): server-resolved identity
  binds chat, interrupt, observation, history and memory tools to one namespace and store.
- Content-bound publication follows [ADR-024](../decisions/024-content-bound-skill-publication.md):
  test reports bind candidate and evaluation content, activation updates durable active state
  transactionally, and native PromptSource reads that state after restart. Legacy unbound
  reports remain readable but do not authorize new publication.
- Native assembly consumes the same PromptSource used by publication and exposes its
  execution snapshot; see [ADR-023](../decisions/023-execution-assembly-snapshot.md).
  Persona base prompts remain profile-specific; published skills cannot grant tool permissions.
- Model hot swaps follow [ADR-022](../decisions/022-execution-model-binding.md):
  DynamicLLMProvider exposes execution binding while retaining direct-call delegation.
  Native execution freezes the selected provider, including its resilience wrapper;
  new executions observe successful management updates. Real Spring/MVC and HTTP/SSE
  round trips must prove the model and tool payloads reaching the remote peer.
- Spring WS interrupt and disconnect must reach the current foreground execution's
  subagents through the existing cancellation chain, verified with real transport;
  see [ADR-020](../decisions/020-foreground-subagent-cancellation.md).
- Native OrchestratorConfig installs no global wait/list/stop controls. Runtime
  assembly creates local task controls for each root and child session; see
  [ADR-019](../decisions/019-subagent-inheritance-and-ownership.md).
- WebSocket handlers bind the current connection's dispatcher into GatewayRequest;
  native tool execution must not depend on the last connected session's global delegate.
  See [ADR-018](../decisions/018-request-tool-execution-scope.md).
- Business logic does NOT go here. It belongs in agent-kernel or domain modules.
- This is the ONLY module that should depend on Spring Boot.
- WebSocket protocol handling stays here; agent logic stays in agent-kernel.

## Ports

Spring `/ws/chat` 的可选 `turnId`、异步启动前取消及身份化清理遵循
[ADR-015](../decisions/015-voice-turn-cancellation.md)。旧请求无该字段时保持原出站格式。
- REST/SSE: 8080 (Spring Boot)
- WebSocket: 8081 (Vert.x)

ADR-026 装配增量（待跨模块验收）：原生默认 Profile 从
`app.orchestrator.max-output-tokens`（4096）和 `app.orchestrator.max-budget-tokens`（0，无限）
读取模型输出上限与根预算。REST reactive SSE 和 Spring/Vert.x WebSocket 将内核
`BUDGET_STOPPED` 转发为 `budget_stopped`，保留结构化 reason 与 usage 数据；不能只正常结束
连接而丢失原因。ChatController 自有执行池由其生命周期关闭，接受中的请求在服务关闭时取消。
# 旧统一 Gateway HTTP 入口

`/gateway/chat` 与 SSE 在请求线程绑定服务器主体，session 历史/摘要/清空也使用同一 scope。
请求 JSON 的 extra 只承载业务元数据，不能决定主体。两套 HTTP 回调 SSE 都保留结构化
预算停止事件和最终 metadata；Controller 自有 executor 随容器销毁释放。见 ADR-025/026。
