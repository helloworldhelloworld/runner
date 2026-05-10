# Architecture Optimization TODO — 2026-05-10

> 基于 OpenClaw 最新架构、Claude Code 泄露源码分析、以及 LangGraph/AutoGen/CrewAI 等开源社区最佳实践，对 runner 现有架构的合理性分析与待优化点总结。

---

## 一、Agent Loop & Tool Calling 层

### TODO-001: ToolCallingLoop 缺少 token 消耗的实际记录

**现状**: `CostTracker` 已实现 `record(inputTokens, outputTokens)` 和 `isOverBudget()` 检查，但 `ToolCallingLoop.executeReactiveLoop()` 只在循环开头检查 `isOverBudget()`，从未调用 `costTracker.record()`。LLMResponse 中的 token 使用量没有被传递到 CostTracker。

**参考**: Claude Code 内部的 cost tracking 是在每次 LLM 调用完成后立即从 response usage 字段提取并记录，形成闭环。

**优化方向**: 在 `ToolCallingLoop` 的 `LLM_COMPLETE` 事件处理中，从 `LLMResponse.getUsage()` 提取 token 数并调用 `costTracker.record()`。需要先确保 `LLMResponse` 有 usage 字段（当前可能缺失）。

**优先级**: P1 — 没有 record 的 isOverBudget 永远是 false，预算机制形同虚设。

---

### TODO-002: ToolCallingLoop 构造器参数膨胀（Telescoping Constructor Anti-pattern）

**现状**: 7 个构造器层层叠加（3参数→4→5→6→7），虽已有 Builder，但旧构造器仍暴露且被内部使用（如 `AgentLoop.run()` 中直接 `new ToolCallingLoop(provider, toolExecutor, maxToolIterations)` 绕过 Builder）。

**参考**: OpenClaw 和 Claude Code 均采用纯 Builder + private constructor 模式，杜绝遗漏新增参数的可能。

**优化方向**: 
1. 将所有非 Builder 构造器标记 `@Deprecated`，最终删除
2. `AgentLoop.run()` 同步路径也改用 Builder，注入 `cancellationToken` 和 `costTracker`
3. 统一 sync/async/reactive 三条路径的参数传递

**优先级**: P2 — 已导致过 `cancellationToken` 同步路径不生效的 bug。

---

### TODO-003: AgentLoop.run() 同步路径缺少 CancellationToken 和 CostTracker 传递

**现状**: `AgentLoop.run()` (同步) 创建 ToolCallingLoop 时没有传入 `cancellationToken` 和 `costTracker`，只有 `runReactive()` 路径传了 cancellationToken。同步路径无法被打断、无法做预算控制。

**参考**: Claude Code 的 agent loop 无论 sync/async 都统一走同一个执行链，不存在分叉。

**优化方向**: 同步路径也接受可选的 `CancellationToken`，或者让 `run()` 内部桥接到 `runReactive().blockLast()`，统一执行链。

**优先级**: P1 — sync 路径不可打断是功能缺陷。

---

### TODO-004: runStream() 流式路径不支持 Tool Calling

**现状**: `AgentLoop.runStream()` 使用 callback-based `completeStream()`，直接拿 `LLMResponse`，没有走 `ToolCallingLoop`。如果 LLM 返回 tool_use，流式路径会丢失工具调用。

**参考**: `runReactive()` 已正确委托给 `ToolCallingLoop.executeWithToolsReactive()`，但 `runStream()` 是独立实现。

**优化方向**: 将 `runStream()` 重写为 `runReactive()` 的 callback 桥接（类似 `Orchestrator.chatStream()` 的做法），而非维护两套独立逻辑。或标记 `@Deprecated` 引导调用方迁移到 `runReactive()`。

**优先级**: P1 — 功能缺陷，tools 在流式路径失效。

---

## 二、Context Management（上下文管理）

### TODO-005: ContextCompactor 缺少 token-aware 压缩策略

**现状**: `SnipCompactor` 按轮次删除旧 TOOL 消息，`MicroCompactor` 按字符数截断。两者都不知道实际 token 数，可能压缩不够（仍超窗口）或过度压缩。

**参考**: 
- Claude Code 的 context compaction 使用实际 token count 驱动：先计算当前 context token 数，与模型 max_context_tokens 比较，差额决定压缩强度。
- LangGraph 的 `trim_messages` 支持 `token_counter` 参数，精确到 token 级别。

**优化方向**: 
1. `ContextCompactor` 接口增加 `compact(messages, currentTokenCount, maxTokens)` 重载
2. 引入 `TokenCounter` 策略接口（不同 provider 的 tokenizer 不同）
3. 实现 `TokenAwareCompactor`：按 token 预算裁剪，优先删除旧 TOOL > 旧 ASSISTANT > 截断长消息

**优先级**: P2 — 当前简单策略对短对话够用，长对话/多轮工具调用场景会出问题。

---

### TODO-006: 缺少 Summarization Compaction 策略

**现状**: 压缩只有"删除"和"截断"两种，没有"摘要"策略。大量 TOOL 结果被直接删除，LLM 丢失了工具执行的上下文。

**参考**: 
- Claude Code 在压缩前会将旧消息用一个轻量级 LLM 调用（如 Haiku）做摘要，摘要替换原始消息。
- AutoGen 的 `TransformMessages` 支持 `MessageSummarize` transform。

**优化方向**: 实现 `SummarizationCompactor`：当 context 超阈值时，将早期的多轮对话用一次 LLM 调用压缩为摘要消息，替换原始消息。需要注意：摘要本身也有 token 成本，需要在 CostTracker 中计入。

**优先级**: P3 — 增强型能力，当前 Snip+Micro 策略是 MVP 可接受的。

---

## 三、Multi-Agent Orchestration（多 Agent 编排）

### TODO-007: SubagentRuntime 使用 FixedThreadPool，缺乏弹性伸缩

**现状**: `SubagentRuntime` 用 `Executors.newFixedThreadPool(maxConcurrent)` 创建固定大小线程池。子 agent 跑 `AgentLoop.run()` (同步阻塞)，一个线程被一个 agent 独占直到完成。

**参考**: 
- Claude Code 的 subagent 执行使用 Project Loom 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），线程成本几乎为零。
- OpenClaw 2026.4 的 subagent 执行是全 reactive（`runReactive()` + `subscribeOn(boundedElastic)`)，不独占线程。

**优化方向**: 
1. 短期：改用 `Executors.newVirtualThreadPerTaskExecutor()`（Java 21 已支持），消除线程池大小限制
2. 中期：subagent 改用 `AgentLoop.runReactive()` 执行，与 boundedElastic 调度器配合
3. 移除硬编码的 `maxConcurrent` 线程池参数，改为 Semaphore 控制并发数

**优先级**: P2 — 当前固定线程池在 agent 数量大时成为瓶颈。

---

### TODO-008: Subagent 之间缺少通信/协作机制

**现状**: 多个 subagent 只能通过 parent 的 wait → 收集结果来协作。没有 agent-to-agent 的直接消息传递或共享状态。

**参考**: 
- AutoGen 的 GroupChat 允许 agent 之间直接对话
- CrewAI 的 Task 有 `context` 字段，允许 task 之间传递上下文
- Claude Code 的多 agent 通过 shared tool results 和 context injection 实现隐式协作

**优化方向**: 
1. 引入 `SharedContext` / `Blackboard` 模式：subagent 可以写入/读取共享状态
2. 增加 `send_message_to_agent` 工具：允许 agent 间发消息（需要深度控制防止死锁）
3. 最小化方案：在 SpawnRequest 中支持 `context` 参数，parent 可以注入已有 subagent 的结果作为新 subagent 的上下文

**优先级**: P3 — 当前 spawn+wait 模式覆盖大多数场景，协作模式是增强功能。

---

### TODO-009: Orchestrator 缺少 Agent 选择策略的可观测性

**现状**: `AgentRouter.route()` 返回 `agentId` 字符串，但没有暴露路由决策的原因。`MetadataAgentRouter` 从 request metadata 读取 agentId，如果没有就返回 default，没有基于意图/能力的智能路由。

**参考**: 
- Claude Code 的 agent routing 基于 system prompt 中的 agent description 做 LLM-based routing
- CrewAI 的 router 支持 condition-based 和 LLM-based 两种模式
- OpenClaw 使用 intent classification + agent capability matching

**优化方向**: 
1. `AgentRouter` 接口返回 `RouteDecision`（含 agentId + reason + confidence）
2. 实现 `IntentBasedRouter`：用 LLM 分析用户意图，匹配 agent 的 capability 描述
3. 在 AGENT_ROUTE 事件中携带路由决策的原因

**优先级**: P3 — 当前 metadata-based 路由足够，智能路由是多 agent 成熟后的需求。

---

## 四、LLM Provider 层

### TODO-010: LLMProvider 接口缺少 Structured Output / JSON Mode 支持

**现状**: `LLMOptions` 没有 `responseFormat` 字段。当需要 LLM 返回结构化 JSON（如工具参数解析、agent routing 决策）时，只能在 system prompt 中要求，无法利用 Claude/OpenAI 的原生 JSON mode。

**参考**: 
- Claude API 支持 `tool_use` 作为 forced tool call（`tool_choice: {"type": "tool", "name": "xxx"}`）
- OpenAI 支持 `response_format: {"type": "json_object"}`
- Claude Code 在需要结构化输出时使用 forced tool call 模式

**优化方向**: 
1. `LLMOptions` 增加 `responseFormat` 枚举（TEXT / JSON / TOOL_CHOICE）
2. `LLMOptions` 增加 `toolChoice` 字段（AUTO / NONE / REQUIRED / SPECIFIC(toolName)）
3. 各 Provider 实现对应的 API 参数映射

**优先级**: P2 — 影响 agent routing、memory summarization 等需要结构化输出的内部场景。

---

### TODO-011: ResilientLLMProvider 的重试策略过于简单

**现状**: 基于 error message 字符串匹配来判断是否可重试（`msg.contains(": 429")`），脆弱且不准确。不同 provider 的错误格式不统一。

**参考**: 
- Claude Code 的重试使用结构化的 error code（`overloaded_error`, `rate_limit_error`），不依赖消息字符串
- Anthropic SDK 的官方重试逻辑基于 HTTP status code + error type 枚举

**优化方向**: 
1. 定义 `LLMProviderException` 的结构化错误类型枚举（RATE_LIMITED, OVERLOADED, AUTH_FAILED, INVALID_REQUEST, NETWORK_ERROR）
2. 各 Provider 在抛异常时设置正确的错误类型
3. `ResilientLLMProvider` 基于错误类型而非字符串匹配判断可重试性
4. 429 场景：从 response header 读取 `Retry-After`，使用服务端建议的等待时间

**优先级**: P2 — 当前实现能工作但不可靠，错误格式变化就会误判。

---

### TODO-012: 缺少 Provider Fallback / Load Balancing

**现状**: 配置中只有单一 LLM provider。如果 Claude API 不可用，整个系统不可用。

**参考**: 
- OpenRouter 本身提供 fallback（但 runner 的 OpenRouterProvider 是作为独立 provider 使用）
- Claude Code 支持多个 API key 轮询 + fallback 到不同模型
- LiteLLM 的 router 支持 load balancing + fallback 策略

**优化方向**: 实现 `FallbackLLMProvider`：配置 primary + secondary provider 列表，primary 失败后自动切换到 secondary。与 `ResilientLLMProvider` 配合：单 provider 重试 → 失败 → 切换 provider。

**优先级**: P3 — 取决于业务对可用性的要求。

---

## 五、Memory 系统

### TODO-013: MemoryProvider.search() 是同步阻塞调用，在 reactive 链中有线程阻塞风险

**现状**: `AgentLoop.runReactive()` 虽然整体包裹在 `subscribeOn(boundedElastic)` 中，但 `memoryProvider.search()` 和 `memoryProvider.addMessage()` 是同步调用。当 `FileMemoryProvider` 执行 SQLite 查询或文件 I/O 时，会阻塞 boundedElastic 线程。

**参考**: 
- Claude Code 的 memory 操作全异步（`async/await`，Node.js 天然非阻塞）
- Spring Data R2DBC 提供 reactive 的数据库访问

**优化方向**: 
1. 短期：当前 `subscribeOn(boundedElastic)` 已经做了隔离，不会阻塞 event loop，可以接受
2. 中期：`MemoryProvider` 增加 `searchReactive()` / `addMessageReactive()` 的 default 方法，返回 `Mono<>`
3. 长期：`FileMemoryProvider` 的 SQLite 访问改用异步驱动

**优先级**: P3 — boundedElastic 调度器已经隔离了阻塞影响，短期不紧急。

---

### TODO-014: Memory search 结果硬编码 top 3，缺乏动态调整

**现状**: `AgentLoop.formatMemoryContext()` 中 `Math.min(3, results.size())` 硬编码最多取 3 条记忆，且 snippet 长度固定 100 字符。

**参考**: 
- Claude Code 的 memory retrieval 根据剩余 context budget 动态决定注入多少条记忆
- RAG 最佳实践：根据 query 相关性分数设置阈值，低于阈值的不注入

**优化方向**: 
1. 根据 `CostTracker.remainingTokens()` 动态决定注入的记忆条数和长度
2. 增加相关性分数阈值过滤（`MemorySearchResult.getScore() > threshold`）
3. 将 top-K 和 snippet 长度配置化（通过 `AgentProfile` 或 `PromptRequest`）

**优先级**: P2 — 影响长对话中记忆注入的质量。

---

## 六、Streaming & Event 系统

### TODO-015: StreamEvent 缺少 token usage 事件

**现状**: `StreamEvent.EventType` 没有 TOKEN_USAGE 或 USAGE_UPDATE 类型。token 消耗信息只在 `LLM_COMPLETE` 的 `LLMResponse` 中（如果 provider 填充了的话），无法实时推送给前端展示消耗。

**参考**: 
- Claude API 的 `message_delta` 事件包含 `usage` 字段
- Claude Code 在每次 LLM 调用后推送 usage 信息给 UI
- OpenAI Streaming 有 `usage` chunk

**优化方向**: 
1. 新增 `StreamEvent.EventType.USAGE_UPDATE` 事件类型
2. 在 `ToolCallingLoop` 每次 LLM 调用完成后发出 usage 事件
3. 前端可以展示实时的 token 消耗和预算余额

**优先级**: P2 — 对用户体验和成本可见性重要。

---

### TODO-016: StreamEvent 不支持 Thinking/Reasoning 事件

**现状**: `EventType` 没有 THINKING_DELTA 类型。Claude 的 extended thinking 模式输出的 thinking block 会被当作普通 TEXT_DELTA 处理，或者直接丢失。

**参考**: 
- Claude API 返回 `thinking` content block，与 `text` 分开
- Claude Code 将 thinking 内容单独展示（灰色折叠区域），不混入主输出

**优化方向**: 
1. 新增 `THINKING_DELTA` 和 `THINKING_COMPLETE` 事件类型
2. `ClaudeProvider` 解析 thinking content block 时发出对应事件
3. 前端可选展示 thinking 过程

**优先级**: P2 — Claude 的 extended thinking 是重要能力，当前无法利用。

---

## 七、Tool 系统

### TODO-017: Tool 接口缺少 Permission / Confirmation 机制

**现状**: 所有 tool 要么 `isAutoExecute()=true` 自动执行，要么 `false` 由客户端确认。但没有细粒度的权限控制：哪些 tool 可以修改文件、哪些只读、哪些需要用户确认。

**参考**: 
- Claude Code 的 tool 有三级权限：auto-approve / ask-user / deny
- Claude Code 的 permission 模式：`allowedTools` + `deniedTools` + per-tool `confirmation` 策略
- MCP 协议的 tool 有 `annotations.audience` 和 `annotations.destructiveHint` 元数据

**优化方向**: 
1. `Tool` 接口增加 `getPermissionLevel()` 方法（READ_ONLY / WRITE / DANGEROUS）
2. `ToolExecutor` 在执行 WRITE/DANGEROUS 级别工具前发出确认事件
3. `AgentProfile` 增加 per-tool 的 permission override

**优先级**: P2 — 安全关键特性，生产环境必需。

---

### TODO-018: ToolExecutor 缺少执行超时控制

**现状**: `ToolExecutor.executeToolCalls()` 没有单个 tool 的执行超时。如果某个 MCP 工具挂起，整个 agent loop 会永久阻塞。

**参考**: 
- Claude Code 的 tool 执行有 per-tool timeout（默认 120 秒，可配置）
- MCP 协议支持 `timeout` 参数
- AutoGen 的 tool 执行有 `timeout_seconds` 配置

**优化方向**: 
1. `Tool` 接口增加 `getTimeout()` 方法（默认 120 秒）
2. `ToolExecutor` 使用 `CompletableFuture.orTimeout()` 包裹执行
3. 超时时返回 `ToolResult.error("Tool execution timed out after Xs")`

**优先级**: P1 — 生产环境必需，MCP 外部服务不可靠时会导致系统挂起。

---

### TODO-019: 缺少 Tool Result Caching

**现状**: 相同参数的工具调用每次都重新执行。对于幂等的只读工具（如文件读取、搜索），重复执行浪费时间和资源。

**参考**: 
- Claude Code 对部分 tool 有 result caching（如 `Read` tool 的文件内容在同一 session 内缓存）
- LangChain 的 `@tool` 装饰器支持 `cache=True` 参数

**优化方向**: 
1. `Tool` 接口增加 `isCacheable()` 方法
2. `ToolExecutor` 对 cacheable 工具按 `(toolName, args hash)` 缓存结果
3. 缓存 TTL 与 session 绑定，session 结束时清理

**优先级**: P3 — 优化性能，非功能必需。

---

## 八、Observability & Tracing

### TODO-020: Trace 系统缺少与 OpenTelemetry 的集成

**现状**: 自定义的 `Tracer` / `SpanContext` / `SpanExporter` 体系，与业界标准的 OpenTelemetry 不兼容。`ConsoleSpanExporter` 和 `StreamEventSpanExporter` 是仅有的两个 exporter。

**参考**: 
- LangSmith / LangFuse 等 LLM observability 平台都基于 OpenTelemetry
- Claude Code 内部使用 Sentry + 自定义 telemetry
- OpenTelemetry 已是 CNCF 毕业项目，行业事实标准

**优化方向**: 
1. 实现 `OpenTelemetrySpanExporter`：将 runner 的 Span 转换为 OTel Span 并导出
2. 或者直接将 `Tracer` 替换为 OTel 的 `io.opentelemetry.api.trace.Tracer`（破坏性变更，需评估）
3. 最小化方案：保留自定义 Tracer，增加一个 OTel bridge exporter

**优先级**: P3 — 取决于是否需要接入外部 observability 平台。

---

## 九、SDK & API 设计

### TODO-021: agent-sdk 与 agent-kernel 存在概念重复

**现状**: `agent-sdk` 中的 `Agent` / `AgentBuilder` / `StreamCallback` 与 `agent-kernel` 中的 `AgentLoop` / `ChatHandler` / `StreamCallback` 概念重叠。`agent-sdk` 看起来是 kernel 的简化封装，但没有明确的边界划分文档。

**参考**: 
- Claude Agent SDK 的设计：SDK 是唯一的对外 API 面，kernel 是内部实现细节
- LangChain vs LangChain-core 的分离：core 定义抽象，langchain 提供实现，应用层只依赖 core

**优化方向**: 
1. 明确 agent-sdk 的定位：它应该是 agent-kernel 的 Public API facade
2. 应用层（agent-web）应该只依赖 agent-sdk，不直接依赖 agent-kernel 的内部类
3. 或者合并 agent-sdk 到 agent-kernel，减少一层间接

**优先级**: P3 — 架构洁癖，不影响功能。

---

### TODO-022: Gateway 层缺少 Rate Limiting 和 Request Validation

**现状**: `GatewayController` 直接将请求转发给 `ChatHandler`，没有速率限制、请求大小校验、session 并发控制。

**参考**: 
- Claude Code 的 API 有 per-session rate limiting
- Spring Cloud Gateway 提供开箱即用的 rate limiter
- 基本 API 安全：最大消息长度、最大 session 数、per-IP 限制

**优化方向**: 
1. 实现 `RateLimitInterceptor`：per-session 和 per-IP 的请求频率限制
2. `GatewayRequest` 增加 validation（消息长度上限、sessionId 格式校验）
3. Orchestrator 层面限制每个 session 的最大活跃 agent 数

**优先级**: P1 — 安全基线，生产部署前必须有。

---

## 十、Testing & Quality

### TODO-023: 测试覆盖不均匀（196 test files vs 302 source files）

**现状**: 测试文件 196 个 vs 源文件 302 个，覆盖率约 65%。但分布不均匀 —— `agent-kernel` 的核心类测试较好，`agent-web` 的 controller/config 类测试薄弱，`agent-mcp` 和 `kernel-memory` 的集成测试缺失。

**参考**: CLAUDE.md 中强调的 TDD 规则要求 outside-in 测试，但多数模块缺少 acceptance test。

**优化方向**: 
1. 为 `agent-mcp` 增加 MCP server mock 的集成测试
2. 为 `agent-web` 的 WebSocket 路径增加端到端测试
3. 为 `kernel-memory` 的 FileMemoryProvider 增加 SQLite 持久化 + 搜索的集成测试

**优先级**: P2 — 按 CLAUDE.md 的 TDD 规则，这些是"技术债"。

---

### TODO-024: 缺少 Benchmark / Load Test 基础设施

**现状**: 没有性能测试。不知道单个 AgentLoop 的吞吐量、多 subagent 并发的延迟、memory search 的 P99 延迟。

**参考**: 
- Claude Code 有内部的 latency benchmark（每个 tool 的执行时间 P50/P95/P99）
- JMH（Java Microbenchmark Harness）适合 kernel 层的微基准测试
- k6 / Gatling 适合 agent-web 层的负载测试

**优化方向**: 
1. 添加 JMH benchmark 模块，覆盖：ToolCallingLoop 执行延迟、ContextCompactor 压缩性能、MemorySearch 查询延迟
2. 添加 k6 脚本，覆盖：WebSocket 并发连接数、SSE 长连接稳定性、多 session 并发

**优先级**: P3 — 在规模化部署前需要。

---

## 总结：优先级排序

| 优先级 | 编号 | 标题 | 类别 |
|--------|------|------|------|
| **P1** | TODO-001 | CostTracker 未被记录 | Agent Loop |
| **P1** | TODO-003 | sync 路径无法打断 | Agent Loop |
| **P1** | TODO-004 | runStream() 不支持 Tool Calling | Agent Loop |
| **P1** | TODO-018 | Tool 执行无超时 | Tool 系统 |
| **P1** | TODO-022 | 缺少 Rate Limiting | API 安全 |
| **P2** | TODO-002 | 构造器参数膨胀 | 代码质量 |
| **P2** | TODO-005 | 缺少 token-aware 压缩 | Context |
| **P2** | TODO-007 | FixedThreadPool 不弹性 | 多 Agent |
| **P2** | TODO-010 | 缺少 Structured Output | LLM Provider |
| **P2** | TODO-011 | 重试策略字符串匹配 | LLM Provider |
| **P2** | TODO-014 | Memory search 硬编码 top 3 | Memory |
| **P2** | TODO-015 | 缺少 token usage 事件 | Streaming |
| **P2** | TODO-016 | 不支持 Thinking 事件 | Streaming |
| **P2** | TODO-017 | 缺少 Tool Permission | Tool 系统 |
| **P2** | TODO-023 | 测试覆盖不均匀 | 质量 |
| **P3** | TODO-006 | 缺少 Summarization 压缩 | Context |
| **P3** | TODO-008 | Subagent 缺少协作机制 | 多 Agent |
| **P3** | TODO-009 | Router 缺少可观测性 | 多 Agent |
| **P3** | TODO-012 | 缺少 Provider Fallback | LLM Provider |
| **P3** | TODO-013 | Memory 同步阻塞 | Memory |
| **P3** | TODO-019 | 缺少 Tool Result Cache | Tool 系统 |
| **P3** | TODO-020 | Trace 未集成 OTel | Observability |
| **P3** | TODO-021 | SDK 与 Kernel 概念重复 | 架构 |
| **P3** | TODO-024 | 缺少 Benchmark | 质量 |

---

## 十一、基于外部研究的新增 TODO（OpenClaw 最新架构 + Claude Code 泄露源码深度分析）

> 以下 TODO 基于 2026-05-10 的外部研究成果补充。来源包括：OpenClaw GitHub (347K+ stars)、VILA-Lab 论文 arXiv:2604.14228（Claude Code 512K 行源码分析）、Google ADK 1.0 GA、以及 LangGraph/AutoGen/CrewAI 最新版本。

### TODO-025: 压缩策略从 2 层升级为 5 层渐进式（对标 Claude Code）

**现状**: runner 仅有 `SnipCompactor`（删 TOOL 消息）+ `MicroCompactor`（截断长消息），2 层。

**参考**: Claude Code 泄露源码揭示了 **5 层渐进式压缩管线**，按成本从低到高依次执行：
1. **Budget Reduction** — 将过大的 tool output 替换为引用指针，零成本
2. **Snip** — 整块删除旧消息，零成本但信息损失高
3. **Microcompact** — 选择性清除单个 tool result，感知 prompt cache 命中情况
4. **Context Collapse** — 分阶段缩减消息块
5. **Auto-Compact** — 发送整个对话给 LLM 做摘要，信息损失最小但最贵

设计原则：**lazy degradation（懒降级）**—— 先用最便宜的策略，不够再升级。

**优化方向**: 
1. 实现 `BudgetReductionCompactor`：对超阈值的 tool result（如文件内容、搜索结果）替换为 `[引用: tool_call_id=xxx, 原始长度=N bytes]`
2. 实现 `ContextCollapseCompactor`：分阶段合并同轮的多条消息
3. `CompactionChain` 按成本排序执行：BudgetReduction → Snip → Micro → Collapse → Summarization

**优先级**: P2 — runner 的 2 层策略在多轮工具调用场景中压缩不足。

---

### TODO-026: 实现 Memory Flush Before Compaction（对标 OpenClaw）

**现状**: `SnipCompactor` 删除旧 TOOL 消息时，直接丢弃内容，没有先提取重要信息。

**参考**: OpenClaw 2026.4 的压缩流程在 Snip 之前有一步 **Memory Flush**：
- 从即将被删除的消息中提取关键事实
- 写入持久化 Memory（Durable 层）
- 然后再执行删除

Claude Code 也有类似机制：`autoDream` 后台任务在空闲时整理记忆，合并矛盾信息，将模糊观察转化为持久事实。

**优化方向**: 
1. `CompactionChain` 增加 pre-compaction hook：`beforeCompact(messagesToRemove)`
2. 实现 `MemoryFlushPreCompactor`：用轻量 LLM 调用从待删消息中提取 key facts
3. 提取结果写入 `memoryProvider.writeDurable("compaction_extracted", facts)`

**优先级**: P2 — 防止长对话中的信息丢失。

---

### TODO-027: Prompt Caching 支持（Claude API cache_control）

**现状**: 每次 LLM 调用都发送完整的 system prompt + tool definitions，没有利用 Claude API 的 prompt caching 能力。

**参考**: 
- Claude Code 内部监控 **14 个 cache-break 条件**，确保稳定内容（system prompt、tool definitions）命中 prompt cache
- Claude API 支持 `cache_control: {type: "ephemeral"}` 标记，可缓存标记之前的所有内容
- 命中 cache 时 input token 成本降低 ~90%

**优化方向**: 
1. `ClaudeProvider` 在构建请求体时，对 system prompt 和 tool definitions 添加 `cache_control` 标记
2. `LLMOptions` 增加 `enablePromptCaching` 选项
3. 监控 cache hit rate（从 response header 的 `anthropic-cache-*` 字段读取）

**优先级**: P1 — 直接降低 80%+ 的 input token 成本，ROI 极高。

---

### TODO-028: Steer Mode — 比 Interrupt 更细粒度的用户干预（对标 OpenClaw）

**现状**: `InterruptibleRun` 只有两种模式：继续运行 或 完全打断（cancel CancellationToken + dispose Flux + 保存部分文本 + 重新执行）。

**参考**: OpenClaw 的 **Steer Mode** 提供了更优雅的中间方案：
- 用户在 agent 运行时发新消息
- 不取消整个 run，而是**跳过当前 pending 的 tool calls**
- 在下一个 tool gap（工具间隙）注入新消息
- LLM 看到"我正在做 X，但用户刚说了 Y"，自行决定是否改变方向

**优化方向**: 
1. `InterruptibleRun` 增加 `steer(newInput)` 方法（与 `interrupt()` 并列）
2. `ToolCallingLoop` 增加 `steerMessage` 注入点：每轮工具执行后检查是否有 steer 消息
3. 如有 steer 消息，将其作为额外 USER 消息追加到对话中，让 LLM 在下一轮决定是否调整

**优先级**: P3 — 增强 UX，但 interrupt+resume 已覆盖主要场景。

---

### TODO-029: Streaming Tool Execution + RWLock（对标 Claude Code）

**现状**: `ToolCallingLoop` 等待 LLM 流式输出完全结束（`LLM_COMPLETE` 事件）后，才开始执行工具。

**参考**: Claude Code 的 `StreamingToolExecutor` + `RWLock` 模式：
- **在 LLM 仍在输出时就开始执行 tool**（当流中检测到完整的 tool_use block 时立即执行）
- Read 操作并行执行（多个只读工具同时跑）
- Write 操作互斥执行（写工具独占锁）
- 显著减少端到端延迟（工具执行与 LLM 输出并行）

**优化方向**: 
1. `ToolCallingLoop.executeReactiveLoop()` 改为在检测到 `TOOL_CALL_START` 事件时立即启动工具执行（而非等 `LLM_COMPLETE`）
2. 引入 `ReadWriteSemaphore`：按 Tool 的 `getPermissionLevel()` 区分读写
3. 这与 TODO-017 (Tool Permission) 有协同效应

**优先级**: P3 — 性能优化，减少多工具场景的延迟。

---

### TODO-030: Lazy Tool Schema Loading（对标 Claude Code + OpenClaw）

**现状**: `ToolRegistry.getToolDefinitions()` 每次都返回所有 enabled tool 的完整定义（name + description + input_schema），全部注入 system prompt。工具数量大时占用大量 context 窗口。

**参考**: 
- Claude Code 初始只加载 tool 名称列表，完整 schema 按需获取
- OpenClaw 的 TOOLS.md 只列工具名称，schema 在调用时才 fetch（token-efficient）
- 减少 system prompt 大小也有利于 prompt cache 命中

**优化方向**: 
1. `LLMOptions` 增加 `toolLoadingStrategy` 枚举（EAGER / LAZY / DEFERRED）
2. LAZY 模式：system prompt 只包含 tool name + description（无 input_schema）
3. 当 LLM 决定调用某 tool 时，`ToolCallingLoop` 在第二轮注入该 tool 的完整 schema
4. 需要评估 LLM 在缺少 schema 时是否仍能正确调用工具

**优先级**: P3 — 优化 token 消耗，工具数量少时收益不明显。

---

### TODO-031: ClaudeProvider tool_result 格式修复（多工具并行调用）

**现状**: 需要验证 `ClaudeProvider` 在发送 tool_result 时是否正确使用了 `{type: "tool_result", tool_use_id: "xxx", content: "..."}` 结构化格式。如果 tool_use_id 丢失，Claude API 在多工具并行调用时无法匹配结果。

**参考**: Claude Code 和所有 production 实现都要求 tool_result 必须包含 `tool_use_id`。当前 `ToolCallingLoop.createToolResultMessage()` 将 `tool_use_id` 放在 `metadata` 中，但 `ClaudeProvider` 构建请求体时是否正确读取这个 metadata 需要验证。

**优化方向**: 
1. 审计 `ClaudeProvider` 的请求体构建逻辑，确保 tool_result content block 格式正确
2. 添加 acceptance test：2+ 个并行 tool call → 结果正确匹配
3. 如果 metadata 传递链有断裂，修复 `ConversationMessage` → `ClaudeProvider` 的映射

**优先级**: P1 — 多工具并行是核心功能，格式错误会导致 API 400 错误。

---

### TODO-032: Workspace-as-Config（SOUL.md / AGENTS.md / TOOLS.md 文件驱动配置）

**现状**: Agent 配置通过 Java 代码（`AgentProfile` + `AgentRegistry` + Spring Config）管理，修改需要重新编译部署。

**参考**: 
- OpenClaw 使用 **SOUL.md**（角色/人格）、**AGENTS.md**（agent 定义）、**TOOLS.md**（工具元数据）文件驱动配置，版本可控、可 diff
- Claude Code 使用 **CLAUDE.md** 作为项目级指令
- 所有配置都是 plain text，不需要重启即可生效

**优化方向**: 
1. 支持从 `agents.yaml` 或 `AGENTS.md` 文件加载 `AgentProfile` 列表
2. 实现 file watcher 热加载，配置文件变更后自动更新 `AgentRegistry`
3. 与当前的 Java Config 并存：文件配置优先，代码配置作为 fallback

**优先级**: P3 — 运维友好，但需要控制好与代码配置的优先级关系。

---

### TODO-033: Subagent 模式扩展（Fork / Teammate / Worktree）

**现状**: `SubagentRuntime` 只有一种执行模式：spawn 一个独立的 AgentLoop 同步执行，结果通过 callback 返回。

**参考**: Claude Code 泄露源码揭示了 **三种 subagent 模式**：
- **Fork**：轻量级，继承父上下文，200-turn 限制，结果 <500 words
- **Teammate**：并行执行，通过文件 mailbox 通信，无限制
- **Worktree**：Git 隔离，每个 agent 独立 branch 工作，适合代码修改任务

**优化方向**: 
1. `SpawnRequest` 增加 `mode` 字段（FORK / INDEPENDENT / ISOLATED）
2. FORK 模式：子 agent 继承父 agent 的消息上下文（MessageSnapshot），结果自动注入父对话
3. ISOLATED 模式：为文件系统隔离场景预留（如独立工作目录）

**优先级**: P3 — 当前单一模式覆盖主要场景，多模式是进阶需求。

---

### TODO-034: A2A (Agent-to-Agent) 协议预备

**现状**: runner 的 multi-agent 通信是内部私有协议（`SubagentRuntime` + `StreamEvent`）。

**参考**: 
- Google ADK 1.0 GA 已内置 A2A 支持
- Linux Foundation AAIF 正在标准化 agent 间通信协议
- A2A 与 MCP 互补：MCP = agent-to-tool，A2A = agent-to-agent

**优化方向**: 
1. 短期：无需变更，runner 的内部协议够用
2. 中期：当需要跨框架 agent 通信时，实现 A2A protocol adapter
3. 预备：`SubagentRuntime` 的 `spawn()` / `getRun()` 接口设计已接近 A2A 的 task model

**优先级**: P4 — 前瞻性，等标准成熟后再投入。

---

## 更新后的优先级排序

| 优先级 | 编号 | 标题 | 类别 |
|--------|------|------|------|
| **P1** | TODO-001 | CostTracker 未被记录 | Agent Loop |
| **P1** | TODO-003 | sync 路径无法打断 | Agent Loop |
| **P1** | TODO-004 | runStream() 不支持 Tool Calling | Agent Loop |
| **P1** | TODO-018 | Tool 执行无超时 | Tool 系统 |
| **P1** | TODO-022 | 缺少 Rate Limiting | API 安全 |
| **P1** | TODO-027 | Prompt Caching 支持 | LLM Provider |
| **P1** | TODO-031 | ClaudeProvider tool_result 格式 | LLM Provider |
| **P2** | TODO-002 | 构造器参数膨胀 | 代码质量 |
| **P2** | TODO-005 | 缺少 token-aware 压缩 | Context |
| **P2** | TODO-007 | FixedThreadPool 不弹性 | 多 Agent |
| **P2** | TODO-010 | 缺少 Structured Output | LLM Provider |
| **P2** | TODO-011 | 重试策略字符串匹配 | LLM Provider |
| **P2** | TODO-014 | Memory search 硬编码 top 3 | Memory |
| **P2** | TODO-015 | 缺少 token usage 事件 | Streaming |
| **P2** | TODO-016 | 不支持 Thinking 事件 | Streaming |
| **P2** | TODO-017 | 缺少 Tool Permission | Tool 系统 |
| **P2** | TODO-023 | 测试覆盖不均匀 | 质量 |
| **P2** | TODO-025 | 压缩策略升级为 5 层 | Context |
| **P2** | TODO-026 | Memory Flush Before Compaction | Memory |
| **P3** | TODO-006 | 缺少 Summarization 压缩 | Context |
| **P3** | TODO-008 | Subagent 缺少协作机制 | 多 Agent |
| **P3** | TODO-009 | Router 缺少可观测性 | 多 Agent |
| **P3** | TODO-012 | 缺少 Provider Fallback | LLM Provider |
| **P3** | TODO-013 | Memory 同步阻塞 | Memory |
| **P3** | TODO-019 | 缺少 Tool Result Cache | Tool 系统 |
| **P3** | TODO-020 | Trace 未集成 OTel | Observability |
| **P3** | TODO-021 | SDK 与 Kernel 概念重复 | 架构 |
| **P3** | TODO-024 | 缺少 Benchmark | 质量 |
| **P3** | TODO-028 | Steer Mode 细粒度干预 | 多 Agent |
| **P3** | TODO-029 | Streaming Tool Execution | 性能 |
| **P3** | TODO-030 | Lazy Tool Schema Loading | 性能 |
| **P3** | TODO-032 | Workspace-as-Config | 运维 |
| **P3** | TODO-033 | Subagent 多模式 | 多 Agent |
| **P4** | TODO-034 | A2A 协议预备 | 前瞻 |

---

## 参考来源

1. **OpenClaw** (GitHub 347K+ stars) — Pi Agent Runtime 两层架构（Gateway + Pi Agent Core）、Steer Mode、Memory Flush Before Compaction、SOUL.md/AGENTS.md/TOOLS.md workspace-as-config、plugin 生命周期钩子
2. **Claude Code 泄露源码** (arXiv:2604.14228, VILA-Lab) — 512K 行 TypeScript、5 层压缩管线、StreamingToolExecutor+RWLock、7 级权限模式+yoloClassifier ML 分类器、14 个 prompt cache-break 监控条件、KAIROS daemon mode、autoDream 记忆整理、Fork/Teammate/Worktree 三种 subagent 模式、44 个 feature flag
3. **LangGraph** — Graph-based execution engine、StateGraph+reducer state、built-in checkpointing with time travel、model retry middleware
4. **AutoGen / AG2 (Microsoft)** — Conversational agent teams、GroupChat 多 agent 协作、event-driven core、向 Microsoft Agent Framework 转型
5. **CrewAI** — Crews（自主团队）vs Flows（事件驱动管线）两种架构模式、Hierarchical memory isolation、Swarm patterns
6. **Google ADK 1.0 GA** — Tool/Agent/Orchestrator 三层、A2A 协议、Event Compaction（38% token 缩减）、Java Maven 支持
7. **Java Agent 框架** — LangChain4j 1.x (agentic 模块)、Spring AI 2.0 (Boot 4 集成)、Embabel (GOAP 规划)、Koog (JetBrains, 显式有向图)、Semantic Kernel Java (OTel 原生)
8. **Anthropic SDK 最佳实践** — 结构化错误类型、Retry-After 处理、JSON mode / tool_choice、prompt caching API
