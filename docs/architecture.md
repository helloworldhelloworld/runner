# Architecture

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
5. **Business modules don't depend on each other** — agent-tools, agent-mcp, kernel-memory, agent-sdk, soul-safety are independent peers.
6. **Exception**: soul-assessment depends on soul-user.

---

## System Architecture

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
  │  buildMessages() → ContextCompactor.compact()   ← Snip + Micro 压缩
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
│     └── 默认注入 CompactionChain(Snip(3), Micro(2000)) │
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
  - ToolPolicyRequest 携带 (toolName, Tool 实例, args, AgentProfile)
    → 既能按名字判断，也能按 Tool.riskLevel() 等元数据判断
    → args 列举阶段为 null、执行前已知，同一接口兼顾两段

工厂: allowAll() / denyList(names) / allowList(names) / byRiskLevel(max) / chain(...)

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
ContextCompactor 链式压缩（在 AgentLoop.buildMessages() 后执行）
  ├── SnipCompactor(keepRecentRounds=3)
  │     删除 N 轮之前的 TOOL role 消息
  │     USER/ASSISTANT/SYSTEM 永远保留
  │
  └── MicroCompactor(maxChars=2000)
        超过阈值的 TOOL 消息截断为首尾各 200 chars + [snipped N chars]

CostTracker（在 ToolCallingLoop 每轮迭代前检查）
  ├── record(inputTokens, outputTokens) — 每次 LLM 调用后记录
  ├── isOverBudget() → true 时优雅退出循环
  └── maxBudgetTokens=0 表示无限预算
  └── 通过 LLMOptions.maxBudgetTokens 配置
```

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

线程模型: 单条进行中轨迹 → 一次 AgentLoop 配一个 observer 实例。
AgentFactory 已按 AgentProfile 创建独立 AgentLoop，天然满足；
切勿把同一实例挂在多个并行 AgentLoop 上（步骤会串台）。
```

> 萃取的输入依赖结构化轨迹：`AgentResponse.ToolCallRecord` 现保留类型化
> `Map<String,Object> args` + `ToolResult`（旧 String 构造 `@Deprecated`，
> 仍逐字回放兼容）；`ToolCallingLoop` 按 callId 把原始 args 配回 PostToolUse。

### Session Key Namespace

```
Main Agent:   agent:<agentId>:main:<sessionId>
Subagent:     agent:<agentId>:subagent:<uuid>
Sub-sub:      agent:<agentId>:subagent:<uuid>:subagent:<uuid>
```

MemoryProvider isolates history by sessionKey — different agents never see each other's conversation.

---

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

把 runner 落成实体小黄人的架构层。详见 [ADR-006](decisions/006-minion-embodiment-architecture.md)、
[voice-gateway.md](modules/voice-gateway.md)、[minion-body.md](modules/minion-body.md)。核心选型：

```
脑在云：runner(JVM) 与 LLM/语音服务同侧
双平面分离（原始音频永不进 JVM）：
  媒体平面 audio: Device ⟷ Voice Gateway ⟷ 厂商流式 STT/TTS
  大脑平面 text : Voice Gateway ⟷ runner（纯文本 + 工具 + 事件）
表情/语音同步：三轨(嘴形 viseme / 情绪 emotion / 手势 bookmark) + 设备端音频时钟 Realizer
瘦 Pi (Python)：唤醒 + VAD + 音频收发 + Realizer + 舵机/摄像头；动作经 MCP 暴露
  眼睛不在 Pi 渲染：2× ESP32-S3 板载自渲染，Pi 经 USB 串口下 directive（ADR-009）
```

runner 侧最小改动：① 可说块边界事件 ② 逐块 emotion ③ barge-in 接线（`InterruptibleRun.interrupt()`）
④ 多模态接线（`ImageContent` → ClaudeProvider 请求体）⑤ `deviceType="minion"` 运动工具集。
新增 StreamEvent 类型须遵守 [ADR-005](decisions/005-streamevent-closed-protocol.md) 闭合枚举规约。
**暂缓**：物理安全、二进制媒体走 runner、重视觉。

①② 的**生产接线**：`SpeakableChunkProcessor`（含可插拔 `EmotionClassifier`）作为一个
`StreamPostProcessor` Bean 在 `agent-web` 的 `PostProcessorConfig` 注册，经 `GatewayConfig` 自动汇入
真 Gateway 后处理管道，由配置开关 `app.voice.speakable-chunk.enabled`（默认 **off**）闸控——
语音/具身部署打开即在 `LLM_COMPLETE` 前发出带 emotion 的 `SPEAKABLE_CHUNK`，非语音部署不受影响、零额外开销。
（开关为部署级粒度；当单个 runner 同时服务多 persona 时按 persona 精细闸控需把 persona 上下文透进后处理管道，列为后续。）

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
