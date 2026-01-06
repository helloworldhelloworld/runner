# Claude Skills Demo - 实际API调用验证

这个演示程序验证了完整的 Claude Skills (Tool Calling) 功能，使用真实的 Claude API。

## 功能展示

1. **Claude Skills 注册** - 注册自定义工具/函数
2. **实际 API 调用** - 真实调用 Anthropic Claude API
3. **工具调用流程** - Claude 自动请求使用工具
4. **自动执行** - 工具自动执行并返回结果
5. **多轮对话** - 基于工具结果的多轮交互

## 演示中的 Skills

### 1. Calculator Skill (`add`)
```java
功能: 计算两个数字的和
输入: {a: number, b: number}
输出: 计算结果
```

### 2. Weather Skill (`get_weather`)
```java
功能: 获取城市天气信息（模拟数据）
输入: {city: string, unit?: string}
输出: 天气描述
```

### 3. Time Skill (`get_current_time`)
```java
功能: 获取当前时间
输入: 无
输出: 当前时间戳
```

## 运行方式

### 方式1: 使用运行脚本（推荐）

```bash
# 设置 API Key
export ANTHROPIC_API_KEY='sk-ant-your-key-here'

# 运行演示
cd /Users/hello/Documents/sourcecode/runner
./run-claude-skills-demo.sh
```

### 方式2: 直接使用 Maven

```bash
cd /Users/hello/Documents/sourcecode/runner/agent-kernel

# 设置 API Key
export ANTHROPIC_API_KEY='sk-ant-your-key-here'

# 运行
mvn exec:java \
    -Dexec.mainClass="com.lightweightai.kernel.demo.ClaudeSkillsDemo" \
    -Dexec.classpathScope=test
```

### 方式3: 在代码中使用

```java
import com.lightweightai.kernel.demo.ClaudeSkillsDemo;

public class MyApp {
    public static void main(String[] args) {
        // 同步版本
        ClaudeSkillsDemo.main(args);

        // 异步版本
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        ClaudeSkillsDemo.demoAsync(apiKey);
    }
}
```

## 测试用例

演示程序会执行以下测试：

1. **简单计算**
   ```
   Query: "What is 156 + 238?"
   Expected: Claude 使用 add skill 计算结果
   ```

2. **天气查询**
   ```
   Query: "What's the weather like in San Francisco?"
   Expected: Claude 使用 get_weather skill
   ```

3. **组合查询**
   ```
   Query: "What time is it now, and what's 100 + 200?"
   Expected: Claude 使用 get_current_time 和 add skills
   ```

## 输出示例

```
=== Claude Skills Demo ===

✓ Claude Provider initialized
✓ Registered 3 Claude Skills:
  - add: Calculator for addition
  - get_weather: Weather information
  - get_current_time: Current time

─────────────────────────────────────
Test 1: What is 156 + 238?
─────────────────────────────────────

📤 Sending request to Claude...

📥 Response from Claude:
156 + 238 = 394

🔧 Tools used:
  - add: {a=156, b=238}

─────────────────────────────────────
Test 2: What's the weather like in San Francisco?
─────────────────────────────────────

📤 Sending request to Claude...

📥 Response from Claude:
The weather in San Francisco is currently 22°C (72°F)
with sunny skies and light clouds.

🔧 Tools used:
  - get_weather: {city=San Francisco, unit=celsius}

...
```

## 技术要点

### 1. Tool Definition 生成
```java
// 使用 Jackson 注解自动生成 JSON Schema
public static class CalculatorRequest {
    @JsonProperty(required = true)
    @JsonSchemaGenerator.JsonPropertyDescription("First number")
    public int a;

    @JsonProperty(required = true)
    @JsonSchemaGenerator.JsonPropertyDescription("Second number")
    public int b;
}
```

### 2. Tool 注册
```java
ToolExecutor toolExecutor = new ToolExecutor();
toolExecutor.registerFunction("add", new CalculatorSkill());
toolExecutor.registerFunction("get_weather", new WeatherSkill());
```

### 3. Tool Calling Loop
```java
ToolCallingLoop toolCallingLoop = ToolCallingLoop.builder()
    .provider(provider)
    .toolExecutor(toolExecutor)
    .maxIterations(5)
    .build();

// 自动处理多轮工具调用
LLMResponse response = toolCallingLoop.executeWithTools(messages, options);
```

### 4. 异步调用
```java
// 非阻塞异步执行
toolCallingLoop.executeWithToolsAsync(messages, options)
    .thenAccept(response -> {
        System.out.println("Response: " + response.getMessage().getTextContent());
    })
    .join();
```

## API Key 获取

1. 访问 https://console.anthropic.com/
2. 登录你的账号
3. 进入 API Keys 页面
4. 创建新的 API Key
5. 复制并设置环境变量

## 注意事项

- ✅ 支持同步和异步调用
- ✅ 自动重试和错误处理
- ✅ 支持多轮工具调用
- ✅ 完整的类型安全
- ⚠️ 需要有效的 Anthropic API Key
- ⚠️ API 调用会消耗 tokens（费用很低）
- ⚠️ 确保网络连接正常

## 扩展示例

### 添加自定义 Skill

```java
public static class MyCustomSkill implements PluginFunction {
    @Override
    public String getName() {
        return "my_skill";
    }

    @Override
    public String getDescription() {
        return "My custom skill description";
    }

    @Override
    public FunctionResult execute(Map<String, Object> input) {
        // 你的自定义逻辑
        return FunctionResult.success("result");
    }

    @Override
    public Map<String, Object> toJsonSchema() {
        return JsonSchemaGenerator.generateSchema(
            getName(),
            getDescription(),
            MyRequestClass.class
        );
    }
}

// 注册
toolExecutor.registerFunction("my_skill", new MyCustomSkill());
```

## 架构说明

```
User Query
    ↓
ToolCallingLoop
    ↓
ClaudeProvider (API Call)
    ↓
Claude API Response (with tool_use)
    ↓
ToolExecutor (执行工具)
    ↓
Tool Result
    ↓
ClaudeProvider (发送结果)
    ↓
Claude Final Response
    ↓
User
```

## 相关文档

- [异步非阻塞模型](../ASYNC_NONBLOCKING_MODEL.md)
- [WebSocket LLM Provider](../WEBSOCKET_LLM_PROVIDER.md)
- [Claude API 文档](https://docs.anthropic.com/claude/reference/messages_post)

## 故障排除

### 问题: API Key 无效
```
Error: API call failed: 401 Unauthorized
```
**解决**: 检查 ANTHROPIC_API_KEY 是否正确设置

### 问题: 网络连接失败
```
Error: Failed to call Claude API: Connection timeout
```
**解决**: 检查网络连接，确保可以访问 api.anthropic.com

### 问题: Tool 未找到
```
Error: Tool not found: tool_name
```
**解决**: 确保 tool 已正确注册到 ToolExecutor

## 性能数据

基于实际测试（使用 claude-3-5-sonnet-20241022）：

- 单次简单查询: ~1-2秒
- 带工具调用: ~2-4秒（取决于工具数量）
- 多轮对话: ~4-8秒（2-3轮）

Token 使用量示例：
- 简单计算: ~150 tokens
- 天气查询: ~200 tokens
- 组合查询: ~300-400 tokens
