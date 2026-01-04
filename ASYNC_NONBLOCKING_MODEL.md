# 异步非阻塞编程模型 (Async Non-blocking Model)

## 概述

本项目实现了真正的异步非阻塞编程模型，即使在任务有依赖关系的情况下，也不会阻塞线程。这是 Reactive Programming 的核心思想。

## 同步 vs 异步模型对比

### 传统同步阻塞模型

```java
// 当前线程被阻塞，等待结果
LLMResponse response1 = provider.complete(messages, options);

// 仍然阻塞，等待工具执行完成
List<ToolResult> results = toolExecutor.executeToolCalls(response1.getToolCalls());

// 继续阻塞，等待下一次 LLM 调用
LLMResponse response2 = provider.complete(updatedMessages, options);
```

**问题**:
- ❌ 每一步都阻塞当前线程
- ❌ 线程资源浪费（等待 I/O 时线程idle）
- ❌ 低吞吐量（一个线程同时只能处理一个请求）
- ❌ 不可组合（无法与其他异步操作组合）

**执行时序图**:
```
Thread-1: [======LLM Call======][==Tool Exec==][======LLM Call======]
          ^                     ^              ^
          阻塞等待网络          阻塞等待执行    阻塞等待网络
```

### 异步非阻塞模型（本实现）

```java
// 立即返回 CompletableFuture，不阻塞
CompletableFuture<LLMResponse> future = provider.completeAsync(messages, options)
    .thenCompose(response1 -> {
        // 链式异步执行工具
        return toolExecutor.executeToolCallsAsync(response1.getToolCalls());
    })
    .thenCompose(results -> {
        // 继续链式异步调用 LLM
        return provider.completeAsync(updatedMessages, options);
    });

// 主线程可以继续做其他事情，或等待结果
future.thenAccept(finalResponse -> {
    // 处理最终结果（在异步线程上执行）
});
```

**优势**:
- ✅ 不阻塞线程
- ✅ 更高的资源利用率
- ✅ 更高的吞吐量（一个线程可处理多个请求）
- ✅ 可组合（能与其他异步操作链接）

**执行时序图**:
```
Thread-1: [发起LLM] --- [处理response1] --- [发起tool] --- [处理results] --- [发起LLM2]
                 \            /                    \             /              \
Thread-pool:      [===LLM网络请求===]               [===Tool执行===]            [===LLM网络请求===]
                  (不阻塞Thread-1)                  (并行/异步执行)             (不阻塞Thread-1)
```

## ToolCallingLoop 异步实现详解

### API 设计

```java
public class ToolCallingLoop {

    // 同步版本（向后兼容）
    public LLMResponse executeWithTools(
        List<ConversationMessage> messages,
        LLMOptions options
    ) {
        // 阻塞执行...
    }

    // 异步非阻塞版本（新增）
    public CompletableFuture<LLMResponse> executeWithToolsAsync(
        List<ConversationMessage> messages,
        LLMOptions options
    ) {
        return executeWithToolsAsync(new ArrayList<>(messages), options, 0);
    }
}
```

### 核心实现

```java
private CompletableFuture<LLMResponse> executeWithToolsAsync(
    List<ConversationMessage> conversation,
    LLMOptions options,
    int iteration
) {
    // 边界检查（同步）
    if (iteration >= maxIterations) {
        return CompletableFuture.failedFuture(
            new RuntimeException("Exceeded max iterations")
        );
    }

    // Step 1: 异步调用 LLM（不阻塞）
    return provider.completeAsync(conversation, options)
        .thenCompose(response -> {
            // Step 2: 检查是否需要工具（在回调中执行）
            if (!response.hasToolCalls()) {
                return CompletableFuture.completedFuture(response);
            }

            // Step 3: 异步执行工具（不阻塞）
            return toolExecutor.executeToolCallsAsync(response.getToolCalls())
                .thenCompose(toolResults -> {
                    // Step 4: 添加结果到对话
                    conversation.add(response.getMessage());
                    for (ToolResult result : toolResults) {
                        conversation.add(createToolResultMessage(result));
                    }

                    // Step 5: 递归异步调用（关键：保持异步）
                    return executeWithToolsAsync(conversation, options, iteration + 1);
                });
        });
}
```

### 关键技术点

#### 1. `thenCompose` vs `thenApply`

```java
// ❌ 错误：会导致嵌套 CompletableFuture
CompletableFuture<CompletableFuture<Result>> nested = future
    .thenApply(value -> {
        return anotherAsyncOperation(value); // 返回 CompletableFuture
    });

// ✅ 正确：使用 thenCompose 扁平化
CompletableFuture<Result> flat = future
    .thenCompose(value -> {
        return anotherAsyncOperation(value); // 自动扁平化
    });
```

**规则**:
- 如果回调返回**普通值**，用 `thenApply`
- 如果回调返回 **CompletableFuture**，用 `thenCompose`

#### 2. 递归异步调用

```java
// 关键：递归调用返回 CompletableFuture，而不是阻塞等待
return executeWithToolsAsync(conversation, options, iteration + 1);

// ❌ 错误做法（会阻塞）：
CompletableFuture<LLMResponse> future =
    executeWithToolsAsync(conversation, options, iteration + 1);
return future.join(); // join() 会阻塞！
```

#### 3. 异常处理

```java
return provider.completeAsync(messages, options)
    .thenCompose(response -> {
        // 可能抛出异常的操作
        if (someError) {
            throw new RuntimeException("Error");
        }
        return nextAsyncOperation();
    })
    .exceptionally(error -> {
        // 统一处理异步链中的所有异常
        throw new RuntimeException("Chain failed", error);
    });
```

## 性能对比

### 场景：多轮工具调用对话

假设：
- LLM API 调用：500ms
- 工具执行：200ms
- 3 轮对话（LLM → Tool → LLM → Tool → LLM）

#### 同步阻塞模型

```
Round 1: [500ms LLM] + [200ms Tool] = 700ms
Round 2: [500ms LLM] + [200ms Tool] = 700ms
Round 3: [500ms LLM]                = 500ms
-------------------------------------------------
总耗时: 1900ms
线程利用率: ~30% (大部分时间在等待网络)
```

#### 异步非阻塞模型

```
Round 1: [500ms LLM (async)] + [200ms Tool (async)] = 700ms
Round 2: [500ms LLM (async)] + [200ms Tool (async)] = 700ms
Round 3: [500ms LLM (async)]                        = 500ms
-------------------------------------------------
总耗时: 1900ms (单个请求)
但是...

并发 10 个请求时:
同步模型: 1900ms × 10 = 19000ms (需要 10 个线程)
异步模型: ~2000ms (只需少量线程，I/O 操作并发)

线程利用率: ~90% (线程始终在执行回调逻辑)
```

### 吞吐量提升

```
假设服务器有 100 个线程

同步模型:
- 同时处理: 100 个请求
- 吞吐量: 100 / 1.9s = 52.6 req/s

异步模型:
- 同时处理: 1000+ 个请求 (受限于连接数，不是线程数)
- 吞吐量: 1000 / 2s = 500 req/s

提升: ~10x
```

## 使用示例

### 示例 1: 基本异步调用

```java
ToolCallingLoop loop = ToolCallingLoop.builder()
    .provider(claudeProvider)
    .toolExecutor(toolExecutor)
    .build();

List<ConversationMessage> messages = List.of(
    ConversationMessage.builder()
        .role(MessageRole.USER)
        .textContent("What is 10 + 20?")
        .build()
);

// 异步非阻塞调用
CompletableFuture<LLMResponse> future =
    loop.executeWithToolsAsync(messages, options);

// 方式 1: 链式处理结果（推荐）
future.thenAccept(response -> {
    System.out.println("Got response: " + response.getMessage().getTextContent());
    // 在异步线程上执行，不阻塞主线程
});

// 方式 2: 阻塞等待（不推荐，失去异步优势）
LLMResponse response = future.get(); // 阻塞当前线程
```

### 示例 2: 组合多个异步操作

```java
// 串行组合（有依赖）
CompletableFuture<String> result = loop.executeWithToolsAsync(messages, options)
    .thenApply(response -> response.getMessage().getTextContent())
    .thenApply(String::toUpperCase)
    .thenCompose(text -> saveToDatabase(text)) // saveToDatabase 返回 CompletableFuture
    .thenApply(saved -> "Processed: " + saved);

result.thenAccept(System.out::println);

// 并行组合（无依赖）
CompletableFuture<LLMResponse> task1 = loop.executeWithToolsAsync(messages1, options);
CompletableFuture<LLMResponse> task2 = loop.executeWithToolsAsync(messages2, options);
CompletableFuture<LLMResponse> task3 = loop.executeWithToolsAsync(messages3, options);

CompletableFuture.allOf(task1, task2, task3)
    .thenAccept(v -> {
        System.out.println("All tasks completed!");
        System.out.println("Task 1: " + task1.join().getMessage().getTextContent());
        System.out.println("Task 2: " + task2.join().getMessage().getTextContent());
        System.out.println("Task 3: " + task3.join().getMessage().getTextContent());
    });
```

### 示例 3: 错误处理

```java
loop.executeWithToolsAsync(messages, options)
    .thenApply(response -> {
        if (response.getMessage().getTextContent().isEmpty()) {
            throw new RuntimeException("Empty response");
        }
        return response;
    })
    .exceptionally(error -> {
        System.err.println("Error: " + error.getMessage());
        // 返回默认响应
        return createDefaultResponse();
    })
    .thenAccept(response -> {
        // 处理成功结果或默认响应
    });
```

### 示例 4: 超时控制

```java
CompletableFuture<LLMResponse> future =
    loop.executeWithToolsAsync(messages, options);

try {
    // 设置超时：30 秒
    LLMResponse response = future.get(30, TimeUnit.SECONDS);
    System.out.println("Response: " + response.getMessage().getTextContent());
} catch (TimeoutException e) {
    System.err.println("Request timed out");
    future.cancel(true);
} catch (ExecutionException e) {
    System.err.println("Execution failed: " + e.getCause());
}
```

## Agent SDK 集成

虽然 `ToolCallingLoop` 提供了异步版本，但 `Agent` 接口目前仍是同步的。未来可以扩展：

```java
public interface Agent {
    // 现有同步方法
    String chat(String message);

    // 未来扩展：异步方法
    CompletableFuture<String> chatAsync(String message);

    // 未来扩展：Reactive 流
    Publisher<String> chatStream(String message);
}
```

## 测试异步代码

### 测试关键点

```java
@Test
void shouldExecuteAsyncNonBlocking() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<LLMResponse> result = new AtomicReference<>();

    // 启动异步操作
    CompletableFuture<LLMResponse> future =
        loop.executeWithToolsAsync(messages, options);

    // 验证没有立即完成（非阻塞）
    assertFalse(future.isDone());

    // 异步回调
    future.thenAccept(response -> {
        result.set(response);
        latch.countDown();
    });

    // 等待完成
    assertTrue(latch.await(5, TimeUnit.SECONDS));

    // 验证结果
    assertNotNull(result.get());
}
```

### 验证线程切换

```java
@Test
void shouldRunOnDifferentThread() throws Exception {
    String mainThread = Thread.currentThread().getName();
    AtomicReference<String> asyncThread = new AtomicReference<>();

    loop.executeWithToolsAsync(messages, options)
        .thenAccept(response -> {
            asyncThread.set(Thread.currentThread().getName());
        })
        .get();

    // 验证在不同线程上执行
    assertNotEquals(mainThread, asyncThread.get());
    assertTrue(asyncThread.get().contains("ForkJoinPool"));
}
```

## 最佳实践

### ✅ DO

1. **优先使用异步 API**
   ```java
   loop.executeWithToolsAsync(messages, options)
       .thenAccept(this::processResponse);
   ```

2. **链式组合操作**
   ```java
   future.thenCompose(this::nextStep)
         .thenApply(this::transform)
         .thenAccept(this::finalProcess);
   ```

3. **统一异常处理**
   ```java
   future.exceptionally(error -> {
       log.error("Failed", error);
       return defaultValue;
   });
   ```

4. **设置超时**
   ```java
   future.orTimeout(30, TimeUnit.SECONDS)
         .thenAccept(this::process);
   ```

### ❌ DON'T

1. **不要在异步链中阻塞**
   ```java
   // ❌ 错误
   future.thenApply(value -> {
       return anotherFuture.join(); // 阻塞！
   });

   // ✅ 正确
   future.thenCompose(value -> anotherFuture);
   ```

2. **不要忽略异常**
   ```java
   // ❌ 错误：异常会被吞掉
   future.thenAccept(this::process);

   // ✅ 正确
   future.thenAccept(this::process)
         .exceptionally(error -> {
             log.error("Error", error);
             return null;
         });
   ```

3. **不要过度使用 `get()`**
   ```java
   // ❌ 错误：失去异步优势
   LLMResponse response = future.get();

   // ✅ 正确：保持异步
   future.thenAccept(this::handleResponse);
   ```

## 与其他异步框架对比

| 特性 | CompletableFuture | RxJava | Project Reactor | Kotlin Coroutines |
|------|-------------------|--------|-----------------|-------------------|
| Java 原生支持 | ✅ 是 | ❌ 否 | ❌ 否 | ❌ 否（Kotlin） |
| 学习曲线 | 低 | 高 | 高 | 中 |
| 背压支持 | ❌ | ✅ | ✅ | ✅ |
| 操作符丰富度 | 中 | 高 | 高 | 高 |
| 性能 | 优秀 | 优秀 | 优秀 | 优秀 |
| 适用场景 | 异步任务链 | Reactive 流 | Reactive 流 | 协程编程 |

**选择 CompletableFuture 的原因**:
- ✅ Java 标准库，无需额外依赖
- ✅ 学习成本低
- ✅ 与现有 Java 生态系统兼容好
- ✅ 对于 AI Agent 场景足够（不需要复杂的流处理）

## 性能调优

### 1. 线程池配置

```java
// 自定义线程池
ExecutorService executor = Executors.newFixedThreadPool(20);

CompletableFuture<LLMResponse> future = CompletableFuture.supplyAsync(
    () -> loop.executeWithTools(messages, options),
    executor  // 指定线程池
);
```

### 2. 批量并发控制

```java
// 限制并发数量
Semaphore semaphore = new Semaphore(10);

List<CompletableFuture<LLMResponse>> futures = requests.stream()
    .map(request -> CompletableFuture.supplyAsync(() -> {
        semaphore.acquire();
        try {
            return loop.executeWithToolsAsync(request.messages, request.options).get();
        } finally {
            semaphore.release();
        }
    }))
    .collect(Collectors.toList());
```

### 3. 超时与重试

```java
public CompletableFuture<LLMResponse> executeWithRetry(
    List<ConversationMessage> messages,
    LLMOptions options,
    int maxRetries
) {
    return loop.executeWithToolsAsync(messages, options)
        .orTimeout(30, TimeUnit.SECONDS)
        .exceptionally(error -> {
            if (maxRetries > 0) {
                return executeWithRetry(messages, options, maxRetries - 1).join();
            }
            throw new RuntimeException("Max retries exceeded", error);
        });
}
```

## 总结

本实现提供了真正的**异步非阻塞编程模型**，核心特点：

✅ **非阻塞**: 即使任务有依赖，也不阻塞线程
✅ **可组合**: 使用 `thenCompose` 链式组合依赖操作
✅ **高性能**: 更好的线程利用率和吞吐量
✅ **易用性**: 基于 Java 标准 CompletableFuture
✅ **向后兼容**: 保留同步 API，新增异步 API

这是构建高性能、可扩展 AI Agent 系统的关键基础设施！
