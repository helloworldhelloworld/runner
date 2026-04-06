# agent-sdk

Public SDK API for building agents. The user-facing surface.

## Responsibility
Simplified API: `Agent`, `AgentBuilder`, `ChatOptions`, `StreamCallback`.

## Dependencies
agent-kernel only.

## Rules
- Keep the public API surface minimal (currently ~8 classes).
- Internal complexity stays in agent-kernel; SDK wraps it simply.
