# Architecture Review & Optimization TODO

Date: 2026-04-23
Sources: OpenClaw 2026.4 / Claude Code (arXiv 2604.14228) / LangGraph・CrewAI・Anthropic Agent SDK best practices

---

## 1. Context Management — 从 2 层升级到 5 层压缩管线

**现状**: runner 只有 SnipCompactor + MicroCompactor 两层，对标 Claude Code 的 5 层梯度压缩差距明显。

**Claude Code 五层对比**:

| Layer | Claude Code | runner 现状 | Gap |
|-------|-------------|------------|-----|
| L1 Budget Reduction | 按消息粒度限制 tool result 大小 | MicroCompactor(2000) 部分覆盖 | 缺少 per-message budget |
| L2 SnipCompact | 时间维度裁剪旧消息 | SnipCompactor(3) 已有 | OK |
| L3 MicroCompact | 零 API 调用清理陈旧 tool results | MicroCompactor 部分覆盖 | 不区分"陈旧"vs"活跃" |
| L4 Context Collapse | ~90% 利用率时触发，可逆压缩 | **完全缺失** | 需新增 |
| L5 AutoCompact | 语义压缩（fork 子 agent 摘要） | **完全缺失** | 需新增 |

### TODO

- [ ] **P0**: 新增 `BudgetReductionCompactor` — 每条 TOOL 消息设上限（如 4000 chars），超限替换为内容引用 + 摘要
- [ ] **P1**: 新增 `ContextCollapseCompactor` — 当 token 使用率 > 90% 时，将旧轮次折叠为 `[collapsed: N rounds, M tokens]` 占位符，保留原始消息引用以支持可逆展开
- [ ] **P2**: 新增 `AutoCompactor` — 语义压缩层，spawn 一个轻量 agent 对历史对话做结构化摘要（保留 key decisions / tool results / code changes），预留 13K token buffer
- [ ] **P1**: `CompactionChain` 增加利用率感知 — 传入 `tokenCounter` 参数，让各层根据实际 context utilization 决定是否激活（而非固定策略）
- [ ] **P2**: `CostTracker` 增加 per-message breakdown — 当前只追踪总量，无法判断哪条消息最占空间

---

## 2. Tool Execution — 并行流式执行 + 安全分级

**现状**: `ToolExecutor.executeToolCallsReactive()` 已支持并行执行，但没有 Claude Code 的"流式重叠执行"和"并发安全分级"。

**Claude Code 模式**:
- `StreamingToolExecutor` 在 LLM 还在生成时就开始执行已完成的 tool_use block
- 工具按并发安全性分区：safe tools 合并为 parallel batch，unsafe tools 启动 serial batch
- safe batch 的 context 修改排队到 batch 完成后再应用

**OpenClaw 模式**:
- 等待完整 tool call block 后执行（与 runner 一致）
- Docker 沙箱隔离每个 session 的工具执行

### TODO

- [ ] **P1**: 实现 Streaming Tool Execution — 在 `ToolCallingLoop.executeReactiveLoop()` 中，当收到 `TOOL_CALL_START` 事件时立即启动对应工具，不等待 `LLM_COMPLETE`。需要改造 `completeStreamReactive` 的 delta 解析逻辑
- [ ] **P1**: Tool 接口增加 `ConcurrencySafety` 标记 — `enum ConcurrencySafety { SAFE, UNSAFE, READ_ONLY }`，用于分区调度
- [ ] **P2**: 工具执行超时机制 — `Tool` 接口增加 `getTimeoutMs()` 默认值 30s，`ToolExecutor` 强制 timeout 后 cancel
- [ ] **P2**: CLI 工具签名验证 — `CliToolSourceProvider` 扫描时验证 manifest 签名，防止恶意插件注入
- [ ] **P3**: Docker 沙箱可选支持 — 对高风险工具（shell、file write）提供 Docker 隔离执行模式

---

## 3. Permission System — 从 allow/deny list 升级为分级信任模型

**现状**: `ScopedToolRegistry` 仅支持静态的 allow/deny list，无运行时权限升降级。

**Claude Code 七级权限模型**:
plan → default → acceptEdits → auto (ML classifier) → dontAsk → bypassPermissions

**OpenClaw 模式**:
per-agent TOOLS.md 配置 + MCP capabilities 声明

### TODO

- [ ] **P1**: 引入 `PermissionLevel` 枚举 — `READ_ONLY / DEFAULT / AUTO_APPROVE / UNRESTRICTED`，AgentProfile 中配置
- [ ] **P2**: 运行时权限决策 — `ToolExecutor` 调用前检查 `PermissionPolicy.evaluate(tool, args, context)` → ALLOW / DENY / ASK_USER
- [ ] **P2**: 基于 tool 类别的默认策略 — read 类工具默认 AUTO_APPROVE，write/delete 类默认 ASK_USER
- [ ] **P3**: AgentObserver 权限审计事件 — 每次权限决策都发 StreamEvent，可追溯

---

## 4. Agent Identity — 文件驱动的 Agent 配置（对标 SOUL.md / CLAUDE.md）

**现状**: `AgentProfile` 纯 Java Builder 构建，配置分散在 `OrchestratorConfig` 和各 `@Configuration` 类中。

**OpenClaw 最佳实践**: SOUL.md 定义人格、AGENTS.md 定义路由、TOOLS.md 定义能力、USER.md 定义个性化。
**Claude Code 最佳实践**: CLAUDE.md 定义项目级指令，system prompt 拼装。

### TODO

- [ ] **P1**: 支持 `agents/` 目录加载 AgentProfile — 每个 agent 一个 YAML/Markdown 文件，包含 system prompt + tool permissions + model override
- [ ] **P2**: 热加载 — 监听 agents/ 目录变更，自动更新 AgentRegistry（与 DynamicPluginProvider 类似模式）
- [ ] **P3**: 继承机制 — 子 agent 可继承父 agent 的 base prompt + tool set，仅覆盖差异部分

---

## 5. Memory System — Hybrid Retrieval + 分层持久化

**现状**: `kernel-memory` 有 BM25 + Vector 检索，但 `MemoryProvider` 接口过于简单（只暴露 `search()` + `getHistory()`），embedding 能力对上层不透明。

**OpenClaw 最佳实践**: Hybrid retrieval（70% vector + 30% BM25），SQLite + FTS5 + embedding extension。

**Claude Code 最佳实践**: Append-oriented session storage + 5 层压缩。重度依赖 session 内上下文而非跨 session 检索。

### TODO

- [ ] **P1**: `MemoryProvider` 接口增加 `searchHybrid(query, options)` — 暴露权重配置和 score 信息
- [ ] **P1**: Session Transcript 持久化 — 当前 session 结束后将完整对话持久化为 transcript，支持跨 session 回溯
- [ ] **P2**: Durable Memory 自动提取 — AgentObserver 在 onAgentComplete 时自动提取 key decisions / learned facts 写入 durable layer
- [ ] **P2**: Memory Provider 健康检查 + 降级 — 如果 embedding service 不可用，自动降级为纯 BM25 模式

---

## 6. Deprecated Code 清理

**现状**: 三套废弃系统仍在代码中，有活跃引用：

| 废弃包 | 文件数 | 活跃引用位置 |
|--------|--------|------------|
| `kernel.instruction` | 6 files | AgentConfig, ChatService, ClaudeSkillAdapter |
| `kernel.skill` | 3 files | AgentConfig, ClaudeSkillAdapter |
| `kernel.plugin` | 7 files | LLMOptions, ToolExecutor, agent-sdk |

### TODO

- [ ] **P0**: 从 `LLMOptions` 移除 `List<PluginFunction> tools` 字段 — 已被 `toolDefinitions` 替代，但仍被 agent-sdk `DefaultAgent` 引用
- [ ] **P1**: 迁移 `AgentConfig` 中的 `InstructionRegistry` / `SkillLoader` 引用到 `PromptEngine`
- [ ] **P1**: 迁移 `ChatService` 中的 `InstructionRegistry` 引用
- [ ] **P2**: 删除 `kernel.instruction` 包（6 files）
- [ ] **P2**: 删除 `kernel.skill` 包（3 files）
- [ ] **P3**: agent-sdk 的 `Plugin` 抽象迁移到 Tool — 需要 SDK API breaking change，考虑 v2

---

## 7. agent-web 模块拆分

**现状**: `agent-web` 13,749 LOC，混合了 Spring 配置、WebSocket 处理、REST 控制器、Skill 创建、Observer 实现等职责。

### TODO

- [ ] **P1**: 提取 `agent-transport` 模块 — WebSocket handler (Vert.x + Spring)、StreamEventSerializer、ClientToolDispatcher
- [ ] **P2**: 提取 `agent-skillcreator` 模块 — SkillCreatorService + 相关 18 个类（占 agent-web ~30% 代码量）
- [ ] **P3**: agent-web 瘦身为纯 assembly + REST controller

---

## 8. Observability & Instrumentation

**现状**: `Tracer` / `SpanContext` 存在但几乎未在生产代码中使用。CostTracker 只追踪 token 总量。无 metrics 暴露。

**社区最佳实践**: OpenTelemetry 集成、per-tool latency histogram、token budget dashboard。

### TODO

- [ ] **P1**: 集成 OpenTelemetry — Tracer 桥接到 OTel SDK，SpanContext 自动传播
- [ ] **P1**: ToolCallingLoop 每轮迭代记录 metrics — 耗时、token 消耗、工具名称
- [ ] **P2**: LLM Provider 调用 latency histogram — 通过 ResilientLLMProvider 的 HealthInfo 暴露
- [ ] **P2**: `/actuator/agent` endpoint — 暴露 active runs、tool registry 状态、memory usage、LLM health
- [ ] **P3**: 成本仪表盘 — 按 session / agent / tool 维度的 token 消耗统计

---

## 9. Resilience & Error Handling

**现状**: `ResilientLLMProvider` 提供重试 + 健康追踪，但与 `ToolCallingLoop` 未深度集成。

### TODO

- [ ] **P1**: ToolCallingLoop 增加 per-iteration timeout — 单次 LLM 调用超时后优雅退出（而非无限挂起）
- [ ] **P1**: Circuit Breaker 模式 — ResilientLLMProvider 连续 3 次 UNHEALTHY 后自动 open circuit，拒绝新请求并快速返回错误
- [ ] **P2**: LLM Provider Fallback — AgentFactory 支持配置 fallback provider 链（primary → secondary → error message）
- [ ] **P2**: AgentLoop 内存搜索失败降级 — 当 MemoryProvider.search() 异常时，跳过记忆注入继续对话

---

## 10. Multi-Agent 增强

**现状**: Orchestrator 4 层架构已实现，但缺少动态路由、Agent 间通信和工作流编排。

**OpenClaw**: sessions_spawn + per-agent workspace + timeout
**Claude Code**: AgentTool + git worktree 隔离 + 递归委托
**LangGraph**: 有向图 + 条件边 + checkpointing

### TODO

- [ ] **P1**: Subagent Reactive 执行 — 当前 `SubagentRuntime.spawn()` 用同步 `childAgent.run()`，应改用 `runReactive()` 以支持流式中间结果
- [ ] **P1**: WaitSubagentTool timeout 支持 — 防止子 agent 无限等待
- [ ] **P2**: Agent 间消息传递 — 兄弟 agent 间通过共享 channel 交换中间结果（而非只能通过父 agent 中转）
- [ ] **P2**: 工作流编排 — 支持 DAG 定义（A→B→C, A→D, [B,D]→E 类型的编排），参考 LangGraph 的 StateGraph
- [ ] **P3**: Workspace 隔离 — 对有文件操作的 subagent 提供独立工作目录（类似 Claude Code 的 git worktree）

---

## 11. StreamEvent 重构

**现状**: `StreamEvent` 有 15+ static factory methods，4 个构造函数（telescoping），新事件类型需要新 factory method。

### TODO

- [ ] **P2**: StreamEvent 改为 sealed interface + record — 每个 EventType 对应一个 record 子类型，携带该类型特有的字段。消除 nullable 字段的模糊性
```java
sealed interface StreamEvent permits TextDelta, ToolCallStart, LlmComplete, AgentRoute, ... {
    EventType type();
    long timestamp();
}
record TextDelta(String delta, Map<String,Object> metadata) implements StreamEvent { ... }
record ToolCallStart(ToolCall toolCall) implements StreamEvent { ... }
```
- [ ] **P2**: StreamEventSerializer 适配新类型 — switch + pattern matching

---

## 12. Testing Gaps

**现状**: 166 test files，但分布不均（kernel 94, web 34, tools 仅 5, demo 0）。

### TODO

- [ ] **P1**: agent-tools 增加工具级集成测试 — 每个 @ToolFunction 至少一个 happy path + error path 测试
- [ ] **P1**: SubagentRuntime 增加并发压力测试 — 多个 spawn 同时执行 + cascade stop 的竞态场景
- [ ] **P2**: agent-web WebSocket 端到端测试 — WebSocket connect → send message → receive StreamEvent 全链路
- [ ] **P2**: ContextCompactor 链式压力测试 — 模拟 100 轮对话后的压缩效果验证
- [ ] **P3**: agent-demo 最低限度的 smoke test — 确保 `mvn clean compile` 不会因接口变更断裂

---

## 13. API Design 细节优化

### TODO

- [ ] **P1**: `AgentLoop` 暴露 `getMemoryProvider()` — 当前 Orchestrator 被迫通过 `agentFactory.getSharedMemory()` 绕行获取，耦合不合理
- [ ] **P1**: `ToolCallingLoop` 的 telescoping constructors (7个!) 统一收敛到 Builder — 当前已有 Builder 但旧的 public constructors 未标记 deprecated
- [ ] **P2**: `InterruptibleRun.messages` 实际未被使用 — resume 时通过 memoryProvider 加载上下文，messages 字段是冗余的，考虑移除
- [ ] **P2**: `Orchestrator.getMemoryProvider()` 的 hack 注释 "AgentLoop 当前没有暴露 memoryProvider getter" — 修复后删除 hack

---

## Priority Summary

| Priority | Count | 预估工作量 |
|----------|-------|-----------|
| **P0** (Critical) | 2 | 1-2 天 |
| **P1** (High) | 22 | 2-3 周 |
| **P2** (Medium) | 18 | 3-4 周 |
| **P3** (Low) | 7 | 2-3 周 |

### 建议执行顺序

**Phase 1 (本周)**: P0 废弃代码清理 + Context 管线 BudgetReduction
**Phase 2 (下周)**: Tool 并发安全分级 + SubagentRuntime reactive 化 + Observability 基础
**Phase 3 (第三周)**: 5 层压缩管线补全 + agent-web 模块拆分 + Permission 分级
**Phase 4 (第四周)**: Memory 增强 + Agent 文件配置 + StreamEvent sealed 重构

---

## 14. Protocol Interoperability — A2A + MCP 标准对齐

**背景**: MCP (Model Context Protocol) 已捐赠 Linux Foundation (2025.12)，专注 Agent-to-Tool 通信。Google 的 A2A (Agent-to-Agent Protocol, 2025.4) 则解决 Agent 间互操作，已有 150+ 组织支持。两者互补。

**现状**: runner 已有 `agent-mcp` 模块支持 MCP 工具桥接，但未跟进 MCP 2.4 规范的强制沙箱和运行时 instrumentation。无 A2A 支持。

### TODO

- [ ] **P2**: MCP 2.4 规范对齐 — 补全 mandatory tool sandboxing 和 runtime instrumentation
- [ ] **P2**: MCP Server Card 暴露 — 通过 `.well-known` URL 发布 agent 能力描述，支持外部发现
- [ ] **P3**: A2A 协议评估 — 如果需要跨系统 agent 互操作，引入 Agent Card + task lifecycle management
- [ ] **P3**: MCP OAuth 2.0 认证 — 当前 MCP 连接无认证，需补全企业级安全

---

## 15. Agentic Memory — 结构化笔记系统（对标 Anthropic Context Engineering 最佳实践）

**背景**: Anthropic 2025.9 工程博客提出三大 context engineering 技术：压缩(compaction)、结构化笔记(note-taking)、多 agent 隔离。runner 已有压缩和隔离，缺结构化笔记。

**最佳实践**: Agent 在执行过程中定期将关键信息写入持久化文件（如 NOTES.md / TODO），后续 session 自动加载。类似 Claude Code 的 CLAUDE.md 项目指令 + OpenClaw 的 SOUL.md 人格定义。

### TODO

- [ ] **P1**: Agent 笔记系统 — AgentLoop 在 onAgentComplete 时可选触发 note-taking：将 key decisions、tool results、architectural choices 写入 `notes/<agentId>/` 目录
- [ ] **P2**: 笔记自动注入 — PromptEngine.build() 时自动加载相关笔记到 system prompt
- [ ] **P2**: 跨 session 知识迁移 — 新 session 启动时检索前序 session 的笔记摘要

---

## 16. 测试增强 — Record & Replay + LLM 评估分层

**背景**: 社区共识是 AI agent 测试分三层：

| Layer | 目的 | 频率 | 工具 |
|-------|------|------|------|
| L1 确定性逻辑 | 工具路由、参数解析、状态机 | 每次提交 | JUnit + Mock LLM |
| L2 LLM 输出质量 | faithfulness、relevance、hallucination | 定期 | DeepEval、LangSmith |
| L3 端到端行为 | 多轮对话 + 复杂工具链 | Release | 场景评估套件 |

**现状**: runner 的测试主要覆盖 L1（outside-in TDD + CapturingLLMProvider），L2/L3 完全缺失。

### TODO

- [ ] **P1**: Record & Replay fixture — 扩展 `CapturingLLMProvider` 为文件支持的 cassette 模式（录制真实 LLM 响应到 JSON，重放时跳过 LLM 调用）
- [ ] **P2**: LLM 输出质量评估套件 — 对核心场景（agent routing、tool selection、interrupt/resume）定义评估指标
- [ ] **P3**: 端到端场景评估 — 模拟完整用户交互链路（WebSocket → Gateway → Orchestrator → Agent → Tool → Response）

---

## 17. Resilience 增强 — Checkpoint + 长时间运行 Agent

**背景**: Anthropic 工程博客提出 long-running agent 的 two-fold 模式：initializer agent 设置环境 + coding agent 增量推进。LangGraph 提供 checkpoint + time travel。

**现状**: runner 的 InterruptibleRun 支持 interrupt/resume 但无 checkpoint 持久化，crash 后状态丢失。

### TODO

- [ ] **P2**: InterruptibleRun 状态持久化 — 将 accumulatedText + messages + phase 序列化到 storage，crash recovery 时可恢复
- [ ] **P2**: LLM Provider fallback chain — AgentFactory 支持 `List<LLMProvider>` fallback 配置，primary 失败自动降级
- [ ] **P3**: Checkpoint + Time Travel — 参考 LangGraph，在每轮 ToolCallingLoop 迭代后自动 checkpoint，支持回滚到任意迭代点

---

## Updated Priority Summary

| Priority | Count | 预估工作量 |
|----------|-------|-----------|
| **P0** (Critical) | 2 | 1-2 天 |
| **P1** (High) | 27 | 3-4 周 |
| **P2** (Medium) | 26 | 4-5 周 |
| **P3** (Low) | 12 | 3-4 周 |

### 建议执行顺序（更新）

**Phase 1 (本周)**: P0 废弃代码清理 + Context 管线 BudgetReduction + Record&Replay fixture
**Phase 2 (下周)**: Tool 并发安全分级 + SubagentRuntime reactive 化 + Observability 基础 + Agent 笔记系统
**Phase 3 (第三周)**: 5 层压缩管线补全 + agent-web 模块拆分 + Permission 分级 + MCP 2.4 对齐
**Phase 4 (第四周)**: Memory 增强 + Agent 文件配置 + StreamEvent sealed 重构 + Checkpoint 持久化
