# Lightweight AI Runner

Runner is a Java 21 reactive AI-agent framework and application assembly. It
provides a tool-calling agent loop, multi-agent orchestration, MCP integration,
memory and skills, REST/WebSocket transports, and an optional OpenClaw brain.
The same runtime also powers the cloud-side brain of the Minion embodied-agent
project.

## Architecture at a glance

```text
REST / SSE / WebSocket
          |
       Gateway
          |
      ChatHandler
       /      \
Orchestrator  OpenClawChatHandler
       |
    AgentLoop -> LLMProvider -> ToolCallingLoop
                              -> local / CLI / MCP / subagent tools
       |
Flux<StreamEvent> -> post-processors -> client
```

The main design constraints are reactive streaming, first-class typed events,
tool-first extension, per-agent tool isolation, bounded context, and strictly
downward module dependencies. See [the architecture guide](docs/architecture.md)
and [accepted design decisions](docs/decisions/) for details.

## Modules

| Module | Responsibility |
|---|---|
| `agent-kernel` | Core agent loop, orchestration, gateway, tools, LLM, prompt and context APIs |
| `agent-tools` | Reusable local tool implementations |
| `agent-mcp` | MCP client/transport bridge and remote tool adaptation |
| `agent-openclaw` | Optional OpenClaw implementation of the brain `ChatHandler` SPI |
| `agent-sdk` | Simplified public API |
| `kernel-memory` | File/SQLite memory with BM25 and vector search |
| `soul-safety` | Crisis detection and content safety |
| `soul-assessment` | PHQ-9, GAD-7 and PSS-10 assessments |
| `soul-user` | User profile and emotion data |
| `agent-web` | Spring Boot assembly, REST, SSE and WebSocket transports |
| `agent-demo` | Runnable examples |
| `agent-plugin-example` | Dynamic plugin example |

`agent-kernel` depends on no internal module. `agent-web` is the only assembly
module and the only module that should own Spring Boot wiring.

## Requirements

- JDK 21
- Maven 3.9+
- Node.js 18+ only when developing the Vue frontend

## Build and test

```bash
mvn clean test
mvn clean install
```

Run one test when iterating:

```bash
mvn -pl agent-mcp -am \
  -Dtest=ProgressNotificationRouterTerminalGraceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Use `mvn clean test` after shared-interface changes. The repository deliberately
uses clean builds to expose cross-module compatibility problems.

## Run the web application

With no credentials, the provider auto-detection falls back to the mock
provider:

```bash
cd agent-web
mvn spring-boot:run
```

For an OpenRouter or OpenAI-compatible endpoint:

```bash
export PROVIDER_TYPE=openrouter
export OPENROUTER_API_KEY=your-key
export OPENROUTER_MODEL=anthropic/claude-sonnet-4
# Optional for a compatible self-hosted gateway:
# export OPENROUTER_BASE_URL=http://localhost:8086/llm/openai

cd agent-web
mvn spring-boot:run
```

The default brain is the native orchestrator. To select OpenClaw:

```bash
export BRAIN_TYPE=openclaw
export OPENCLAW_URL=ws://127.0.0.1:18789
cd agent-web
mvn spring-boot:run
```

OpenClaw support currently has fake-peer integration coverage; production auth,
persistent connection/reconnect, persona workspace, and a real-gateway smoke
test remain integration work.

Default endpoints:

- REST/SSE: `http://localhost:8080`
- Health: `GET http://localhost:8080/api/health`
- Spring WebSocket: `ws://localhost:8080/ws/chat`
- Vert.x WebSocket: `ws://localhost:8081/ws/chat` when `WS_PROVIDER=vertx`

See [agent-web/README.md](agent-web/README.md) for configuration and API entry
points.

## Minion embodiment

Runner is the cloud brain in a split-plane device architecture:

```text
Media: Device <-> Voice Gateway <-> streaming STT/TTS
Brain: Voice Gateway <-> runner/OpenClaw
Tools: runner/OpenClaw <-> MCP <-> Raspberry Pi body
Eyes: Raspberry Pi <-> USB directives <-> ESP32-S3 displays
```

Raw audio intentionally bypasses the JVM. Runner handles text, typed events,
tool calls, visual feedback, interruption and speakable chunks. Start with
[ADR-006](docs/decisions/006-minion-embodiment-architecture.md) and the
[current Minion roadmap](todo/2026-07-04.md).

## Documentation

- [Agent instructions](AGENTS.md) and [full engineering rules](CLAUDE.md)
- [Architecture](docs/architecture.md)
- [Code conventions](docs/conventions.md)
- [Module responsibilities](docs/modules/)
- [Architecture decisions](docs/decisions/)
- [Extension guides](docs/guides/)
- [Current Minion roadmap](todo/2026-07-04.md)
- [Architecture backlog](todo/2026-07-03.md)

Historical files under `todo/` and `architecture-todos/` document how the
design evolved. They can contain superseded findings; prefer the current
architecture, ADRs, module docs and the newest roadmap.

## Contributing

Read [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) before changing code.
Public APIs are compatibility-sensitive, architectural changes are docs-first,
and cross-layer behavior should be protected by outside-in acceptance tests.
