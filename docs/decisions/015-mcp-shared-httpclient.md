# ADR-015: MCP transport 默认共享 JDK HttpClient

状态：Accepted（2026-09-12）。关联 [issue #200](https://github.com/helloworldhelloworld/runner/issues/200)、
[ADR-008](008-mcp-transport-cloud-to-pi.md)、[ADR-011](011-mcp-connection-resilience.md)。

## Context

消费侧（DialogManager）按请求为每条 MCP upstream 新建 `McpToolClient` / transport。
JDK `HttpClient` 的 `SelectorManager` 线程是 GC Root：MCP SDK 0.17.0 的
`HttpClientStreamableHttpTransport` / `HttpClientSseClientTransport` 在 `Builder.build()`
里 `clientBuilder.connectTimeout(d).build()` 新建 client，`closeGracefully()` 只关 MCP
session，**不关 `HttpClient`**。本仓库 `WebSocketMcpClientTransport.connect()` 把
`HttpClient` 做成局部变量，`closeGracefully()` 同样关不掉。

Heap dump（Full GC 前）里约 13,001 个 `HttpClientImpl`、数千条 `SelectorManager`，
按请求数 × 5 条 upstream 对得上。`McpToolClient.close()` 持有的是 `McpClientTransport`，
补 close 也到不了 `HttpClient`。

复用长驻 `McpToolClient` 能消掉每轮 initialize / tools/list，但动态 header 要改走
`_meta` / Reactor Context，行为变更大。本轮只修泄漏，不改每请求新建 transport 的语义。

## Decision

### D1. 进程内默认共享 1 个 `HttpClient`

`McpHttpClients.shared()` 在首次使用时构建一个进程级 `HttpClient`：

- `version(HTTP_1_1)` —— 对齐 MCP SDK 默认（SDK #433：HTTP/2 会打坏 streamable HTTP / SSE 握手）
- `connectTimeout(30s)` —— 共享实例创建时定死；SDK 随后调用的 `connectTimeout` 被 sharing builder 忽略

`ToolClient.Builder.createTransport` 的既有 3 参数签名内部改走共享实例（不是 `null`）。
新增 4 参数重载与 `Builder.sharedHttpClient(...)`，供测试隔离 / 特殊 TLS。
`ToolClient.close()` **不**关闭共享实例。

### D2. 用 sharing builder 拦截 SDK 的 `Builder.build()`

SDK 只接受 `HttpClient.Builder`，不接受已构建的 `HttpClient`。公开入口是
`McpHttpClients.sharing(HttpClient)`，返回的 builder：配置方法 no-op，`build()` 返回传入实例。
实现类保持私有，避免把 JDK `HttpClient.Builder` 的全量方法做成公开契约。

HTTP / SSE transport 经 `clientBuilder(McpHttpClients.sharing(client))` 注入。
WS transport 经 `WebSocketMcpClientTransport.Builder.httpClient(client)` 注入；
未注入时默认 `McpHttpClients.shared()`，不再每条连接 `newBuilder().build()`。
共享 / 注入的 client 一律由调用方或进程生命周期管理，transport `closeGracefully()` 不 shutdown。

### D3. 不把“共享 HttpClient”当成 session close 的替代

共享只把 `HttpClientImpl` / `SelectorManager` 从“每请求一个”收成“进程一个”。
`PendingRequest` / MCP session 仍依赖 `McpToolClient.close()`。early-return 不关 client
时，pending 会堆在这一个共享 client 上，照样 OOM。

### D4. runner 装配侧补 close（同批）

`McpConfig.connectMcpServer` / `tryConnectReturning` 在 `initialize` 或 `registerTools`
失败时 close 刚建的 client。`healthTick` 探活失败重连前先 `current.close()`。
这修的是本仓库自己的慢泄漏（ADR-011 重连丢旧 client），不是 DialogManager 的 Full GC 主因。

## Consequences

- **Positive**：走 `createTransport` 的调用方（含 `McpConfig`、升库后的消费侧）默认不再
  per-request 新建 `HttpClient` + `SelectorManager`。公共契约 additive-only。
- **Positive**：每请求仍新建 transport，动态 header snapshot 行为不变。
- **Trade-off**：直接 `HttpClientStreamableHttpTransport.builder(...).build()` 的消费侧
  不会自动修好，必须改走 `createTransport` 或 `McpHttpClients.sharing(...)`。
- **Trade-off**：共享 client 在进程退出前不关；测试若需要隔离，传入自定义 `HttpClient`。
- **Verification**：`SharedHttpClientTransmissionAcceptanceTest` 从生产 `createTransport`
  断言两个 HTTP transport 持有同一 `HttpClient` 实例（payload，不是“方法被调用”）。
