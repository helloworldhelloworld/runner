# ADR-029：持久任务与检查点恢复

- 日期：2026-09-10
- 状态：Accepted
- 前置：ADR-019、ADR-020、ADR-025、ADR-028

## 决策

持久任务独立于前台 `InterruptibleRun`。任务记录保存目标、主体、状态、尝试次数、检查点、
幂等键和结果；每次尝试使用新的 `runId`。状态迁移为 `QUEUED → RUNNING → SUCCEEDED/FAILED/CANCELLED`
或 `RECOVERY_REQUIRED`，`WAITING_USER` 可作为人工阻塞状态。

第一阶段只承诺明确检查点恢复：输入已持久化、步骤结果已记录、下一步操作已知。外部副作用
使用幂等键；提交后崩溃且无法确认结果时进入 `RECOVERY_REQUIRED`，禁止盲目重做。

文件存储使用临时文件写入后原子替换，重复创建按 idempotencyKey 返回同一 taskId。读取到的
状态快照不可直接覆盖更新，所有更新按版本号 compare-and-set，防止旧 worker 覆盖新检查点。

## 范围

本 ADR 交付 kernel 的任务模型、存储 SPI、文件实现和恢复判定；调度器、Web API、Pi 投递和
真实外部副作用核对属于后续增量。旧前台 Gateway/ChatHandler API 不变。

## 后续实施状态

2026-09-12 已在该决策之上增加 `DurableTaskRunner`、主体隔离的 Web API、持久
`TaskDeliveryStore` 和失败可重试的 `TaskDeliveryDispatcher`。当前仍未完成的是生产触发器、
按真实外部系统回查的持久副作用账本、部署侧投递 consumer 和 Pi 投递；这些边界以
[当前实施状态](../design/runner-completion-plan.md)为准。
