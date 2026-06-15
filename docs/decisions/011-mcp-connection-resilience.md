# ADR-011: MCP 连接韧性 —— 后台重连 + 自动注册（不依赖重启）

状态：Accepted（2026-06-14）。关联 [ADR-008 MCP 云→Pi 传输](008-mcp-transport-cloud-to-pi.md)、
[ADR-010 全双工](010-full-duplex-cloud-barge-in.md)。

## Context
`McpConfig.mcpToolRegistrar` 是一个**启动期只跑一次**的 `@Bean`：遍历 `app.mcp.servers`，
对每个 server `connectMcpServer + registerTools`。若某 server（如 Pi 的小黄人 MCP `:8765`）
**启动时还没上线**，`initialize()` 抛异常被 `catch` → 仅 `logger.warn("Failed to connect…")`
→ **该 server 永不重试**。后果：runner 必须在 Pi MCP server **之后**启动才注册得到 `set_eyes`
等设备工具；顺序错了就得**重启 runner**——这正是实际踩到的"眼睛不动，只能靠重启"。

## Decision
**每个 server 一个常驻"保活"周期任务**，统一处理"启动没起"和"运行中掉线/重启"，全程不需重启 runner：
- `tryConnectReturning(name, config, registry) → McpToolClient|null`（连接+注册，成功返回 client、
  失败 logged 返回 null）。
- 启动时每个 server 试连一次，并用一个 daemon `ScheduledExecutorService` 每
  `app.mcp.retry-seconds`（默认 15s）跑一次 **`healthTick`**：
  - `current==null`（没连上）→ 连接；
  - `current!=null` → 用 `client.refreshTools(registry)` **探活**（内部 listTools，连接死会抛）→
    成功保持；**抛异常(连接死)→ 重连+重注册**。
- 这样既覆盖"Pi 的 MCP server 在 runner 之后才起"（startup retry），也覆盖"server 中途重启/掉线"
  （mid-run reconnect）——都自动恢复。
- 注册进**共享 `ToolRegistry`**；`Orchestrator` 每请求重新快照 registry，故晚注册/重注册的工具
  下次请求即可见——无需重启。`@PreDestroy` 关 scheduler + 各 client。

`healthTick(current, probe, connect)` 抽成**可单测**静态方法（McpConfigHealthTest：未连→连、
探活成功→保持、探活抛→重连），用 `McpToolClient.refreshTools`/`connectMcpServer`，**不改
agent-mcp 公共契约**。

## Consequences
- ✅ Pi 的 eyes/MCP server **任何时刻上线/重启**，runner 都自动连上并(重)注册 `set_eyes`，
  **不再依赖启动顺序/重启**（startup retry + mid-run reconnect 都覆盖）。
- ✅ 纯 additive，不改 agent-mcp 公共契约（用 refreshTools 探活 + connectMcpServer 重连）。
- ⚠️ 探活/重连按 `retry-seconds`（默认 15s）粒度——掉线到恢复最多滞后一个周期；探活用 listTools，
  对 server 有轻量周期请求。
- 真机端到端验证待 Pi 在线（healthTick 逻辑已单测；本轮 Pi 掉线未能 e2e 验）。仍建议保留 RealPiConnectionIT。
