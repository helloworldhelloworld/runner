# Architecture Optimization Analysis — 2026-04-14

> 基于 OpenClaw 架构设计、Claude Code 泄露源码分析、以及 2025-2026 开源社区最佳实践，对 runner 项目现有架构进行全面审视。

---

## 一、对标分析：runner vs OpenClaw vs Claude Code vs 社区最佳实践

### 1.1 已对齐的优势（无需调整）

| 维度 | runner 现状 | 行业对标 | 评价 |
|------|------------|---------|------|
| Agent Loop | ToolCallingLoop (TAOR reactive loop + CancellationToken) | OpenClaw 7-stage loop / Claude Code queryLoop async generator | 完全对齐 |
| 流式架构 | `Flux<StreamEvent>` + 18种 EventType 枚举 + factory methods | Spring AI `Flux<ChatResponse>` / OpenClaw WebSocket events | **领先** — 比多数框架的 string-based events 更健壮 |
| 多 Agent 编排 | 4层架构 (Profile→Registry→Orchestrator→Runtime) | OpenClaw channel/brain/body 3层 / Claude Code lead+subagents | 完全对齐 |
| Tool 权限隔离 | ScopedToolRegistry (deny > allow > all) | OpenClaw 三级权限 / Claude Code per-tool deny/ask/allow | 完全对齐 |
| Session Key 命名空间 | `agent:<agentId>:main:<sessionId>` / `subagent:<uuid>` | OpenClaw 同样的命名空间方案 | 完全对齐 |
| Memory 隔离 | MemoryProvider 按 sessionKey 隔离 | 行业共识：不同 agent/session 永远不可见对方历史 | 完全对齐 |
| 中断/恢复 | InterruptibleRun + CancellationToken + 上下文保存 | Claude Code 无此功能 / OpenClaw 有限支持 | **领先** — 全双工中断恢复是差异化特性 |
| TDD 纪律 | Outside-in 先行 + 146+ 测试 | 行业正在追赶这一实践 | **领先** |
| MCP 集成 | McpToolWrapper + ProgressRouter + LoggingRouter | MCP 已成为 2026 工具标准 | 完全对齐 |
| 多源 Tool 架构 | 5种 ToolSourceProvider (SPI/Manual/Plugin/MCP/CLI) | OpenClaw PI 仅4种内建工具 + 社区贡献 | 完全对齐 |

### 1.2 待优化差距

以下是与行业最佳实践的差距，按优先级排列：

---

## 二、TODO 清单（按优先级排序）

### P0 — 架构级缺失（影响生产可靠性）

#### TODO-001: Context Compression（上下文压缩机制）
- **差距来源**: Claude Code 泄露源码揭示了三层压缩策略
- **现状**: runner 无上下文压缩机制，长对话将直接撞 context window 上限
- **目标**: 实现分层压缩策略
  - **MicroCompact**: 零 API 调用，直接裁剪旧 tool output（超过 N 轮的 TOOL_RESULT 只保留摘要）
  - **AutoCompact**: 接近上下文上限时触发，LLM 生成结构化摘要（预留 13K token buffer，生成 ≤20K token 摘要）
  - **FullCompact**: 压缩全部历史，重新注入最近访问的文件/active plans/skill schemas
- **执行优先级**: snip drops → microcompact masks → context collapse LLM-summarizes → autocompact
- **涉及模块**: `agent-kernel` (ToolCallingLoop, AgentLoop, MemoryProvider)
- **参考**: Claude Code `queryLoop` 在每次迭代前检查 token 用量，渐进式触发压缩
- **预估复杂度**: 高
- **建议**: 先实现 MicroCompact（纯本地裁剪），再逐步添加 LLM 驱动的压缩层

#### TODO-002: LLM 调用弹性（Circuit Breaker + Retry + Fallback）
- **差距来源**: 社区最佳实践（Resilience4j 2.2.0）
- **现状**: LLMProvider 调用无 circuit breaker，无指数退避重试，无 fallback 模型切换
- **目标**: 
  - 为 `LLMProvider.completeStreamReactive()` 添加 Resilience4j Circuit Breaker（Closed/Open/Half-Open 三态）
  - 指数退避 + jitter 重试（LLM API 调用失败率 1-5%）
  - Provider 级 fallback（如 Claude → OpenRouter fallback）
  - MCP 远程工具调用同样需要 circuit breaker
- **涉及模块**: `agent-kernel` (LLMProvider, ToolExecutor), `agent-mcp`
- **预估复杂度**: 中
- **依赖**: 添加 `resilience4j-reactor` Maven 依赖

#### TODO-003: Checkpointing / 状态持久化
- **差距来源**: LangGraph Pregel/BSP 执行模型
- **现状**: InterruptibleRun 状态纯内存，进程崩溃即丢失全部对话上下文
- **目标**: 
  - Agent loop 每次迭代后持久化 checkpoint（messages list + tool results + accumulated state）
  - 支持从 checkpoint 恢复（crash recovery）
  - 可选：支持 time-travel debugging（回溯到任意 checkpoint 重放）
- **涉及模块**: `agent-kernel` (InterruptibleRun, AgentLoop, ToolCallingLoop)
- **参考**: LangGraph 在每个 super-step 后自动 checkpoint，支持 SQLite/Postgres 后端
- **预估复杂度**: 高

---

### P1 — 功能增强（提升竞争力）

#### TODO-004: Progressive Tool/Skill Loading（渐进式工具加载）
- **差距来源**: Google ADK + OpenClaw PI 设计哲学
- **现状**: 所有注册 tool 的 schema 一次性全量注入 LLM context，100+ tool 时 context window 占用巨大
- **目标**: 
  - Tool 分层：core tools（始终加载）+ domain tools（按需加载）
  - PromptEngine 仅注入 tool 元信息摘要（name + 一句话描述），而非完整 schema
  - LLM 请求具体 tool 时再加载完整 schema（类似 Claude Code 的 ToolSearch deferred tools）
  - Google ADK 实践：10个 skill 从 10,000 token 降到 ~1,000 token（90% 缩减）
- **涉及模块**: `agent-kernel` (ToolRegistry, PromptEngine, ToolCallingLoop)
- **预估复杂度**: 中

#### TODO-005: Graph Memory（图记忆层）
- **差距来源**: 2026 Memory 架构演进（Mem0, 社区共识）
- **现状**: 三层 Memory（Session/Ephemeral/Durable）+ BM25+vector 混合检索
- **缺失**: 无图记忆 — 事实之间的关系无法建模（如"用户A提到的项目X依赖服务Y"）
- **目标**: 
  - 在现有 MemoryProvider 接口上扩展 `GraphMemoryProvider`
  - 支持 entity-relation-entity 三元组存储与查询
  - 检索策略：structured lookup first → vector search as fallback
- **涉及模块**: `kernel-memory`
- **参考**: 2026 行业趋势 — 图记忆从实验阶段进入生产就绪
- **预估复杂度**: 高

#### TODO-006: A2A Protocol 支持（Agent-to-Agent 互操作）
- **差距来源**: Google A2A Protocol (v0.3), Microsoft Agent Framework 1.0
- **现状**: runner 的多 Agent 编排是内部闭环，无法与外部 Agent 框架互通
- **目标**: 
  - 实现 Agent Card (`/.well-known/agent.json`) 用于能力发布
  - 支持 A2A JSON-RPC 2.0 over HTTP 的 request-response 模式
  - 支持 SSE streaming 和 Task Object 结构化工作单元
- **涉及模块**: `agent-web` (新 controller), `agent-kernel` (AgentProfile 扩展)
- **参考**: MCP 连接 agent-to-tool，A2A 连接 agent-to-agent，两者互补
- **预估复杂度**: 高

#### TODO-007: Agent Handoff 模式
- **差距来源**: OpenAI Agents SDK handoff 设计
- **现状**: 多 Agent 通过 SubagentRuntime 异步 spawn，但不支持 conversation handoff（将对话控制权完整移交另一个 Agent）
- **目标**: 
  - 实现 Handoff 作为特殊 Tool 类型（类似 OpenAI Agents SDK）
  - 对话上下文（messages）随 handoff 一起迁移
  - Input guardrails 仅在首个 agent 应用，output guardrails 仅在产出最终响应的 agent 应用
- **涉及模块**: `agent-kernel` (Orchestrator, AgentRouter)
- **预估复杂度**: 中

---

### P2 — 工程质量提升

#### TODO-008: InterruptibleRun 线程安全强化
- **差距来源**: 内部架构审查
- **现状**: InterruptibleRun 使用 CopyOnWriteArrayList 管理 messages，但复合操作（读-改-写）存在竞态风险
- **目标**: 
  - 审查所有 InterruptibleRun 的状态访问路径
  - 对 accumulatedText + messages + phase 的复合操作加锁或使用 actor 模型
  - 添加并发压力测试（模拟快速连续中断/恢复）
- **涉及模块**: `agent-kernel` (InterruptibleRun)
- **预估复杂度**: 中

#### TODO-009: MCP ProgressRouter 超时清理
- **差距来源**: 内部架构审查
- **现状**: McpToolWrapper 依赖 doOnTerminate() 清理 router 注册，但工具异常退出时可能遗漏
- **目标**: 
  - 为 ProgressNotificationRouter / LoggingNotificationRouter 添加 timeout-based cleanup
  - 工具执行超过 N 秒后自动 complete() router，防止通知累积
  - 添加 router 泄漏检测指标
- **涉及模块**: `agent-mcp`
- **预估复杂度**: 低

#### TODO-010: ToolCallingLoop 递归深度保护
- **差距来源**: 内部架构审查
- **现状**: `executeReactiveLoop()` 递归调用 iteration+1，依赖 maxIterations 限制（默认10），但无硬性栈深度保护
- **目标**: 
  - 将递归改为迭代（使用 `expand()` 或 `repeatWhen()` 操作符）
  - 或添加显式栈深度检查 + 友好错误消息
  - 同时为 SubagentRuntime 的深层 spawn 链添加相同保护
- **涉及模块**: `agent-kernel` (ToolCallingLoop, SubagentRuntime)
- **预估复杂度**: 低

#### TODO-011: Cost Tracking 强制化
- **差距来源**: Claude Code 源码分析（每次 LLM 调用都跟踪 token/cost）
- **现状**: ToolCallingLoop.Builder 接受 CostTracker 但非必需，实际部署中可能遗漏
- **目标**: 
  - 将 CostTracker 设为 AgentLoop 构建的必需组件（至少提供 NoOpCostTracker）
  - 在 Gateway 层暴露 per-request cost metrics
  - 添加 per-agent cost budget 限制（超限自动停止）
- **涉及模块**: `agent-kernel` (ToolCallingLoop, AgentLoop, Gateway)
- **预估复杂度**: 低

#### TODO-012: Session Key 命名空间长度治理
- **差距来源**: 内部架构审查
- **现状**: 子 agent spawn 子 agent 时 key 无限增长：`agent:A:main:s:subagent:uuid:subagent:uuid:...`
- **目标**: 
  - 为 session key 设最大层级限制（与 maxSpawnDepth 联动）
  - 超深 key 使用 hash 缩短策略
  - 添加监控指标：活跃 session key 数量 + 最大深度
- **涉及模块**: `agent-kernel` (SubagentRuntime)
- **预估复杂度**: 低

---

### P3 — 前瞻性能力储备

#### TODO-013: Workflow / Pipeline Engine（工作流引擎）
- **差距来源**: OpenClaw Lobster + CrewAI Flows + Google ADK SequentialAgent/ParallelAgent
- **现状**: runner 的编排是隐式的（Orchestrator routing），无声明式工作流定义
- **目标**: 
  - 支持 YAML 定义 agent pipeline（sequence / parallel / conditional）
  - 类似 OpenClaw Lobster 的 typed workflow shell
  - 支持 human-in-the-loop 审批节点（pause + resume token）
- **预估复杂度**: 高（新模块）

#### TODO-014: Agent Evaluation / Benchmark Pipeline
- **差距来源**: LangChain 2026 State of AI Agents Report（32% 组织将质量视为 agent 生产化的首要障碍）
- **现状**: 仅有功能测试，无 agent 质量评估框架
- **目标**: 
  - 建立 evaluation dataset（input → expected tool calls → expected output）
  - CI 中运行 nightly eval（mock LLM 为主 + 定期 real LLM）
  - 指标：answer quality, tool usage correctness, cost per task, latency
- **预估复杂度**: 中（新测试模块）

#### TODO-015: Sandbox 隔离增强
- **差距来源**: 2026 安全最佳实践（OWASP Agentic AI Top 10）
- **现状**: Tool 执行在主进程中，无沙箱隔离
- **目标**: 
  - 对 CLI tools / bash 类工具添加沙箱执行（容器/microVM/seccomp）
  - 限制文件系统访问范围
  - 网络访问白名单
- **预估复杂度**: 高

#### TODO-016: Plugin Lifecycle hooks（插件生命周期钩子）
- **差距来源**: OpenClaw 的 16 种 plugin hooks
- **现状**: 有 AgentObserver 但 hook 点有限（5个：start/request/response/complete/error）
- **目标**: 扩展 hook 点覆盖完整生命周期
  - `before_tool_call` / `after_tool_call` — 工具调用前后拦截
  - `before_compaction` / `after_compaction` — 上下文压缩前后
  - `session_start` / `session_end` — 会话生命周期
  - `message_received` / `message_sending` — 消息收发拦截
- **涉及模块**: `agent-kernel` (AgentObserver 扩展或新 HookRegistry)
- **预估复杂度**: 中

---

## 三、优先级矩阵

```
              Impact (高 →)
              ┌─────────────────────────────────┐
              │                                 │
        高    │  TODO-001 Context Compression   │
              │  TODO-002 Circuit Breaker       │
              │  TODO-003 Checkpointing         │
   Urgency    │                                 │
   (高 →)     ├─────────────────────────────────┤
              │  TODO-004 Progressive Loading   │
        中    │  TODO-007 Agent Handoff         │
              │  TODO-008 Thread Safety         │
              │  TODO-011 Cost Tracking         │
              ├─────────────────────────────────┤
              │  TODO-005 Graph Memory          │
        低    │  TODO-006 A2A Protocol          │
              │  TODO-013 Workflow Engine        │
              │  TODO-014 Eval Pipeline          │
              │  TODO-015 Sandbox               │
              └─────────────────────────────────┘
```

## 四、建议执行路径

```
Sprint 1 (当务之急):
  TODO-001 MicroCompact 层 → TODO-002 Resilience4j 集成 → TODO-009 Router 超时
  
Sprint 2 (稳定性):
  TODO-008 线程安全 → TODO-010 递归改迭代 → TODO-011 Cost Tracking → TODO-012 Key 治理

Sprint 3 (功能增强):
  TODO-001 AutoCompact/FullCompact → TODO-004 渐进式加载 → TODO-003 Checkpointing

Sprint 4 (前瞻储备):
  TODO-007 Handoff → TODO-016 Plugin Hooks → TODO-005 Graph Memory

Sprint 5+ (长期):
  TODO-006 A2A → TODO-013 Workflow → TODO-014 Eval → TODO-015 Sandbox
```

---

## 五、参考来源

### OpenClaw
- [OpenClaw GitHub](https://github.com/openclaw/openclaw) — 100K+ stars
- OpenClaw 三层架构: channel / brain / body
- PI Toolkit: 4 core tools (Read/Write/Edit/Bash) 极简主义
- Lobster: YAML 声明式 workflow engine
- 16 种 plugin lifecycle hooks
- 三级权限模型 (base profiles + allow list + deny list)

### Claude Code (泄露源码分析)
- 512,000 行 TypeScript (2026-03-31 npm source map 泄露)
- queryLoop: async generator + while(true) + 7 yield points
- 三层 Context Compression: MicroCompact → AutoCompact → FullCompact
- Query Engine: ~46,000 行，处理所有 LLM 交互
- Tool System: ~29,000 行，~19 permission-gated tools
- Mailbox pattern: subagent 高风险操作路由到 coordinator 审批

### 社区最佳实践
- **LangGraph**: Pregel/BSP 执行模型 + checkpoint 持久化 + time-travel debugging
- **CrewAI**: Coordinator-Worker + Collaborative Peer Group + Flows 事件驱动层
- **Google ADK**: Progressive disclosure + AutoFlow + SequentialAgent/ParallelAgent
- **OpenAI Agents SDK**: Handoff as special tool type + input/output guardrails 分离
- **Microsoft Agent Framework 1.0**: Actor model + A2A + MCP 统一互操作
- **Resilience4j 2.2.0**: Circuit breaker + retry + bulkhead (Java 标准)
- **MCP 2026**: Streamable HTTP + OAuth 2.1 + 已捐赠 Linux Foundation AAIF
- **A2A v0.3**: Agent Card + Task Object + gRPC streaming
- **OWASP Agentic AI Top 10**: Least Privilege / Least Agency 原则
