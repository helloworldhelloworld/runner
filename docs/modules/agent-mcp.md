# agent-mcp

MCP protocol bridge. Wraps MCP tools as `Tool` interface.

## Responsibility
- `McpToolWrapper` adapts MCP tools to `Tool` interface
- Progress/logging notifications → `ToolResultChunk` events
- Supports subprocess (STDIO), SSE, Streamable HTTP, and WebSocket transports
- Provides a process-scoped shared JDK `HttpClient` for HTTP/SSE/WS transports

## HTTP client lifecycle

`ToolClient.Builder.createTransport(name, config, headerProvider)` keeps its
existing three-argument contract and uses the process-scoped client from
`McpHttpClients` by default. The four-argument overload accepts an explicitly
provided client for isolated tests or special TLS configuration. HTTP and SSE
transports receive it through the MCP SDK's `clientBuilder` hook; WebSocket
transports receive it directly.

The shared client is created with `HTTP_1_1` because MCP SDK 0.17.0's HTTP
transports use that protocol by default. A transport or `ToolClient.close()`
closes MCP sessions, sockets, and subscriptions, but never shuts down the
shared client. Its lifecycle is process-scoped. A WebSocket transport also
cleans up its connection state when connect fails, so a failed handshake does
not leave its scheduler or pending readiness signal behind.

This deliberately shares only the underlying HTTP client while preserving
per-transport construction and request-header snapshots. Reusing a long-lived
`McpToolClient` would change session and dynamic-header semantics and is a
separate optimization.

## Dependencies
agent-kernel only.
