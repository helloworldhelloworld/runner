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
- HTTP / SSE transports created by `ToolClient.Builder.createTransport`, and SDK
  HTTP / SSE transports passed to `McpToolClient.builder().transport(...)` or
  `ToolClient.addMcpServer(...)` (builder or runtime), track requests per transport.
  Existing callers keep their transport construction and `close()` calls unchanged.
  Closing cancels in-flight GET / POST requests
  immediately, including requests waiting for response headers, then allows up
  to one second for session DELETE cleanup. Other transports using the shared
  client remain usable. Repeated close calls share one cleanup operation.
  Caller-built transports retain their endpoint, headers, mapper and HTTP client.
  Because SDK 0.17.0 exposes no HTTP-client setter, an internal compatibility
  adapter installs the request scope before the runner client connects. It uses
  reflection only on the two known SDK HTTP transport implementations and fails
  during construction if their internal layout becomes incompatible. SDK upgrades
  must run the close regression tests. Clients constructed entirely with the SDK,
  without `McpToolClient` / `ToolClient`, are outside runner's lifecycle management.

## Dependencies
agent-kernel only.
