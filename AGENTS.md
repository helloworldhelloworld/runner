# AGENTS.md

This file is the repository entry point for coding agents. The complete and
authoritative engineering rules live in [CLAUDE.md](CLAUDE.md); read that file
in full before changing code. Do not maintain a divergent copy of those rules
here.

## Before changing code

1. Read [CLAUDE.md](CLAUDE.md).
2. Read the relevant architecture and module documentation:
   - [docs/architecture.md](docs/architecture.md)
   - [docs/conventions.md](docs/conventions.md)
   - [docs/modules/](docs/modules/)
   - [docs/decisions/](docs/decisions/)
3. Confirm the baseline is green with `mvn clean test` when the change can
   affect shared interfaces or cross-module wiring.
4. Search every caller before changing a signature or shared payload.

## Non-negotiable rules

- Use docs-first development: update architecture/module docs and add an ADR
  for a non-trivial decision before implementing it.
- Keep public contracts additive-only. In particular, do not break or remove
  `Tool`, `ToolSourceProvider`, or public `agent-mcp` APIs, including deprecated
  members retained for compatibility.
- Keep dependency flow downward. `agent-kernel` is the foundation and
  `agent-web` is the only assembly/Spring Boot module.
- Use `Flux<StreamEvent>` for reactive streaming. New lifecycle events require
  an explicit `StreamEvent.EventType` and factory; do not encode protocol
  events as `TRACE` strings.
- Prefer the `Tool` system. The legacy plugin package is not an extension point
  for new work.
- Follow outside-in TDD: first prove the cross-component scenario, then add
  focused unit tests. Assert the payload received by the downstream consumer,
  not merely that a method was called.
- For third-party SPIs, let the real framework drive the integration and fake
  only the remote peer. Cover a complete round trip and failure timing.
- Do not add real sleeps or test-only waits to hide a race.

## Build commands

```bash
mvn clean test
mvn clean install
mvn test -Dtest=ClassName#methodName
cd agent-web && mvn spring-boot:run
```

After an interface change, use `mvn clean test`, not plain `mvn test`, so stale
locally installed module artifacts cannot hide a breakage. Modules without
tests still need to compile.

## Generated CI results

`ci-results/` is written by GitHub Actions. Do not hand-edit it. Follow the
conflict-resolution procedure in [CLAUDE.md](CLAUDE.md) when rebasing.
