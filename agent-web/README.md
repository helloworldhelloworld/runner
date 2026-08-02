# agent-web

`agent-web` is runner's Spring Boot assembly and transport module. It wires the
kernel, tools, MCP, memory, Soul modules and the selected brain implementation,
then exposes REST, SSE and WebSocket entry points.

Business logic belongs in its owning module, normally `agent-kernel` or a domain
module. `agent-web` should contain transport adapters and application wiring.

## Start

From the repository root:

```bash
cd agent-web
mvn spring-boot:run
```

The default configuration uses:

- HTTP port `8080`
- native `Orchestrator` brain
- Spring WebSocket at `/ws/chat`
- automatic LLM-provider detection, falling back to mock when no credentials
  are available

Check the service:

```bash
curl http://localhost:8080/api/health
```

## LLM providers

OpenRouter or another OpenAI-compatible HTTP endpoint:

```bash
export PROVIDER_TYPE=openrouter
export OPENROUTER_API_KEY=your-key
export OPENROUTER_MODEL=anthropic/claude-sonnet-4
# Optional:
# export OPENROUTER_BASE_URL=http://localhost:8086/llm/openai
```

Anthropic API:

```bash
export PROVIDER_TYPE=api
export ANTHROPIC_API_KEY=your-key
```

Other supported provider types are `pro`, `ws` and `mock`. The authoritative
defaults and environment-variable names are in
[`application.yml`](src/main/resources/application.yml).

## Brain selection

Native orchestration is the default. OpenClaw can be selected without changing
the transport or voice post-processing pipeline:

```bash
export BRAIN_TYPE=openclaw
export OPENCLAW_URL=ws://127.0.0.1:18789
```

See [ADR-014](../docs/decisions/014-brain-swappable-chathandler-openclaw.md)
and the [agent-openclaw module guide](../docs/modules/agent-openclaw.md) for the
current implementation boundary and remaining production integration work.

## WebSocket provider

Spring WebSocket is enabled by default:

```text
ws://localhost:8080/ws/chat
```

To use the standalone Vert.x server:

```bash
export WS_PROVIDER=vertx
export VERTX_WS_PORT=8081
```

```text
ws://localhost:8081/ws/chat
```

Both handlers share request construction and `StreamEvent` serialization.

## Main HTTP entry points

| Endpoint | Purpose |
|---|---|
| `POST /api/chat` | Synchronous chat API |
| `POST /api/chat/stream/reactive` | Reactive SSE chat stream |
| `GET /api/health` | Application and provider health |
| `GET /api/skills` | Registered skills |
| `GET /api/tools` | Registered tools |
| `GET /api/session/{id}/history` | Session history |
| `GET /gateway/tools` | Gateway-visible tools |
| `GET /gateway/tools/mcp/servers` | MCP server state |
| `GET /api/model-config` | Current runtime model configuration |

Additional controllers expose user, assessment, client-tool, schema and plugin
management endpoints. Consult their controller classes before treating those
APIs as stable public contracts.

## MCP

MCP clients are disabled by default. Enable the configured servers with:

```bash
export MCP_ENABLED=true
export MINION_MCP_URL=http://127.0.0.1:8765/mcp
```

The configuration supports STDIO, SSE and Streamable HTTP transports, static
headers and application-provided dynamic headers. See
[`application.yml`](src/main/resources/application.yml) and
[the MCP module documentation](../docs/modules/agent-mcp.md).

## Voice and Minion

Speakable-chunk post-processing is opt-in so non-voice deployments pay no
additional cost. Enable it through the corresponding
`app.voice.speakable-chunk.enabled` property when running the Minion voice
plane. Raw audio is handled by the separate Voice Gateway and never crosses the
runner JVM.

Relevant documentation:

- [agent-web module responsibilities](../docs/modules/agent-web.md)
- [system architecture](../docs/architecture.md)
- [voice text contract](../contract/minion-voice-text/README.md)
- [Minion roadmap](../todo/2026-07-04.md)

## Frontend

The Vue 3/Vite frontend lives in `frontend/`:

```bash
cd agent-web/frontend
npm install
npm run dev
```

Build static assets with `npm run build`.

## Test

From the repository root:

```bash
mvn clean test
```

Use a clean reactor build after shared-interface or wiring changes. The detailed
repository rules are in [AGENTS.md](../AGENTS.md) and
[CLAUDE.md](../CLAUDE.md).
