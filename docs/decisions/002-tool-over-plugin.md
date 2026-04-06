# ADR-002: Tool Interface over Plugin System

## Status
Accepted

## Context
The original Plugin system (`Plugin`, `PluginFunction`, `PluginRegistry`) was designed before LLM tool calling became standard. It doesn't map cleanly to LLM function calling schemas.

## Decision
Introduced `Tool` interface that directly maps to LLM tool calling. Plugin remains as legacy fallback in `ToolExecutor`.

## Consequences
- **Positive**: `Tool.getSchema()` → `ToolSchema` maps 1:1 to OpenAI/Claude function calling format.
- **Positive**: MCP remote tools wrap cleanly via `McpToolWrapper implements Tool`.
- **Rule**: All new tools MUST implement `Tool`. Never create new `Plugin` implementations.
