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

## Dependencies
Depends on ALL other modules — it is the top-level assembly.

## Rules
- Business logic does NOT go here. It belongs in agent-kernel or domain modules.
- This is the ONLY module that should depend on Spring Boot.
- WebSocket protocol handling stays here; agent logic stays in agent-kernel.

## Ports
- REST/SSE: 8080 (Spring Boot)
- WebSocket: 8081 (Vert.x)
