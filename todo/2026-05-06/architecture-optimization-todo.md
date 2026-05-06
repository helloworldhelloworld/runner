# Architecture Optimization TODO — 2026-05-06

> Sources: Claude Code leaked source analysis (arxiv 2604.14228), OpenClaw architecture, LangGraph/AutoGen/CrewAI/Semantic Kernel/Anthropic Agent SDK/OpenAI Agents SDK/Vercel AI SDK/Spring AI best practices, runner codebase static analysis.

---

## Executive Summary

Claude Code 的源码泄露揭示了一个关键洞察：**仅 1.6% 的代码是 AI 决策逻辑，98.4% 是确定性基础设施**（权限门控、上下文管理、工具路由、恢复逻辑）。runner 项目的核心 Agent Loop 设计合理，但围绕它的基础设施层——上下文压缩、可观测性、错误恢复、安全防护——仍有显著差距。

以下 TODO 按优先级（P0/P1/P2/P3）和领域分组，每项标注来源依据和预估工作量。

---

## P0 — Critical (阻塞生产可用性)

### 1. [Thread Safety] InterruptibleRun.accumulatedText 并发修改风险
- **现状**: `accumulatedText` 使用非线程安全的 `StringBuilder`，在 reactive pipeline 和 interrupt 线程之间存在并发修改风险
- **位置**: `InterruptibleRun.java:52, 99-105`
- **依据**: codebase analysis — interrupt() 从外部线程写入 accumulatedText，同时 reactive pipeline 也在累加
- **方案**: 替换为 `StringBuffer` 或使用 `synchronized` 块保护复合操作；更优方案是改为 `AtomicReference<String>` + immutable append
- **工作量**: S (1-2h)

### 2. [Thread Safety] SubagentRuntime TOCTOU 竞态
- **现状**: `synchronized(activeRuns)` 仅保护 size check + put，后续 `executor.submit()` 和 `run.setFuture()` 不在锁内
- **位置**: `SubagentRuntime.java:84-127`
- **依据**: codebase analysis — 并发 spawn 可超出 maxConcurrent 限制
- **方案**: 扩大 synchronized 范围覆盖 submit+setFuture，或使用 Semaphore 替代手动计数
- **工作量**: S (2-3h)

### 3. [Thread Safety] Orchestrator.activeRuns 清理竞态
- **现状**: `doFinally()` 中的 remove 与 phase check 非原子操作
- **位置**: `Orchestrator.java:177-182`
- **方案**: 使用 `ConcurrentHashMap.compute()` 原子化 phase-check + remove
- **工作量**: S (1-2h)

### 4. [Error Handling] Orchestrator.chat() 无异常保护
- **现状**: sync 路径中 `agent.run()` 抛出异常时直接传播到 Spring controller，无降级处理
- **位置**: `Orchestrator.java:90-99`
- **依据**: codebase analysis — LLM/memory 故障直接导致 500
- **方案**: 包装 try-catch，返回 `StreamEvent.error()` 或 `GatewayResponse.error()`
- **工作量**: S (1-2h)

---

## P1 — High Priority (显著提升架构质量)

### 5. [Context] 五层压缩管道 — 补齐 Layer 1 (Budget Reduction) 和 Layer 5 (Auto-Compact)
- **现状**: `CompactionChain(Snip(3), Micro(2000))` 仅覆盖 Claude Code 五层中的第 2-3 层
- **依据**: Claude Code 泄露源码 `query.ts:365-453` 实现了五层级联压缩（Budget Reduction → Snip → Microcompact → Context Collapse → Auto-Compact），cheapest-first 策略
- **Claude Code 做法**:
  - Layer 1 (Budget Reduction): Tool output > 50K chars 时写入磁盘文件，上下文只保留 ~2KB preview + 文件路径
  - Layer 4 (Context Collapse): 消息数超阈值时分阶段折叠旧消息块
  - Layer 5 (Auto-Compact): 语义压缩，调用 LLM 生成结构化摘要，作为最后手段
- **方案**:
  ```
  CompactionChain(
    BudgetReductionCompactor(50_000),  // 新增: 大 Tool 结果写文件
    SnipCompactor(3),                  // 已有
    MicroCompactor(2000),              // 已有
    ContextCollapseCompactor(50),      // 新增: 消息数阈值折叠
    AutoCompactor(provider)            // 新增: LLM 语义压缩
  )
  ```
- **额外**: 添加 Image Stripping — 将图片/文档替换为 `[image]`/`[document]` 占位符
- **工作量**: L (3-5d)

### 6. [Context] 锚定式迭代摘要 (Anchored Iterative Summarization)
- **现状**: 每次压缩从头生成摘要
- **依据**: Factory.ai 研究（36,000 条工程会话评估）表明增量合并摘要比全量重建更准确
- **方案**: 为 session 添加 `SummaryState` 持久字段，Auto-Compact 时将现有摘要 + 新消息合并更新，而非从头重建
- **工作量**: M (2-3d)

### 7. [Context] 上下文利用率 70% 阈值触发压缩
- **现状**: CostTracker 仅跟踪 token 消耗，无主动压缩触发
- **依据**: 研究表明模型回忆准确率在 context 利用率超 70% 后显著下降（Context Rot 效应）
- **方案**: ToolCallingLoop 每次迭代检查 `contextUtilization = usedTokens / maxContextTokens`，超 70% 时触发 CompactionChain
- **工作量**: M (1-2d)

### 8. [Tool] 动态工具发现 (ToolSearchTool)
- **现状**: 所有工具定义在每次 LLM 调用时全量注入，多工具场景消耗大量 token
- **依据**: Claude Code 的 tool registry 支持 feature-gated loading；Spring AI 实现动态发现后 token 节约 34-64%
- **方案**: 将 `ToolRegistry.search(keyword)` 提升为一等公民 `ToolSearchTool`，LLM 先搜索发现相关工具，再使用；大量工具场景默认不全量注入
- **工作量**: M (2-3d)

### 9. [Tool] 流式工具执行 (Streaming Tool Execution)
- **现状**: 等待 LLM 完整响应后才开始执行工具
- **依据**: Claude Code 使用 `StreamingToolExecutor` + `RWLock`，在模型仍在流式生成时即刻派发工具执行，显著降低延迟
- **方案**: 在 `ToolCallingLoop` 中监听 `content_block_stop` 事件，完整 tool_use block 一出现就启动执行，无需等待整个 response 完成
- **工作量**: L (3-5d)

### 10. [Tool] Guardrail Chain — 工具执行前置策略
- **现状**: `AgentObserver.onPreToolUse()` 返回 void，无法阻止工具执行
- **依据**: OpenAI Agents SDK 三层 guardrail（input/output/tool）；Claude Code per-tool `checkPermissions()`；EU AI Act Article 14 (2026.08 生效) 要求高风险 AI 的人类监督
- **方案**:
  ```java
  public interface ToolPolicy {
      PolicyDecision evaluate(String toolName, Map<String, Object> args, AgentContext ctx);
  }
  // PolicyDecision: ALLOW, DENY(reason), REQUIRE_APPROVAL
  ```
  新增 `APPROVAL_REQUIRED` StreamEvent，暂停 ToolCallingLoop 等待人工审批
- **工作量**: L (3-5d)

### 11. [Tool] 工具 Prompt 自注入
- **现状**: 系统 prompt 静态枚举工具描述
- **依据**: Claude Code 每个 tool 定义 `prompt()` 方法，可向 system prompt 注入内容
- **方案**: Tool 接口增加 `default String getSystemPromptContribution() { return ""; }`，AgentLoop 构建 system prompt 时收集所有 active tools 的贡献
- **工作量**: M (1-2d)

### 12. [Orchestrator] 并发子代理聚合 (WaitAllSubagentsTool)
- **现状**: SubagentRuntime 支持并行 spawn，但缺少 "spawn N + wait all + aggregate" 的原子操作
- **依据**: Semantic Kernel 五种编排模式中的 Concurrent 模式；这是当前缺失的最高价值编排模式
- **方案**: 实现 `WaitAllSubagentsTool`，接受多个 spawn 请求，并行执行，聚合凝练结果返回
- **工作量**: M (2-3d)

### 13. [Orchestrator] 上下文继承式 Handoff 模式
- **现状**: 子代理通过隔离 session (`agent:<id>:subagent:<uuid>`) 启动，无法获取父代理上下文
- **依据**: OpenAI Agents SDK 的 Handoff 模式传递完整消息历史；Claude Code 子代理可继承 compact 后的上下文
- **方案**: SpawnRequest 增加 `contextMode` 枚举：`ISOLATED`（现有）/ `INHERIT_COMPACT`（继承压缩后的父上下文）
- **工作量**: M (2-3d)

### 14. [Observability] OpenTelemetry 标准化追踪
- **现状**: 自定义 `Tracer` 最小化实现，无 tool span，无延迟跟踪，无结构化错误
- **依据**: 2026 行业共识 — OpenTelemetry 是 AI agent 遥测标准；OpenAI Agents SDK 自动 instrument 所有 LLM/tool/handoff/guardrail 调用
- **方案**:
  - LLM Span: model, prompt_tokens, completion_tokens, latency, cost
  - Tool Span: tool_name, input_args, output, duration, success/failure
  - Agent Span: agent_id, session_key, total_turns, total_cost
  - Subagent Span: parent-child hierarchy
- **工作量**: L (5-7d)

### 15. [Observability] Metrics 基础设施
- **现状**: 无任何 metrics — 无工具延迟/成功率、LLM 调用延迟/token 消耗、子代理 spawn/完成率
- **方案**: 引入 Micrometer，暴露核心指标：
  - `agent.tool.execution.duration` (histogram, by tool_name)
  - `agent.llm.call.duration` (histogram, by provider)
  - `agent.llm.tokens.consumed` (counter, by input/output)
  - `agent.subagent.spawn.count` / `agent.subagent.complete.count`
  - `agent.interrupt.count`
- **工作量**: M (2-3d)

### 16. [Error] Circuit Breaker per Tool
- **现状**: ToolExecutor 无 circuit breaker，工具持续失败时会反复重试
- **依据**: 2025-2026 共识的五层容错栈：schema validation → backoff → circuit breaker → idempotency → fallback
- **方案**: ToolExecutor 维护 per-tool 失败率滑动窗口，连续 N 次失败后标记 degraded，通过 system message 通知 LLM
- **工作量**: M (2-3d)

### 17. [Error] ResilientLLMProvider 补齐 Reactive 路径
- **现状**: `ResilientLLMProvider` 的重试逻辑仅覆盖 sync `complete()` 和 stream 变体，`completeReactive()` 未被包装
- **位置**: `ResilientLLMProvider.java`
- **方案**: 使用 Reactor `retryWhen(Retry.backoff())` 包装 reactive 路径
- **工作量**: S (2-3h)

### 18. [Error] 错误分类与差异化处理
- **现状**: ToolCallingLoop 将所有 Throwable 包装为 `RuntimeException("Tool calling loop failed: ...")`，丢失原始类型信息
- **位置**: `ToolCallingLoop.java:217-220`
- **依据**: 行业最佳实践按错误类型差异化处理（429 退避重试、500 circuit breaker、context overflow 触发压缩、401 立即失败）
- **方案**: 定义 `AgentException` 层级（`RateLimitException`, `ProviderUnavailableException`, `ContextOverflowException`, `ToolExecutionException`），在 ToolCallingLoop 按类型分派
- **工作量**: M (2-3d)

---

## P2 — Medium Priority (提升工程质量)

### 19. [Security] 输入验证
- **现状**: GatewayController / ChatController 的 `@RequestBody` 无 `@Valid` 注解，无消息长度限制，sessionId 格式无校验
- **位置**: `GatewayController.java:60-77`, `ChatController.java:62-92`
- **方案**: 添加 Bean Validation (`@Valid`, `@Size`, `@Pattern`)，限制消息最大长度，校验 sessionId 格式
- **工作量**: S (2-3h)

### 20. [Security] 工具执行超时与沙箱
- **现状**: Tool.execute() 无超时限制，恶意/错误工具可无限挂起
- **方案**: ToolExecutor 添加 per-tool configurable timeout（默认 30s），使用 `CompletableFuture.orTimeout()`；高危工具使用进程隔离
- **工作量**: M (1-2d)

### 21. [Security] Session 端点鉴权
- **现状**: `GET /gateway/session/{sessionId}/history` 无认证检查
- **位置**: `GatewayController.java:289-300`
- **方案**: 添加 session 所有权校验或 token-based 认证
- **工作量**: M (1-2d)

### 22. [Security] 工具参数敏感信息脱敏
- **现状**: Tool args 的 key names 直接记录到日志
- **位置**: `ToolCallingLoop.java:504-509`
- **方案**: 定义敏感字段列表（api_key, password, token, secret），日志中自动替换为 `[REDACTED]`
- **工作量**: S (2-3h)

### 23. [API] HTTP 错误码规范化
- **现状**: GatewayController 错误时返回 HTTP 200 + error body，无错误分类
- **位置**: `GatewayController.java:75`
- **方案**: 使用 `ResponseEntity` 返回正确 HTTP status（400/401/429/500/503），添加错误码分类
- **工作量**: S (3-4h)

### 24. [API] Rate Limiting
- **现状**: 无速率限制，可通过刷请求耗尽线程池
- **方案**: 基于 Bucket4j 或 Spring Gateway 的 per-session/per-IP rate limiting
- **工作量**: M (1-2d)

### 25. [Cost] 预算级别注入 System Prompt (BATS Pattern)
- **现状**: CostTracker 仅提供 boolean `isOverBudget()`
- **依据**: BATS (Budget-Aware Tool Selection, 2025.11) — 将预算级别注入 system prompt 让 LLM 自我调节
- **方案**:
  ```java
  public enum BudgetLevel { HIGH, MEDIUM, LOW, CRITICAL }
  // HIGH (>=70% remaining): 正常操作
  // MEDIUM (30-70%): 倾向低成本操作
  // LOW (10-30%): 仅必要工具
  // CRITICAL (<10%): 强制完成，禁止工具调用
  ```
  AgentLoop 在构建 system prompt 时注入当前 BudgetLevel
- **工作量**: M (1-2d)

### 26. [Cost] 子代理层级成本聚合
- **现状**: CostTracker 按单 session 跟踪，父代理看不到子代理的 token 消耗
- **方案**: SubagentRuntime 完成时将子代理 CostTracker 合并回父代理，父代理看到总成本
- **工作量**: M (1-2d)

### 27. [Memory] 语义向量搜索补充 BM25
- **现状**: kernel-memory 仅支持 BM25 关键词搜索
- **依据**: 2026 行业标准是 two-pass 检索（vector similarity 候选 + reranker 精排）
- **方案**: kernel-memory 添加 embedding-based 语义搜索路径，与 BM25 结果 fusion
- **工作量**: L (3-5d)

### 28. [Memory] Agent 自主管理记忆工具
- **现状**: 记忆保存是隐式的（AgentLoop doOnComplete 自动保存）
- **依据**: Spring AI `AutoMemoryToolsAdvisor`（2026.04）让 agent 通过 `remember/recall/forget` 工具显式管理长期记忆
- **方案**: 实现三个 Tool：`MemoryRememberTool`, `MemoryRecallTool`, `MemoryForgetTool`
- **工作量**: M (2-3d)

### 29. [Memory] 两层记忆架构 (Claude Code Pattern)
- **现状**: 单层 MemoryProvider (ephemeral + durable + history)
- **依据**: Claude Code 两层记忆 — Tier 1: CLAUDE.md (~150 行 compact briefing，启动时自动读取) + Tier 2: .memory/state.json (完整记忆存储，关键词/标签/NL 搜索)
- **方案**: 分离 HotMemory（小型、高频、自动注入 system prompt）和 ColdMemory（大型、可搜索、按需检索）
- **工作量**: M (2-3d)

### 30. [Config] 外部化 Agent Profile
- **现状**: OrchestratorConfig 中 agent profiles 硬编码，修改需重新部署
- **位置**: `OrchestratorConfig.java:37-50`
- **方案**: 支持从 YAML/JSON 文件加载 AgentProfile，支持 hot-reload
- **工作量**: M (1-2d)

### 31. [Config] Per-Tool Feature Flag
- **现状**: 只有全局 `tools-enabled: true`，无法单独禁用某个工具
- **方案**: 支持 `tools.disabled: [tool1, tool2]` 配置，ToolRegistry 加载时跳过
- **工作量**: S (3-4h)

### 32. [Streaming] 事件订阅过滤
- **现状**: WebSocket 客户端接收所有 StreamEvent，无法按类型过滤
- **依据**: LangGraph 支持 7 种同时订阅模式（values/updates/messages/custom/checkpoints/tasks/debug）
- **方案**: WebSocket 握手时指定订阅的 EventType 集合，服务端过滤后推送
- **工作量**: M (1-2d)

### 33. [Streaming] 事件 ID 去重
- **现状**: StreamEvent 无 stable eventId
- **依据**: Vercel AI SDK v2 每个事件有 unique ID，用于客户端去重和排序
- **方案**: StreamEvent 添加 `eventId` (UUID) 字段
- **工作量**: S (1-2h)

### 34. [Test] 错误恢复验收测试
- **现状**: 缺少 LLM 调用失败、Tool 执行异常的 e2e 恢复测试
- **依据**: CLAUDE.md TDD 规则要求先写验收测试
- **方案**: 添加 `ErrorRecoveryAcceptanceTest`（LLM 失败 → 重试 → 降级 → 正确错误事件）
- **工作量**: M (1-2d)

### 35. [Test] 并发安全测试
- **现状**: 无 concurrent execute() + interrupt() 测试
- **方案**: 添加 `ConcurrencyStressTest`，多线程模拟并发 interrupt/resume/spawn
- **工作量**: M (1-2d)

### 36. [Test] 长运行内存泄漏测试
- **现状**: 无 activeRuns 累积、subscription 泄漏的测试
- **方案**: 添加 `MemoryLeakTest`，循环 1000 次 session，断言 activeRuns size 稳定
- **工作量**: M (1d)

---

## P3 — Nice to Have (远期改进)

### 37. [Orchestrator] A2A Protocol Agent Cards
- **依据**: Google A2A Protocol v0.3 (Linux Foundation) — JSON 格式的 agent 能力发现
- **方案**: AgentProfile 支持导出 A2A Agent Card，用于 agent 路由决策
- **工作量**: M (2-3d)

### 38. [Orchestrator] Git Worktree 子代理隔离
- **依据**: Claude Code 使用 `isolation: worktree` 为每个子代理创建独立 git worktree
- **方案**: SubagentRuntime spawn 时可选创建 worktree，子代理在隔离副本上工作
- **工作量**: L (3-5d)

### 39. [Resilience] 持久化 Checkpoint (Event Sourcing)
- **现状**: InterruptibleRun 状态纯内存，崩溃丢失
- **依据**: LangGraph 自动 checkpoint 每个 super-step；OpenAI 在 Temporal 上运行 Codex
- **方案**: 持久化 StreamEvent 序列到 SQLite/PostgreSQL，支持 crash recovery 和 time-travel debugging
- **工作量**: XL (5-10d)

### 40. [Resilience] StateReducer 组合式状态管理
- **依据**: LangGraph 的 reducer-driven state — 每个状态字段有 merge 函数
- **方案**:
  ```java
  @FunctionalInterface
  public interface StateReducer<T> { T reduce(T current, T update); }
  ```
  支持 append、increment、deep-merge 等组合策略
- **工作量**: L (3-5d)

### 41. [Prompt] 模板引擎与版本管理
- **现状**: System prompt 通过 StringBuilder 拼接，无模板变量替换，无版本跟踪
- **位置**: `AgentLoop.java:307-316`
- **方案**: 引入轻量模板引擎（Mustache/Handlebars），支持 `{{variable}}` 替换；prompt 版本化存储
- **工作量**: M (2-3d)

### 42. [Permission] ML 分类器辅助权限判断
- **依据**: Claude Code Auto Mode 使用 Sonnet 4.6 两阶段分类（快速单 token 过滤 + CoT 推理），reasoning-blind by design
- **方案**: 高级权限模式下，使用低成本模型对 tool call 进行安全分类
- **工作量**: L (5-7d)

### 43. [Memory] SubagentRuntime completedRuns LRU 优化
- **现状**: O(n) 单次淘汰，MAX_COMPLETED_CACHE=200
- **位置**: `SubagentRuntime.java:220-231`
- **方案**: 替换为 `LinkedHashMap(accessOrder=true)` 或 Caffeine Cache 实现 O(1) LRU
- **工作量**: S (1-2h)

### 44. [Debt] agent-sdk TODO 清理
- **现状**: 生产代码中存在 3 处 `// TODO` 表示功能未实现
- **位置**: `AgentBuilder.java:201`, `DefaultAgent.java:76,232`
- **方案**: 实现或删除这些 placeholder
- **工作量**: M (1-2d)

### 45. [Debt] 废弃代码清理
- **现状**: `mock-mode`, `InstructionRegistry (@Deprecated)`, `Skill loading (@Deprecated)` 等残留
- **方案**: 删除所有 @Deprecated 标注的类和未使用的配置
- **工作量**: S (3-4h)

---

## Priority / Impact Matrix

```
Impact ▲
       │
  HIGH │  [5]Context5层  [9]流式Tool  [14]OTel   [10]Guardrail
       │  [8]ToolSearch  [12]WaitAll   [16]CB     [1-4]ThreadSafe
       │
  MED  │  [25]BATS       [27]Vector   [13]Handoff [18]ErrorClass
       │  [6]IterSummary  [28]MemTool  [15]Metrics [29]2LayerMem
       │
  LOW  │  [37]A2A        [39]Checkpoint [42]MLPerm [40]Reducer
       │  [38]Worktree   [41]Template   [43]LRU   [44-45]Debt
       │
       └──────────────────────────────────────────────────► Effort
            S(hours)      M(1-3d)      L(3-5d)    XL(5-10d)
```

## Recommended Execution Order (Sprint Plan)

| Sprint | Items | Theme | Est. |
|--------|-------|-------|------|
| Sprint 1 | #1-4, #17, #19, #22-23, #31, #33 | P0 修复 + 低挂果实 | 3-4d |
| Sprint 2 | #5, #7, #8, #11 | Context 管道 + Tool 发现 | 5-7d |
| Sprint 3 | #10, #16, #18, #20 | 安全 + 错误恢复 | 5-7d |
| Sprint 4 | #9, #12, #13 | 流式执行 + 多代理增强 | 5-7d |
| Sprint 5 | #14, #15, #25, #26 | 可观测性 + 成本管理 | 7-10d |
| Sprint 6 | #27-29, #30, #34-36 | Memory + Config + Testing | 7-10d |
| Backlog | #6, #24, #32, #37-45 | 远期优化 | — |

---

## References

- [Dive into Claude Code (arxiv 2604.14228)](https://arxiv.org/abs/2604.14228) — 1,900 文件、512K 行源码分析
- [Claude Code Context Compaction 5-Layer Cascade](https://finisky.github.io/en/claude-code-context-compaction/)
- [Claude Code Auto Mode Permission Classifier](https://www.anthropic.com/engineering/claude-code-auto-mode)
- [OpenClaw Architecture](https://github.com/openclaw/openclaw/blob/main/docs/concepts/architecture.md)
- [Spring AI Dynamic Tool Discovery](https://spring.io/blog/2025/12/11/spring-ai-tool-search-tools-tzolov/) — 34-64% token 节约
- [Spring AI AutoMemoryTools](https://spring.io/blog/2026/04/07/spring-ai-agentic-patterns-6-memory-tools/)
- [Anthropic: Effective Context Engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)
- [OpenAI Agents SDK Guardrails](https://openai.github.io/openai-agents-python/guardrails/)
- [Semantic Kernel Agent Orchestration (5 modes)](https://learn.microsoft.com/en-us/semantic-kernel/frameworks/agent/agent-orchestration/)
- [BATS: Budget-Aware Tool Selection (2025.11)](https://www.mindstudio.ai/blog/ai-agent-token-budget-management-claude-code)
- [Microsoft Agent Governance Toolkit (2026.04)](https://opensource.microsoft.com/blog/2026/04/02/introducing-the-agent-governance-toolkit-open-source-runtime-security-for-ai-agents/)
- [A2A Protocol v0.3 (Google/Linux Foundation)](https://a2a-protocol.org/latest/)
- [Factory.ai: Evaluating Context Compression](https://factory.ai/news/evaluating-compression)
- [LangGraph Multi-Agent Orchestration](https://latenode.com/blog/ai-frameworks-technical-infrastructure/langgraph-multi-agent-orchestration/)
- [OpenTelemetry AI Agent Observability](https://opentelemetry.io/blog/2025/ai-agent-observability/)
