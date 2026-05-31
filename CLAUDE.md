# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Build Commands

```bash
mvn clean install          # Build all modules
mvn clean test             # Build + run all tests (ALWAYS use after interface changes)
mvn test -Dtest=Class#method  # Run single test
cd agent-web && mvn spring-boot:run  # Run service
```

## Critical Build Rules

- **Interface changes → `mvn clean test`**: Plain `mvn test` uses stale `.m2` jars, hiding breakage.
- **Grep all callers**: Before changing a method signature, `grep -r` across the entire repo.
- **No module left behind**: Modules without tests (e.g. `agent-demo`) still need `mvn clean compile`.

## Git CI-Results Conflict Resolution

`ci-results/` is auto-updated by GitHub Actions. On conflicts:
```bash
git checkout --theirs ci-results/build.log ci-results/latest.json
git add ci-results/
git rebase --continue
```

## Architecture Knowledge Base

Before making changes, read the relevant docs:

| What you need to know | Where to look |
|---|---|
| Module structure & dependency rules | [docs/architecture.md](docs/architecture.md) |
| Naming & code conventions | [docs/conventions.md](docs/conventions.md) |
| Why a design decision was made | [docs/decisions/](docs/decisions/) |
| A specific module's responsibility | [docs/modules/](docs/modules/) |
| How to add a provider/tool/skill | [docs/guides/](docs/guides/) |

## Core Principles

1. **Agent Loop pattern** — LLM call → tool detection → tool execution → re-prompt loop
2. **Reactive streaming** — `Flux<StreamEvent>` as the unified streaming abstraction
3. **Events as first-class citizens** — All lifecycle events MUST be `StreamEvent.EventType` enum values with factory methods. NEVER use TRACE + string conventions for new event types.
4. **Tool-first extensibility** — `Tool` interface + `ToolRegistry` + MCP bridge
5. **Multi-Agent Orchestrator** — Orchestrator routes to multiple AgentLoop instances via AgentProfile; ScopedToolRegistry enforces per-agent tool permissions; SubagentRuntime handles async spawn/announce/cascade-stop
6. **Context budget awareness** — ContextCompactor (Snip+Micro) prevents context overflow; CostTracker tracks token consumption per request chain
7. **Dependency flows downward** — See [docs/architecture.md](docs/architecture.md) for allowed dependency directions

## TDD Rules (ENFORCED)

### Outside-in first, inside-out second

1. **Start with acceptance tests** — Write scenario-level tests that cross component boundaries BEFORE writing unit tests. These tests simulate real user/LLM interaction chains (e.g. "spawn 3 subagents → wait all → use results").
2. **Let red tests drive design** — The acceptance test won't compile because classes don't exist yet. That compilation failure tells you exactly what to build.
3. **Then write unit tests** — For each class identified by the acceptance test, write focused unit tests.
4. **Green from outside in** — Unit tests green first, then acceptance test green.

### Why this order matters

Inside-out TDD (unit test per class) has a blind spot: each class works in isolation, but the **wiring between classes** is untested. Real bugs found by outside-in approach in this project:

- `CancellationToken` created in `InterruptibleRun` but never passed to `AgentLoop` → `ToolCallingLoop` (transmission chain broken — each class tested fine alone)
- `ScopedToolRegistry` overrode `get()` and `has()` but not `isEnabled()` → `ToolExecutor` used `isEnabled()` → tools invisible (unit tests for `get()/has()` all green, but the real call path failed)
- `spawn_subagent` tool worked alone, but no `wait_subagent` tool existed → LLM could spawn but never collect results (feature gap invisible to unit tests)
- `AgentFactory` built `AgentLoop` with default empty `LLMOptions` — `toolRegistry.getToolDefinitions()` was never injected into `LLMOptions.toolDefinitions`, so Claude/OpenRouter never saw any tools on the Orchestrator path (both `ToolRegistry.getToolDefinitions()` and `ClaudeProvider.buildRequestBody` had green unit tests; only the wiring between them was broken)

### UT rules — when writing/adding tests, follow these (extracted from real bugs above)

The pattern behind all four bugs above: **each zero-link works, the chain doesn't carry the payload.** Future UTs MUST follow these rules to catch this class of bug:

1. **Assert the payload, not just the ceremony.** `assertNotNull(agent)` / "no exception thrown" / "method was called" are **not** valid test endpoints for wiring tests. Always follow with an assertion on the actual field that downstream code consumes (e.g. `llmOptions.getToolDefinitions()`, `event.getResponse().getStopReason()`, the outgoing HTTP body's `tools` array).

2. **Mocks MUST capture, not swallow.** When stubbing a cross-layer dependency (`LLMProvider`, `ToolExecutor`, `MemoryProvider`), record the inputs into an `AtomicReference` / `List` so tests can later assert on them. A mock that ignores its arguments is how the `toolDefinitions` bug survived for so long. Use/extend the shared `CapturingLLMProvider` test helper instead of re-inventing per-file anonymous mocks.

3. **Every producer–consumer pair needs ≥ 1 transmission test.** For any field that flows through the agent stack (Profile → Factory → AgentLoop → ToolCallingLoop → Provider → HTTP body), write one test that sets it at the top and asserts it shows up at the bottom. Missing links in this chain are what kept toolDefinitions invisible.

4. **Any `// TODO verify via execution` / "间接验证" comment IS a bug waiting to happen.** Replace with an explicit assertion or delete the test — those comments are a known failure mode in this codebase (see `AgentFactoryTest.createsAgentWithScopedTools` history).

5. **When fixing a transmission-chain bug, add a payload-capture acceptance test first.** Don't stop at the unit test for the class you changed. Example: this repo's `OrchestratorToolDefinitionsAcceptanceTest` uses a spy `LLMProvider` to prove the chain carries the filtered tool list end-to-end — that template is the canonical shape for future chain-payload tests.

6. **Red→green discipline:** acceptance test must fail BEFORE the fix for the right reason (assert the missing payload, not a NullPointerException). If the red test only fails because of a crash, the assertion is too weak.

### Baseline discipline

- Run `mvn clean test` BEFORE making changes — confirm baseline is green
- Run `mvn clean test` AFTER each completed module — catch regressions immediately
- Never assume "should work" — actually run it
- If existing tests fail, fix them BEFORE adding new code

### Integration-seam rules — when our code implements a third-party framework SPI

The UT rules above guard transmission chains *inside* our own stack. This section guards the layer *outside* it: the seam between our code and an external framework (MCP SDK, the Claude/OpenRouter HTTP APIs, the web WebSocket layer). Every bug in the `WebSocketMcpClientTransport` `initialize` saga lived here — each unit was green, but **we assumed how the framework calls us instead of verifying it**, and no test ever let the real framework drive our code. Rules:

1. **Let the framework drive your code — don't call yourself.** Any class implementing a framework SPI (e.g. `McpClientTransport`, an `LLMProvider`) needs ≥1 test where the *real framework* exercises it end-to-end (e.g. drive `WebSocketMcpClientTransport` through a real `McpAsyncClient.initialize()`, not by calling `transport.connect()/sendMessage()` yourself). Hand-rolled calls reproduce *your* assumptions, never the framework's actual orchestration (the `connect().subscribe()` fire-and-forget call that broke initialize was invisible to hand-rolled tests).

2. **Fake the remote peer, not your own layer.** Stand up a faithful fake of the *network peer* (like `MiniWebSocketServer`, a real RFC-6455 server) and keep everything between your code and it real — real SDK, real transport, real JSON-RPC. NEVER mock the SDK or the transport itself; mocking the seam erases the very thing under test.

3. **Derive contract assertions from the framework's own reference implementation.** Before implementing an SPI, read the framework's bundled impls (e.g. `HttpClientSseClientTransport`) and assert the behaviors it actually relies on. For MCP transports the proven contract is: `connect()` is fire-and-forget so `sendMessage` may be called before the link is ready → **`sendMessage` must suspend on a readiness signal, not reject**; the response path must route correlated request→response back to the handler; handshake failure must surface the real cause, not a swallowed timeout.

4. **No test-only waits.** `await(isConnected)` / `Thread.sleep` / retry loops inserted to make a test pass are patches for a wait the real caller never performs — they mask timing bugs (an `await(isConnected)` between `connect()` and `send` hid the race for two iterations). Timing tests assert the post-condition at the instant the operation returns; the only sanctioned `sleep` is a *negative* one ("confirm X still hasn't happened").

5. **Round-trip, not one-way; failure timing, not just happy path.** Integration tests must drive a full request→response round-trip with id correlation (return-path bugs hide in one-way tests), and must cover reverse timing: connect-failure fails fast with the real cause, post-disconnect sends fail fast rather than hang. `WebSocketMcpInitializeRoundTripTest` (real SDK initialize over a fake server) is the canonical template; `MiniWebSocketServer` can auto-answer `initialize` to make these cheap.

Backstop: a faithful fake can still drift from a real server — keep one periodic smoke/contract test against a real peer where feasible.

## Orchestrator Architecture (4 Layers)

```
Layer 0: AgentProfile           — Agent identity & config
Layer 1: AgentRegistry          — Multi-agent registry + ScopedToolRegistry (deny > allow)
Layer 2: Orchestrator           — ChatHandler impl, routing + InterruptibleRun (interrupt/resume)
Layer 3: SubagentRuntime        — Async spawn + push announce + cascade stop
```

Key classes: see [docs/architecture.md](docs/architecture.md) for full execution flow and package structure.

### Adding a new Agent

1. Create an `AgentProfile` with agentId, systemPrompt, toolAllowList/toolDenyList, maxSpawnDepth
2. Register it in `AgentRegistry`
3. Orchestrator will auto-route requests by `agentId` in GatewayRequest metadata

### Session Key Namespace

```
Main Agent:   agent:<agentId>:main:<sessionId>
Subagent:     agent:<agentId>:subagent:<uuid>
```

MemoryProvider isolates by sessionKey — different agents never see each other's history.
