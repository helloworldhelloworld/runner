package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.StreamEventHandler;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.tools.math.MathTools;
import com.lightweightai.tools.time.TimeTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo 1: 本地工具注册与模型调用
 *
 * 演示两种本地工具定义方式，以及完整的 ToolCallingLoop 流程：
 *   用户消息 → LLM → tool_use → ToolExecutor 执行 → 结果回传 → LLM → 最终回复
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.LocalToolDemo
 */
public class LocalToolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 1: Local Tool Registration");
        System.out.println("========================================\n");

        // ── Step 1: 创建 ToolRegistry ──
        ToolRegistry registry = new ToolRegistry();

        // ── Step 2a: 注解方式注册（推荐，最简洁） ──
        //   写一个普通类 + @ToolFunction + @ToolParam，就是一个工具
        registry.registerObject(new MathTools());       // 内置：add, multiply, divide
        registry.registerObject(new TimeTools());       // 内置：get_time
        registry.registerObject(new WeatherTool());     // 自定义工具（注解方式）

        // ── Step 2b: 接口方式注册（最灵活） ──
        //   适合需要复杂初始化、有状态、自定义 schema 的场景
        registry.register(new RandomNumberTool());      // 自定义工具（接口方式）

        // ── Step 2c: SPI 自动扫描（零代码注册） ──
        //   classpath 上有 META-INF/services/com.lightweightai.kernel.agent.ToolSource 时
        //   一行代码即可发现所有工具：
        //   registry.scanAndRegister();

        // ── Step 3: ToolExecutor 包装 registry，供 ToolCallingLoop 使用 ──
        ToolExecutor executor = new ToolExecutor(registry);

        System.out.println("[Registry] Registered " + executor.getFunctionCount() + " tools:");
        executor.getToolDefinitions().forEach(def ->
            System.out.println("  - " + def.get("name") + ": " + def.get("description"))
        );

        // ── Step 4: 构建 ToolCallingLoop ──
        //   实际项目中替换 MockLLMProvider 为：
        //     new ClaudeProvider(apiKey, "claude-sonnet-4-20250514")
        //     new OpenRouterProvider(apiKey, "anthropic/claude-3.5-sonnet")
        LLMProvider llm = new MockLLMProvider("add", Map.of("a", 42, "b", 58));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(llm)
            .toolExecutor(executor)
            .maxIterations(5)
            .build();

        // ── Step 5: 发送消息，触发工具调用循环 ──
        System.out.println("\n--- ToolCallingLoop Start ---\n");

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("Calculate 42 + 58 for me")
            .build());

        LLMResponse response = loop.executeWithTools(messages,
            LLMOptions.builder()
                .toolDefinitions(executor.getToolDefinitions())
                .build());

        System.out.println("\n--- ToolCallingLoop End ---");
        System.out.println("Final answer: " + response.getMessage().getTextContent());
    }

    // ================================================================
    //  方式一：注解方式（@ToolFunction）—— 推荐
    //
    //  优点：代码量最少，5 行一个工具方法
    //  底层：AnnotatedToolScanner 扫描 @ToolFunction 注解，
    //        AnnotatedToolWrapper 包装为 Tool 接口
    // ================================================================

    /**
     * 天气查询工具 — 注解方式
     *
     * 只需：
     *   1. 写一个普通类
     *   2. 方法上加 @ToolFunction（定义名称、描述、分类等）
     *   3. 参数上加 @ToolParam（定义参数名、描述、是否必填）
     *   4. registry.registerObject(new WeatherTool()) 注册
     *
     * AnnotatedToolScanner 自动完成：
     *   - 扫描所有 @ToolFunction 方法
     *   - 生成 JSON Schema（从 @ToolParam 注解 + Java 类型推断）
     *   - 包装为 Tool 接口（AnnotatedToolWrapper）
     *   - 处理参数类型转换（Number/String/Boolean 自动转换）
     */
    public static class WeatherTool {

        @ToolFunction(
            name = "get_weather",
            description = "Get current weather for a city",
            category = "utility",
            tags = {"weather", "external"},
            readOnly = true
        )
        public String getWeather(
            @ToolParam(name = "city", description = "City name", required = true) String city
        ) {
            // 实际项目中这里调用外部天气 API
            return "Weather in " + city + ": sunny, 22°C";
        }
    }

    // ================================================================
    //  方式二：接口方式（implements Tool）—— 最灵活
    //
    //  优点：完全控制 schema、执行逻辑、错误处理
    //  适用：需要构造函数注入、有状态、复杂 schema 的工具
    // ================================================================

    /**
     * 随机数工具 — 接口方式
     *
     * 实现 Tool 接口的 4 个方法：
     *   getName()       → 工具名称（唯一标识）
     *   getDescription() → 描述（LLM 据此决定是否调用）
     *   getSchema()      → JSON Schema（参数定义）
     *   execute(args)    → 执行逻辑，返回 ToolResult
     *
     * 可选实现 ToolMetadata 接口添加分类/标签等元数据。
     */
    public static class RandomNumberTool implements Tool {

        private final Random random = new Random();

        @Override
        public String getName() {
            return "random_number";
        }

        @Override
        public String getDescription() {
            return "Generate a random integer between min and max (inclusive)";
        }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.withRequired(Map.of(
                "min", Map.of("type", "integer", "description", "Minimum value (inclusive)"),
                "max", Map.of("type", "integer", "description", "Maximum value (inclusive)")
            ), "min", "max");
        }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            int min = ((Number) args.get("min")).intValue();
            int max = ((Number) args.get("max")).intValue();
            if (min > max) {
                return ToolResult.error("min must be <= max");
            }
            int result = random.nextInt(max - min + 1) + min;
            return ToolResult.success(String.valueOf(result));
        }
    }

    // ================================================================
    //  模拟 LLM — 演示 ToolCallingLoop 循环流程
    // ================================================================

    /**
     * 模拟 LLM Provider：
     *   第 1 轮：返回 tool_use（调用指定工具）
     *   第 2 轮：拿到工具结果后生成最终文本回复
     *
     * 实际项目直接替换为：
     *   new ClaudeProvider(apiKey, "claude-sonnet-4-20250514")
     */
    static class MockLLMProvider implements LLMProvider {
        private final String toolName;
        private final Map<String, Object> toolArgs;
        private int callCount = 0;

        MockLLMProvider(String toolName, Map<String, Object> toolArgs) {
            this.toolName = toolName;
            this.toolArgs = toolArgs;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            System.out.println("[MockLLM] Round " + callCount + " (messages: " + messages.size() + ")");

            if (callCount == 1) {
                // 第 1 轮：模拟 LLM 返回 tool_use
                System.out.println("[MockLLM] → tool_use: " + toolName + "(" + toolArgs + ")");
                ToolCall call = new ToolCall("call_001", toolName, toolArgs);
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("")
                        .metadata(Map.of("tool_calls", List.of(call)))
                        .build())
                    .toolCalls(List.of(call))
                    .build();
            }

            // 第 2 轮：拿到工具结果，生成最终回复
            String toolResult = messages.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .reduce((a, b) -> b)  // 最后一个 TOOL 消息
                .map(ConversationMessage::getTextContent)
                .orElse("(no result)");
            System.out.println("[MockLLM] ← tool result: " + toolResult);
            System.out.println("[MockLLM] → final text response");

            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .textContent("The result is " + toolResult)
                    .build())
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            return completeAsync(messages, options);
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }
    }
}
