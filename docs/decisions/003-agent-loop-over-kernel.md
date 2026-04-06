# ADR-003: Agent Loop over Kernel Pattern

## Status
Accepted

## Context
The original design used a `Kernel` abstraction for task orchestration. In practice, the dominant execution pattern is: LLM call → detect tool calls → execute tools → re-prompt until done.

## Decision
Replaced abstract `Kernel` with concrete `AgentLoop` + `ToolCallingLoop`. The loop is pragmatic and explicit rather than abstractly orchestrated.

## Consequences
- **Positive**: Simple, debuggable, maps directly to the LLM tool calling protocol.
- **Positive**: `ToolCallingLoop` handles recursive LLM calls with tool results appended.
- **Trade-off**: Less flexible than a generic task DAG, but sufficient for current use cases.
