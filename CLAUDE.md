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
3. **Tool-first extensibility** — `Tool` interface + `ToolRegistry` + MCP bridge
4. **Dependency flows downward** — See [docs/architecture.md](docs/architecture.md) for allowed dependency directions
