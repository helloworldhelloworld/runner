# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git CI-Results Conflict Resolution

The `ci-results/` directory (`ci-results/build.log`, `ci-results/latest.json`) is auto-updated by GitHub Actions CI after each push. This frequently causes merge/rebase conflicts. **Always resolve these conflicts automatically:**

- When rebasing or merging and `ci-results/` files conflict, always accept the **theirs** (remote/incoming) version:
  ```bash
  git checkout --theirs ci-results/build.log ci-results/latest.json
  git add ci-results/
  git rebase --continue
  ```
- Never manually edit `ci-results/` files to resolve conflicts.
- When `git push` is rejected due to remote ci-results updates, fetch and rebase first, resolve ci-results conflicts as above, then push.

## Project Overview

**Lightweight AI Kernel** is a Java-based AI agent framework with reactive streaming, tool calling, MCP integration, and persistent memory. The architecture centers on an Agent Loop pattern with Reactor Flux-based streaming throughout.

**Core Design Principles:**
- **Agent Loop pattern** - LLM call → tool detection → tool execution → re-prompt loop
- **Reactive streaming** - Reactor `Flux<StreamEvent>` as the unified streaming abstraction
- **Tool-first extensibility** - `Tool` interface + `ToolRegistry` + MCP bridge
- **Persistent memory** - File-based ephemeral/durable memory with BM25 + vector hybrid search
- **Protocol-agnostic Gateway** - Decouples transport (REST/WebSocket/SSE) from business logic

## Multi-Module Structure

```
lightweight-ai-kernel/
├── pom.xml                    (Parent POM - dependency management)
├── agent-kernel/              (Core framework: AgentLoop, ToolCallingLoop, Gateway, LLM providers)
├── agent-tools/               (Tool implementations)
├── agent-mcp/                 (MCP protocol bridge - wraps MCP tools as Tool interface)
├── kernel-memory/             (File/SQLite memory: BM25 + vector hybrid search)
├── agent-sdk/                 (Public SDK API for building agents)
├── soul-safety/               (Crisis detection & content filtering)
├── soul-assessment/           (PHQ-9/GAD-7/PSS-10 psychological scales)
├── soul-user/                 (User profile & session management, SQLite)
├── agent-web/                 (Spring Boot REST/SSE + Vert.x WebSocket server)
└── agent-demo/                (Example application)
```

## Build Commands

```bash
# Build all modules (from root)
mvn clean install

# Build specific module
cd agent-kernel && mvn clean install

# Run all tests
mvn test

# Run single test
mvn test -Dtest=ClassName#methodName

# Skip tests
mvn install -DskipTests

# Run Spring Boot service
cd agent-web && mvn spring-boot:run
```

## Architecture Overview

### Execution Flow

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
  ├── ToolExecutor → ToolRegistry → Tool.executeReactive() → Flux<ToolResultChunk>
  ├── MCP tools via McpToolWrapper
  └── Recursive LLM calls until no more tool_use
  ↓
Flux<StreamEvent> (TEXT_DELTA, TOOL_CALL_START, TOOL_PROGRESS, TOOL_RESULT, LLM_COMPLETE)
```

### Package Structure

**Agent & Gateway:**
- `com.lightweightai.kernel.agent` - AgentLoop, Tool, ToolRegistry, ToolScanner, AgentObserver
- `com.lightweightai.kernel.gateway` - Gateway, ChatHandler, GatewayRequest/Response

**Core Execution:**
- `com.lightweightai.kernel.core` - ToolCallingLoop, ToolExecutor, StreamEvent, ToolResultChunk

**LLM Providers:**
- `com.lightweightai.kernel.llm` - LLMProvider, LLMResponse, LLMOptions, ConversationMessage, ToolCall
- `com.lightweightai.kernel.llm.claude` - ClaudeProvider, ClaudeProProvider
- `com.lightweightai.kernel.llm.openrouter` - OpenRouterProvider

**Memory:**
- `com.lightweightai.kernel.memory` - MemoryProvider, ConversationMemory, Message
- `com.lightweightai.kernel.memory.file` - FileMemoryManager, SessionTranscript
- `com.lightweightai.kernel.memory.index` - MemoryIndex (BM25 + vector)

**Prompt:**
- `com.lightweightai.kernel.prompt` - PromptEngine, PromptContext, Skill

**Plugin (legacy, used by ToolExecutor fallback):**
- `com.lightweightai.kernel.plugin` - Plugin, PluginFunction, FunctionResult

## Core Abstractions

### 1. Tool Interface

All tools implement this interface. MCP remote tools are wrapped via `McpToolWrapper`.

```java
public interface Tool {
    String getName();
    String getDescription();
    ToolSchema getSchema();
    ToolResult execute(Map<String, Object> args);
    default Flux<ToolResultChunk> executeReactive(Map<String, Object> args);
}
```

### 2. LLMProvider Interface

Four execution modes:

```java
public interface LLMProvider {
    LLMResponse complete(messages, options);                           // Sync
    CompletableFuture<LLMResponse> completeAsync(messages, options);   // Async
    CompletableFuture<LLMResponse> completeStream(messages, options, handler); // Callback
    Flux<StreamEvent> completeStreamReactive(messages, options);       // Reactive
}
```

### 3. Reactive Streaming (`Flux<StreamEvent>`)

The entire pipeline uses `Flux<StreamEvent>` as the unified stream type:
- `TEXT_DELTA` - LLM text chunks
- `TOOL_CALL_START` - LLM decides to call a tool
- `TOOL_PROGRESS` / `TOOL_LOG` - MCP progress/logging notifications
- `TOOL_RESULT` - Tool execution complete
- `LLM_COMPLETE` - Final response

### 4. ToolCallingLoop (Reactive)

```java
// Core reactive tool loop pattern:
Flux<StreamEvent> stream = toolCallingLoop.executeWithToolsReactive(messages, options);

// Internally:
// 1. LLM streaming via completeStreamReactive()
// 2. Tool call detection from accumulated events
// 3. Parallel tool execution via Flux.merge()
// 4. .cache() pattern for dual subscription (display + result collection)
// 5. Recursive LLM calls with tool results appended
```

### 5. Gateway Pattern

```java
Gateway gateway = Gateway.builder()
    .chatHandler(handler)
    .sessionManager(manager)
    .build();

gateway.handle(request);                    // Sync
gateway.handleAsync(request);              // Async
gateway.handleStream(request, handler);    // Callback
gateway.handleStreamReactive(request);     // Flux<StreamEvent>
```

### 6. Memory System (Three-Layer)

```
Layer 1: Session Memory   - Conversation history (MemoryProvider.getHistory)
Layer 2: Ephemeral Memory - Daily logs, BM25-searchable (FileMemoryManager)
Layer 3: Durable Memory   - Long-term knowledge, always injected into system prompt
```

Hybrid search: BM25 (keyword) + Vector (semantic), configurable weights.

### 7. PromptEngine

Constructs the final prompt from multiple sources:
```
System Prompt = Base Prompt + Durable Memory + Active Skills + Relevant Memory Snippets
```
Skills are auto-detected from user message via trigger words, or explicitly activated.

## Development Workflow

### Adding a New LLM Provider

1. Implement `LLMProvider` interface (all four methods)
2. Implement `ModelCapability` for the model
3. Wire into Spring configuration or builder

### Adding a New Tool

1. Implement `Tool` interface
2. Register via `ToolRegistry.register(tool)` or use `@ToolFunction` annotation with `ToolScanner`

### Adding an MCP Tool Server

Configure in `application.yml`:
```yaml
app:
  mcp:
    enabled: true
    servers:
      my-server:
        command: ["node", "my-mcp-server.js"]
        # or: url: "http://localhost:3000/sse"
```
MCP tools are auto-discovered and registered as `McpToolWrapper` → `ToolRegistry`.

### Adding a New Skill

1. Create a `Skill` object with trigger words and system prompt
2. Register with `PromptEngine`
3. Skills auto-activate when trigger words match user input

## MCP Integration

The `agent-mcp` module bridges MCP protocol tools into the framework:
- `McpToolWrapper` adapts MCP tools to the `Tool` interface
- `ProgressNotification` → `ToolResultChunk.PROGRESS` events
- `LoggingNotification` → `ToolResultChunk.LOG` events
- Supports subprocess and SSE transports

## REST API & WebSocket

**REST (Spring Boot, port 8080):**
```
POST /gateway/chat                    # Sync response
POST /gateway/chat/stream             # SSE streaming
POST /assessment/questionnaire/{id}   # Psychological assessment
GET  /assessment/results/{userId}     # Assessment history
```

**WebSocket (Vert.x, port 8081):**
- Bidirectional streaming with real-time tool progress notifications

## Configuration

```yaml
# application.yml
server:
  port: 8080

app:
  provider-type: api          # mock | api | pro | openrouter
  claude:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-20250514
  openrouter:
    api-key: ${OPENROUTER_API_KEY}
  soul:
    db-path: ./data/soul_data.db
  mcp:
    enabled: false

vertx:
  websocket:
    port: 8081
```

## Dependencies

**agent-kernel (core):**
- **Reactor 3.6** - Reactive Streams (Flux/Mono)
- **OkHttp 4.12** - Async HTTP client
- **Jackson 2.16** - JSON/YAML processing
- **SLF4J 2.0 + Logback** - Logging
- **JUnit 5** - Testing

**agent-mcp:**
- **MCP SDK 0.17.0** - Model Context Protocol

**kernel-memory:**
- **SQLite JDBC 3.45** - Persistent storage

**agent-web:**
- **Spring Boot 3.2.1** - REST/MVC
- **Vert.x 4.5** - WebSocket server

## Testing

```bash
# Run all tests
mvn test

# Integration tests (requires API keys)
export ANTHROPIC_API_KEY=sk-ant-...
mvn verify -P integration-tests
```

## Key Design Decisions

1. **Reactor Flux over custom StreamSource** - Proven reactive library with backpressure, composition, error handling
2. **Tool interface over Plugin system** - Simpler, directly maps to LLM tool calling; Plugin remains as legacy fallback in ToolExecutor
3. **Agent Loop over Kernel pattern** - Pragmatic: detect tool calls → execute → re-prompt, rather than abstract task orchestration
4. **File-based memory with SQLite** - Portable, no external services required, supports hybrid BM25+vector search
5. **Gateway + ChatHandler** - Protocol-agnostic business logic, multiple transports (REST/SSE/WebSocket)
6. **MCP as first-class integration** - Remote tools via McpToolWrapper, progress/log streaming through the reactive pipeline
