# agent-mcp

MCP protocol bridge. Wraps MCP tools as `Tool` interface.

## Responsibility
- `McpToolWrapper` adapts MCP tools to `Tool` interface
- Progress/logging notifications → `ToolResultChunk` events
- Supports subprocess (STDIO), SSE, streamable HTTP, and WebSocket transports
- Recovers `riskLevel()` across the MCP boundary so `ToolPolicy.byRiskLevel`
  governs remote tools (`_meta` explicit risk → MCP annotations
  `readOnlyHint`/`destructiveHint` → default SAFE). See
  [ADR-007](../decisions/007-risk-level-across-mcp.md).
- HTTP / SSE / WS transport **default to one process-wide JDK `HttpClient`**
  (`McpHttpClients.shared()`, HTTP/1.1). `ToolClient.Builder.createTransport`
  injects it so per-request transport creation does not spawn a `SelectorManager`
  thread each time. See [ADR-015](../decisions/015-mcp-shared-httpclient.md).
  `ToolClient.close()` does not shut that shared client down. Direct SDK
  `builder().build()` callers must use `McpHttpClients.sharing(client)` themselves.
  Sharing an `HttpClient` does not replace `McpToolClient.close()` — unclosed
  sessions still accumulate `PendingRequest` on the shared client.

## Dependencies
agent-kernel only.
