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
- Business logic does NOT go here. It belongs in agent-kernel or domain modules.
- This is the ONLY module that should depend on Spring Boot.
- WebSocket protocol handling stays here; agent logic stays in agent-kernel.

## Ports
- REST/SSE: 8080 (Spring Boot)
- WebSocket: 8081 (Vert.x)
