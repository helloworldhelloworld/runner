# agent-mcp

MCP protocol bridge. Wraps MCP tools as `Tool` interface.

## Responsibility
- `McpToolWrapper` adapts MCP tools to `Tool` interface
- Progress/logging notifications → `ToolResultChunk` events
- Supports subprocess (STDIO) and SSE transports

## Dependencies
agent-kernel only.
