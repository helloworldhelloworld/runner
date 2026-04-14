# Architecture

## Design Principles

1. **Reactive streaming** — `Flux<StreamEvent>` 作为全链路统一流式抽象
2. **Events as first-class citizens** — 所有生命周期事件在 `StreamEvent.EventType` 枚举中定义，不用字符串约定
3. **Tool-first extensibility** — `Tool` 接口 + `ToolRegistry` + MCP bridge，工具是一等公民
4. **Multi-Agent Orchestrator** — 对标 OpenClaw 2026.4，多 Agent 路由 + per-Agent 工具隔离 + Subagent spawn
5. **Dependency flows downward** — agent-kernel 是基座，不依赖任何内部模块

## Module Dependency Graph

```
agent-web (Spring Boot + Vert.x WebSocket, assembles everything)
├── agent-kernel     (core: Orchestrator, AgentLoop, Gateway, LLM, Prompt)
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

### Execution Flow (End-to-End)

```
WebSocket / REST / SSE (transport layer)
  ↓
Gateway (protocol-agnostic entry point)
  ↓
Orchestrator (implements ChatHandler)
  ├── AgentRouter            — route request → target Agent
  ├── InterruptibleRun       — per-session stateful container (interrupt / resume)
  └── SubagentRuntime        — async spawn / push announce / cascade stop
     ↓
AgentLoop (per-agent instance, isolated context)
  ├── PromptEngine           — system prompt + PromptSection + skills + memory
  ├── MemoryProvider          — search + history (isolated by sessionKey namespace)
  ├── ScopedToolRegistry     — per-Agent tool filtering (deny > allow)
  ↓
LLMProvider.completeStreamReactive() → Flux<StreamEvent>
  ↓
ToolCallingLoop (reactive TAOR loop)
  ├── LLM streaming
  ├── Tool call detection → ToolExecutor → parallel execution
  ├── CancellationToken check per iteration
  └── Recursive LLM calls until no more tool_use
  ↓
Flux<StreamEvent> → Gateway → Client
```

### Multi-Agent Orchestrator (4 Layers)

参照 OpenClaw 2026.4 的 Agent Core 架构设计。

```
┌─────────────────────────────────────────────────────┐
│ Layer 3: SubagentRuntime                            │
│   SpawnSubagentTool (LLM 调用) → 非阻塞 spawn      │
│   SubagentRun 状态跟踪 → push announce → 级联停止    │
│   Session Key: agent:<id>:subagent:<uuid>           │
├─────────────────────────────────────────────────────┤
│ Layer 2: Orchestrator (ChatHandler impl)            │
│   AgentRouter → MetadataAgentRouter                 │
│   AgentFactory → ScopedToolRegistry + AgentLoop     │
│   InterruptibleRun → interrupt() / resume()         │
│   CancellationToken → ToolCallingLoop 每轮检查       │
├─────────────────────────────────────────────────────┤
│ Layer 1: AgentRegistry + ScopedToolRegistry         │
│   Agent 注册/查找/default fallback                   │
│   工具权限: deny list > allow list > 全部放行         │
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

Built-in `StreamingTool` base class for real streaming (file reads, HTTP, shell).

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

### PromptEngine

```
System Prompt = Base Prompt + PromptSections (conditional) + Durable Memory + Active Skills + Memory Snippets
```

`PromptSection` supports conditional activation via `Predicate<PromptRequest>`.

---

## Package Structure

### agent-kernel core packages

| Package | Purpose |
|---------|---------|
| `.agent` | Tool, ToolRegistry, AgentLoop, AgentProfile, AgentRegistry, ScopedToolRegistry |
| `.agent.annotation` | `@ToolFunction`, `@ToolParam`, `@ClientTool` |
| `.agent.directive` | Directive system |
| `.core` | ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk, CancellationToken |
| `.core.postprocess` | StreamPostProcessor pipeline |
| `.orchestrator` | Orchestrator, AgentRouter, AgentFactory, InterruptibleRun, SubagentRuntime |
| `.gateway` | Gateway, ChatHandler, GatewayRequest/Response, SessionManager |
| `.llm` | LLMProvider, LLMProviderSpi (ServiceLoader), ConversationMessage, ToolCall |
| `.llm.claude` | ClaudeProvider, ClaudeProProvider, ClaudeProviderSpi |
| `.llm.openrouter` | OpenRouterProvider |
| `.memory` | MemoryProvider, Message, MessageSnapshot |
| `.prompt` | PromptEngine, PromptSection, Skill |
| `.trace` | Tracing and observability |
| `.plugin` | **Legacy** — do NOT add new code here |

### orchestrator package detail

```
com.lightweightai.kernel.orchestrator/
  Orchestrator.java           — ChatHandler impl, routes + manages InterruptibleRuns
  AgentRouter.java            — routing strategy interface
  MetadataAgentRouter.java    — route by request metadata (default impl)
  AgentFactory.java           — builds AgentLoop from AgentProfile
  InterruptibleRun.java       — stateful execution: startFresh / interrupt / resume
  SubagentRuntime.java        — async spawn / push announce / cascade stop
  SubagentRun.java            — run state tracking (RUNNING/COMPLETED/FAILED/CANCELLED)
  SpawnRequest.java           — spawn parameters + depth calculation
  SpawnSubagentTool.java      — Tool impl for LLM to invoke spawn_subagent
```

---

## Design Decisions

See [docs/decisions/](decisions/) for Architecture Decision Records:

| ADR | Decision |
|-----|----------|
| [001](decisions/001-reactor-flux-over-custom-stream.md) | Reactor Flux over custom StreamSource |
| [002](decisions/002-tool-over-plugin.md) | Tool interface over Plugin system |
| [003](decisions/003-agent-loop-over-kernel.md) | AgentLoop over abstract Kernel pattern |
| [004](decisions/004-multi-agent-orchestrator.md) | Multi-Agent Orchestrator architecture |
