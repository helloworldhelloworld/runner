# Architecture

## Module Dependency Graph

```
agent-web (Spring Boot + Vert.x WebSocket, assembles everything)
├── agent-kernel     (core: AgentLoop, ToolCallingLoop, Gateway, LLM, Prompt)
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

## Dependency Rules (ENFORCED)

These rules MUST NOT be violated:

1. **agent-kernel depends on nothing** (except external libs). It is the foundation.
2. **soul-user depends on nothing** internally. It is standalone.
3. **No circular dependencies**. Dependency flows strictly downward.
4. **agent-web is the only assembly module** — it pulls in all modules. No other module should depend on agent-web.
5. **Business modules don't depend on each other** — agent-tools, agent-mcp, kernel-memory, agent-sdk, soul-safety are all independent peers that only depend on agent-kernel.
6. **Exception**: soul-assessment depends on soul-user (for user profiles in assessments).

## Execution Flow

```
Gateway (protocol-agnostic entry point)
  ↓
ChatHandler (business logic interface)
  ├── AgentLoopChatHandler (wraps AgentLoop)
  └── Custom implementations
     ↓
AgentLoop (agent orchestration)
  ├── PromptEngine (system prompt + skills + memory context)
  ├── MemoryProvider (search + history)
  ↓
LLMProvider.completeStreamReactive() → Flux<StreamEvent>
  ↓
ToolCallingLoop (reactive tool loop)
  ├── Tool call detection
  ├── ToolExecutor → ToolRegistry → Tool.executeReactive()
  ├── MCP tools via McpToolWrapper
  └── Recursive LLM calls until no more tool_use
  ↓
Flux<StreamEvent> (TEXT_DELTA, TOOL_CALL_START, TOOL_PROGRESS, TOOL_RESULT, LLM_COMPLETE)
```

## Core Abstractions

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

### LLMProvider Interface (4 execution modes)
```java
public interface LLMProvider {
    LLMResponse complete(messages, options);                           // Sync
    CompletableFuture<LLMResponse> completeAsync(messages, options);   // Async
    CompletableFuture<LLMResponse> completeStream(messages, options, handler); // Callback
    Flux<StreamEvent> completeStreamReactive(messages, options);       // Reactive
}
```

### Gateway Pattern
```java
Gateway gateway = Gateway.builder()
    .chatHandler(handler).sessionManager(manager).build();
gateway.handleStreamReactive(request);  // → Flux<StreamEvent>
```

### Memory System (Three-Layer)
```
Layer 1: Session Memory   — Conversation history (MemoryProvider.getHistory)
Layer 2: Ephemeral Memory — Daily logs, BM25-searchable (FileMemoryManager)
Layer 3: Durable Memory   — Long-term knowledge, always injected into system prompt
```

### PromptEngine
```
System Prompt = Base Prompt + Durable Memory + Active Skills + Relevant Memory Snippets
```
Skills are auto-detected via trigger words or explicitly activated.
