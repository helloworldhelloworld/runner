# Claude Skills 集成设计

## 📋 需求分析

### Claude Skills 是什么？

Claude Skills 是 Anthropic 提供的工具调用（Tool Use）功能，允许 Claude 在对话中调用外部函数/工具来完成任务。

**核心流程：**
```
用户请求 → Claude 判断需要调用工具 → 返回 tool_use 块
→ 执行工具 → 将结果返回给 Claude → Claude 生成最终回答
```

**Claude API 工具定义格式：**
```json
{
  "name": "get_weather",
  "description": "获取指定城市的天气信息",
  "input_schema": {
    "type": "object",
    "properties": {
      "location": {
        "type": "string",
        "description": "城市名称"
      },
      "unit": {
        "type": "string",
        "enum": ["celsius", "fahrenheit"],
        "description": "温度单位"
      }
    },
    "required": ["location"]
  }
}
```

**Claude 返回的 tool_use：**
```json
{
  "type": "tool_use",
  "id": "toolu_01A09q90qw90lq917835lq9",
  "name": "get_weather",
  "input": {
    "location": "San Francisco",
    "unit": "celsius"
  }
}
```

## 🎯 与现有架构的映射

### 当前 Plugin 设计

```java
public abstract class Plugin {
    public abstract String getName();
    public abstract String getDescription();

    protected void registerFunction(String name,
        Function<Map<String, Object>, Object> handler) {
        // TODO: Implementation needed
    }
}
```

**问题：**
1. ❌ 缺少参数 schema 定义（Claude 需要 input_schema）
2. ❌ 没有类型安全的参数绑定
3. ❌ 没有工具调用循环处理逻辑
4. ❌ 不支持多工具并发调用

## 🏗️ 设计方案

### 方案 1: 增强现有 Plugin（推荐 ⭐）

**核心思路：** 扩展 Plugin 支持 JSON Schema 定义，内部自动生成 Claude 工具格式

#### 1.1 增强 Plugin API

```java
// agent-sdk/src/main/java/com/lightweightai/agent/plugin/Plugin.java
public abstract class Plugin {
    private final Map<String, PluginFunction> functions = new HashMap<>();

    public abstract String getName();
    public abstract String getDescription();

    /**
     * Register a function with parameter schema
     */
    protected <T> void registerFunction(
        String name,
        String description,
        Class<T> parameterType,
        Function<T, Object> handler
    ) {
        PluginFunction function = new PluginFunction(
            name,
            description,
            parameterType,
            handler
        );
        functions.put(name, function);
    }

    /**
     * Get all registered functions
     */
    public Map<String, PluginFunction> getFunctions() {
        return Collections.unmodifiableMap(functions);
    }

    /**
     * Convert to Claude tool definition format
     */
    public List<ClaudeToolDefinition> toClaudeTools() {
        return functions.values().stream()
            .map(PluginFunction::toClaudeToolDefinition)
            .collect(Collectors.toList());
    }
}
```

#### 1.2 PluginFunction 定义

```java
// agent-sdk/src/main/java/com/lightweightai/agent/plugin/PluginFunction.java
public class PluginFunction {
    private final String name;
    private final String description;
    private final Class<?> parameterType;
    private final Function<Object, Object> handler;
    private final JsonSchema schema;

    public PluginFunction(String name, String description,
                          Class<?> paramType, Function<?, Object> handler) {
        this.name = name;
        this.description = description;
        this.parameterType = paramType;
        this.handler = (Function<Object, Object>) handler;
        this.schema = JsonSchemaGenerator.generate(paramType); // 自动生成
    }

    public Object execute(Map<String, Object> input) {
        // 将 Map 转换为强类型参数对象
        Object param = JsonMapper.fromMap(input, parameterType);
        return handler.apply(param);
    }

    public ClaudeToolDefinition toClaudeToolDefinition() {
        return ClaudeToolDefinition.builder()
            .name(name)
            .description(description)
            .inputSchema(schema)
            .build();
    }
}
```

#### 1.3 使用示例

```java
public class WeatherPlugin extends Plugin {

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "获取天气信息";
    }

    public WeatherPlugin() {
        // 类型安全的函数注册
        registerFunction(
            "get_weather",
            "获取指定城市的天气",
            WeatherRequest.class,  // 强类型参数
            this::getWeather
        );

        registerFunction(
            "get_forecast",
            "获取未来天气预报",
            ForecastRequest.class,
            this::getForecast
        );
    }

    private WeatherResponse getWeather(WeatherRequest request) {
        // 类型安全的实现
        return new WeatherResponse(
            request.getLocation(),
            25.0,
            request.getUnit()
        );
    }

    private ForecastResponse getForecast(ForecastRequest request) {
        // ...
    }
}

// 参数定义（使用 Jackson 注解）
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeatherRequest {
    @JsonProperty(required = true)
    @JsonPropertyDescription("城市名称")
    private String location;

    @JsonPropertyDescription("温度单位")
    private String unit = "celsius";

    // getters/setters
}
```

### 方案 2: 支持 Annotation 方式（可选）

```java
@Tool(name = "get_weather", description = "获取天气")
public class WeatherPlugin extends Plugin {

    @ToolFunction(description = "获取指定城市天气")
    public WeatherResponse getWeather(
        @Param(description = "城市名称", required = true) String location,
        @Param(description = "温度单位") String unit
    ) {
        // ...
    }
}
```

**优点：** 更简洁，自动扫描
**缺点：** 需要反射，增加复杂度

## 🔄 Tool Calling 执行流程

### 完整调用链路

```
1. 用户发送消息 → Agent.chat("今天旧金山天气怎么样?")

2. Agent 准备请求
   - 收集所有 Plugin 的工具定义
   - 转换为 Claude 工具格式
   - 发送到 Claude API

3. Claude 返回 tool_use
   {
     "type": "tool_use",
     "id": "toolu_xxx",
     "name": "get_weather",
     "input": {"location": "San Francisco", "unit": "celsius"}
   }

4. Agent 执行工具
   - 根据 name 找到对应的 Plugin 和 Function
   - 执行 function.execute(input)
   - 获取结果

5. Agent 发送 tool_result 回 Claude
   {
     "type": "tool_result",
     "tool_use_id": "toolu_xxx",
     "content": "{\"temperature\": 18, \"condition\": \"sunny\"}"
   }

6. Claude 生成最终回答
   "旧金山今天天气晴朗，温度18摄氏度。"

7. 返回给用户
```

### 核心实现

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/core/ToolExecutor.java
public class ToolExecutor {

    private final Map<String, Plugin> plugins;

    /**
     * Execute a tool call from Claude
     */
    public ToolResult executeToolCall(ToolUse toolUse) {
        try {
            // 1. Find the plugin
            Plugin plugin = findPluginForTool(toolUse.getName());
            if (plugin == null) {
                return ToolResult.error(toolUse.getId(),
                    "Tool not found: " + toolUse.getName());
            }

            // 2. Get the function
            PluginFunction function = plugin.getFunctions().get(toolUse.getName());
            if (function == null) {
                return ToolResult.error(toolUse.getId(),
                    "Function not found: " + toolUse.getName());
            }

            // 3. Execute
            Object result = function.execute(toolUse.getInput());

            // 4. Serialize result
            String resultJson = JsonMapper.toJson(result);

            return ToolResult.success(toolUse.getId(), resultJson);

        } catch (Exception e) {
            return ToolResult.error(toolUse.getId(),
                "Execution failed: " + e.getMessage());
        }
    }

    /**
     * Handle tool calling loop
     */
    public LLMResponse handleToolCallingLoop(
        List<ConversationMessage> messages,
        LLMProvider provider,
        List<ClaudeToolDefinition> tools,
        int maxIterations
    ) {
        int iteration = 0;
        List<ConversationMessage> conversation = new ArrayList<>(messages);

        while (iteration < maxIterations) {
            // Call LLM
            LLMResponse response = provider.complete(conversation,
                LLMOptions.builder().tools(tools).build());

            // Check if needs tool calling
            if (!response.hasToolUse()) {
                return response; // Done
            }

            // Execute all tool calls
            List<ToolUse> toolUses = response.getToolUses();
            List<ToolResult> toolResults = toolUses.stream()
                .map(this::executeToolCall)
                .collect(Collectors.toList());

            // Add assistant message with tool_use
            conversation.add(ConversationMessage.assistant(response.getContent()));

            // Add user message with tool_result
            conversation.add(ConversationMessage.toolResults(toolResults));

            iteration++;
        }

        throw new AgentException("Tool calling loop exceeded max iterations: " + maxIterations);
    }
}
```

## 🎨 架构层次

### 分层设计

```
┌─────────────────────────────────────┐
│  用户应用                            │
│  Agent.chat("天气怎么样?")           │
└─────────────────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│  agent-sdk (公共 API)               │
│  • Plugin (抽象类)                   │
│  • PluginFunction (工具函数)        │
│  • WeatherPlugin (用户实现)         │
└─────────────────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│  agent-kernel (内部框架)            │
│  • ToolExecutor (工具执行器)        │
│  • ClaudeProvider (LLM 集成)        │
│  • ToolCallingLoop (调用循环)       │
└─────────────────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│  Claude API                         │
│  • POST /v1/messages                │
│  • tools: [...]                     │
└─────────────────────────────────────┘
```

## 📊 关键类设计

### ClaudeToolDefinition

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/llm/claude/ClaudeToolDefinition.java
public class ClaudeToolDefinition {
    private final String name;
    private final String description;
    private final JsonSchema inputSchema;

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toApiFormat() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", inputSchema.toMap());
        return tool;
    }
}
```

### ToolUse (Claude 响应)

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/llm/ToolUse.java
public class ToolUse {
    private final String id;           // toolu_xxx
    private final String name;         // get_weather
    private final Map<String, Object> input;  // {location: "SF"}

    // Parse from Claude response content block
    public static ToolUse fromContentBlock(Map<String, Object> block) {
        return new ToolUse(
            (String) block.get("id"),
            (String) block.get("name"),
            (Map<String, Object>) block.get("input")
        );
    }
}
```

### ToolResult

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/llm/ToolResult.java
public class ToolResult {
    private final String toolUseId;
    private final String content;
    private final boolean isError;

    public static ToolResult success(String id, String content) {
        return new ToolResult(id, content, false);
    }

    public static ToolResult error(String id, String errorMessage) {
        return new ToolResult(id, errorMessage, true);
    }

    public Map<String, Object> toApiFormat() {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "tool_result");
        result.put("tool_use_id", toolUseId);
        result.put("content", content);
        if (isError) {
            result.put("is_error", true);
        }
        return result;
    }
}
```

## 🔧 JSON Schema 自动生成

### JsonSchemaGenerator

```java
// agent-kernel/src/main/java/com/lightweightai/kernel/plugin/JsonSchemaGenerator.java
public class JsonSchemaGenerator {

    private final ObjectMapper mapper = new ObjectMapper();

    public static JsonSchema generate(Class<?> type) {
        JsonSchemaGenerator generator = new JsonSchemaGenerator();
        return generator.generateSchema(type);
    }

    private JsonSchema generateSchema(Class<?> type) {
        // 使用 Jackson 注解生成 JSON Schema
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (Field field : type.getDeclaredFields()) {
            JsonProperty prop = field.getAnnotation(JsonProperty.class);
            if (prop == null) continue;

            Map<String, Object> fieldSchema = new HashMap<>();
            fieldSchema.put("type", mapJavaTypeToJsonType(field.getType()));

            JsonPropertyDescription desc = field.getAnnotation(JsonPropertyDescription.class);
            if (desc != null) {
                fieldSchema.put("description", desc.value());
            }

            properties.put(field.getName(), fieldSchema);

            if (prop.required()) {
                required.add(field.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return new JsonSchema(schema);
    }

    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == Integer.class || javaType == int.class) return "integer";
        if (javaType == Double.class || javaType == double.class) return "number";
        if (javaType == Boolean.class || javaType == boolean.class) return "boolean";
        if (javaType.isArray() || Collection.class.isAssignableFrom(javaType)) return "array";
        return "object";
    }
}
```

## 🚀 MVP 实现优先级

### Phase 1: 核心功能 (P0)

1. ✅ **Plugin 增强**
   - `PluginFunction` 类
   - `registerFunction()` 实现
   - `toClaudeTools()` 转换

2. ✅ **JSON Schema 生成**
   - `JsonSchemaGenerator`
   - 基于 Jackson 注解

3. ✅ **Tool Execution**
   - `ToolExecutor`
   - `executeToolCall()`
   - 类型安全的参数绑定

4. ✅ **Tool Calling Loop**
   - `handleToolCallingLoop()`
   - 多轮对话处理
   - 最大迭代限制

### Phase 2: 增强功能 (P1)

5. ⏳ **并发工具调用**
   - 同时执行多个工具
   - CompletableFuture 并行

6. ⏳ **工具调用缓存**
   - 相同参数缓存结果
   - TTL 过期策略

7. ⏳ **错误处理**
   - 工具执行超时
   - 参数验证
   - 重试策略

### Phase 3: 高级特性 (P2)

8. ⏳ **Streaming 支持**
   - 工具调用中间结果流式返回
   - 实时反馈

9. ⏳ **工具权限控制**
   - 危险工具确认
   - 用户授权

10. ⏳ **工具组合**
    - 工具链（Tool Chain）
    - 自动编排

## 📝 使用示例

### 完整示例

```java
// 1. 定义插件
public class WeatherPlugin extends Plugin {
    @Override
    public String getName() { return "weather"; }

    @Override
    public String getDescription() { return "天气服务"; }

    public WeatherPlugin() {
        registerFunction(
            "get_weather",
            "获取当前天气",
            WeatherRequest.class,
            this::getWeather
        );
    }

    private WeatherResponse getWeather(WeatherRequest req) {
        // 实际调用天气 API
        return new WeatherResponse(req.getLocation(), 25.0, "sunny");
    }
}

// 2. 使用 Agent
Agent agent = Agent.builder()
    .claude(apiKey)
    .plugin(new WeatherPlugin())
    .build();

// 3. 对话（自动调用工具）
String response = agent.chat("旧金山今天天气怎么样？");
// Claude 自动调用 get_weather 工具，然后生成回答
// 输出: "旧金山今天天气晴朗，温度25摄氏度。"
```

## 🎯 总结

### 核心价值

1. **类型安全** - 强类型参数，编译时检查
2. **自动化** - JSON Schema 自动生成
3. **透明** - 用户无需关心工具调用细节
4. **扩展** - 易于添加新工具

### 与现有架构的契合度

✅ **完全契合：**
- Plugin 系统已设计，只需增强
- 不影响现有 API
- 内部实现对用户透明
- 符合 TDD 开发流程

### 实现工作量

- **新增类：** 8 个
  - PluginFunction
  - JsonSchemaGenerator
  - JsonSchema
  - ToolExecutor
  - ClaudeToolDefinition
  - ToolUse
  - ToolResult
  - ToolCallingLoop

- **修改类：** 2 个
  - Plugin (增加 registerFunction 实现)
  - ClaudeProvider (增加 tool calling 支持)

- **新增测试：** 6 个
  - PluginFunctionTest
  - JsonSchemaGeneratorTest
  - ToolExecutorTest
  - ToolCallingLoopTest
  - WeatherPluginIntegrationTest
  - ToolCallingE2ETest

**预估时间：** Phase 1 核心功能 - 完整实现约 2-3 天工作量（TDD 开发）

---

**下一步：** 是否开始实现 Phase 1 核心功能？
