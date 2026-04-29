# ADR-005: StreamEvent stays as closed enum + factory protocol

## Status
Accepted (2026-04-30)

## Context
During Tier-2 architecture work, a candidate refactor was raised: convert
`StreamEvent` from `class + EventType enum + factory methods` into
`sealed interface StreamEvent` with each variant as a record. The stated
benefit: adding a new event type would no longer require touching the
enum and a constructor in two places.

Survey of usage:
- `event.getType()` and `EventType.X` references: **~498 occurrences across
  94 files** in `agent-kernel`, `agent-web`, `agent-mcp`, `kernel-memory`,
  `agent-demo`, plus serializers and tests.
- The existing model already has two **open extension slots**:
  - `EventType.TRACE` — `(phase, message, data)` carrier for arbitrary
    lifecycle events without modifying the enum.
  - `EventType.POST_PROCESS_DATA` — `(category, data)` carrier for
    pipeline post-processors to inject typed payloads (cards,
    annotations, risk signals).

## Decision
Keep `StreamEvent` as a closed enum + factory protocol. Do not migrate
to a `sealed interface` representation in this iteration.

When new event types are added:
- **Open extension** (caller-defined trace category, post-process data):
  use the existing `TRACE` / `POST_PROCESS_DATA` slots — zero kernel
  changes.
- **First-class event type** (joins the closed protocol, gains
  switch-exhaustiveness in consumers): pay the small fixed cost of
  adding an `EventType` enum constant and a factory method on
  `StreamEvent`. The two-edit cost is the *intended* discipline gate
  for the closed protocol.

## Consequences
- **Positive**: avoids touching ~498 call sites and breaking 94 files of
  consumers, serializers, and tests for a refactor whose runtime
  behaviour is identical.
- **Positive**: `switch (event.getType())` continues to be exhaustive at
  every consumer; future additions trigger compiler warnings on
  non-exhaustive switches.
- **Positive**: open extension slots already cover the common "add a
  custom signal without modifying the kernel" case.
- **Negative**: pattern matching with destructuring (`case AgentRoute(var
  id, var session)`) is not available; consumers still call `getType()`
  and the relevant typed getter.
- **Migration path**: if the cost calculus ever flips (e.g. consumer
  count shrinks, or kotlin-style sealed variants become a hard
  requirement), the migration would be: introduce sealed `StreamEvent`
  alongside; add adapters; deprecate `EventType` after a major version.

## Related
- ADR-001 (Reactor Flux as the unifying abstraction): committed us to a
  single event type flowing end-to-end. This ADR keeps that event type's
  shape stable.
