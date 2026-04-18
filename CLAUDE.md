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

### Baseline discipline

- Run `mvn clean test` BEFORE making changes — confirm baseline is green
- Run `mvn clean test` AFTER each completed module — catch regressions immediately
- Never assume "should work" — actually run it
- If existing tests fail, fix them BEFORE adding new code

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
