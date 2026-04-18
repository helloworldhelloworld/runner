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
│   工具权限: deny list > allow list > 全部放行         │
│   ScopedToolRegistry overrides: get/has/isEnabled/   │
│     getEnabled/getAll/getToolDefinitions/size         │
├─────────────────────────────────────────────────────┤
│ Layer 0: AgentProfile                               │
│   agentId, systemPrompt, modelOverride              │
│   toolAllowList, toolDenyList                       │
│   maxToolIterations, maxSpawnDepth                  │
└─────────────────────────────────────────────────────┘
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
```

---

## Package Structure

### agent-kernel packages

| Package | Purpose | Status |
|---------|---------|--------|
| `.agent` | Tool, ToolRegistry, AgentLoop, AgentProfile, AgentRegistry, ScopedToolRegistry, AgentObserver | Active |
| `.agent.annotation` | `@ToolFunction`, `@ToolParam`, `@ClientTool` | Active |
| `.agent.directive` | Directive system | Active |
| `.cli` | CliTool, CliManifest, CliToolSourceProvider | Active |
| `.context` | ContextCompactor, SnipCompactor, MicroCompactor, CompactionChain, CostTracker | Active |
| `.core` | ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk, CancellationToken | Active |
| `.core.postprocess` | StreamPostProcessor pipeline | Active |
| `.orchestrator` | Orchestrator, AgentRouter, AgentFactory, InterruptibleRun, SubagentRuntime, SpawnSubagentTool, WaitSubagentTool, ListSubagentsTool | Active |
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

## Design Decisions

| ADR | Decision |
|-----|----------|
| [001](decisions/001-reactor-flux-over-custom-stream.md) | Reactor Flux over custom StreamSource |
| [002](decisions/002-tool-over-plugin.md) | Tool interface over Plugin system |
| [003](decisions/003-agent-loop-over-kernel.md) | AgentLoop over abstract Kernel pattern |
| [004](decisions/004-multi-agent-orchestrator.md) | Multi-Agent Orchestrator architecture |
