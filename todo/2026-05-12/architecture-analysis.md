# Architecture Analysis & Optimization TODOs — 2026-05-12

> 基于 Claude Code 泄露源码分析、OpenClaw 2026.4 架构对标、以及 LangGraph/CrewAI/AutoGen/Semantic Kernel 等开源社区最佳实践，对 runner 现有架构进行合理性分析并总结优化项。

---

## 一、参考架构对标总结

### 1.1 Claude Code 架构关键模式

> 来源：2026.3.31 npm 泄露事件（v2.1.88 包含 59.8MB source map，暴露 ~1900 TypeScript 文件、512,000+ 行代码）。学术分析：[VILA-Lab/Dive-into-Claude-Code](https://github.com/VILA-Lab/Dive-into-Claude-Code) (arXiv:2604.14228)。

| 模式 | Claude Code 实现 | runner 现状 | 差距 |
|------|-----------------|------------|------|
| **Permission System** | 三级权限模型（auto/ask/deny），per-tool 粒度，支持 glob pattern，23 种 bash 安全检查 | ScopedToolRegistry 只有 allow/deny 列表 | 缺少运行时动态权限询问、glob 匹配、bash 命令安全分析 |
| **Hook System** | PreToolUse / PostToolUse / Notification hooks，可执行外部脚本 | AgentObserver 接口 + onPreToolUse/onPostToolUse | 缺少外部脚本执行能力、hook 配置化 |
| **5-Stage Compaction Pipeline** | Budget Reduction → Snip → Microcompact → Context Collapse → Auto-Compact（lazy-degradation，最便宜的先执行） | 只有 SnipCompactor + MicroCompactor 两级 | **缺少 3 个关键阶段**：Budget Reduction（工具结果截断）、Context Collapse（归档旧消息+延迟提交）、Auto-Compact（LLM 语义摘要） |
| **Deferred Tool Loading** | MCP 工具默认只加载名称到 context，调用时才加载完整 schema（节省 context token） | McpToolClient 启动时全量加载 | 大量 MCP 工具时浪费 context 空间 |
| **3 种 Sub-Agent 模型** | Fork（同步克隆）、Teammate（P2P 邮箱通信）、Worktree（git worktree 隔离） | 只有 spawn/wait（类似 Fork） | 缺少 Teammate（对等通信）和 Worktree（文件系统隔离）模型 |
| **3-Stage Error Recovery** | 413 错误级联恢复：Context Collapse drain (cost=0) → Emergency compaction (cost=1 API call) → Conversation reset with summary | `Flux.error()` 直接抛异常 | 无级联恢复策略 |
| **Tool Result Caching** | 相同参数的工具调用结果缓存（file_read 等幂等工具） | 无缓存 | 高频工具重复调用浪费 token |
| **Streaming Architecture** | SSE + 结构化事件，工具调用在流式输出中实时检测 | Flux\<StreamEvent\> 统一事件 | 基本对齐，但缺少 thinking/reasoning 事件 |
| **Model Router** | 按任务复杂度动态选择模型（fast mode 用同模型加速） | AgentFactory 只用 defaultProvider | 无动态模型路由 |
| **System Prompt Cache** | 稳定部分（CLAUDE.md + tool defs）跨 turn 缓存，动态部分每 turn 重建；CLAUDE.md 在所有 compaction 中存活 | 每次 buildMessages() 全量重建 | 无 prompt cache 意识 |
| **Feature Flags** | 44 个 feature flag 控制 20+ 未发布功能 | 无 feature flag 系统 | 无法灰度发布新功能 |
| **MCP Integration** | 完整 MCP client，支持 stdio/SSE/streamable-http，per-server 配置 | McpToolClient + McpConfiguration | 基本对齐 |

#### Claude Code 5-Stage Compaction Pipeline 详解（runner 最大差距）

```
每次 LLM 调用前执行，按成本从低到高逐级触发（lazy-degradation）：

Stage 1: Budget Reduction (always active)
  └─ 工具结果超过 size limit 时截断（类似 runner 的 MicroCompactor）

Stage 2: Snip (HISTORY_SNIP)
  └─ 删除特定消息范围，处理时间深度（类似 runner 的 SnipCompactor）

Stage 3: Microcompact
  └─ 对 cache 开销做出反应，提前卸载大体积工具结果（runner 的 MicroCompactor 部分覆盖）

Stage 4: Context Collapse ← runner 完全缺失
  └─ 归档旧消息，查询时投影为折叠视图
  └─ 预选消息块作为缩减候选；413 错误时立即提交候选（零成本恢复）

Stage 5: Auto-Compact ← runner 完全缺失
  └─ 语义压缩（最后手段）：用 LLM 调用摘要旧对话历史
  └─ 最昂贵但保留最多语义

预处理步骤：
  - 图片/文档替换为 [image]/[document] 占位符
  - tool_use + tool_result 配对作为原子单元处理
  - thinking block 在摘要前剥离

阈值：auto-compact buffer = 13,000 tokens, warning = 20,000 tokens
```

### 1.2 OpenClaw 2026.4 架构对标

> OpenClaw（[github.com/openclaw/openclaw](https://github.com/openclaw/openclaw)）：371,000+ stars，TypeScript，5,400+ skills，162 production-ready agent templates。

#### Hub-and-Spoke 四层架构

```
OpenClaw 四层               runner 对应                    差距
─────────────────────────────────────────────────────────────────────
Layer 1: Model              LLMProvider + SPI              基本对齐（OpenClaw 支持 23+ providers）
Layer 2: Memory             MemoryProvider 三层            基本对齐（Session/Ephemeral/Durable）
Layer 3: Tools              Tool + ToolRegistry + MCP      基本对齐（OpenClaw 用 ClawHub + MCPorter）
Layer 4: Orchestrator       Orchestrator + Gateway         基本对齐
```

#### OpenClaw 独有设计模式

| 模式 | OpenClaw 实现 | runner 现状 | 差距 |
|------|-------------|------------|------|
| **Workspace-First (SOUL.md)** | 配置文件是 single source of truth：SOUL.md（人格）、TOOLS.md（能力）、AGENTS.md（多 agent）、HEARTBEAT.md（调度） | AgentProfile 是 Java Builder 对象 | 缺少声明式 agent 配置（配置即代码） |
| **Heartbeat Loop** | cron 定时唤醒 agent，读 HEARTBEAT.md 决定是否需要主动通知用户 | 无自主唤醒机制 | 缺少 proactive agent 能力 |
| **Non-deterministic Orchestration** | agent 在运行时自主决定调哪些工具、是否 spawn 子 agent、何时压缩记忆 | Orchestrator 按 AgentRouter 固定路由 | runner 路由策略较静态 |
| **Memory Search as Tool** | `memory_search` / `memory_get` 是 agent 可调用的工具 | `MemoryProvider.search()` 在 AgentLoop 内部隐式调用 | 记忆检索对 LLM 不透明 |
| **Multi-Channel** | WhatsApp/Telegram/Slack/Discord/iMessage 等 15+ 渠道 | WebSocket + REST | 单渠道 |
| **fs-safe** | `@openclaw/fs-safe` 根边界文件访问、原子写、归档解压、秘密文件助手 | 无文件系统安全层 | 工具可能访问任意路径 |

runner 已对齐 OpenClaw 的：
- 三层记忆系统（Session / Ephemeral / Durable）
- 多 Agent 路由 + per-Agent 工具隔离
- Subagent spawn/wait/list 机制
- Session Key 命名空间隔离
- ReAct Agent Loop 模式（LLM → tool detection → tool execution → re-prompt）

runner 尚缺的：
- **Agent Handoff 协议** — Agent 间状态传递（不仅是 spawn，还包括 delegate/transfer）
- **Structured Output Validation** — 对 LLM 输出强制 JSON Schema 验证
- **Guardrails Layer** — 独立的安全护栏层（不是混在 Observer 中）
- **Agent Memory Sharing** — 指定 agent 间可共享部分记忆（当前完全隔离）
- **Workspace-First 声明式配置** — SOUL.md / AGENTS.md 式的配置即代码
- **Heartbeat 自主唤醒** — 定时 agent 自主决策是否通知用户
- **记忆检索作为工具** — 让 LLM 自主决定何时搜索记忆

### 1.3 开源社区最佳实践

| 框架 | 关键模式 | runner 可借鉴点 |
|------|---------|----------------|
| **LangGraph** | StateGraph + Reducer 驱动的不可变状态；每次状态转换 checkpoint 持久化；支持 time-travel debug 和 human-in-the-loop 审批 | runner 的 InterruptibleRun 有类似思想，但缺少显式状态机 + checkpoint 持久化 |
| **CrewAI** | Role-based crews + 两种模式（Crews 自主团队 / Flows 事件驱动管道）；Hierarchical 模式自动生成 manager agent；共享 short/long-term/entity/contextual memory | AgentProfile 有 role 概念，但缺少 Flows 管道模式和跨 agent 共享记忆 |
| **AutoGen/AG2** | v0.4 重写为事件驱动核心 + 图编排；GroupChat 多 agent 共享线程；正在从 Manager Agent 决定发言人 → 显式图工作流迁移 | Orchestrator 路由但不支持 agent 间直接对话/共享线程 |
| **Semantic Kernel** | AutoGen + SK 合并为 Microsoft Agent Framework（2025.10 preview）；5 种编排模式（Concurrent/Sequential/Handoff/GroupChat/Magentic）；YAML/JSON 声明式 agent 定义 | runner 缺少声明式 agent 定义和多种编排模式 |
| **Spring AI** | Advisor chain（前置/后置拦截链）；MCP Java SDK 整合；SSE 已废弃 → Streamable HTTP 是新标准 | AgentObserver 不如 Advisor chain 灵活；MCP transport 需跟进 Streamable HTTP |
| **Embabel** (Rod Johnson) | Spring Boot + Kotlin；**GOAP（Goal-Oriented Action Planning）** 借鉴自游戏 AI；A* 算法规划器（非 LLM）决定最优行动序列；OODA 循环执行 | runner 的编排完全依赖 LLM 决策，缺少算法辅助的行动规划 |
| **Koog** (JetBrains) | Kotlin 协程 + 图策略模型；多平台（JVM/Android/iOS/WASM）；checkpoint 恢复从最近检查点重启而非从头 | runner 缺少 checkpoint 中间恢复能力 |

#### 跨框架共识模式

1. **"Cheapest First" 原则** — 所有成熟框架都把成本意识作为生存特征：按需加载工具定义、用免费方法先压缩 context、按任务复杂度分层模型、在 prompt 边界积极缓存
2. **Model Tiering** — 便宜/快速模型（Haiku, GPT-mini）做分诊/路由，高能力模型（Sonnet, GPT-4）做推理，减少 40-60% 成本
3. **MCP 作为通用标准** — SSE transport 已废弃（MCP spec 2025-03-26），Streamable HTTP 是新标准；43% 公开 MCP server 存在命令注入漏洞
4. **Checkpoint 持久化** — LangGraph（每次转换）、CrewAI（SQLite 任务边界）、Koog（图节点）都有 checkpoint，runner 完全在内存中
5. **State Management 分化** — 从不可变 reducer（LangGraph）到文件系统（OpenClaw）到事件溯源（AG2），没有唯一正确答案，但"无持久化"是明确的反模式

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

#### TODO-005: 对齐 Claude Code 5-Stage Compaction Pipeline
- **现状**: runner 只有 SnipCompactor + MicroCompactor 两级（对应 Claude Code 的 Stage 2 + 部分 Stage 1/3）
- **问题**: 缺少 3 个关键阶段，导致长对话质量退化：
  - 无 Context Collapse（Stage 4）：不能归档旧消息为折叠视图，413 错误时无零成本恢复路径
  - 无 Auto-Compact（Stage 5）：不能用 LLM 语义摘要旧对话，只有硬删除和截断
  - 无预处理：不剥离图片/文档/thinking block，浪费 compaction 前的空间
- **方案**: 按 lazy-degradation 原则扩展 CompactionChain：
  1. `BudgetReductionCompactor` — 工具结果超限截断（Stage 1，增强现有 MicroCompactor）
  2. 保留现有 `SnipCompactor` (Stage 2) + `MicroCompactor` (Stage 3)
  3. 新增 `ContextCollapseCompactor` — 归档旧消息，延迟提交候选块；413 恢复时零成本提交
  4. 新增 `AutoCompactSummarizer` — 用轻量 LLM（haiku 级别）摘要旧对话为 1 条 SYSTEM 消息
  5. 新增 preprocessing 步骤：图片→占位符、tool_use+tool_result 原子配对、thinking block 剥离
- **阈值参考**: auto-compact buffer = 13,000 tokens, warning = 20,000 tokens
- **参考**: Claude Code 5-stage pipeline (arXiv:2604.14228); LangGraph summarize_messages

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

#### TODO-027: Deferred MCP Tool Loading（按需加载工具 schema）
- **现状**: `McpToolClient` 启动时通过 `asyncClient.listTools().block()` 全量加载所有 MCP 工具定义
- **问题**: 大量 MCP server（如 GitHub 50+ tools）的完整 schema 占用 context window；LLM 每次调用都携带全部工具定义
- **方案**: 仿 Claude Code 的 deferred tool loading：
  1. 初始只加载工具名称 + 简短描述到 LLM context
  2. LLM 决定调用某工具时，才从 MCP server 获取完整 schema 并执行
  3. 在 `ToolRegistry` 中增加 `DeferredTool` 代理类
- **参考**: Claude Code 泄露源码中 MCP 工具默认 deferred，只有名称消耗 context

#### TODO-028: 413 Error Cascade Recovery
- **现状**: 当 LLM 返回 413（prompt too long）错误时，`ResilientLLMProvider` 按 `isRetryable()` 逻辑判断——400 被标记为不可重试，直接失败
- **问题**: 413 是可恢复的（压缩 context 后重试），但当前直接放弃
- **方案**: 仿 Claude Code 3-stage cascade：
  1. Stage 1: 立即触发 Context Collapse drain（提交预选的缩减候选块，成本=0）
  2. Stage 2: Emergency compaction（1 次 API 调用摘要历史）
  3. Stage 3: Conversation reset with summary（最后手段）
  在 ToolCallingLoop 的 LLM 调用外层包裹 recovery handler
- **参考**: Claude Code 413 三阶段级联恢复

#### TODO-029: Teammate Sub-Agent 模型（P2P 邮箱通信）
- **现状**: SubagentRuntime 只支持 spawn/wait 模式（类似 Claude Code 的 Fork 模型）
- **问题**: 子 agent 执行完返回结果给父 agent 后就销毁，不支持多 agent 持续协作
- **方案**: 引入 Teammate 模型：
  1. Agent 间通过 Mailbox（per-agent 消息队列）P2P 通信
  2. 一个 agent 作为 team lead 分解任务，其他 agent 是 teammate
  3. Teammate 是独立 AgentLoop 实例，有自己的 context
  4. 任务列表带依赖跟踪，完成依赖后自动解锁
- **参考**: Claude Code Teammate 模型（Agent Teams）

#### TODO-030: Worktree Sub-Agent 隔离（git worktree）
- **现状**: 所有 agent 共享同一个工作目录
- **问题**: 并行代码修改的 agent 可能互相覆盖文件
- **方案**: 为代码编辑类 subagent 创建独立 git worktree：
  - `SubagentRuntime.spawn()` 检查 profile 是否需要 worktree 隔离
  - 自动 `git worktree add` 创建隔离工作目录
  - 完成后自动清理（无变更）或返回 branch 信息（有变更）
- **参考**: Claude Code Worktree 子 agent 模型

#### TODO-031: 声明式 Agent 配置（Workspace-First）
- **现状**: AgentProfile 通过 Java Builder API 构建，agent 定义在代码中
- **问题**: 添加/修改 agent 需要改代码+重新编译+重启
- **方案**: 支持 YAML/Markdown 文件声明 agent（类似 OpenClaw 的 SOUL.md / AGENTS.md）：
  ```yaml
  # agents/code-reviewer.yaml
  agentId: code-reviewer
  systemPrompt: |
    你是一个代码审查专家...
  modelOverride: claude-sonnet-4-6
  toolAllowList: [file_read, grep, git_diff]
  toolDenyList: [file_write, shell_exec]
  maxSpawnDepth: 1
  ```
  `AgentRegistry` 启动时扫描配置目录，热更新
- **参考**: OpenClaw SOUL.md/AGENTS.md；Semantic Kernel YAML agent definitions

#### TODO-032: Memory Search 作为 Agent 可调用工具
- **现状**: `MemoryProvider.search()` 在 `AgentLoop.run()` 中被隐式调用，LLM 无法控制何时搜索
- **问题**: LLM 不知道自己有记忆搜索能力；无法主动搜索特定话题的历史
- **方案**: 将 memory_search 和 memory_write 注册为 Tool：
  ```java
  class MemorySearchTool implements Tool {
      ToolResult execute(Map<String, Object> args) {
          return memoryProvider.search(args.get("query").toString());
      }
  }
  ```
  让 LLM 自主决定何时检索记忆（OpenClaw 模式），而非每次都自动检索
- **参考**: OpenClaw 的 memory_search / memory_get 工具

#### TODO-033: Prompt Cache 感知的 System Prompt 构建
- **现状**: `AgentLoop.buildMessages()` 每次全量重建 system prompt（base + memory context）
- **问题**: Claude API 支持 prompt caching（stable prefix 跨 turn 复用），但 runner 未利用
- **方案**: 将 system prompt 分为稳定部分（base prompt + tool definitions）和动态部分（memory context + active skills），通过 `LLMOptions` 传递 cache boundary 标记；ClaudeProvider 在请求体中设置 `cache_control` 标记
- **参考**: Claude Code 的 stable/dynamic system prompt 分离 + prompt cache

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
8. **TODO-028** — 413 Error Cascade Recovery（当前直接失败，应可恢复）
9. **TODO-033** — Prompt Cache 感知（直接节省 API 费用）

### 中期（1 个月内）
10. **TODO-005** — 5-Stage Compaction Pipeline（runner 最大架构差距）
11. **TODO-006** — Tool result 缓存
12. **TODO-027** — Deferred MCP Tool Loading（大量工具时的 context 节省）
13. **TODO-007** — 动态权限系统
14. **TODO-011** — 结构化错误模型
15. **TODO-013** — Virtual Threads 升级
16. **TODO-020** — OpenTelemetry 对接
17. **TODO-032** — Memory Search 作为工具（OpenClaw 模式）

### 长期（路线图）
18. **TODO-023** — Agent Handoff
19. **TODO-029** — Teammate Sub-Agent 模型（P2P 协作）
20. **TODO-030** — Worktree Sub-Agent 隔离
21. **TODO-031** — 声明式 Agent 配置（YAML/Markdown）
22. **TODO-025** — Checkpoint 持久化
23. **TODO-026** — CLI 入口

---

## 四、对标分析方法论

本次分析采用以下方法：

1. **源码深度阅读** — 逐文件阅读 agent-kernel 所有核心类（AgentLoop, ToolCallingLoop, Orchestrator, InterruptibleRun, SubagentRuntime, StreamEvent, LLMProvider, MemoryProvider, ToolRegistry, ScopedToolRegistry, Gateway, AgentFactory, ResilientLLMProvider, ContextCompactor, CostTracker, Tracer）
2. **架构文档对照** — 逐篇阅读 docs/ 下的 architecture.md, conventions.md, 4 篇 ADR, 模块文档
3. **Claude Code 泄露源码分析** — 基于 npm v2.1.88 source map 泄露（512,000+ 行 TypeScript）、arXiv:2604.14228 学术论文、以及多篇逆向分析文章
4. **OpenClaw 对标** — runner 文档明确 "对标 OpenClaw 2026.4"，基于 OpenClaw GitHub 文档 + 3 篇架构分析（Ken Huang Design Patterns 系列）
5. **社区最佳实践** — LangGraph (StateGraph + checkpoint), CrewAI (Crews + Flows), AutoGen/AG2 (事件驱动 + 图编排), Semantic Kernel (Microsoft Agent Framework), Spring AI (Advisor + MCP), Embabel (GOAP), Koog (Kotlin 协程图)
6. **代码 smell 检测** — 构造器重载爆炸、God Object、TODO 占位符、deprecated 未清理、功能路径不一致

---

## 五、参考来源

### Claude Code
- [VILA-Lab/Dive-into-Claude-Code (arXiv:2604.14228)](https://github.com/VILA-Lab/Dive-into-Claude-Code)
- [Claude Code Source Leak Analysis (Layer5)](https://layer5.io/blog/engineering/the-claude-code-source-leak-512000-lines-a-missing-npmignore-and-the-fastest-growing-repo-in-github-history/)
- [Claude Code Architecture Analysis (bits-bytes-nn)](https://bits-bytes-nn.github.io/insights/agentic-ai/2026/03/31/claude-code-architecture-analysis.html)
- [Claude Code Compaction Explained (okhlopkov.com)](https://okhlopkov.com/claude-code-compaction-explained/)
- [Claude Code Internals (Taeho Kim)](https://taeho.io/en/reading/claude-code-internal-architecture-analysis_20264)
- [Claude Code Context Management (Ken Huang)](https://kenhuangus.substack.com/p/claude-code-pattern-6-context-management)

### OpenClaw
- [OpenClaw GitHub (371K+ stars)](https://github.com/openclaw/openclaw)
- [OpenClaw Design Patterns Part 1-3 (Ken Huang)](https://kenhuangus.substack.com/p/openclaw-design-patterns-part-1-of)
- [OpenClaw Agent Loop / Memory / Tool Docs](https://docs.openclaw.ai/concepts/)
- [OpenClaw Architecture Explained (ppaolo)](https://ppaolo.substack.com/p/openclaw-system-architecture-overview)

### 开源框架
- [LangGraph v1.0 Architecture Guide](https://latenode.com/blog/ai-frameworks-technical-infrastructure/langgraph-multi-agent-orchestration/)
- [CrewAI Docs](https://docs.crewai.com/)
- [AG2 GroupChat Docs](https://docs.ag2.ai/latest/docs/user-guide/advanced-concepts/groupchat/)
- [Microsoft Agent Framework (Semantic Kernel)](https://learn.microsoft.com/en-us/semantic-kernel/frameworks/agent/)
- [Embabel (Rod Johnson)](https://github.com/embabel/embabel-agent)
- [Koog (JetBrains)](https://github.com/JetBrains/koog)
- [AI Agent Design Patterns (Microsoft Azure)](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/ai-agent-design-patterns)
- [Context Engineering for AI Agents (Anthropic)](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)

---

## 六、下次分析方向

- 测试覆盖率与 acceptance test 缺口
- MCP 集成深度（auth、reconnection、resource management、MCP spec Streamable HTTP 迁移）
- soul-* 模块与 kernel 的集成质量
- agent-web 的 WebSocket 双向通信完整性（client tool dispatch、session binding）
- 安全层完整性（bash 命令安全检查、文件系统边界、MCP tool poisoning 防护）
- Token 估算准确性（ModelCapability 实现 vs 实际 token 消耗对比）
