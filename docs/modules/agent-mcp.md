# agent-mcp

MCP protocol bridge. Wraps MCP tools as `Tool` interface.

## Responsibility
- `McpToolWrapper` adapts MCP tools to `Tool` interface
- Progress/logging notifications → `ToolResultChunk` events
- Supports subprocess (STDIO), SSE, streamable HTTP, and WebSocket transports
- HTTP / SSE / WS transport **default to one process-wide JDK `HttpClient`**
  (`McpHttpClients.shared()`, HTTP/1.1). `ToolClient.Builder.createTransport`
  injects it so per-request transport creation does not spawn a `SelectorManager`
  thread each time. See [ADR-015](../decisions/015-mcp-shared-httpclient.md).
  `ToolClient.close()` does not shut that shared client down. Direct SDK
  `builder().build()` callers must use `McpHttpClients.sharing(client)` themselves.
  Sharing an `HttpClient` does not replace `McpToolClient.close()` — unclosed
  sessions still accumulate `PendingRequest` on the shared client.
- HTTP / SSE transports created by `ToolClient.Builder.createTransport` also
  track requests per transport. Closing cancels in-flight GET / POST requests
  immediately, including requests waiting for response headers, then allows up
  to five seconds for session DELETE cleanup. Other transports using the shared
  client remain usable. Repeated close calls share one cleanup operation.
  Direct SDK builders (including those using `McpHttpClients.sharing`) bypass
  this cancellation fix; use the transport factory to obtain it.

## Dependencies
agent-kernel only.
