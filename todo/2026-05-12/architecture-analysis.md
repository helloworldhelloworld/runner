# Architecture Analysis & Optimization TODOs — 2026-05-12

> 基于 Claude Code 泄露源码分析、OpenClaw 2026.4 架构对标、以及 LangGraph/CrewAI/AutoGen/Semantic Kernel 等开源社区最佳实践，对 runner 现有架构进行合理性分析并总结优化项。

---

## 一、参考架构对标总结

### 1.1 Claude Code 架构关键模式

Claude Code 的核心架构围绕以下模式构建：

| 模式 | Claude Code 实现 | runner 现状 | 差距 |
|------|-----------------|------------|------|
| **Permission System** | 三级权限模型（auto/ask/deny），per-tool 粒度，支持 glob pattern | ScopedToolRegistry 只有 allow/deny 列表 | 缺少运行时动态权限询问、glob 匹配 |
| **Hook System** | PreToolUse / PostToolUse / Notification hooks，可执行外部脚本 | AgentObserver 接口 + onPreToolUse/onPostToolUse | 缺少外部脚本执行能力、hook 配置化 |
| **Context Window Management** | 自动摘要压缩（summarize old messages），保留最近 N 轮 + 系统关键信息 | SnipCompactor + MicroCompactor 链式压缩 | 缺少 LLM-based summarization compactor |
| **Streaming Architecture** | SSE + 结构化事件（content_block_delta, tool_use 等） | Flux\<StreamEvent\> 统一事件 | 基本对齐，但缺少 thinking/reasoning 事件 |
| **Tool Result Caching** | 相同参数的工具调用结果缓存（file_read 等幂等工具） | 无缓存 | 高频工具重复调用浪费 token |
| **Conversation Compaction** | 自动检测 context 接近窗口时触发压缩 | CostTracker 检查预算但不触发压缩 | 被动检查 vs 主动触发 |
| **Model Router** | 按任务复杂度动态选择模型（fast mode 用同模型加速） | AgentFactory 只用 defaultProvider | 无动态模型路由 |
| **MCP Integration** | 完整 MCP client，支持 stdio/SSE/streamable-http，per-server 配置 | McpToolClient + McpConfiguration | 基本对齐 |

### 1.2 OpenClaw 2026.4 五层架构对标

```
OpenClaw 五层                     runner 现状                         差距
─────────────────────────────────────────────────────────────────────────
Input Sources                     WebSocket + REST                    缺少 CLI 入口
Integration Gateway               Gateway + ChatHandler               基本对齐
Agent Core (Orchestrator)          Orchestrator 4 层                   基本对齐
Output/Action                     StreamEventSerializer               缺少 structured output 验证
External Systems                  MCP + CLI tools                     缺少 OAuth/API key 管理
```

OpenClaw 的关键设计 runner 已参照实现的：
- 三层记忆系统（Session / Ephemeral / Durable）
- 多 Agent 路由 + per-Agent 工具隔离
- Subagent spawn/wait/list 机制
- Session Key 命名空间隔离

OpenClaw 有但 runner 尚缺的：
- **Agent Handoff 协议** — Agent 间状态传递（不仅是 spawn，还包括 delegate/transfer）
- **Structured Output Validation** — 对 LLM 输出强制 JSON Schema 验证
- **Guardrails Layer** — 独立的安全护栏层（不是混在 Observer 中）
- **Agent Memory Sharing** — 指定 agent 间可共享部分记忆（当前完全隔离）

### 1.3 开源社区最佳实践

| 框架 | 关键模式 | runner 可借鉴点 |
|------|---------|----------------|
| **LangGraph** | 状态机驱动的 agent 流程编排，checkpoint/resume | runner 的 InterruptibleRun 已有类似思想，但缺少显式状态机定义 |
| **CrewAI** | Role-based agent 配置，task 分解 + 任务依赖图 | AgentProfile 已有 role 概念，但缺少任务依赖 DAG |
| **AutoGen/AG2** | 对话式多 agent 协作，GroupChat Manager | Orchestrator 路由但不支持 agent 间直接对话 |
| **Semantic Kernel** | Kernel + Plugin + Planner 三层，强类型 function calling | Tool 接口已对齐 function calling，但缺少 Planner 层 |
| **Spring AI** | 与 Spring 生态深度整合，Advisor chain（前置/后置拦截链） | AgentObserver 类似但不如 Advisor chain 灵活 |

---

## 二、架构优化 TODOs

### P0 — 阻塞性问题（影响正确性或稳定性）

#### TODO-001: ToolCallingLoop 构造器爆炸 — 重构为纯 Builder 模式
- **现状**: `ToolCallingLoop` 有 7 个构造器重载（从 3 参到 7 参），逐级叠加参数
- **问题**: 违反 CLAUDE.md 中 "constructor injection" 原则；新增参数必须再加一层重载；Builder 已存在但构造器未标记 private
- **方案**: 将所有构造器标记为 package-private 或 private，强制通过 Builder 构造
- **文件**: `agent-kernel/.../core/ToolCallingLoop.java:49-91`

#### TODO-002: AgentLoop.runStream() 不走 ToolCallingLoop — 功能退化
- **现状**: `runStream()` 方法直接调用 `llmProvider.completeStream()`，绕过了 `ToolCallingLoop`
- **问题**: 流式路径不支持工具调用循环、不支持 context compaction、不支持 CancellationToken、不支持 observer hooks
- **方案**: 废弃 `runStream()` 或重写为委托 `runReactive()` + 桥接回 callback 模式（参考 `Orchestrator.chatStream()` 的桥接实现）
- **文件**: `agent-kernel/.../agent/AgentLoop.java:158-198`

#### TODO-003: StreamEvent 是 God Object — 字段膨胀
- **现状**: `StreamEvent` 有 12 个字段，每种事件只用其中 2-3 个，其余全是 null
- **问题**: 内存浪费（每个 TEXT_DELTA 事件都携带 10 个 null 字段）；构造器已有 4 层重载嵌套
- **方案**: 采用 sealed interface + record 子类模式（Java 21 已支持）：
  ```java
  sealed interface StreamEvent permits TextDelta, ToolCallStart, LlmComplete, ... {
      EventType type();
  }
  record TextDelta(String delta, Map<String, Object> metadata) implements StreamEvent { ... }
  ```
- **影响范围**: 全链路（需 grep 所有 `StreamEvent.` 调用点）
- **参考**: Claude Code 的 SSE 事件也是每种事件独立 schema

#### TODO-004: Orchestrator.getMemoryProvider() 临时方案需修复
- **现状**: `Orchestrator.getMemoryProvider(agent)` 注释 "AgentLoop 当前没有暴露 memoryProvider getter"，转而调用 `agentFactory.getSharedMemory()`
- **问题**: 当不同 Agent 使用不同 MemoryProvider 时会取到错误的实例；违反 agent-kernel 的 "sessionKey 命名空间隔离" 设计
- **方案**: 在 `AgentLoop` 上暴露 `getMemoryProvider()` getter，或让 `AgentFactory.create()` 返回包含 agent + memory 的 tuple/record
- **文件**: `agent-kernel/.../orchestrator/Orchestrator.java:196-200`

### P1 — 架构性优化（影响扩展性和可维护性）

#### TODO-005: 引入 LLM-based Context Summarization Compactor
- **现状**: SnipCompactor 直接删除旧 TOOL 消息，MicroCompactor 截断长消息
- **问题**: 删除/截断会丢失关键上下文信息，LLM 在长对话中可能"遗忘"早期重要决策
- **方案**: 新增 `SummarizationCompactor`：当消息总 token 超阈值时，用一次快速 LLM 调用将旧消息摘要为 1 条 SYSTEM 消息
- **参考**: Claude Code 的 conversation compaction 机制；LangGraph 的 summarize_messages
- **注意**: 需要一个轻量 LLM 调用（haiku 级别），避免用主模型

#### TODO-006: Tool Result 缓存机制
- **现状**: 每次工具调用都执行完整逻辑，即使参数完全相同
- **问题**: 文件读取、配置查询等幂等工具在多轮对话中重复调用，浪费时间和 token
- **方案**: 在 `ToolExecutor` 层增加 per-session LRU 缓存：
  - Tool 接口新增 `default boolean isCacheable() { return false; }` 标记
  - 缓存 key = toolName + args hash
  - 支持 TTL 过期
- **参考**: Claude Code 对 file_read 等工具的结果缓存

#### TODO-007: Permission System 从静态列表升级为动态策略
- **现状**: `ScopedToolRegistry` 的 allowList/denyList 在 AgentProfile 构建时固定
- **问题**: 无法实现"首次使用某工具时询问用户是否允许"的交互式权限模型
- **方案**: 引入 `ToolPermissionPolicy` 接口：
  ```java
  enum Decision { ALLOW, DENY, ASK_USER }
  Decision check(String toolName, Map<String, Object> args, String agentId);
  ```
  在 `ToolExecutor` 调用前检查，ASK_USER 时通过 StreamEvent 推送权限请求到客户端
- **参考**: Claude Code 的三级权限模型（auto-allow / ask / deny）

#### TODO-008: AgentFactory 缺少 LLMProvider 路由（modelOverride 无效）
- **现状**: `AgentFactory.create()` 永远使用 `defaultProvider`，完全忽略 `profile.getModelOverride()`
- **问题**: AgentProfile 的 `modelOverride` 字段形同虚设；不同复杂度的 agent 无法使用不同模型
- **方案**: 引入 `LLMProviderFactory` SPI 或简单的 provider registry：
  ```java
  LLMProvider provider = providerRegistry.getOrDefault(profile.getModelOverride(), defaultProvider);
  ```
- **文件**: `agent-kernel/.../orchestrator/AgentFactory.java:45-46`

#### TODO-009: Hook System 从 Observer 升级为可配置 Hook Chain
- **现状**: `AgentObserver` 是 Java 接口，需要编写代码实现
- **问题**: 无法在运行时动态添加/移除 hook；无法通过配置文件定义 hook 行为
- **方案**: 
  1. 保留 `AgentObserver` 作为代码级 hook
  2. 新增 `HookConfiguration` 支持声明式 hook（类似 Claude Code 的 hooks 配置）：
     ```json
     { "preToolUse": [{ "match": "shell_*", "action": "script:./hooks/security-check.sh" }] }
     ```
  3. HookExecutor 在 ToolCallingLoop 的 firePreToolUse 中执行
- **参考**: Claude Code 的 PreToolUse/PostToolUse/Notification hook system

#### TODO-010: Thinking/Reasoning 事件支持
- **现状**: StreamEvent.EventType 没有 THINKING 或 REASONING 类型
- **问题**: Claude 3.5+ 和其他模型支持 extended thinking，但 runner 无法传递 thinking tokens 到客户端
- **方案**: 新增 `THINKING_DELTA` 和 `THINKING_COMPLETE` 事件类型；在 `ClaudeProvider` 中解析 thinking content_block
- **文件**: `agent-kernel/.../core/StreamEvent.java`, `agent-kernel/.../llm/claude/ClaudeProvider.java`

#### TODO-011: 统一错误处理与结构化错误事件
- **现状**: 错误处理分散在各层：AgentLoop catch + log、ToolCallingLoop RuntimeException、ResilientLLMProvider wrap
- **问题**: 客户端收到的错误信息不统一；缺少错误分类（可重试/不可重试/用户可操作）
- **方案**: 定义结构化错误模型：
  ```java
  record AgentError(ErrorCategory category, String code, String message, boolean retryable) {}
  enum ErrorCategory { LLM_ERROR, TOOL_ERROR, PERMISSION_DENIED, BUDGET_EXCEEDED, CONTEXT_OVERFLOW }
  ```
  所有层抛出 `AgentException`，Gateway 统一转换为 `StreamEvent.error(AgentError)`
- **参考**: OpenClaw 的 ErrorBoundary 模式；Claude Code 的 structured error responses

### P2 — 性能与效率优化

#### TODO-012: CostTracker 与 ContextCompactor 联动
- **现状**: `CostTracker.isOverBudget()` 为 true 时 ToolCallingLoop 直接 `Flux.empty()` 退出
- **问题**: 超预算是硬截断，没有"接近预算时先压缩上下文、减少 input tokens"的弹性策略
- **方案**: 在 `CostTracker` 中增加 warning threshold（如 80%），触发 `ContextCompactor.compact()` 而非直接退出
- **文件**: `agent-kernel/.../core/ToolCallingLoop.java:252-255`

#### TODO-013: SubagentRuntime 使用 Virtual Threads
- **现状**: `SubagentRuntime` 用 `Executors.newFixedThreadPool(maxConcurrent)` 限制并发
- **问题**: Java 21 已支持 Virtual Threads，固定线程池在高并发场景下成为瓶颈
- **方案**: 改用 `Executors.newVirtualThreadPerTaskExecutor()`，通过 Semaphore 控制并发上限
- **注意**: 需确认 Reactor + Virtual Threads 的兼容性
- **文件**: `agent-kernel/.../orchestrator/SubagentRuntime.java:47-48`

#### TODO-014: ToolExecutor 并行执行优化
- **现状**: `ToolExecutor.executeToolCallsReactive()` 已支持并行，但无并发度控制
- **问题**: 当 LLM 一次返回大量工具调用时（如 10+ 文件读取），可能压垮外部服务
- **方案**: 在 `ToolExecutor` 中增加 `maxParallelism` 配置，使用 `Flux.flatMap(fn, maxConcurrency)` 限流
- **参考**: Claude Code 对并行工具调用的并发度控制

#### TODO-015: StreamEventSerializer 性能优化
- **现状**: 每个 StreamEvent 单独 JSON 序列化
- **问题**: 高频 TEXT_DELTA 事件（可能每秒数十个）的序列化开销
- **方案**: 
  - 对 TEXT_DELTA 使用轻量序列化（直接字符串拼接而非 Jackson）
  - 考虑事件合并（buffering adjacent TEXT_DELTA within 50ms window）
  - 参考: Claude Code 的 SSE 实现对 content_block_delta 的优化

### P3 — 代码清理与技术债

#### TODO-016: 清理 Legacy Plugin 系统
- **现状**: `com.lightweightai.kernel.plugin` 包有 7 个类（Plugin, PluginFunction, JsonSchema, etc.），标记为 Legacy
- **问题**: 新开发者可能误用 Plugin 而非 Tool；增加认知负担
- **方案**: 
  1. 确认没有生产代码依赖 Plugin 包
  2. 标记 `@Deprecated(forRemoval = true)`
  3. 下个大版本删除
- **文件**: `agent-kernel/.../plugin/*`

#### TODO-017: 清理 @Deprecated 包（instruction, skill）
- **现状**: `com.lightweightai.kernel.instruction` 和 `com.lightweightai.kernel.skill` 在 architecture.md 中标记为 @Deprecated
- **问题**: 与新的 `com.lightweightai.kernel.prompt.Skill` 混淆
- **方案**: 同 TODO-016，逐步移除

#### TODO-018: agent-sdk TODO 占位符实现
- **现状**: `AgentBuilder.java:201`, `DefaultAgent.java:76,232` 有 "TODO: 实际实现" 占位符
- **问题**: SDK 是对外接口，不完整的实现会误导使用者
- **方案**: 要么实现完整的 SDK 入口（连通 agent-kernel），要么标注 SDK 为 experimental

#### TODO-019: 补全 ModelCapability 实现
- **现状**: `ClaudeModelCapability:69` 和 `OpenRouterModelCapability:77` 有 TODO 占位
- **问题**: MessageFormatter 和 TokenCounter 未实现，影响 token 估算和上下文管理的准确性
- **方案**: 实现各 provider 的 token counter（至少支持 tiktoken 或近似计算）

### P4 — 可观测性与运维

#### TODO-020: Tracer 对接 OpenTelemetry
- **现状**: 自定义 `Tracer` + `SpanContext` + `SpanExporter` 接口
- **问题**: 与行业标准 OpenTelemetry 不兼容，无法接入 Jaeger/Zipkin/Grafana Tempo
- **方案**: 
  1. 提供 `OtelSpanExporter` 实现，桥接到 OpenTelemetry SDK
  2. 或直接用 OpenTelemetry API 替换自定义 Tracer（保持 agent-kernel 只依赖 API jar，不依赖 SDK）
- **参考**: LangSmith / LangFuse 的 agent tracing 标准

#### TODO-021: 结构化 Metrics 导出
- **现状**: 只有 `ResilientLLMProvider.HealthInfo` 和 `CostTracker` 提供运行时数据
- **问题**: 无法接入 Prometheus/Grafana 监控；缺少以下关键指标：
  - Agent 平均响应时间
  - 工具调用成功/失败率
  - Token 消耗趋势
  - 并发 session 数
- **方案**: 在 `AgentObserver` 的默认实现中采集 Micrometer metrics

#### TODO-022: Health Check Endpoint
- **现状**: `ResilientLLMProvider.getHealthInfo()` 存在但未暴露为 HTTP endpoint
- **问题**: Kubernetes/云平台无法做健康检查和自动重启
- **方案**: 在 agent-web 中暴露 `/actuator/health` 端点，聚合 LLM provider 健康状态、memory 连接状态、MCP server 状态

### P5 — 架构进化方向

#### TODO-023: Agent Handoff Protocol
- **现状**: Orchestrator 只支持路由（选一个 agent 处理整个请求）和 spawn（子 agent 执行子任务）
- **问题**: 不支持 "Agent A 处理到一半发现应该交给 Agent B 继续" 的场景
- **方案**: 引入 `handoff` 工具：
  ```
  LLM → tool_call: handoff(targetAgentId, context_summary)
  Orchestrator → 保存 A 的部分结果 → 路由到 B → B 看到 A 的摘要 + 用户原始输入
  ```
- **参考**: OpenClaw 2026.4 的 Agent Handoff 协议；Anthropic 的 multi-agent handoff pattern

#### TODO-024: Structured Output / JSON Mode
- **现状**: LLMOptions 没有 `responseFormat` 配置
- **问题**: 无法强制 LLM 返回 JSON 格式，工具结果解析依赖字符串匹配
- **方案**: 在 `LLMOptions` 中增加 `responseFormat` 字段（text / json_object / json_schema）；各 Provider 映射到对应的 API 参数
- **参考**: OpenAI 的 response_format；Claude 的 tool_choice + prefill 技巧

#### TODO-025: Agent 状态持久化（Checkpoint/Resume）
- **现状**: `InterruptibleRun` 的状态全部在内存中，服务重启后丢失
- **问题**: 长时间运行的 agent 任务在服务重启后无法恢复
- **方案**: 引入 `RunStateStore` 接口，将 InterruptibleRun 的关键状态（messages, phase, accumulatedText）序列化到持久存储
- **参考**: LangGraph 的 checkpoint persistence；Temporal/Durable Functions 的 workflow 持久化

#### TODO-026: CLI 入口（非 Web）
- **现状**: runner 只有 WebSocket/REST 入口，没有命令行入口
- **问题**: 开发者无法在终端直接与 agent 交互；不利于 CI/CD 集成
- **方案**: 新增 `agent-cli` 模块，基于 picocli 或 Spring Shell，直连 Gateway
- **参考**: Claude Code 本身就是 CLI-first 架构

---

## 三、优先级执行建议

### 立即执行（本周）
1. **TODO-002** — runStream 功能退化，存在用户感知的 bug
2. **TODO-004** — MemoryProvider 获取方式有正确性风险
3. **TODO-001** — 构造器爆炸阻碍新参数添加

### 短期（2 周内）
4. **TODO-008** — modelOverride 无效是 API 约定的断裂
5. **TODO-003** — StreamEvent 重构，为后续 THINKING 事件等奠基
6. **TODO-010** — Thinking 事件支持（Claude 4.x 标配能力）
7. **TODO-016/017** — 清理 legacy 代码减少认知负担

### 中期（1 个月内）
8. **TODO-005** — LLM-based 摘要压缩
9. **TODO-006** — Tool result 缓存
10. **TODO-007** — 动态权限系统
11. **TODO-011** — 结构化错误模型
12. **TODO-013** — Virtual Threads 升级
13. **TODO-020** — OpenTelemetry 对接

### 长期（路线图）
14. **TODO-023** — Agent Handoff
15. **TODO-025** — Checkpoint 持久化
16. **TODO-026** — CLI 入口

---

## 四、对标分析方法论

本次分析采用以下方法：

1. **源码深度阅读** — 逐文件阅读 agent-kernel 所有核心类（AgentLoop, ToolCallingLoop, Orchestrator, InterruptibleRun, SubagentRuntime, StreamEvent, LLMProvider, MemoryProvider, ToolRegistry, ScopedToolRegistry, Gateway, AgentFactory, ResilientLLMProvider, ContextCompactor, CostTracker, Tracer）
2. **架构文档对照** — 逐篇阅读 docs/ 下的 architecture.md, conventions.md, 4 篇 ADR, 模块文档
3. **Claude Code 架构分析** — 基于公开的 Claude Code 源码和设计文档，提取核心架构模式
4. **OpenClaw 对标** — runner 自身文档中明确 "对标 OpenClaw 2026.4"，基于五层架构模型做差距分析
5. **社区最佳实践** — LangGraph (状态机编排), CrewAI (role-based agent), AutoGen (对话式协作), Semantic Kernel (强类型), Spring AI (Advisor chain)
6. **代码 smell 检测** — 构造器重载爆炸、God Object、TODO 占位符、deprecated 未清理、功能路径不一致

下次分析将关注：
- 测试覆盖率与 acceptance test 缺口
- MCP 集成深度（auth、reconnection、resource management）
- soul-* 模块与 kernel 的集成质量
- agent-web 的 WebSocket 双向通信完整性
