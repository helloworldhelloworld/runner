# ADR-008: cloud↔Pi 的 MCP transport 与 NAT 穿透

## Status

Proposed (2026-06-08) — 决策已定，实现待 B4（minion-body 仓 producer 侧 + runner 仓纯配置）。

## Context

[ADR-006](006-minion-embodiment-architecture.md) D1 定了"脑在云"：runner(JVM) 跑云端，
树莓派是瘦身体，离散动作以 **MCP 工具**（`move`/`look`/`set_eyes`…）暴露给 runner（D5）。
ADR-006 只写了"runner 连 Pi 的 MCP server"，**没定连接方式与拨向**——这是本 ADR 补的缺口。

落地时暴露两个硬事实：

1. **现状是 stdio，只能同主机。** minion-body 的 `mcp_server.main()` 用 FastMCP 默认
   **stdio** transport。stdio 的语义是"**客户端把 server 当本地子进程 spawn，走 stdin/stdout**"——
   client 与 server 必须在同一台机。M0/M1 在笔记本上（runner + 身体同机）跑得通，但
   **云端 runner 没法 stdio-spawn 一个远端 Pi 上的进程**。

2. **Pi 在 NAT 后，云端拨不进。** 家用树莓派在路由器 NAT 后（常见还叠 CGNAT），
   公网/云端**主动发起入站连接到达不了** Pi，除非端口转发（脆弱、暴露面大、CGNAT 直接失效）。

而 MCP 的连接模型是 **client 连 server**：工具的**提供方**（Pi——执行器在它身上）必须是
**server**，runner 是 client。所以不能靠"反转角色"绕开 NAT——只能让连通性**由 Pi 外拨**建立。

runner 的 MCP **客户端侧已经具备**远程能力：`application.yml` 的 `app.mcp.servers` 支持
`transport: sse` / `streamable_http` + `url` + 静态/动态 `headers`（`McpHeaderProvider`）。
缺口只在 **producer 侧（minion-body 仍是 stdio）** 与 **网络拨向**。

## Decision

### D1. Transport：prod 用 streamable_http，dev 保留 stdio

minion-body 的 MCP server 支持按运行环境选 transport：

- **生产（Pi↔云）：`streamable-http`** —— Pi 上把 server 起在本地 HTTP 端口
  （FastMCP `run(transport="streamable-http")` + host/port）。runner 侧**纯配置**：
  `transport: streamable_http` + `url: http://<pi-overlay-addr>:<port>/mcp`。
- **开发 / 同机冒烟（M0/M1）：`stdio` 保留** —— 笔记本上 runner 直接 spawn 身体进程，零网络。

`mcp_server.main()` 由参数/环境变量选择 transport（默认 stdio 不变，向后兼容 B1 的用法）。

### D2. NAT 穿透：Pi 外拨的 overlay 网络（默认 Tailscale）

连接模型保持 client(runner)→server(Pi)，但**可达性由 Pi 主动外拨建立**，不开家用路由入站口：

| 方案 | 拨向 | 取舍 |
|---|---|---|
| **Tailscale / WireGuard overlay**（默认）| Pi 外拨协调服务器入网 | Pi 拿稳定 `100.x` 地址，云端 runner 同入 tailnet 即可直连；ACL 限制可达者；无端口转发。03-pi-setup 已把 Tailscale 列为可选——**在方案 A 里从"可选"升为基本必需** |
| 反向隧道（cloudflared / ngrok / `ssh -R`）| Pi 外拨暴露本地端口为公网 URL | 同样 Pi 发起；但多一个公网入口，**必须**配 bearer token 鉴权 |
| 路由端口转发 + DDNS | 云端入站 | 脆弱、暴露面大、CGNAT 即失效——**拒绝** |

runner 经 overlay 地址连 Pi 的 `streamable_http` URL。

### D3. 鉴权：传输外加 token，不裸跑

即便在 tailnet 内，MCP server 也带 **bearer token**（runner 经 `app.mcp.servers.*.headers`
静态注入，或 `McpHeaderProvider` 动态注入）。走公网反向隧道时鉴权**强制**。tailnet ACL
作为第一道边界，token 作第二道。

## Consequences

- **Positive**：runner 侧**零代码改动、纯配置**（客户端远程 transport 能力已在），符合
  ADR-006 / CLAUDE.md "Public API Stability"——不碰 `Tool`/`agent-mcp` 对外契约。
- **Positive**：producer 侧只是给 `main()` 加 transport 选择，**stdio 默认保留**，B1/M0/M1 同机
  路径不受影响；prod 切 `streamable-http` 即可。
- **Positive**：MCP 工具调用是**离散动作**（`move`/`look`），不在音频热路径上（音频走
  Pi⟷Voice Gateway，见 ADR-006 D2）。overlay 上 home↔cloud 往返 ~数十 ms，对离散动作可接受；
  多步工具循环本就留在服务端（D1）。
- **Trade-off**：新增一层 overlay 运维（tailnet/隧道 + token + ACL）。断网/重启后需容忍 server
  消失再重连——MCP 客户端应对 server 不可达**快失败**而非挂起（呼应 CLAUDE.md 集成-seam 规则
  "post-disconnect sends fail fast rather than hang"），重连策略待 B4 实测确认。
- **传输 round-trip 已先验（风险探针，2026-06-11）**：`streamable_http` 生产传输不再只有配置解析断言。
  `StreamableHttpMcpInitializeRoundTripTest` 经**生产路径** `ToolClient.Builder.createTransport`
  让真 SDK（`HttpClientStreamableHttpTransport` + `McpAsyncClient`）对一个 fake HTTP MCP peer
  （`MiniStreamableHttpMcpServer`，JDK `com.sun.net.httpserver`）跑 initialize→tools/list→tools/call
  完整往返，并覆盖 connect 失败快失败、断连后调用快失败不挂起；同时断言 `destructiveHint`/`readOnlyHint`
  经真 tools/list 恢复为 `SYSTEM`/`SAFE`（ADR-007）。这把 B4 的传输不确定性提前打掉，对标 WebSocket 的
  `WebSocketMcpInitializeRoundTripTest`。
- **Deferred（B4 落地）**：minion-body `main()` 的 transport 开关与 host/port；runner
  `application.yml` 的 `minion` server 条目（`streamable_http` + overlay url + token header）；
  对**真 Pi/真 overlay** 的连接生命周期/重连实测（faithful fake 已覆盖协议往返与失败时序，
  真对端的周期性 smoke 仍按集成-seam 规则保留）。
- **Deferred**：物理安全反射仍按 ADR-006 D7 落设备端，与本传输层无关。
