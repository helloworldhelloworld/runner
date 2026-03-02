package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.tools.math.MathTools;
import com.lightweightai.tools.time.TimeTools;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * ToolUsageDemo — 完整示例：如何构建本地 Tool 并让模型调用
 *
 * 演示三种工具注册方式：
 * 1. 注解方式（@ToolFunction）    — 最简洁，推荐
 * 2. 接口方式（implements Tool）  — 最灵活，适合复杂逻辑
 * 3. MCP 方式（McpToolClient）   — 连接外部 MCP 服务端
 *
 * 以及完整的端到端调用流程：
 *   用户消息 → LLM → 返回 tool_use → ToolExecutor 执行 → 结果回传 LLM → 最终回复
 */
public class ToolUsageDemo {

    /**
     * main 入口 —— 完整的使用流程
     */
    public static void main(String[] args) {
        System.out.println("=== Lightweight AI Kernel — Tool Usage Demo ===\n");

        // ──────────────────────────────────────────────
        // Step 1: 创建 ToolRegistry（工具注册中心）
        // ──────────────────────────────────────────────
        ToolRegistry registry = new ToolRegistry();

        // ──────────────────────────────────────────────
        // Step 2a: 注解方式注册工具（推荐）
        // ──────────────────────────────────────────────
        // 直接写一个普通类，用 @ToolFunction 标注方法
        registry.registerObject(new MathTools());      // add, multiply, divide
        registry.registerObject(new TimeTools());      // get_time
        registry.registerObject(new DemoWeatherTool()); // get_weather（本 demo 内定义）

        // ──────────────────────────────────────────────
        // Step 2b: 接口方式注册（适合复杂逻辑）
        // ──────────────────────────────────────────────
        registry.register(new RandomNumberTool());     // random_number（implements Tool）

        // ──────────────────────────────────────────────
        // Step 2c: SPI 自动扫描（零代码注册）
        // ──────────────────────────────────────────────
        // 如果 jar 中有 META-INF/services/com.lightweightai.kernel.agent.ToolSource
        // 或 META-INF/services/com.lightweightai.kernel.agent.Tool，一行搞定：
        // registry.scanAndRegister();

        // ──────────────────────────────────────────────
        // Step 2d: MCP 方式（连接外部 MCP 服务端）
        // ──────────────────────────────────────────────
        // McpToolClient mcpClient = McpToolClient.builder()
        //     .serverName("weather-server")
        //     .transport(new StdioClientTransport(
        //         ServerParameters.builder("npx")
        //             .args("-y", "@modelcontextprotocol/server-weather")
        //             .build()))
        //     .build();
        // mcpClient.initialize();
        // registry.registerFrom(mcpClient);  // 发现并注册 MCP 工具

        // ──────────────────────────────────────────────
        // Step 3: 创建 ToolExecutor + ToolCallingLoop
        // ──────────────────────────────────────────────
        ToolExecutor executor = new ToolExecutor(registry);
        System.out.println("Registered tools: " + executor.getFunctionCount());
        System.out.println("Tool definitions (sent to LLM):");
        executor.getToolDefinitions().forEach(def ->
            System.out.println("  - " + def.get("name") + ": " + def.get("description"))
        );

        // 用模拟 LLM 演示完整调用链路
        LLMProvider mockLLM = new ToolDemoLLMProvider();

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockLLM)
            .toolExecutor(executor)
            .maxIterations(5)
            .build();

        // ──────────────────────────────────────────────
        // Step 4: 发送用户消息，触发工具调用
        // ──────────────────────────────────────────────
        System.out.println("\n--- Running tool calling loop ---\n");

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("Calculate 42 + 58 for me")
            .build());

        LLMResponse response = loop.executeWithTools(messages,
            LLMOptions.builder()
                .tools(executor.getToolDefinitions())
                .build());

        System.out.println("\nFinal response: " + response.getMessage().getTextContent());
    }

    // ================================================================
    //  方式一：注解方式定义工具（@ToolFunction） —— 推荐，最简洁
    // ================================================================

    /**
     * 天气查询工具（注解方式）
     *
     * 只需要：
     * 1. 写一个普通类
     * 2. 方法上加 @ToolFunction
     * 3. 参数上加 @ToolParam
     * 4. registry.registerObject(new DemoWeatherTool()) 完成注册
     */
    public static class DemoWeatherTool {

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
            // 实际项目中这里调用天气 API
            return "Weather in " + city + ": sunny, 22°C";
        }
    }

    // ================================================================
    //  方式二：接口方式定义工具（implements Tool） —— 最灵活
    // ================================================================

    /**
     * 随机数工具（接口方式）
     *
     * 适用于需要复杂初始化、状态管理或自定义 schema 的场景。
     */
    public static class RandomNumberTool implements Tool {

        private final Random random = new Random();

        @Override
        public String getName() {
            return "random_number";
        }

        @Override
        public String getDescription() {
            return "Generate a random number between min and max";
        }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.withRequired(Map.of(
                "min", Map.of("type", "integer", "description", "Minimum value"),
                "max", Map.of("type", "integer", "description", "Maximum value")
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
    //  方式三：MCP 方式 —— 连接外部 MCP 服务端（伪代码）
    // ================================================================
    //
    //  // 1. 启动外部 MCP 服务端（如 Python/Node.js 编写的天气服务）
    //  //    npx -y @modelcontextprotocol/server-weather
    //
    //  // 2. 在 Java 中连接并注册
    //  McpToolClient client = McpToolClient.builder()
    //      .serverName("weather-mcp")
    //      .transport(new StdioClientTransport(
    //          ServerParameters.builder("npx")
    //              .args("-y", "@modelcontextprotocol/server-weather")
    //              .build()))
    //      .build();
    //
    //  client.initialize();                  // MCP 握手
    //  registry.registerFrom(client);        // 发现 + 注册 MCP 工具
    //
    //  // MCP 工具和本地工具一样被 LLM 调用，无差别
    //  // ToolExecutor 统一执行，McpToolWrapper 内部通过 MCP 协议转发调用
    //
    //  client.close();                       // 使用完毕关闭

    // ================================================================
    //  模拟 LLM Provider：演示工具调用循环
    // ================================================================

    /**
     * 模拟 LLM — 第一轮返回 tool_use 调用 add，第二轮返回最终文本
     *
     * 实际项目中替换为 ClaudeProvider / OpenRouterProvider：
     *   LLMProvider provider = new ClaudeProvider(apiKey, "claude-sonnet-4-20250514");
     */
    static class ToolDemoLLMProvider implements LLMProvider {

        private int callCount = 0;

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            System.out.println("[LLM] Call #" + callCount + ", messages: " + messages.size());

            if (callCount == 1) {
                // 第一轮：LLM 决定调用 add 工具
                System.out.println("[LLM] -> Deciding to call tool: add(42, 58)");

                ToolCall toolCall = new ToolCall("call_001", "add",
                    Map.of("a", 42, "b", 58));

                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("")
                        .metadata(Map.of("tool_calls", List.of(toolCall)))
                        .build())
                    .toolCalls(List.of(toolCall))
                    .build();
            } else {
                // 第二轮：LLM 拿到工具结果，生成最终回复
                String toolResult = messages.stream()
                    .filter(m -> m.getRole() == MessageRole.TOOL)
                    .map(ConversationMessage::getTextContent)
                    .reduce("", (a, b) -> b);  // 取最后一个 TOOL 消息

                System.out.println("[LLM] -> Got tool result: " + toolResult);
                System.out.println("[LLM] -> Generating final response");

                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("42 + 58 = " + toolResult)
                        .build())
                    .build();
            }
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
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "demo-mock"; }
    }
}
