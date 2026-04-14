# ADR-004: Multi-Agent Orchestrator Architecture

## Status
Accepted (阶段 0-3 全部完成)

## Context

原有架构是 Gateway → 单一 ChatHandler → 单一 AgentLoop 的直通模型。参照 OpenClaw 2026.4 的五层架构（Input Sources → Integration Gateway → Agent Core → Output/Action → External Systems），runner 缺乏：

1. **多 Agent 路由** — 不同领域的 Agent（邮件分类、代码助手、RAG 检索）无法共存
2. **per-Agent 工具权限** — ToolRegistry 是全局的，所有 Agent 共享全部工具
3. **Subagent spawn** — 无法在 Agent 内部创建隔离的子 Agent 执行子任务
4. **全双工打断接续** — WebSocket 层可 dispose 流，但中间状态丢失，无法从断点继续

## Decision

引入四层 Orchestrator 架构，通过**新增类 + 组合**实现，不修改 agent-kernel 现有核心代码。

### 四层设计

```
Layer 0: AgentProfile          — Agent 身份与配置（原语）
Layer 1: AgentRegistry         — 多 Agent 注册 + ScopedToolRegistry 权限过滤
Layer 2: Orchestrator          — ChatHandler 实现，路由 + InterruptibleRun 打断接续
Layer 3: SubagentRuntime       — spawn / announce / lifecycle / 级联停止
```

### 关键设计决策

1. **Orchestrator 实现 ChatHandler 接口** — 直接替代 GatewayService 插入 Gateway，不改 Gateway 代码
2. **ScopedToolRegistry 继承 ToolRegistry** — 过滤代理模式，不复制工具实例，deny 优先于 allow
3. **Session Key 命名空间化** — `agent:<agentId>:main:<sessionId>` / `agent:<agentId>:subagent:<uuid>`，MemoryProvider 通过 key 自然隔离
4. **事件是一等公民** — 新增 `AGENT_ROUTE`, `AGENT_INTERRUPT`, `AGENT_RESUME`, `SUBAGENT_SPAWN/COMPLETE/ERROR/CANCELLED` 作为 StreamEvent.EventType 枚举值
5. **CancellationToken** — 轻量级取消信号，ToolCallingLoop 每轮迭代检查，实现优雅退出

## Consequences

- **Positive**: 多 Agent 共存，per-Agent 工具隔离，与 OpenClaw 对齐
- **Positive**: 全双工打断接续保留对话上下文，用户体验大幅提升
- **Positive**: agent-kernel 核心代码零修改（AgentLoop, Gateway, ToolCallingLoop 现有逻辑不变）
- **Trade-off**: Orchestrator 增加一层间接性；单 Agent 场景下略有开销
- **Trade-off**: SubagentRuntime 的 announce 机制需要与 MemoryProvider 配合，增加集成复杂度
