# ADR-005: Share the MCP HTTP client across transports

## Status

Accepted

## Context

MCP transports created per request were also creating JDK `HttpClient`
instances. JDK HTTP clients own selector-manager resources that can remain
strongly reachable after the MCP session is closed. Repeating this across
upstreams and requests grows the number of threads and retained transport
state, producing Full GC pressure.

MCP SDK 0.17.0 exposes `clientBuilder(HttpClient.Builder)` for its HTTP and
SSE transports, but the builder creates the client internally. The custom
WebSocket transport previously created an unreferenced client inside
`connect()`. The existing three-argument `createTransport` method is a public
contract and cannot be changed incompatibly.

## Decision

- Add public `McpHttpClients.shared()` and `McpHttpClients.sharing(client)`.
- Create the shared client once per JVM with `HTTP_1_1` and a 30-second
  connect timeout.
- Make the existing three-argument transport factory use that shared client.
- Add an additive four-argument factory overload for an explicitly supplied
  client. The caller owns that client; MCP transport close never shuts it down.
- Pass the shared client to SDK HTTP/SSE builders through a package-private
  builder adapter whose `build()` returns the supplied client. The adapter
  accepts SDK timeout/configuration calls without replacing the shared client.
- Pass the shared client directly to WebSocket transport. WebSocket connect
  failures use the same cleanup path as explicit close.
- Preserve per-transport construction and dynamic request-header snapshots.
  Do not make long-lived `McpToolClient` reuse part of this fix.

## Consequences

The number of JDK HTTP clients and selector managers is bounded by the shared
client lifecycle, while MCP sessions and WebSocket connections retain their
existing per-transport behavior. `ToolClient.close()` cannot release the
shared client because it may be used by other clients; process shutdown owns
that lifecycle. Custom clients supplied through the additive overload remain
the caller's responsibility.
