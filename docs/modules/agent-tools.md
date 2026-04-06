# agent-tools

Tool implementations (math, time, weather, web search, client tools).

## Responsibility
Concrete `Tool` implementations registered via `ToolRegistry`.

## Dependencies
agent-kernel only.

## Rules
- All new tools go HERE, not in agent-kernel.
- Each tool category gets its own sub-package under `com.lightweightai.tools.<category>`.
