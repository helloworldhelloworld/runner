# ADR-028：统一执行生命周期与取消责任

- 日期：2026-09-10
- 状态：Accepted
- 前置：ADR-015、ADR-020、ADR-026

## 问题

语音 WebSocket、Gateway、Orchestrator、AgentLoop 各自持有一部分订阅和状态，导致用户打断、
新连接接管、连接断开、预算停止和模型自然完成混用同一条取消路径。上层 stream 已结束时，
下游 provider 仍可能观察到正常完成或迟到事件，旧连接也可能影响新 turn。

## 决策

一次模型执行由唯一的生命周期拥有者管理。传输层只订阅输出并报告连接状态；Gateway/Orchestrator
通过生命周期对象取消执行，不再分别维护 provider、AgentLoop 和 WebSocket 的订阅。

执行状态单向迁移：

```text
NEW → RUNNING → COMPLETED | FAILED | CANCELLED
```

取消原因独立记录：`USER_INTERRUPT`、`CONNECTION_REPLACED`、`CLIENT_DISCONNECTED`、
`BUDGET_EXCEEDED`、`SYSTEM_SHUTDOWN`。取消幂等，第一次原因胜出；取消顺序为 token、provider
subscription、输出 subscription，最后才结束传输层。

逻辑会话只能有一个 active run。新 run 原子替换旧 run；所有事件携带 run identity，旧 run 的迟到
事件必须丢弃。接续创建新的执行身份，沿用旧 run 保存的 checkpoint。

正常完成、取消、预算停止和失败保持不同的协议语义：正常完成发送 `stream_end`，主动打断发送
`speech_interrupted`，预算停止发送结构化 `budget_stopped`，断开连接不发送传输事件。

## 兼容性

保留 `Gateway.interrupt`、`InterruptibleRun.execute/interrupt/resume` 及所有 Tool/MCP 公共 API。
新增生命周期类型优先保持 package-private；已有 `StreamEvent` 类型和无 `turnId` 的旧协议形状不变。

## 验收

验收必须同时检查生命周期终态、取消原因、provider 的 cancel/dispose 信号、客户端事件、迟到事件
隔离和记忆写入次数。不得用 sleep、readiness 轮询或把取消改写为正常完成来通过测试。
