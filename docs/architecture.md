# Architecture

全局演进主方案：[Runner 与 Minion：端到端产品、架构与演进方案](design/runner-minion-system-evolution.md)。
该文从产品设计、架构全景展开到 runner 执行平台、Pi 交互、数据与协议、实体约束和迁移工作包；本页维护 runner 内部架构。
历史路线与四仓证据见 [系统架构分析](system-architecture.md)，
原始发现见 [四仓 review](reviews/2026-09-06-minion-system-review.md)。
目标提案与已实现能力分别标注；具体工程约束与已接受决策以 CLAUDE.md 和对应 ADR 为准。

## Design Principles

1. **Reactive streaming** — `Flux<StreamEvent>` 作为全链路统一流式抽象
2. **Events as first-class citizens** — 所有生命周期事件在 `StreamEvent.EventType` 枚举中定义，不用字符串约定
3. **Tool-first extensibility** — `Tool` 接口 + `ToolRegistry` + MCP bridge + CLI，工具是一等公民
4. **Multi-Agent Orchestrator** — 对标 OpenClaw 2026.4，多 Agent 路由 + per-Agent 工具隔离 + Subagent spawn
5. **Context budget awareness** — ContextCompactor 防 context 溢出，CostTracker 追踪 token 消耗
6. **Outside-in TDD** — 先写场景级 acceptance test，再写 unit test，防止组件间接线断裂
7. **Dependency flows downward** — agent-kernel 是基座，不依赖任何内部模块

---

## Module Dependency Graph

```
agent-web (Spring Boot + Vert.x WebSocket, assembles everything)
├── agent-kernel     (core: Orchestrator, AgentLoop, Gateway, LLM, Prompt, CLI)
├── agent-tools      (tool implementations) → depends on agent-kernel
├── agent-mcp        (MCP protocol bridge) → depends on agent-kernel
├── agent-openclaw   (OpenClaw 大脑适配：ChatHandler 实现) → depends on agent-kernel
├── kernel-memory    (file/SQLite memory, BM25+vector) → depends on agent-kernel
├── agent-sdk        (public SDK API) → depends on agent-kernel
├── soul-safety      (crisis detection) → depends on agent-kernel
├── soul-assessment  (psychological scales) → depends on agent-kernel, soul-user
└── soul-user        (user profile, SQLite) → standalone

agent-demo           (example app) → depends on agent-kernel, agent-tools, agent-mcp
agent-plugin-example (plugin example) → depends on agent-kernel
```

### Dependency Rules (ENFORCED)

1. **agent-kernel depends on nothing** (except external libs). It is the foundation.
2. **soul-user depends on nothing** internally. It is standalone.
3. **No circular dependencies**. Dependency flows strictly downward.
4. **agent-web is the only assembly module** — no other module should depend on agent-web.
5. **Business modules don't depend on each other** — agent-tools, agent-mcp, agent-openclaw, kernel-memory, agent-sdk, soul-safety are independent peers.
6. **Exception**: soul-assessment depends on soul-user.

---

## System Architecture

### Development evidence workflow

受控运行框架候选与评测证据包按 [ADR-021](decisions/021-frozen-candidate-evidence-bundle.md)
实施：仓库脚本冻结完整源码和评测定义，在独立副本执行 Maven/native 验收，导出绑定内容摘要的报告
与开发交接包。它属于开发/CI 工具，不进入在线对话链或 Spring 装配；生产版本消费和发布仍按 ADR-017 后续接通。
使用方式见[操作文档](guides/improvement-loop.md)。

### End-to-End Execution Flow

```
WebSocket / REST / SSE (transport layer)
  ↓
Gateway (protocol-agnostic entry point)
  ↓
Orchestrator (implements ChatHandler)
  ├── AgentRouter            — route request → target Agent
  ├── InterruptibleRun       — per-session stateful container (interrupt / resume)
  ├── CancellationToken      — propagated to ToolCallingLoop for graceful exit
  └── SubagentRuntime        — async spawn / push announce / cascade stop
     ↓
AgentLoop (per-agent instance, isolated context)
  ├── PromptEngine           — system prompt + skills + memory
  ├── MemoryProvider         — search + history (isolated by sessionKey namespace)
  ├── ScopedToolRegistry     — per-Agent tool filtering (deny > allow)
  │
  │  buildMessages() → ContextCompactor.compact()   ← 保留消息配对的 Micro 压缩
  ↓
LLMProvider.completeStreamReactive() → Flux<StreamEvent>
  ↓
ToolCallingLoop (reactive TAOR loop)
  ├── CancellationToken check (每轮迭代前)
  ├── CostTracker.isOverBudget() check (每轮迭代前)
  ├── LLM streaming → tool call detection
  ├── AgentObserver.onPreToolUse()     ← hook: 工具调用前
  ├── ToolExecutor → parallel execution
  ├── AgentObserver.onPostToolUse()    ← hook: 工具调用后
  ├── 视觉回灌：工具结果带 ImageContent 时，除 TOOL 文本消息外追加一条
  │   USER 多模态消息承载帧 → 下一轮 LLM 请求体含 image block（ADR-010）
  └── Recursive LLM calls until no more tool_use
  ↓
Flux<StreamEvent> → StreamEventSerializer → WebSocket / SSE → Client
```

### Multi-Agent Orchestrator (4 Layers)

```
┌─────────────────────────────────────────────────────┐
│ Layer 3: SubagentRuntime                            │
│   SpawnSubagentTool → 非阻塞 spawn                  │
│   WaitSubagentTool → 阻塞等待 + 收集结果             │
│   ListSubagentsTool → 查看活跃状态                   │
│   SubagentRun 状态跟踪 → push announce → 级联停止    │
│   Session Key: agent:<id>:subagent:<uuid>           │
│   destroyMethod=shutdown 防止线程池泄漏              │
├─────────────────────────────────────────────────────┤
│ Layer 2: Orchestrator (ChatHandler impl)            │
│   AgentRouter → MetadataAgentRouter                 │
│   AgentFactory → ScopedToolRegistry + AgentLoop     │
│     └── 默认注入 Micro(2000)，每次模型调用前执行 │
│   InterruptibleRun → interrupt() / resume()         │
│   CancellationToken → ToolCallingLoop 每轮检查       │
│   TTL 清理线程: 过期 INTERRUPTED/COMPLETED run 自动回收 │
│   @PreDestroy shutdown 清理线程池                    │
├─────────────────────────────────────────────────────┤
│ Layer 1: AgentRegistry + ScopedToolRegistry         │
│   Agent 注册/查找/default fallback                   │
│   工具权限由 ToolPolicy 决策(见下),非字符串名单      │
│   ScopedToolRegistry overrides: get/has/isEnabled/   │
│     getEnabled/getAll/getToolDefinitions/size         │
├─────────────────────────────────────────────────────┤
│ Layer 0: AgentProfile                               │
│   agentId, systemPrompt, modelOverride              │
│   toolPolicy(优先) 或 toolAllowList/toolDenyList     │
│   maxToolIterations, maxSpawnDepth                  │
└─────────────────────────────────────────────────────┘
```

### Tool Permission — ToolPolicy + RiskLevel

工具权限从早期的 `Set<String>` allow/deny 名单升级为可组合的 **`ToolPolicy`** 三值决策：

```
ToolPolicy.evaluate(req) → Decision {ALLOW, DENY, ABSTAIN}
  - ABSTAIN 让 chain 继续到下一层；全员 ABSTAIN 时由 ScopedToolRegistry 决定默认动作
  - ToolPolicyRequest 携带 (toolName, Tool 实例, args, AgentProfile, ToolExecutionContext)
    → 保留旧四参数构造器，执行上下文为可选增量
    → 既能按名字判断，也能按 Tool.riskLevel() 等元数据判断
    → args 列举阶段为 null、执行前已知，同一接口兼顾两段

工厂: allowAll() / denyList(names) / allowList(names) / byRiskLevel(max) / chain(...) / intersection(...)
intersection: 任一 DENY 胜出，供子任务权限交集使用；chain 的语义不变

多层组合示例:
  ToolPolicy.chain(
    denyList(Set.of("dangerous_tool")),   // 全局硬拒
    byRiskLevel(RiskLevel.WRITE),         // 风险上限
    allowList(agentWhitelist))            // per-agent 白名单

RiskLevel 序: SAFE < WRITE < SYSTEM（Tool.riskLevel() 暴露）
  - SAFE   只读无副作用（search / read_file）
  - WRITE  改自有 scope 状态（write_file / send_email）
  - SYSTEM 任意命令或 spawn（shell_exec / spawn_subagent）
  典型: 主 agent 放开 SYSTEM、subagent 限到 WRITE；生产收紧到 SAFE
```

`AgentProfile` 归一优先级：显式 `toolPolicy` > 由 allow/deny list 合成的策略 > `allowAll`（向后兼容旧 Profile）。

Native 子任务上下文与权限继承、局部 wait/list/stop、终态及级联取消按
[ADR-019](decisions/019-subagent-inheritance-and-ownership.md) 实施；权限交集独立于
`ToolPolicy.chain`，不能被子 Profile 的 ALLOW 放宽。任务控制按根会话和独立子任务路径绑定，
不能用客户端拼接的 session 文本冒充子任务。旧管理 API 保留兼容。

前台取消与 native 子任务生命周期的接线按 [ADR-020](decisions/020-foreground-subagent-cancellation.md)
实施：每次执行的取消 token 随 ToolExecutionContext 继承，接续换新 token；取消回调绑定与任务终态一起清理。
正常完成保留异步后代，不以 session 字符串批量取消任务。

### Proactive Trigger — TriggerSource SPI

Orchestrator 不再只被动接收 Gateway 推送的请求，可订阅主动触发源：

```
orchestrator.subscribeTriggerSource(source)   // source: TriggerSource
  TriggerSource.name()        — 日志 / unsubscribe 的稳定标识
  TriggerSource.requests()    — 持续推送的 Flux<GatewayRequest>
                                Flux 完成 = 该 source 不再产生 request
orchestrator.unsubscribeTriggerSource(name)   // 终止该 source

实现示例: 定时心跳 / cron 调度 / 外部 webhook(GitHub·Slack 事件转 GatewayRequest)
```

### Full-Duplex Interrupt & Resume

```
User Message A → InterruptibleRun.startFresh("A")
  │  emit AGENT_ROUTE
  │  streaming TEXT_DELTA...
  │  accumulatedText = "你好，让我来..."
  │
User Message B (interrupt) → InterruptibleRun.interrupt()
  │  emit AGENT_INTERRUPT
  │  messages += assistant("你好，让我来...\n[被用户打断]")
  │  cancellationToken.cancel() → ToolCallingLoop exits gracefully
  │  save partial response to memory
  │
  └→ InterruptibleRun.resume("B")
     emit AGENT_RESUME
     messages += user("B")
     re-enter ToolCallingLoop with full context
     LLM sees: previous partial answer + new question
```

### Context Management

```
ContextCompactor：原生默认 MicroCompactor(maxChars=2000)
  ├── 每次模型调用前执行（包括工具回包后的下一轮）
  ├── 大 TOOL 消息截断，保留 tool call / result ID 配对和图像消息
  └── SnipCompactor / CompactionChain 保留公开兼容 API，不再默认删除孤立 TOOL 消息

ExecutionUsageScope（ADR-026 实施中）
  ├── 每个根执行创建，子任务继承；实际尝试的已知用量与未知消耗分开
  ├── 并发准入先预留；逐次结算；额度不足发出 BUDGET_STOPPED
  └── CostTracker 保留既有 API；估算准入不等于精确账单上限

```

执行生命周期与取消责任按 [ADR-028](decisions/028-run-lifecycle-and-cancellation.md) 统一：
一次执行由唯一生命周期拥有者持有取消 token、provider subscription 和输出 subscription；
传输层只订阅输出并报告断开。正常完成、主动打断、连接接管、客户端断开和预算停止使用不同的
终态与协议事件，逻辑 session 同时只允许一个 active run，迟到事件按 run identity 丢弃。

### AgentObserver Hooks

```
AgentLoop lifecycle:
  onAgentStart(input, sessionId)
  onLLMRequest(messages)
  onLLMResponse(response)
  onAgentComplete(response)
  onError(throwable)

ToolCallingLoop lifecycle:
  onPreToolUse(toolName, args)       ← 工具调用前拦截（安全审计/参数校验）
  onPostToolUse(toolName, args, result) ← 工具调用后拦截（结果过滤/成本追踪）
  onToolCall(toolName, args, result) ← 通用工具调用事件（兼容）
```

### Self-Improving — learn 包（Hermes 风格）

`SkillGeneratorObserver` 是 `AgentObserver` 的一个实现，把成功完成的执行轨迹自动萃取成新 `Skill`：

```
onAgentStart   → 重置状态，记录 userInput / sessionId
onPostToolUse  → 累积 Trajectory.Step(toolName, args, result)
onError        → 标记 errored=true（后续 complete 不萃取）
onAgentComplete→ 构造 Trajectory → SkillExtractor.extract()
                 → 若 present，Consumer<Skill>.accept(skill)（典型: engine.registerSkill）

SkillExtractor          — 萃取 SPI（HeuristicSkillExtractor 为默认启发式实现）
Trajectory / Trajectory.Step — 不可变轨迹载体（userInput·steps·finalText·stopReason·errored）

线程模型: 单条进行中轨迹 → 每个并发 run 需要独立 observer 实例。
新建 AgentLoop 不自动保证其传入 observer 也独立；装配与验收需检查实例归属。
切勿把同一实例挂在多个并行运行上（步骤会串台）。
```

> 萃取的输入依赖结构化轨迹：`AgentResponse.ToolCallRecord` 现保留类型化
> `Map<String,Object> args` + `ToolResult`（旧 String 构造 `@Deprecated`，
> 仍逐字回放兼容）；`ToolCallingLoop` 按 callId 把原始 args 配回 PostToolUse。

2026-09-06 核对：该 observer 的仓内构造调用仅见测试；以上描述组件行为，
不代表生产已接通自改进。目标是保留候选、绑定被测内容、验证下一轮生效，详见
[主方案的改进闭环](design/runner-minion-system-evolution.md#114-持续改进从运行证据到下一版可验证变更)
与 [ADR-017（Proposed）](decisions/017-versioned-harness-improvement-loop.md)。

### Session Key Namespace

```
Main Agent:   agent:<agentId>:main:<sessionId>
Subagent:     agent:<agentId>:subagent:<uuid>
Sub-sub:      agent:<agentId>:subagent:<uuid>:subagent:<uuid>
```

MemoryProvider isolates history by sessionKey — different agents never see each other's conversation.

---

执行级模型选择遵循 [ADR-022](decisions/022-execution-model-binding.md)：kernel 的可选 Provider 绑定 SPI
与 ExecutionModelScope 由 AgentLoop/ToolCallingLoop 消费，经 ToolExecutionContext 传给子任务。
web 的 DynamicLLMProvider 热切换影响新执行；旧轮及其同源后代保留原 Provider 和重试包装。
这只固定模型选择；完整 Prompt/Tool/策略/模型参数清单与发布核对仍待接通。

## Core Abstractions

### StreamEvent — First-Class Event Types

All lifecycle events are defined as `StreamEvent.EventType` enum values with factory methods.

| Category | EventType | Purpose |
|----------|-----------|---------|
| LLM Streaming | `TEXT_DELTA`, `LLM_COMPLETE` | Text chunks, final response |
| Tool Execution | `TOOL_CALL_START`, `TOOL_PROGRESS`, `TOOL_LOG`, `TOOL_RESULT`, `TOOL_ERROR` | Full tool lifecycle |
| Orchestrator | `AGENT_ROUTE`, `AGENT_INTERRUPT`, `AGENT_RESUME` | Routing, interrupt, resume |
| Subagent | `SUBAGENT_SPAWN`, `SUBAGENT_COMPLETE`, `SUBAGENT_ERROR`, `SUBAGENT_CANCELLED` | Full subagent lifecycle |
| Post-processing | `POST_PROCESS_DATA` | Cards, annotations, risk signals |
| Tracing | `TRACE` | Call-chain logging |
| Pipeline | `ERROR` | Pipeline-level errors |

WebSocket 事件序列化统一通过 `StreamEventSerializer` 处理（Vert.x 和 Spring 共用）。

### Tool Source — 5 种供给形态

```
ToolSourceProvider（统一接口，带 start/stop 生命周期）
├── LegacyScanProvider       — Java SPI 扫描
├── ManualToolProvider       — 手动注册（需构造参数的工具）
├── DynamicPluginProvider    — jar 热加载（监听 plugins/ 目录）
├── McpToolSourceProvider    — MCP 远程工具（JSON-RPC 协议）
└── CliToolSourceProvider    — CLI 命令行工具（进程 I/O，沙箱执行）
     ├── 扫描 cli-plugins/ 目录
     ├── 解析 cli-manifest.json（name, description, entry_point, output_format）
     ├── CliTool extends StreamingTool → stdout 逐行推送 PROGRESS → COMPLETE/ERROR
     ├── 进程 I/O 经 CliExecutor 接缝（LocalProcessExecutor 实跑 / FakeCliExecutor 可测）
     ├── 超时: process.destroyForcibly()，stdout 读取在 daemon 线程中
     └── ToolRegistry.search(keyword) 支持按 description 模糊检索

所有 ToolSourceProvider 在 @PreDestroy 时调用 stop() 清理资源。
```

### Tool Interface

```java
public interface Tool {
    String getName();
    String getDescription();
    ToolSchema getSchema();
    ToolResult execute(Map<String, Object> args);
    default Flux<ToolResultChunk> executeReactive(Map<String, Object> args);
}
```

`StreamingTool` 基类支持真实流式输出（CLI / 文件读取 / HTTP / Shell）。

### LLMProvider Interface (4 execution modes)

```java
public interface LLMProvider {
    LLMResponse complete(messages, options);                           // Sync
    CompletableFuture<LLMResponse> completeAsync(messages, options);   // Async
    CompletableFuture<LLMResponse> completeStream(messages, options, handler); // Callback
    Flux<StreamEvent> completeStreamReactive(messages, options);       // Reactive
}
```

Provider discovery via `ServiceLoader<LLMProviderSpi>` — no hardcoded switch.

### Gateway Pattern

```java
Gateway gateway = Gateway.builder()
    .chatHandler(orchestrator)    // Orchestrator implements ChatHandler
    .sessionManager(manager)
    .build();
gateway.handleStreamReactive(request);  // → Flux<StreamEvent>
```

### Memory System (Three-Layer)

```
Layer 1: Session Memory    — Conversation history (MemoryProvider.getHistory)
Layer 2: Ephemeral Memory  — Daily logs, BM25-searchable (FileMemoryManager)
Layer 3: Durable Memory    — Long-term knowledge, always in system prompt
```

`MessageSnapshot` provides immutable views for API calls, decoupled from mutable storage.

### PromptEngine — Skill 统一入口

```
PromptEngine 是 Skill 的唯一注册点：
  registerSkill(Skill)     — 注册
  getSkillObjects()        — 获取所有 Skill 对象
  build(PromptRequest)     — 组装 PromptContext（含 system prompt + tools）

System Prompt = Base Prompt + Active Skills' systemPrompts + Durable Memory + Memory Snippets

Skill 激活方式：
  - 显式指定：PromptRequest.addActiveSkill("name")
  - 自动检测：Skill.triggers 匹配用户消息
  - 按 priority 排序

Skill 懒加载主体：
  - Skill.Builder.lazyBody(Supplier<String>) 注册的 Skill，
    仅在被实际激活（显式或触发）时才执行 supplier 加载 system prompt 主体，
    避免为未命中的 Skill 预先读盘 / 拼接长文本。
```

---

## Package Structure

### agent-kernel packages

| Package | Purpose | Status |
|---------|---------|--------|
| `.agent` | Tool (含 riskLevel), ToolRegistry, AgentLoop, AgentProfile, AgentRegistry, ScopedToolRegistry, AgentObserver, ToolPolicy, RiskLevel | Active |
| `.agent.annotation` | `@ToolFunction`, `@ToolParam`, `@ClientTool` | Active |
| `.agent.directive` | Directive system | Active |
| `.cli` | CliTool, CliManifest, CliToolSourceProvider, CliExecutor (LocalProcessExecutor) | Active |
| `.context` | ContextCompactor, SnipCompactor, MicroCompactor, CompactionChain, CostTracker | Active |
| `.core` | ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk, CancellationToken | Active |
| `.core.postprocess` | StreamPostProcessor pipeline | Active |
| `.learn` | SkillGeneratorObserver, SkillExtractor, HeuristicSkillExtractor, Trajectory (self-improving skill 萃取) | Active |
| `.orchestrator` | Orchestrator, AgentRouter, AgentFactory, InterruptibleRun, SubagentRuntime, SpawnSubagentTool, WaitSubagentTool, ListSubagentsTool, TriggerSource | Active |
| `.gateway` | Gateway, ChatHandler, GatewayRequest/Response, SessionManager | Active |
| `.llm` | LLMProvider, LLMProviderSpi, LLMOptions (含 maxBudgetTokens), ConversationMessage, ToolCall | Active |
| `.llm.claude` | ClaudeProvider, ClaudeProProvider, ClaudeProviderSpi | Active |
| `.llm.openrouter` | OpenRouterProvider | Active |
| `.memory` | MemoryProvider, Message, MessageSnapshot | Active |
| `.prompt` | PromptEngine, Skill (统一入口) | Active |
| `.trace` | Tracer, SpanContext | Active |
| `.instruction` | InstructionRegistry, InstructionPackage, ProviderAdapter | **@Deprecated** — 使用 PromptEngine |
| `.skill` | SkillRegistry, Skill (filesystem-loaded) | **@Deprecated** — 使用 prompt/Skill |
| `.plugin` | Plugin, PluginFunction | **Legacy** — 不要新增代码 |

### agent-web key packages

| Package | Purpose |
|---------|---------|
| `.config` | AgentConfig, GatewayConfig, OrchestratorConfig (含 @PreDestroy shutdown) |
| `.gateway` | GatewayService (仅 SessionManager 实现) |
| `.websocket` | VertxChatWebSocketHandler, SpringChatWebSocketHandler, StreamEventSerializer |
| `.skillcreator` | SkillCreatorService, SkillPublishService, SkillTestExecutionService |

---

## Embodiment / Device Layer（Minion 具身化）

**当前落地增量：轮次与取消**（[ADR-015](decisions/015-voice-turn-cancellation.md)）：
Pi 侧组合 Voice Gateway 与身体适配，保持媒体不进 JVM；Spring `/ws/chat` 增量支持
可选 `turnId`，可说块与终态按轮次关联，打断确认本身终结旧轮。输入端聚合 STT 分段，
本地取消先停播并保留插话，不等待云端 `stream_end`。生成结束与播放结束分别管理。
这是当前工作区实现的收敛，不表示 Pi 部署已验收、独立远端媒体服务或音频时钟 Realizer 已经完成。

把 runner 落成实体小黄人的架构层。历史设计见 [ADR-006](decisions/006-minion-embodiment-architecture.md)，
当前系统边界与演进见[整体架构](system-architecture.md)。保留的分层及目标增量：

```
远端 runner(JVM)：语义执行、Tool/MCP、会话与配置
Pi 当前组合：body 采音/VAD → voice-gateway 云 STT/TTS 客户端 → 本地播放
  媒体：Pi ⟷ 厂商流式 STT/TTS，原始音频不进 JVM
  文本：voice-gateway ⟷ runner，轮次与终态经 WS 关联
  工具：runner MCP client ⟷ Pi body MCP server
眼睛：2× ESP32-S3 自渲染，Pi 经 USB 串口下 directive（ADR-009）
目标增量：唯一设备所有者、实际播放时钟驱动 Realizer、播放/动作回执
```

runner 已有基础：① 可说块边界事件 ② 逐块 emotion 字段 ③ barge-in 接线（`InterruptibleRun.interrupt()`）
④ 多模态接线（`ImageContent` → ClaudeProvider 请求体）。设备上下文、真实能力与执行授权仍需贯穿主链。
新增 StreamEvent 类型须遵守 [ADR-005](decisions/005-streamevent-closed-protocol.md) 闭合枚举规约。
眼睛与语音同步优先落地；实体嘴为喇叭格栅，viseme 保留扩展位置。真实运动上线前须验证限位、停止和执行反馈。

③ 的**入站口**：Voice Gateway 经 WS 送 `interrupt`；当前双工脚本依据云 STT 出字确认插话，
确认后本地立即取消输出，再经 `Gateway.interrupt(sessionId)` → `Orchestrator.interrupt()` 打断在途 run →
下发 `SPEECH_INTERRUPTED`。大脑平面文本契约（入站 `chat`/`interrupt`、出站
`token`/`speakable_chunk`/`speech_interrupted`/`stream_end`）固化为共享 JSON Schema，
见 [ADR-012](decisions/012-voice-text-plane-contract.md)、[ADR-015](decisions/015-voice-turn-cancellation.md)
与 `contract/minion-voice-text/`。本地取消不等待远端确认；全本地插话判定是后续方向。

①② 的**生产接线**：`SpeakableChunkProcessor`（含可插拔 `EmotionClassifier`）作为一个
`StreamPostProcessor` Bean 在 `agent-web` 的 `PostProcessorConfig` 注册，经 `GatewayConfig` 自动汇入
真 Gateway 后处理管道，由配置开关 `app.voice.speakable-chunk.enabled`（默认 **off**）闸控——
语音/具身部署打开即在 `LLM_COMPLETE` 前发出带 emotion 的 `SPEAKABLE_CHUNK`，非语音部署不受影响、零额外开销。
（开关为部署级粒度；当单个 runner 同时服务多 persona 时按 persona 精细闸控需把 persona 上下文透进后处理管道，列为后续。）

**首响延迟杠杆**：`FirstChunkPolicy` 控制**首个**可说块多早成形（chunk_form，喂 TTS 的起点）。
默认 `sentenceOnly()` 等整句；`eager` 给首块三条内容维度提前切边界（子句末 / `maxChars` 硬切 / 与句末取最早），
外加 `maxWaitMillis` 时间维度兜底——自首个 `TEXT_DELTA` 起墙钟到点即整段 flush 首块，约束慢 token 下的 TTFA，
见 [ADR-013](decisions/013-first-chunk-timed-flush.md)。是否接生产由延迟 spike bench 数据定（`docs/latency-spike-results.md`）。

**脑 = 可切换 `ChatHandler`（[ADR-014](decisions/014-brain-swappable-chathandler-openclaw.md)）**：大脑平面的耦合点是
`ChatHandler.chatStreamReactive` 产出的 `Flux<StreamEvent>` 与 `interrupt(sessionId)`；上面 ①②③ 的语音平面
（可说块/emotion/序列化/WS/barge-in 入口）对 `ChatHandler` 实现**泛型无关**。故脑可切换：`Orchestrator`(native) /
`OpenClawChatHandler`(openclaw，模块 `agent-openclaw`)，由 `app.brain.type` + `@Primary` 选。openclaw 模式下
loop/工具/compaction/历史由 OpenClaw 负责的方案需配套设备 MCP 和会话管理。
当前适配已有受控远端测试，真实鉴权、长期连接与历史查询/清空尚待验证，见[模块状态](modules/agent-openclaw.md)。

Pi 本地统一入口、MCP 与 Realizer 共享设备实例的目标取舍记录在
[ADR-016（Proposed）](decisions/016-local-interaction-runtime.md)。

---

## Design Decisions

| ADR | Decision |
|-----|----------|
| [001](decisions/001-reactor-flux-over-custom-stream.md) | Reactor Flux over custom StreamSource |
| [002](decisions/002-tool-over-plugin.md) | Tool interface over Plugin system |
| [003](decisions/003-agent-loop-over-kernel.md) | AgentLoop over abstract Kernel pattern |
| [004](decisions/004-multi-agent-orchestrator.md) | Multi-Agent Orchestrator architecture |
| [005](decisions/005-streamevent-closed-protocol.md) | Keep StreamEvent as a closed enum protocol |
| [006](decisions/006-minion-embodiment-architecture.md) | Minion 具身化：脑在云 + 媒体/大脑双平面 + Voice Gateway + 瘦 Pi |
| [007](decisions/007-risk-level-across-mcp.md) | RiskLevel 跨 MCP 边界恢复（annotations + `_meta`）|
| [008](decisions/008-mcp-transport-cloud-to-pi.md) | cloud↔Pi 的 MCP transport（streamable_http）+ NAT 穿透（Tailscale overlay）|
| [009](decisions/009-minion-eyes-esp-directive.md) | Minion 眼睛：ESP32-S3 板载自渲染 + Pi 下 USB-串口 directive |
| [010](decisions/010-visual-feedback-chain.md) | 视觉回灌链：tool frame → ImageContent → 下一轮多模态消息 |
| [011](decisions/011-mcp-connection-resilience.md) | MCP 连接韧性：后台重连 + 自动注册（不依赖重启）|
| [012](decisions/012-voice-text-plane-contract.md) | 语音文本平面契约 + 入站 barge-in（Voice Gateway ⟷ runner WS）|
| [013](decisions/013-first-chunk-timed-flush.md) | 首块早发的时间维度兜底（max-wait timed flush）|
| [014](decisions/014-brain-swappable-chathandler-openclaw.md) | 脑=可切换 ChatHandler SPI + OpenClaw 完整大脑适配 |
| [015](decisions/015-voice-turn-cancellation.md) | 轮次关联与取消；runner 自动化已验证，Pi 验收待完成 |
| [016](decisions/016-local-interaction-runtime.md) | Proposed：Pi 本地交互运行时与状态归属 |
| [017](decisions/017-versioned-harness-improvement-loop.md) | Proposed：版本化运行装配与证据驱动的改进闭环 |
| [018](decisions/018-request-tool-execution-scope.md) | 请求工具上下文、局部注册和执行前参数策略；工作区实现与自动化验收完成 |
| [019](decisions/019-subagent-inheritance-and-ownership.md) | 子任务上下文、权限交集、结构化所有权、终态与内存并发；工作区实现与自动化验收完成 |
| [020](decisions/020-foreground-subagent-cancellation.md) | 前台取消信号继承、后代停止与回调释放；正常完成保留异步任务 |
| [021](decisions/021-frozen-candidate-evidence-bundle.md) | 冻结源码、受控 native 候选执行、证据绑定与重跑；开发/CI 工作流已验收 |
| [022](decisions/022-execution-model-binding.md) | 执行级模型绑定；根工具循环与同源子任务固定 Provider，新执行采用新配置；真实 Spring/MVC/HTTP 已验收 |
| [023](decisions/023-execution-assembly-snapshot.md) | 提示/工具/参数执行快照、同源继承与实际装配观察 |
| [024](decisions/024-content-bound-skill-publication.md) | 候选/评测内容绑定、持久激活与重启后的真实消费 |
| [025](decisions/025-authenticated-session-memory.md) | 服务器主体、会话命名空间与统一持久记忆（已验收） |
| [026](decisions/026-execution-usage-budget-compaction.md) | Proposed：真实用量、父子共享预算与循环内压缩 |
| [027](decisions/027-comparable-candidate-reports.md) | CLI 已验收：同一冻结评测下的候选比较与分析交接 |
