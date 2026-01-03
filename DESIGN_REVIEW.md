# 框架设计Review

## 🔍 整体架构评审

### ✅ 优点

#### 1. 分层清晰
```
用户应用 → agent-sdk (公共API) → agent-kernel (内部框架) → Providers
```
- 关注点分离良好
- 依赖方向正确（向内依赖）
- 用户只看到简洁的API

#### 2. 抽象层次合理
```
高层：Agent, Plugin (用户概念)
中层：Kernel, LLMProvider (编排层)
低层：AsyncTask, StreamSource (框架抽象)
```

#### 3. 扩展性设计
- 接口驱动（Interface-based）
- 策略模式（ContextStrategy, FusionStrategy）
- 工厂模式（ModelCapability, MessageFormatter）

### ❌ 问题和改进建议

## 1. 🚨 AsyncTask设计问题

### 问题：抽象过度复杂

**当前设计：**
```java
public interface AsyncTask<T> {
    T execute();                          // 同步
    CompletableFuture<T> executeAsync();  // 异步
    CompletableFuture<T> executeStream(StreamHandler<T> handler); // 流式
    TaskMetadata getMetadata();
}
```

**问题分析：**
1. **违反接口隔离原则（ISP）** - 一个接口包含3种执行模式
2. **实现者负担重** - 每个实现都要提供3种模式
3. **语义混乱** - `executeStream`返回`CompletableFuture<T>`但同时有`StreamHandler`回调

**改进建议：**
```java
// 方案1: 分离接口
public interface Task<T> {
    T execute();
}

public interface AsyncTask<T> extends Task<T> {
    CompletableFuture<T> executeAsync();
}

public interface StreamableTask<T> {
    Publisher<T> stream();  // 使用响应式流
}

// 方案2: 使用组合而非继承
public interface Task<T> {
    T execute();

    // 可选能力
    default boolean supportsAsync() { return false; }
    default boolean supportsStreaming() { return false; }
}

public interface AsyncCapability<T> {
    CompletableFuture<T> executeAsync();
}

public interface StreamCapability<T> {
    Publisher<T> stream();
}
```

**推荐：方案2（组合）**
- 更灵活
- 实现者只需实现需要的能力
- 运行时检查能力

## 2. 🚨 StreamSource vs StreamCallback 冲突

### 问题：两套流式抽象

**当前设计：**
```java
// 框架层：响应式流风格
public interface StreamSource<T> {
    Subscription subscribe(StreamSubscriber<T> subscriber);
}

// 公共API：回调风格
public interface StreamCallback {
    void onToken(String token);
    void onComplete(String fullText);
    void onError(Throwable error);
}
```

**问题：**
1. **抽象不一致** - 内部用Publisher/Subscriber，外部用Callback
2. **转换成本** - 需要在两者之间转换
3. **功能冗余** - StreamCallback无法控制背压

**改进建议：**

```java
// 方案1: 统一为响应式流（对高级用户）
public interface Agent {
    Publisher<String> chatStream(String message);
}

// 使用
agent.chatStream("Hello").subscribe(new Subscriber<String>() {
    public void onNext(String token) { ... }
    public void onComplete() { ... }
});

// 方案2: 提供两层API（推荐）
public interface Agent {
    // 简单API - 回调
    void chatStream(String message, StreamCallback callback);

    // 高级API - 响应式流
    Publisher<ChatEvent> chatStreamAdvanced(String message);
}

// ChatEvent可以是token、tool_call、完成等不同事件
```

**推荐：方案2**
- 简单场景用回调（大多数用户）
- 复杂场景用响应式流（需要背压控制）

## 3. 🚨 Plugin设计不够灵活

### 问题：注册机制不清晰

**当前设计：**
```java
public abstract class Plugin {
    public abstract String getName();
    public abstract String getDescription();

    protected void registerFunction(String name, Function<Map<String, Object>, Object> handler) {
        // TODO: 实现函数注册逻辑
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
```

**问题：**
1. **状态管理不清楚** - 函数注册到哪里？
2. **类型不安全** - `Map<String, Object>`丢失类型信息
3. **缺少元数据** - 如何生成JSON Schema？
4. **构造函数时序问题** - `registerFunction`在构造函数调用，但父类可能未初始化

**改进建议：**

```java
// 方案1: Builder模式
public abstract class Plugin {
    private final List<PluginFunction> functions = new ArrayList<>();

    protected Plugin() {
        configure();
    }

    // 子类覆盖此方法注册函数
    protected abstract void configure();

    // 类型安全的Builder
    protected <T> FunctionBuilder<T> function(String name, Class<T> resultType) {
        return new FunctionBuilder<>(name, resultType, this.functions);
    }
}

// 使用
public class MathPlugin extends Plugin {
    @Override
    protected void configure() {
        function("add", Double.class)
            .description("Add two numbers")
            .parameter("a", Double.class, "First number")
            .parameter("b", Double.class, "Second number")
            .handler(args -> args.get("a") + args.get("b"));
    }
}

// 方案2: 注解+反射（类似Spring）
public class MathPlugin extends Plugin {
    @FunctionDef(
        name = "add",
        description = "Add two numbers"
    )
    public double add(
        @Param("a") double a,
        @Param("b") double b
    ) {
        return a + b;
    }
}
```

**推荐：两者都支持**
- 方案1用于运行时动态注册
- 方案2用于声明式定义（需要反射扫描）

## 4. 🚨 ConversationMemory职责不清

### 问题：接口过于简单

**当前设计：**
```java
public interface ConversationMemory {
    int getMessageCount();
    void clear();
}
```

**问题：**
1. **功能不足** - 无法获取消息、添加消息
2. **与ConversationContext重复** - 两者职责重叠
3. **缺少关键方法** - 无法查询历史、分页等

**改进建议：**

```java
// 方案1: 合并到ConversationContext
// 删除ConversationMemory接口，统一使用ConversationContext

// 方案2: 重新定义职责
public interface ConversationMemory {
    // 查询
    List<ConversationMessage> getMessages();
    List<ConversationMessage> getMessages(int limit);
    ConversationMessage getMessageById(String id);

    // 修改
    void addMessage(ConversationMessage message);
    void clear();

    // 统计
    int getMessageCount();
    int estimateTokens(TokenCounter counter);

    // 持久化
    void save();
    void load();
}

// ConversationContext专注于上下文优化
public interface ConversationContext {
    List<ConversationMessage> prepareForModel(ModelCapability model);
    ConversationContext branch(String fromMessageId);
}
```

**推荐：方案2（职责分离）**
- Memory负责存储
- Context负责优化

## 5. 🚨 LLMProvider接口设计问题

### 问题：同步异步都暴露

**当前设计：**
```java
public interface LLMProvider {
    LLMResponse complete(List<ConversationMessage> messages, LLMOptions options);
    CompletableFuture<LLMResponse> completeAsync(...);
    CompletableFuture<LLMResponse> completeStream(..., StreamEventHandler handler);
}
```

**问题：**
1. **实现负担** - 每个Provider都要实现3个方法
2. **同步方法阻塞** - 同步方法会阻塞线程
3. **重复逻辑** - 同步通常是异步的`.join()`

**改进建议：**

```java
// 方案1: 只保留异步接口
public interface LLMProvider {
    CompletableFuture<LLMResponse> complete(List<ConversationMessage> messages, LLMOptions options);
    Publisher<ChatEvent> stream(List<ConversationMessage> messages, LLMOptions options);
}

// 同步方法在Agent层提供
public interface Agent {
    String chat(String message) {
        return chatAsync(message).join();  // 用户层转同步
    }

    CompletableFuture<String> chatAsync(String message);
}

// 方案2: 默认方法
public interface LLMProvider {
    // 核心方法 - 异步
    CompletableFuture<LLMResponse> completeAsync(...);

    // 便捷方法 - 默认实现
    default LLMResponse complete(...) {
        return completeAsync(...).join();
    }
}
```

**推荐：方案2**
- Provider只需实现异步版本
- 同步方法自动提供

## 6. 🚨 配置框架类型安全问题

### 问题：泛型配置丢失类型

**当前设计：**
```java
public class MemoryConfiguration {
    public static class StrategyConfig {
        private String type = "sliding";
        private Map<String, Object> config = new HashMap<>();  // ❌ 类型丢失
    }
}
```

**问题：**
1. **类型不安全** - `Map<String, Object>`无法编译检查
2. **配置错误运行时才发现** - 如`config.put("maxMessages", "abc")`
3. **IDE支持差** - 无法自动补全

**改进建议：**

```java
// 方案1: 每种策略专门的配置类
public abstract class StrategyConfig {
    private final String type;

    protected StrategyConfig(String type) {
        this.type = type;
    }
}

public class SlidingWindowConfig extends StrategyConfig {
    private int maxMessages = 50;
    private boolean keepSystemMessage = true;

    public SlidingWindowConfig() {
        super("sliding");
    }

    // 类型安全的getters/setters
}

public class SummarizationConfig extends StrategyConfig {
    private int triggerThreshold = 100000;
    private int keepRecentCount = 20;

    public SummarizationConfig() {
        super("summarization");
    }
}

// 使用
MemoryConfiguration memConfig = new MemoryConfiguration();
memConfig.setStrategy(new SlidingWindowConfig()
    .setMaxMessages(100)
    .setKeepSystemMessage(true)
);

// 方案2: 泛型配置
public class MemoryConfiguration<T extends StrategyConfig> {
    private T strategy;

    public void setStrategy(T strategy) {
        this.strategy = strategy;
    }
}
```

**推荐：方案1**
- 完全类型安全
- IDE友好
- 编译期检查

## 7. 🚨 错误处理缺失

### 问题：没有统一的异常体系

**当前状态：**
- 没有自定义异常类
- 使用通用的`RuntimeException`
- 错误信息不结构化

**改进建议：**

```java
// 异常层次
public class AgentException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> context;

    public AgentException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.context = new HashMap<>();
    }
}

public enum ErrorCode {
    // LLM相关
    LLM_API_ERROR(1001, "LLM API调用失败"),
    LLM_TIMEOUT(1002, "LLM调用超时"),
    LLM_RATE_LIMIT(1003, "超出速率限制"),

    // Plugin相关
    PLUGIN_NOT_FOUND(2001, "插件未找到"),
    PLUGIN_EXECUTION_ERROR(2002, "插件执行失败"),

    // 配置相关
    CONFIG_INVALID(3001, "配置无效"),
    CONFIG_MISSING(3002, "缺少必要配置"),

    // 通用
    INTERNAL_ERROR(9999, "内部错误");

    private final int code;
    private final String defaultMessage;
}

// 具体异常
public class LLMException extends AgentException { ... }
public class PluginException extends AgentException { ... }
public class ConfigException extends AgentException { ... }

// 使用
try {
    llmProvider.complete(...);
} catch (LLMException e) {
    if (e.getCode() == ErrorCode.LLM_RATE_LIMIT) {
        // 重试逻辑
    }
}
```

## 8. 🚨 资源管理问题

### 问题：缺少生命周期管理

**潜在问题：**
```java
Agent agent = Agent.builder()
    .claude(apiKey)
    .build();

agent.chat("Hello");
// ❌ 谁关闭HTTP连接？
// ❌ 谁清理内存？
// ❌ Agent用完了怎么释放资源？
```

**改进建议：**

```java
// 方案1: 实现AutoCloseable
public interface Agent extends AutoCloseable {
    String chat(String message);

    @Override
    void close();  // 释放资源
}

// 使用
try (Agent agent = Agent.builder().claude(apiKey).build()) {
    agent.chat("Hello");
}  // 自动关闭

// 方案2: 显式生命周期管理
public interface Agent {
    void initialize();  // 初始化资源
    void shutdown();    // 关闭资源
    boolean isActive(); // 检查状态
}

// 方案3: Spring集成时用@PreDestroy
@Component
public class AgentService {
    private final Agent agent;

    @PreDestroy
    public void cleanup() {
        agent.close();
    }
}
```

**推荐：方案1（AutoCloseable）**
- Java标准做法
- try-with-resources支持
- 用户习惯

## 9. 🚨 并发安全问题

### 问题：没有考虑线程安全

**潜在问题：**
```java
Agent agent = Agent.builder().claude(apiKey).build();

// 多线程使用
executor.submit(() -> agent.chat("Hello"));  // 线程1
executor.submit(() -> agent.chat("World"));  // 线程2
// ❌ ConversationMemory是否线程安全？
// ❌ Plugin状态是否有竞争？
```

**改进建议：**

```java
// 方案1: 文档化线程安全保证
/**
 * Agent是线程安全的，可以被多个线程并发调用。
 * 但每个会话(conversation)应该在单线程中处理。
 */
public interface Agent { ... }

// 方案2: 会话隔离
public interface Agent {
    // 创建新会话（线程隔离）
    Conversation createConversation();
}

public interface Conversation extends AutoCloseable {
    String chat(String message);  // 单线程使用
}

// 使用
Agent agent = Agent.builder().build();  // 共享，线程安全

// 每个线程自己的会话
Conversation conv1 = agent.createConversation();
Conversation conv2 = agent.createConversation();

// 方案3: 不可变设计
public final class Agent {
    private final LLMProvider provider;  // final
    private final List<Plugin> plugins;  // immutable

    // 所有状态都不可变
}
```

**推荐：方案2 + 方案3组合**
- Agent不可变（线程安全）
- Conversation隔离状态

## 10. 🚨 测试设计问题

### 问题：缺少Mock基础设施

**当前状态：**
- 测试创建真实Agent但无法真正调用
- 没有Mock LLMProvider
- 难以测试边界情况

**改进建议：**

```java
// 创建测试工具类
public class TestAgents {
    // Mock LLM Provider
    public static Agent mockAgent() {
        return Agent.builder()
            .withProvider(new MockLLMProvider())
            .build();
    }

    // 可配置响应
    public static Agent mockAgent(String... responses) {
        return Agent.builder()
            .withProvider(new MockLLMProvider(responses))
            .build();
    }
}

public class MockLLMProvider implements LLMProvider {
    private Queue<String> responses;

    public MockLLMProvider(String... responses) {
        this.responses = new LinkedList<>(Arrays.asList(responses));
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(...) {
        String response = responses.poll();
        return CompletableFuture.completedFuture(
            LLMResponse.of(response)
        );
    }
}

// 测试
@Test
void shouldHandleMultiTurnConversation() {
    Agent agent = TestAgents.mockAgent(
        "Hello! I'm Claude.",
        "Sure, 123 + 456 = 579"
    );

    assertEquals("Hello! I'm Claude.", agent.chat("Hi"));
    assertEquals("Sure, 123 + 456 = 579", agent.chat("Calculate 123+456"));
}
```

## 📊 设计模式使用评审

### ✅ 使用得当的模式

1. **Builder模式** - `AgentBuilder`
   - ✅ 流畅API
   - ✅ 参数可选
   - ✅ 验证集中

2. **策略模式** - `ContextStrategy`, `FusionStrategy`
   - ✅ 算法可替换
   - ✅ 开闭原则

3. **适配器模式** - `MessageFormatter`
   - ✅ 统一不同LLM的消息格式

### ⚠️ 可能过度设计的地方

1. **AsyncTask** - 太通用，实际只用于LLM调用
2. **StreamSource** - 功能与StreamCallback重复
3. **TaskExecutor** - 当前MVP可能用不到

### 💡 缺少的模式

1. **观察者模式** - Agent事件（开始、完成、错误）
2. **责任链模式** - Plugin执行链、中间件
3. **单例模式** - Provider可能需要连接池

## 🎯 优先级改进建议

### P0 - 必须修复（影响可用性）

1. ✅ **实现Plugin.registerFunction()** - 当前抛异常
2. ✅ **实现DefaultAgent.chat()** - 当前抛异常
3. ✅ **添加异常体系** - 当前无结构化错误
4. ✅ **添加资源管理** - 实现AutoCloseable

### P1 - 应该修复（影响质量）

5. ✅ **统一流式抽象** - StreamSource vs StreamCallback
6. ✅ **改进Plugin设计** - 类型安全+Builder
7. ✅ **配置类型安全** - 专门的Config类
8. ✅ **并发安全文档** - 明确线程安全保证

### P2 - 可以改进（优化体验）

9. ⭕ **简化AsyncTask** - 接口隔离
10. ⭕ **测试基础设施** - Mock工具类
11. ⭕ **添加observability** - 日志、指标、追踪

## 💡 架构演进建议

### 阶段1: MVP修复（当前）
- 修复P0问题
- 实现基本功能
- 添加集成测试

### 阶段2: API稳定
- 修复P1问题
- 完善文档
- 性能测试

### 阶段3: 生产就绪
- 修复P2问题
- 添加监控
- 安全审计

### 阶段4: 企业级
- 分布式支持
- 高可用
- 可观测性

## 总结

### 优势 ✅
1. 分层清晰，关注点分离
2. 用户API简洁（5行代码）
3. 扩展性好（接口驱动）
4. TDD保证质量

### 问题 ❌
1. 部分接口过度设计（AsyncTask）
2. 流式抽象不一致
3. 缺少错误处理体系
4. 资源管理缺失
5. 并发安全未明确

### 建议 💡
1. **先实现核心功能**，验证设计
2. **渐进式重构**，不要一次性改完
3. **保持API稳定**，内部可以重构
4. **补充文档**，特别是线程安全保证

**整体评价：7.5/10**
- 架构思路正确，但细节需要打磨
- SDK设计优秀，但内部框架可以简化
- 建议先做减法（简化），再做加法（扩展）
