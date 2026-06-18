# 架构优化分析 — 2026-06-18

> **研究基础**：OpenClaw (379K star, TypeScript 自主 agent 平台)、Claude Code (泄露源码 512K 行 TypeScript)、
> 以及 LangGraph / CrewAI / AutoGen / Spring AI / LangChain4j / Strands / Google ADK / Smolagents / Pydantic AI
> 等开源社区最佳实践。结合 runner 现有架构的对标分析。

---

## 一、对标总结：runner vs 业界

| 维度 | runner 现状 | Claude Code | OpenClaw | 业界最佳实践 | 差距评估 |
|---|---|---|---|---|---|
| Agent Loop | `while` 循环 + Flux 流 | AsyncGenerator yield 事件 | 委托 pi-agent-core | Generator/Event-driven | **基本对齐** |
| 工具系统 | Tool 接口 + 5 种 SourceProvider | ~40 工具 + MCP 原生 | Gateway 工具分发 + MCP | 统一 Tool 接口 + MCP | **对齐** |
| Context 管理 | Snip + Micro 两层压缩 | 五层级联压缩管线 | 压缩前静默持久化 | 多层渐进式压缩 | **有差距** |
| 权限系统 | ToolPolicy chain (ALLOW/DENY/ABSTAIN) | 6 级权限模式 + ML 分类器 | 默认全开（安全问题） | 分级 + defense-in-depth | **基本对齐，可加强** |
| 多 Agent | 4 层 Orchestrator + SubagentRuntime | hub-spoke 单层 subagent | Gateway 路由 + sub-agent | 多种编排模式 | **对齐** |
| 流式 | Flux<StreamEvent> 全链路 | SSE + for-await | WebSocket bidirectional | 全链路流式 | **对齐** |
| 记忆系统 | 3 层 MemoryProvider | 3 层 (in-context / file / CLAUDE.md) | Markdown 文件 + SQLite 向量 | 短期/长期/实体/情节多层 | **有差距** |
| 可观测性 | Tracer 骨架，无导出 | 内置 tracing + cost tracking | Gateway 日志 | OTel + 分布式 tracing | **明显差距** |
| LLM Provider SPI | 硬编码 switch | 单一 Anthropic | 可插拔 (Claude/GPT/DeepSeek) | ServiceLoader / DI | **有差距** |
| 检查点/恢复 | 无 | append-only JSONL | JSONL + JSON | 每节点检查点 (LangGraph) | **缺失** |
| 主动式 Agent | 无 | 无 (用户触发) | Heartbeat + Cron | 定时/事件驱动 | **按需** |
| Prompt 缓存 | 无 | 三层缓存 (静态/项目/会话) | 无特殊处理 | 前缀缓存优化 | **有差距** |

---

## 二、TODO 清单

### P0 — 核心链路补强（影响正确性和成本）

#### TODO-001: Context 压缩管线从 2 层升级到 4-5 层

**现状**：仅有 `SnipCompactor`（删旧工具结果）+ `MicroCompactor`（压缩大结果），`AutoCompactor`（调 LLM 总结）已定义但推迟。

**参照**：
- Claude Code 五层级联：Budget Reduction → Snip → Microcompact → Context Collapse → Auto-Compact，**最便宜的先跑**
- OpenClaw：压缩前执行"静默 agentic turn"将关键状态持久化到磁盘，**防止压缩擦除重要上下文**
- LangGraph：每节点 checkpoint，压缩时有可回溯的源

**建议**：
1. 实现 `BudgetReductionCompactor`：针对超限的单个工具输出做截断（最轻量）
2. 在 Snip 和 Micro 之间加 `CacheBreakCompactor`：检测系统提示变更导致的缓存失效，将易变内容后移保护缓存前缀
3. 实现 `ContextCollapseCompactor`：分段摘要最早的实现讨论
4. 实现 `AutoCompactor`：调 LLM 生成全局摘要，保留不超过 20K token
5. 在 Auto-Compact 前加 pre-compaction hook：持久化关键状态到 MemoryProvider（学习 OpenClaw 的"静默刷盘"）
6. 触发阈值：~70% 启动轻量压缩，~85% 启动摘要压缩，~92% 触发 Auto-Compact

**涉及模块**：`agent-kernel` (core/compact 包)
**验收**：outside-in 测试证明 5 层按序执行；超长对话不 OOM 且关键上下文不丢

---

#### TODO-002: LLMProvider SPI 解耦 — ServiceLoader 替代硬编码

**现状**：`LLMProvider` 选择通过硬编码 switch 语句；新增 provider 需改核心代码。

**参照**：
- Spring AI：每个 provider 是一个 `@ConditionalOnProperty` Bean，通过 DI 自动注册
- LangChain4j：provider 通过 ServiceLoader 发现
- OpenClaw：model-agnostic，provider 作为独立包接入
- Semantic Kernel：AI Service Layer 通过 connector 抽象

**建议**：
1. 定义 `LLMProviderSpi` 接口（带 `supports(String modelId)` 方法）
2. 使用 Java `ServiceLoader<LLMProviderSpi>` 自动发现
3. `LLMProviderFactory` 变为 registry，遍历 SPI 实现匹配 modelId
4. 每个 provider（Claude、OpenRouter）提供 `META-INF/services` 文件
5. 支持优先级排序和 failover chain

**涉及模块**：`agent-kernel` (llm 包)
**验收**：删除 switch 语句；新增 provider 只需加 jar + services 文件，不改核心代码

---

#### TODO-003: Prompt 缓存优化 — 三层前缀分离

**现状**：系统提示每次完整组装，无缓存分离策略。

**参照**：
- Claude Code：系统提示在 `SYSTEM_PROMPT_DYNAMIC_BOUNDARY` 处分割
  - 分界线前：指令 + 工具定义（全局缓存，跨所有用户复用）
  - 分界线后：会话级内容（CLAUDE.md、git 状态、日期）
- Claude API：前缀精确匹配，变更点之后全部重算；缓存 TTL 5min/1h

**建议**：
1. `PromptEngine` 组装时按变更频率排序：
   - Layer 1（稳定）：核心指令 + 工具 schema → 几乎不变，可长期缓存
   - Layer 2（会话级）：AgentProfile systemPrompt + durable memory → 会话内稳定
   - Layer 3（动态）：ephemeral memory snippets + 当前上下文 → 每轮变
2. 在 Layer 1/2 之间、Layer 2/3 之间插入 cache_control breakpoint
3. 确保 Layer 1 内容排序稳定（工具定义按名称排序）
4. 统计缓存命中率，通过 CostTracker 体现节省

**涉及模块**：`agent-kernel` (prompt 包、llm 包)
**验收**：连续多轮对话中 Layer 1 的 token 不重复计费

---

### P1 — 记忆与状态管理（影响长期体验）

#### TODO-004: 记忆系统从 3 层扩展到 5 层

**现状**：MemoryProvider 有 Session（会话历史）、Ephemeral（每日 BM25 日志）、Durable（长期知识）三层。

**参照**：
- Claude Code 三层：in-context / 外部文件索引（memory.md 指针网络）/ CLAUDE.md 静态配置
- CrewAI 认知记忆：短期/长期/实体/情境四层 + LLM 分析内容自动推断范围和重要性
- LangGraph：跨线程记忆 + 语义搜索
- OpenClaw：Markdown 文件 = source of truth，LLM context = cache

**建议**：
1. 新增 **Entity Memory 层**：自动提取和记忆对话中出现的实体（人名、项目名、概念），支持跨会话积累
2. 新增 **Episodic Memory 层**：结构化记录过往交互（时间戳、动作、结果），支持单次学习
3. 将 Durable Memory 升级为**指针网络**模式：主索引文件指向领域特定的记忆文件，按需加载而非全部注入
4. 增加记忆的**重要性评分 + 时间衰减**：复合评分 = 语义相似度 × 时近度 × 重要性
5. 增加**记忆作用域树**：按 `/agent/task/subtask` 路径隔离，精确检索

**涉及模块**：`kernel-memory`
**验收**：跨会话记忆提取准确；实体记忆跨对话可用；长期记忆不线性增长 context 占用

---

#### TODO-005: 会话检查点与恢复

**现状**：无检查点机制；进程重启后会话状态丢失。

**参照**：
- LangGraph：每节点自动 checkpoint，支持 "Time Travel"（回退到任意节点、修改上下文、分叉执行）
- Claude Code：append-only JSONL 记录所有对话，无重写、无锁、无损坏风险
- OpenClaw：JSONL + JSON 持久化会话

**建议**：
1. 定义 `Checkpoint` 接口：序列化 AgentLoop 当前状态（消息历史、工具结果、CostTracker 状态）
2. 实现 `JournalCheckpointer`：append-only JSONL 写入，每个 ToolCallingLoop 迭代后记录
3. `AgentLoop.resume(checkpointId)` 从检查点恢复执行
4. 开发环境支持 `MemorySaver`，生产环境支持 `SqliteCheckpointer`
5. 可选：支持检查点分叉（同一点生成多个执行路径用于评估）

**涉及模块**：`agent-kernel` (core 包)、`kernel-memory`
**验收**：kill -9 后重启能从最近检查点恢复；无数据丢失

---

#### TODO-006: ToolSourceProvider 生命周期激活

**现状**：`start()/stop()` 方法已声明但未被调用——ToolSourceProvider 的生命周期管理是死代码。

**参照**：
- OpenClaw Plugin Hooks：完整生命周期 `session_start/session_end/before_agent_start/agent_end`
- Spring 惯例：`@PostConstruct/@PreDestroy` 管理 Bean 生命周期

**建议**：
1. 在 Spring 配置中为所有 `ToolSourceProvider` 添加 `@PostConstruct` 调用 `start()`
2. 在 `@PreDestroy` 调用 `stop()`
3. 支持优雅关闭：stop 时等待正在执行的工具完成
4. 为 MCP 类型的 provider 支持重连和健康检查

**涉及模块**：`agent-web` (config)、`agent-kernel` (agent 包)
**验收**：应用启动时 provider 初始化日志可见；关闭时 MCP 连接正确断开

---

### P2 — 可观测性与运维（影响生产就绪度）

#### TODO-007: OpenTelemetry 导出 — 从骨架到可用

**现状**：`Tracer` + `SpanContext` 骨架存在，但无导出实现。`StreamEvent.TRACE` 存在但不对接标准 tracing 后端。

**参照**：
- Claude Code：内置 tracing + cost tracking，所有工具调用有 correlation ID
- Strands (AWS)：原生 CloudWatch + OTEL 集成
- 业界共识 (2025)：分布式 tracing + token 核算 + 自动化 evals = baseline

**建议**：
1. 实现 `OTelTracerBridge`：将 `StreamEvent.TRACE` 映射为 OTel Span
2. 每个 AgentLoop 迭代 = 一个 Span；工具调用 = 子 Span
3. Span attributes 包含：token 消耗、模型 ID、工具名、耗时、是否缓存命中
4. CostTracker 数据作为 Span metrics 导出
5. 配置 OTLP exporter（Jaeger/Zipkin/Grafana Tempo）
6. 可选：关键指标 Prometheus exporter（p99 延迟、token/request、tool 调用成功率）

**涉及模块**：`agent-kernel` (trace 包)、`agent-web`
**验收**：Jaeger 中可看到完整的 agent 调用链路，包含每步 token 消耗

---

#### TODO-008: 结构化错误恢复级联

**现状**：错误处理分散，无统一的渐进恢复策略。

**参照**：
- Claude Code 多策略恢复级联：
  1. 指数退避重试（500/529/超时/429/断连，最多 10 次）
  2. 输出 token 升级（8K → 64K，一次性）
  3. prompt-too-long 时先排空压缩再尝试激进压缩
  4. 流式失败回退到非流式
  5. 收益递减检测（防止无限恢复循环）
- 业界模式：Try-Rewrite-Retry（将错误信息注入对话，让 LLM 自我修正）

**建议**：
1. 定义 `RecoveryStrategy` SPI：`canHandle(error)` + `recover(context, error)`
2. 实现策略链：`RetryWithBackoff` → `TokenBudgetEscalation` → `ContextCompactionRecovery` → `StreamFallback`
3. 每个策略记录尝试次数，防止无限循环（diminishing returns detection）
4. 工具执行失败时自动注入错误信息回 LLM（Try-Rewrite-Retry 模式）
5. 记录所有恢复事件到 trace

**涉及模块**：`agent-kernel` (core 包)
**验收**：模拟 API 500 → 自动重试成功；模拟 context overflow → 自动压缩后继续

---

### P3 — 扩展性与开发体验（影响生态成长）

#### TODO-009: AgentObserver Hook 体系完善

**现状**：`AgentObserver` 定义了 `onAgentStart/onLLMRequest/onLLMResponse/onError/onPreToolUse/onPostToolUse`，但接入不完整。

**参照**：
- Claude Code Hooks：PreToolUse / PostToolUse / UserPromptSubmit / Stop / SubagentStart/Stop / PreCompact / InstructionsLoaded — hooks 在应用进程跑，不消耗 context
- OpenClaw Plugin Hooks：session_start / session_end / before_agent_start / agent_end / before_compaction / after_compaction / heartbeat_prompt_contribution
- CrewAI：before/after tool call hooks + before/after LLM call hooks + task guardrails

**建议**：
1. 补充 hook 点：`onSessionStart` / `onSessionEnd` / `onPreCompact` / `onPostCompact` / `onSubagentStart` / `onSubagentStop`
2. Hook 执行不消耗 LLM context（在应用进程级别运行）
3. Hook 返回值可以短路流程（如 `onPreToolUse` 返回 DENY 阻止工具执行）
4. 支持通过配置文件注册 shell 命令作为 hook（学习 Claude Code）
5. 完善所有已定义 hook 的接线（确保真正被 AgentLoop/Orchestrator 调用）

**涉及模块**：`agent-kernel` (agent 包、core 包、orchestrator 包)
**验收**：outside-in 测试证明每个 hook 在正确时机被调用；hook 可以阻止工具执行

---

#### TODO-010: 动态工具加载 — 延迟加载 + 语义检索

**现状**：所有注册工具的 schema 在每次 LLM 调用时全量注入 system prompt。

**参照**：
- Claude Code：工具定义默认延迟加载，通过 `ToolSearch` 按需加载 schema，节省 context
- Google ADK `SkillToolset`：按需加载上下文，而非一次性全部注入
- 业界模式：通过向量相似度检索与当前任务相关的工具子集

**建议**：
1. 工具注册时分为 `core`（始终加载）和 `deferred`（按需加载）两类
2. 实现 `ToolSearch` 内部工具：LLM 发现需要某类工具时，通过语义搜索加载
3. 维护工具描述的嵌入索引（可复用 kernel-memory 的向量能力）
4. 工具数量 > N（如 20）时自动启用延迟加载
5. 已使用的工具在本次对话中保持加载

**涉及模块**：`agent-kernel` (agent 包、prompt 包)
**验收**：40+ 工具注册时，首次 LLM 调用只包含 core 工具定义；ToolSearch 可按需加载其余工具

---

#### TODO-011: PromptEngine 条件组装 — 模式感知

**现状**：PromptEngine 支持 Skill 注册 + lazy loading，但缺少基于模式的条件段。

**参照**：
- Claude Code：~110+ 条指令按条件组装；约 50 个工具有条件决定是否注入 context
- Claude Code 模式：Plan / Auto / Default — 不同模式下工具和指令集不同
- Google ADK：Progressive Disclosure — SkillToolset 在检测到匹配任务模式时才注入详细指引

**建议**：
1. 定义 `PromptSection`：带 `condition(AgentContext) → boolean` 的条件化 prompt 片段
2. 支持模式切换：Plan（只读推理）/ Auto（自主执行）/ Learning（记录轨迹）
3. 每种模式定义不同的活跃 section 集合 + 可用工具集
4. Skill 的 lazy loading 条件化：检测到特定任务模式或关键词时才加载
5. Section 排序稳定以保护 prompt 缓存

**涉及模块**：`agent-kernel` (prompt 包)
**验收**：切换 Plan 模式后 LLM 不可调用写工具；切换回 Auto 后恢复全工具

---

#### TODO-012: 任务级 Guardrails

**现状**：ToolPolicy 处理工具级别的权限，但缺少任务级输出验证。

**参照**：
- CrewAI：Task Guardrails — 函数式（严格规则）或自然语言式（LLM 评估质量）
- Pydantic AI：每个返回值自动 schema 验证
- Claude Code：Auto 模式 ML 分类器评估"用户是否授权了这个具体动作"

**建议**：
1. 定义 `TaskGuardrail` 接口：`validate(TaskResult) → GuardrailResult(pass/fail + reason)`
2. 支持两种 guardrail 类型：
   - `SchemaGuardrail`：验证输出符合 JSON Schema / Java POJO
   - `LLMGuardrail`：自然语言描述的质量约束，调 LLM 评估
3. Guardrail 失败时将错误信息回注 agent，触发重试（最多 N 次）
4. Guardrail 可以附加在 SubagentRuntime 的 spawn 结果上

**涉及模块**：`agent-kernel` (core 包)
**验收**：子 agent 返回不符合 schema 的结果 → 自动重试 → 第二次返回正确结果

---

### P4 — 架构演进方向（中长期）

#### TODO-013: 多 Provider Failover + 降级

**现状**：单 provider 调用，无 failover 机制。

**参照**：
- OpenClaw：model-agnostic，可配置主/备 provider
- 业界模式：主 provider 不可用时自动切换备用 provider（如 Claude → OpenRouter → 本地模型）

**建议**：
1. `LLMProviderChain`：按优先级排列的 provider 列表
2. 主 provider 连续失败 N 次后自动切换到下一个
3. 降级策略：高级模型不可用时降级到能力稍弱的模型（如 Opus → Sonnet）
4. Circuit breaker 模式：半开/全开/关闭状态
5. 恢复后自动切回主 provider

**涉及模块**：`agent-kernel` (llm 包)

---

#### TODO-014: A2A (Agent-to-Agent) 协议支持

**现状**：多 agent 协作仅限本进程内的 Orchestrator + SubagentRuntime。

**参照**：
- Google A2A 协议 (April 2025)：水平 agent 间通信标准，Linux Foundation 治理，150+ 组织支持
- MCP 处理的是 agent→tool 垂直通信；A2A 处理 agent→agent 水平通信
- Spring AI：已集成 A2A 支持

**建议**：评估 A2A 协议适用性；若 runner 需要跨服务 agent 协作，优先实现 A2A 而非自定义 RPC

**涉及模块**：新模块 `agent-a2a`

---

#### TODO-015: Virtual Threads 混合模型

**现状**：全 Reactor 响应式，工具执行也在响应式线程上。

**参照**：
- Spring AI：同步 `.call()` + 响应式 `.stream()` 双模式
- AgentScope Java：Reactor 做流式，Virtual Threads 做阻塞 I/O
- Java 21+ Virtual Threads：每线程 ~1KB 栈，可 spawn 百万级

**建议**：
1. Agent Loop 流式路径保持 Reactor（LLM token streaming → Flux<StreamEvent>）
2. Tool execution 路径用 Virtual Thread executor：`Tool.execute()` 多为阻塞 I/O（HTTP、文件、shell），用虚拟线程避免阻塞响应式线程
3. 通过 `Mono.fromCallable(tool::execute).subscribeOn(Schedulers.fromExecutor(virtualThreadExecutor))` 桥接
4. 并行工具执行：多个 READ 级别工具可并行（学习 Claude Code：只读工具并发，写工具串行）

**涉及模块**：`agent-kernel` (core 包)

---

#### TODO-016: 工具执行并发模型优化

**现状**：工具执行看起来是串行的。

**参照**：
- Claude Code：Read-only 工具（Read/Glob/Grep）**并发**执行；Write 工具（Edit/Write/Bash）**串行**执行并在批次间传播上下文
- Claude Code：StreamingToolExecutor 在模型还在流式生成时就开始执行已解析的工具调用（重叠 I/O）

**建议**：
1. 利用 `Tool.riskLevel()` 区分：`SAFE` 级别工具可并发，`WRITE`/`SYSTEM` 级别串行
2. 流式工具执行：工具调用的 JSON 在 LLM 流中完整出现即可开始执行，不必等整个响应完成
3. Write 工具之间串行，但 Write 工具和后续的 Read 工具可以流水线

**涉及模块**：`agent-kernel` (core 包 — ToolExecutor)

---

## 三、从 OpenClaw 学到的独特模式（runner 缺失）

| OpenClaw 模式 | 描述 | runner 适用性 |
|---|---|---|
| **压缩前静默刷盘** | Auto-compact 前先跑一个隐式 agent turn 将关键状态写到磁盘 | 高 — 防止压缩丢关键上下文 |
| **文件记忆 = source of truth** | LLM context 是 cache，Markdown 文件是 truth | 中 — durable memory 已有类似思路，可强化 |
| **Heartbeat 主动唤醒** | 每 N 分钟唤醒，检查邮箱/日历/通知，无事不打扰 | 中 — Minion 场景有价值 |
| **Skill as text** | 技能是 Markdown 文件（进入 context），不是可执行代码 | 已有 — PromptEngine.Skill 基本对齐 |
| **ClawHub 技能市场** | 公共技能注册中心 + 向量搜索 + 社区贡献 | 低（暂不需要） |
| **单端口 HTTP+WS 复用** | Express 5 上同端口 serve REST + WebSocket | 已有 — agent-web 已实现 |

## 四、从 Claude Code 学到的独特模式（runner 缺失）

| Claude Code 模式 | 描述 | runner 适用性 |
|---|---|---|
| **五层压缩级联** | 从最便宜到最贵依次尝试 | 高 — 见 TODO-001 |
| **prompt 三层缓存** | 静态指令 / 项目配置 / 动态内容分层 | 高 — 见 TODO-003 |
| **延迟工具加载** | 工具 schema 按需加载节省 context | 高 — 见 TODO-010 |
| **Read-before-Edit 强制** | Edit 工具要求先 Read 过文件 | 中 — 提高编辑安全性 |
| **6 级权限模式** | plan → default → acceptEdits → auto → dontAsk → bypass | 中 — ToolPolicy 可扩展 |
| **ML 分类器审批** | Auto 模式下用独立模型评估"是否越权" | 低（需要额外模型推理） |
| **Anti-distillation** | 注入假工具定义防蒸馏 | 不适用 |
| **98.4% 是基础设施** | AI 决策逻辑仅 1.6%，其余是确定性基础设施 | 已对齐 — 正确的设计哲学 |

## 五、从社区最佳实践学到的模式

| 模式 | 来源 | 描述 | runner 适用性 |
|---|---|---|---|
| **Graph-based 执行** | LangGraph | 显式状态图控制执行流 | 低 — runner 的 while-loop 已足够 |
| **Checkpoint + Time Travel** | LangGraph | 每节点保存状态，支持回溯 | 中 — 见 TODO-005 |
| **Role/Goal/Backstory 三元组** | CrewAI | 结构化 agent 身份定义 | 中 — AgentProfile.systemPrompt 可扩展 |
| **任务 Guardrails** | CrewAI | 任务完成后自动验证输出 | 中 — 见 TODO-012 |
| **认知记忆** | CrewAI | LLM 分析内容自动推断范围和重要性 | 中 — 见 TODO-004 |
| **Advisor 链** | Spring AI | chain-of-responsibility 处理请求/响应 | 已有 — StreamPostProcessor 管线 |
| **Code Agent** | Smolagents | agent 写代码而非 JSON 调工具 | 低 — 不适合当前场景 |
| **Progressive Disclosure** | Google ADK | 按需注入详细指引而非全量 | 高 — 见 TODO-010/011 |
| **Agent Identity + Least Privilege** | OWASP Top 10 for Agents | 每个 agent/tool 唯一身份 + 最小权限 | 已有 — ToolPolicy + ScopedToolRegistry |
| **Event Sourcing** | 业界共识 | agent 从事件日志重建状态 | 中 — 见 TODO-005 |
| **Hybrid Workflow+Agent** | Anthropic 研究 | 确定性工作流 + 自主 agent 混合 | 高 — Orchestrator 可增强 |

---

## 六、优先级排序建议

### 立即（本月）
1. **TODO-006** ToolSourceProvider 生命周期激活 — 最小改动，修复死代码
2. **TODO-002** LLM Provider SPI — 解耦核心，后续所有 provider 工作的前提

### 短期（Q3 2026）
3. **TODO-001** Context 压缩管线升级 — 对长对话质量影响最大
4. **TODO-003** Prompt 缓存优化 — 直接省钱
5. **TODO-007** OTel 导出 — 生产可观测性基线
6. **TODO-008** 结构化错误恢复 — 鲁棒性

### 中期（Q4 2026）
7. **TODO-009** AgentObserver Hook 完善 — 扩展性
8. **TODO-004** 记忆系统扩展 — 长期体验
9. **TODO-010** 动态工具加载 — context 效率
10. **TODO-016** 工具执行并发模型 — 延迟优化

### 长期（2027）
11. **TODO-005** 会话检查点 — 可靠性
12. **TODO-011** PromptEngine 条件组装 — 模式感知
13. **TODO-012** 任务级 Guardrails — 质量保障
14. **TODO-013** 多 Provider Failover — 高可用
15. **TODO-015** Virtual Threads 混合模型 — 性能
16. **TODO-014** A2A 协议 — 分布式 agent

---

## 七、研究来源

### OpenClaw
- GitHub: github.com/openclaw/openclaw (379K stars, MIT)
- 架构文档: docs.openclaw.ai/concepts/architecture
- Gateway: 单进程 Node.js，HTTP+WS 复用，session 管理 + 工具分发
- Agent Loop: 委托 pi-agent-core，multi-turn 推理循环
- 记忆: Markdown 文件 + SQLite/sqlite-vec 向量搜索
- 压缩: 70% 阈值触发，压缩前静默 agentic turn 刷盘

### Claude Code (泄露源码分析)
- 源码泄露: 2026-03-31 npm v2.1.88 source map，512K 行 TypeScript
- 学术分析: arXiv:2604.14228 "Dive into Claude Code"
- Agent Loop: query.ts ~1729 行 `while(true)` AsyncGenerator
- 工具: ~40 内置 + MCP，全部是 MCP 原生
- Context: 五层级联压缩管线
- 权限: 6 级模式 + Auto 模式 ML 分类器
- Prompt 缓存: 三层前缀分离 (静态/项目/动态)
- Subagent: hub-spoke 单层，Explore/Plan/General-purpose 三种

### 社区框架
- LangGraph: Graph 状态机 + checkpoint + Time Travel (34.5M/月下载)
- CrewAI: Role-based + 认知记忆 + Task Guardrails (31.2K stars)
- Spring AI: Advisor 链 + 双模式执行 + MCP/A2A
- LangChain4j: @Tool 注解 + ServiceLoader + Quarkus/Spring 集成
- Strands (AWS): model-driven + CloudWatch/OTEL 原生
- Google ADK: Event-driven + Progressive Disclosure (20K stars)
- Smolagents: ~1000 行核心 + Code Agent 模式 (27.7K stars)
