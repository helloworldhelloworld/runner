# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Lightweight AI Kernel** is a Java-based AI orchestration framework similar to Microsoft's Semantic Kernel, with enhanced focus on multi-model orchestration, RAG integration, and result fusion.

**Core Design Principles:**
- **Framework-level async/stream abstractions** - not tied to specific implementations
- **Orchestration-first** - compose multiple LLMs, RAG, and external services
- **Unified task execution** - all operations use `AsyncTask<T>` abstraction
- **Generic streaming** - `StreamSource<T>` works for LLMs, APIs, RAG, plugins
- **Multi-module architecture** - Core is lightweight (no Spring), Spring Boot integration is optional
- **Microservice-ready** - WebSocket + REST API support for distributed deployment
- Configuration-driven with API/UI extensibility

## Multi-Module Structure

```
lightweight-ai-kernel/
├── pom.xml                          (Parent POM)
├── kernel-core/                     (Core framework - no Spring)
├── kernel-websocket/                (WebSocket abstraction)
├── kernel-spring-boot-starter/      (Spring Boot auto-configuration)
├── kernel-rest-api/                 (REST API controllers)
└── kernel-example-service/          (Example microservice)
```

## Build Commands

```bash
# Build all modules (from root)
mvn clean install

# Build specific module
cd kernel-core && mvn clean install

# Run all tests
mvn test

# Run single test
mvn test -Dtest=ClassName#methodName

# Package
mvn package

# Skip tests
mvn install -DskipTests

# Run Spring Boot service
cd kernel-example-service && mvn spring-boot:run
```

## Architecture Overview

### Layered Architecture

```
┌──────────────────────────────────────────────────┐
│   Application Layer                              │
│   User Code + Orchestrator + Planner             │
└──────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────┐
│   Orchestration Layer (CRITICAL)                 │
│   • Multi-LLM coordination & result fusion       │
│   • RAG integration & semantic retrieval         │
│   • Workflow composition & decision making       │
│   • External service integration                 │
└──────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────┐
│   Framework Layer (CORE ABSTRACTIONS)            │
│   • AsyncTask<T> - unified task execution        │
│   • StreamSource<T> - generic streaming          │
│   • TaskExecutor - execution strategies          │
│   • ConversationContext - memory management      │
└──────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────┐
│   Provider Layer                                 │
│   LLM Providers + Plugin Registry + Memory Store │
└──────────────────────────────────────────────────┘
                      ↓
┌──────────────────────────────────────────────────┐
│   Infrastructure Layer                           │
│   HTTP (OkHttp) + JSON (Jackson) + Logging       │
└──────────────────────────────────────────────────┘
```

### Package Structure

**Core Framework:**
- `com.lightweightai.kernel.core` - Kernel, AsyncTask, StreamSource, TaskExecutor
- `com.lightweightai.kernel.config` - Configuration framework

**Execution & Orchestration:**
- `com.lightweightai.kernel.orchestration` - Multi-LLM workflows, pipelines
- `com.lightweightai.kernel.fusion` - Result fusion strategies

**LLM & Plugins:**
- `com.lightweightai.kernel.llm` - LLM abstractions (ModelCapability, MessageFormatter, TokenCounter)
- `com.lightweightai.kernel.llm.providers` - Claude, OpenAI, Gemini implementations
- `com.lightweightai.kernel.plugin` - Plugin system

**Memory & RAG:**
- `com.lightweightai.kernel.memory` - Context management, strategies
- `com.lightweightai.kernel.rag` - Vector stores, embeddings, retrieval

**Utilities:**
- `com.lightweightai.kernel.util` - JSON, validation, helpers

## Critical Architectural Concepts

### 1. Framework-Level Task Abstraction (`AsyncTask<T>`)

**ALL operations** (LLM calls, plugin execution, RAG queries, external APIs) implement `AsyncTask<T>`:

```java
public interface AsyncTask<T> {
    T execute();                          // Sync (blocking)
    CompletableFuture<T> executeAsync();  // Async (non-blocking)
    CompletableFuture<T> executeStream(StreamHandler<T> handler); // Streaming
    TaskMetadata getMetadata();           // Timeout, cancellation, etc.
}
```

**Why This Matters:**
- Uniform interface for sync/async/streaming across ALL components
- Not tied to LLM specifics - works for any operation
- External APIs, RAG queries, plugin functions all use the same abstraction
- Enables framework-level execution strategies (parallel, sequential, timeout)

**Example Usage:**
```java
// LLM call as AsyncTask
AsyncTask<LLMResponse> llmTask = new ClaudeLLMTask(messages, options);

// RAG query as AsyncTask
AsyncTask<List<Document>> ragTask = new VectorSearchTask(query, topK);

// Plugin function as AsyncTask
AsyncTask<FunctionResult> pluginTask = new PluginExecutionTask(function, args);

// Execute uniformly
taskExecutor.execute(llmTask);
taskExecutor.executeParallel(List.of(llmTask, ragTask));
```

### 2. Generic Streaming (`StreamSource<T>`)

Streaming is **framework-level**, not just for LLMs:

```java
public interface StreamSource<T> {
    Subscription subscribe(StreamSubscriber<T> subscriber);

    interface StreamSubscriber<T> {
        void onSubscribe(Subscription subscription);
        void onNext(T chunk);           // Stream chunks
        void onComplete();
        void onError(Throwable error);
    }
}
```

**What Can Stream:**
- LLM responses (text deltas)
- RAG results (documents as they're retrieved)
- Plugin outputs (progress updates)
- External API responses (chunked data)
- Orchestration steps (intermediate results)

**Pattern:** Reactive Streams-inspired (like Project Reactor, but lightweight)

### 3. Task Execution Framework (`TaskExecutor`)

Framework manages how tasks execute:

```java
public interface TaskExecutor {
    <T> CompletableFuture<T> execute(AsyncTask<T> task);
    <T> CompletableFuture<List<T>> executeParallel(List<AsyncTask<T>> tasks);
    <T> CompletableFuture<T> executeSequential(List<AsyncTask<T>> tasks);
    <T> CompletableFuture<T> executeWithTimeout(AsyncTask<T> task);
}
```

**Execution Strategies:**
- **Parallel** - Run multiple LLMs/RAG simultaneously, aggregate results
- **Sequential** - Pipeline where one task's output feeds the next
- **With Timeout** - Respect `TaskMetadata.timeout`, cancel if exceeded
- **Retry Logic** - Framework-level retry for failed tasks

**Not bound to implementations** - any `AsyncTask` can use these strategies.

### 4. Multi-LLM Orchestration

Coordinate multiple LLMs for complex workflows:

**Parallel Consensus:**
```java
AsyncTask<LLMResponse> claude = new ClaudeLLMTask(...);
AsyncTask<LLMResponse> gpt4 = new OpenAILLMTask(...);
AsyncTask<LLMResponse> gemini = new GeminiLLMTask(...);

List<LLMResponse> results = taskExecutor.executeParallel(
    List.of(claude, gpt4, gemini)
).join();

LLMResponse consensus = fusionStrategy.fuse(results); // Voting/weighted
```

**Sequential Pipeline:**
```java
// Step 1: Claude analyzes intent
AsyncTask<String> intentTask = new IntentAnalysisTask(userQuery);

// Step 2: RAG retrieves relevant docs based on intent
AsyncTask<List<Document>> ragTask = intentTask.thenCompose(intent ->
    new RAGRetrievalTask(intent)
);

// Step 3: GPT-4 generates answer with retrieved context
AsyncTask<String> answerTask = ragTask.thenCompose(docs ->
    new AnswerGenerationTask(userQuery, docs)
);

String finalAnswer = taskExecutor.executeSequential(
    List.of(intentTask, ragTask, answerTask)
).join();
```

### 5. RAG Integration

RAG components implement framework abstractions:

```java
// Vector search as AsyncTask
public class VectorSearchTask implements AsyncTask<List<Document>> {
    public CompletableFuture<List<Document>> executeAsync() {
        return vectorStore.searchAsync(embedding, topK);
    }
}

// Embedding as AsyncTask
public class EmbeddingTask implements AsyncTask<float[]> {
    public CompletableFuture<float[]> executeAsync() {
        return embeddingProvider.embedAsync(text);
    }
}

// Documents can stream as they're retrieved
public class StreamingRAGTask implements AsyncTask<List<Document>>, StreamSource<Document> {
    public CompletableFuture<List<Document>> executeStream(StreamHandler<List<Document>> handler) {
        // Stream documents one by one as they're found
        subscribe(StreamSource.create(
            doc -> handler.onChunk(doc),
            handler::onError,
            () -> handler.onComplete(allDocs)
        ));
    }
}
```

### 6. Result Fusion Strategies

When multiple LLMs/sources produce results:

```java
public interface FusionStrategy<T> {
    T fuse(List<T> results);
}

// Voting-based
public class VotingFusionStrategy implements FusionStrategy<String> {
    public String fuse(List<String> responses) {
        // Return most common answer
    }
}

// LLM-based synthesis
public class LLMSynthesisFusionStrategy implements FusionStrategy<LLMResponse> {
    public LLMResponse fuse(List<LLMResponse> responses) {
        // Use another LLM to synthesize all responses
        String combined = responses.stream()
            .map(LLMResponse::getText)
            .collect(Collectors.joining("\n"));

        return synthesisLLM.complete("Synthesize: " + combined);
    }
}
```

### 7. External Service Integration

External APIs/services implement `AsyncTask` and optionally `StreamSource`:

```java
// Weather API as AsyncTask
public class WeatherAPITask implements AsyncTask<WeatherData> {
    public CompletableFuture<WeatherData> executeAsync() {
        return httpClient.getAsync(weatherApiUrl)
            .thenApply(json -> parseWeatherData(json));
    }
}

// Database query as AsyncTask with streaming
public class DatabaseQueryTask implements AsyncTask<List<Row>>, StreamSource<Row> {
    public CompletableFuture<List<Row>> executeStream(StreamHandler<List<Row>> handler) {
        // Stream rows as they're fetched
    }
}

// These can be used in orchestration pipelines
taskExecutor.executeSequential(List.of(
    new WeatherAPITask(location),
    new LLMTask(weather -> "Recommend activities for: " + weather)
));
```

### 8. Configuration Framework

Multi-source configuration with priorities:

```yaml
# kernel-config.yaml
llm:
  defaultProvider: "claude"
  providers:
    claude:
      type: "claude"
      apiKey: "${ANTHROPIC_API_KEY}"
      model: "claude-3-5-sonnet-20241022"
      maxTokens: 2048
    gpt4:
      type: "openai"
      apiKey: "${OPENAI_API_KEY}"

orchestration:
  fusionStrategy: "voting"  # voting, weighted, llm-synthesis
  parallelism: 3
  timeout: "5m"

memory:
  strategy:
    type: "sliding"
    config:
      maxMessages: 50
  modelStrategies:
    "claude-3-5-sonnet-20241022":
      type: "sliding"
      config:
        maxMessages: 100  # Claude has large context

rag:
  vectorStore:
    type: "pinecone"  # pinecone, weaviate, chroma
    config:
      apiKey: "${PINECONE_API_KEY}"
      index: "my-index"
  embeddingProvider:
    type: "openai"
    model: "text-embedding-3-small"
```

## Development Workflow

### Adding a New LLM Provider

1. Implement `LLMProvider` interface
2. **Wrap operations as `AsyncTask`:**
   ```java
   public class ClaudeLLMTask implements AsyncTask<LLMResponse> {
       public CompletableFuture<LLMResponse> executeAsync() { ... }
       public CompletableFuture<LLMResponse> executeStream(StreamHandler handler) { ... }
   }
   ```
3. Implement `ModelCapability`, `MessageFormatter`, `TokenCounter`
4. Register in configuration

### Adding External Service Integration

1. Create service client class
2. **Implement `AsyncTask<ResultType>`:**
   ```java
   public class MyAPITask implements AsyncTask<APIResponse> { ... }
   ```
3. Optionally implement `StreamSource<ChunkType>` for streaming
4. Create plugin wrapper if needed for LLM tool calling

### Adding RAG Capability

1. Implement `VectorStore` interface as `AsyncTask`
2. Implement `EmbeddingProvider` interface as `AsyncTask`
3. Create `RAGPlugin` that uses these components
4. Add to configuration

### Adding Orchestration Workflow

1. Define workflow steps as `List<AsyncTask<?>>`
2. Choose execution strategy:
   - Parallel: `taskExecutor.executeParallel(tasks)`
   - Sequential: `taskExecutor.executeSequential(tasks)`
   - Custom: Compose with `thenCompose`, `thenCombine`
3. Apply fusion strategy to results

## Implementation Patterns

### Pattern 1: Async LLM Call
```java
ClaudeLLMTask task = new ClaudeLLMTask(messages, options);
LLMResponse response = task.executeAsync().join();
```

### Pattern 2: Streaming LLM
```java
task.executeStream(new AsyncTask.StreamHandler<>() {
    public void onChunk(Object chunk) {
        System.out.print((String) chunk);  // Print as it streams
    }
    public void onComplete(LLMResponse result) {
        System.out.println("\n\nFull response: " + result);
    }
});
```

### Pattern 3: Multi-LLM with Fusion
```java
List<AsyncTask<LLMResponse>> llmTasks = List.of(
    new ClaudeLLMTask(...),
    new GPT4LLMTask(...),
    new GeminiLLMTask(...)
);

List<LLMResponse> results = taskExecutor.executeParallel(llmTasks).join();
LLMResponse consensus = new VotingFusionStrategy().fuse(results);
```

### Pattern 4: RAG-Enhanced Generation
```java
// Step 1: Embed query
EmbeddingTask embedTask = new EmbeddingTask(userQuery);

// Step 2: Search vector store
VectorSearchTask searchTask = embedTask.thenCompose(embedding ->
    new VectorSearchTask(embedding, topK: 5)
);

// Step 3: Generate answer with context
LLMTask llmTask = searchTask.thenCompose(docs ->
    new LLMTask("Answer using these docs: " + docs)
);

String answer = taskExecutor.executeSequential(
    List.of(embedTask, searchTask, llmTask)
).join();
```

### Pattern 5: External API Integration
```java
// Weather + LLM recommendation pipeline
WeatherAPITask weatherTask = new WeatherAPITask(location);
LLMTask recommendTask = weatherTask.thenCompose(weather ->
    new LLMTask("Recommend activities for: " + weather)
);

String recommendations = taskExecutor.executeSequential(
    List.of(weatherTask, recommendTask)
).join();
```

## Current State (MVP Skeleton)

**Completed:**
- ✅ Framework-level abstractions (AsyncTask, StreamSource, TaskExecutor)
- ✅ Core interfaces (Kernel, LLMProvider, Plugin, ConversationContext)
- ✅ Message model (ConversationMessage, ContentBlock)
- ✅ Configuration framework
- ✅ Package structure

**Next Priority:**
1. Implement `DefaultTaskExecutor` (parallel, sequential, timeout strategies)
2. Implement `ClaudeProvider` + `ClaudeLLMTask` (OkHttp + Jackson)
3. Implement `InMemoryPluginRegistry`
4. Implement `DefaultKernel` with task-based execution
5. Implement basic plugins (Math, Time)
6. Add RAG interfaces (VectorStore, EmbeddingProvider as AsyncTask)
7. Implement fusion strategies

**Future:**
- REST API to expose kernel as service
- WebSocket streaming for real-time updates
- Workflow DSL (YAML-based workflow definitions)
- Visual orchestration UI
- Distributed execution (multiple kernel instances)

## Testing

```bash
# Unit tests with mocks
mvn test

# Integration tests (requires API keys)
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
mvn verify -P integration-tests

# Test async operations
@Test
void testAsyncExecution() {
    AsyncTask<String> task = ...;
    CompletableFuture<String> future = task.executeAsync();
    String result = future.get(10, TimeUnit.SECONDS);
    assertEquals("expected", result);
}

# Test streaming
@Test
void testStreaming() {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> chunks = new ArrayList<>();

    task.executeStream(new StreamHandler<>() {
        public void onChunk(Object chunk) {
            chunks.add((String) chunk);
        }
        public void onComplete(String result) {
            latch.countDown();
        }
    });

    latch.await(30, TimeUnit.SECONDS);
    assertTrue(chunks.size() > 0);
}
```

## Key Design Decisions

1. **AsyncTask over direct CompletableFuture**: Provides metadata, timeout, cancellation support
2. **StreamSource over callback interfaces**: Enables backpressure, composition, reactive patterns
3. **TaskExecutor over ExecutorService**: Higher-level strategies (parallel/sequential/timeout)
4. **Configuration-driven**: All behavior configurable, supports runtime changes
5. **No Spring**: Keep lightweight, explicit dependencies
6. **Async-first**: All I/O operations are async by default, sync is convenience wrapper

## Microservice Architecture

### Module Dependencies

```
kernel-example-service (Spring Boot app)
    ├── kernel-rest-api
    ├── kernel-spring-boot-starter
    └── kernel-websocket

kernel-rest-api (Spring MVC)
    ├── kernel-core
    └── spring-boot-starter-web

kernel-spring-boot-starter
    └── kernel-core

kernel-websocket
    ├── kernel-core
    └── spring-boot-starter-websocket (optional)

kernel-core
    └── no Spring dependencies ✓
```

### WebSocket Support

**Server-Side Protocol:**

```json
// Client → Server: Chat request
{
  "type": "chat",
  "sessionId": "session-123",
  "data": {
    "message": "用户消息",
    "stream": true
  }
}

// Server → Client: Stream delta
{
  "type": "stream_delta",
  "sessionId": "session-123",
  "data": { "delta": "AI回复片段" }
}

// Server → Client: Complete
{
  "type": "stream_complete",
  "sessionId": "session-123",
  "data": {
    "fullText": "完整回复",
    "usage": { "inputTokens": 100, "outputTokens": 50 }
  }
}
```

**WebSocket Endpoint:** `ws://localhost:8080/ws/kernel`

### REST API Endpoints

```
POST   /api/v1/chat              # Synchronous chat
POST   /api/v1/chat/stream       # Streaming chat (SSE)
POST   /api/v1/plugins/{plugin}/functions/{function}  # Execute plugin
GET    /api/v1/sessions/{id}    # Get session info
DELETE /api/v1/sessions/{id}    # Clear session
GET    /api/v1/health            # Health check
```

### Spring Boot Configuration

```yaml
# application.yml
kernel:
  llm:
    providers:
      claude:
        apiKey: ${ANTHROPIC_API_KEY}
        model: claude-3-5-sonnet-20241022
        maxTokens: 2048

  memory:
    storage:
      type: redis  # or inmemory
      config:
        host: localhost
        port: 6379

  execution:
    maxToolCalls: 10
    timeout: 5m

server:
  port: 8080

spring:
  redis:
    host: localhost
    port: 6379
```

### Deployment

**Standalone:**
```bash
cd kernel-example-service
mvn spring-boot:run
```

**Docker:**
```dockerfile
FROM openjdk:17-slim
COPY target/kernel-example-service.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**Multiple Instances (Load Balanced):**
```
Nginx/ALB
    ↓
┌────────┐  ┌────────┐  ┌────────┐
│Service │  │Service │  │Service │
│  :8080 │  │  :8081 │  │  :8082 │
└────┬───┘  └────┬───┘  └────┬───┘
     └──────────┼──────────────┘
                ↓
         [Redis - 共享会话]
```

## Dependencies

**Core Module:**
- **OkHttp 4.12** - Async HTTP client
- **Jackson 2.16** - JSON/YAML parsing
- **SLF4J 2.0** - Logging
- **JUnit 5** - Testing

**Spring Modules:**
- **Spring Boot 3.2.1** - Auto-configuration
- **Spring Web** - REST API
- **Spring WebSocket** - WebSocket support
- **Spring Data Redis** - Session sharing (optional)

Core remains lightweight, Spring integration is opt-in.
