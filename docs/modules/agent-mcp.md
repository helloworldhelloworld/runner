# agent-mcp

MCP protocol bridge. Wraps MCP tools as `Tool` interface.

## Responsibility
- `McpToolWrapper` adapts MCP tools to `Tool` interface
- Progress/logging notifications → `ToolResultChunk` events
- Supports subprocess (STDIO) and SSE transports
- Recovers `riskLevel()` across the MCP boundary so `ToolPolicy.byRiskLevel`
  governs remote tools (`_meta` explicit risk → MCP annotations
  `readOnlyHint`/`destructiveHint` → default SAFE). See
  [ADR-007](../decisions/007-risk-level-across-mcp.md).

## Dependencies
agent-kernel only.
