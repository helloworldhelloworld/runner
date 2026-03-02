package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolAdapter;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

/**
 * Demo 3: MCP 工具集成 — 真实可运行
 *
 * 演示三部分：
 *
 * [A] McpToolAdapter 转换 + 通过 MCP handler 执行
 *     ToolRegistry 中的本地工具 → 转为 MCP SyncToolSpecification
 *     → 通过 MCP handler 调用 → 得到 MCP CallToolResult
 *     → 验证完整往返链路
 *
 * [B] McpToolServer — 将本地工具暴露为 MCP 服务端
 *     可被 Claude Desktop / Cursor / 其他 AI Agent 发现和调用
 *
 * [C] McpToolClient — 连接外部 MCP 服务端，导入远程工具
 *     远程工具注册到 ToolRegistry 后，和本地工具完全一样使用
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.McpToolDemo
 */
public class McpToolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 3: MCP Tool Integration");
        System.out.println("========================================\n");

        demoAdapterAndExecution();
        demoMcpServerSetup();
        demoMcpClientSetup();
    }

    // ================================================================
    //  [A] McpToolAdapter — 真实转换 + 真实执行
    //
    //  链路：
    //    本地 Tool
    //      ↓ McpToolAdapter.toMcpTool()
    //    MCP SyncToolSpecification (包含 tool 定义 + handler)
    //      ↓ handler.apply(exchange, args)
    //    MCP CallToolResult (包含 TextContent)
    //      ↓ 提取文本
    //    和本地 Tool.execute() 结果一致
    // ================================================================

    static void demoAdapterAndExecution() {
        System.out.println("--- [A] McpToolAdapter: Tool → MCP 转换 + 执行 ---\n");

        // Step 1: 注册本地工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());
        registry.scanAndRegister();  // 也拉入 agent-tools 的 MathTools/TimeTools/WebTools

        System.out.println("[Registry] " + registry.enabledCount() + " tools registered\n");

        // Step 2: 转换为 MCP 格式
        List<McpServerFeatures.SyncToolSpecification> mcpSpecs = McpToolAdapter.toMcpTools(registry);

        System.out.println("[Adapter] Converted to " + mcpSpecs.size() + " MCP tool specs:");
        for (McpServerFeatures.SyncToolSpecification spec : mcpSpecs) {
            McpSchema.Tool tool = spec.tool();
            System.out.println("  - " + tool.name() + ": " + tool.description());
            System.out.println("    inputSchema: " + tool.inputSchema());
        }

        // Step 3: 通过 MCP handler 执行工具（模拟 MCP 协议调用）
        System.out.println("\n[Execution] 通过 MCP handler 调用工具：\n");

        // 找到 get_city_info 的 MCP spec，通过 handler 执行
        McpServerFeatures.SyncToolSpecification citySpec = mcpSpecs.stream()
            .filter(s -> s.tool().name().equals("get_city_info"))
            .findFirst()
            .orElseThrow();

        CallToolResult mcpResult = citySpec.handler().apply(null, Map.of("city", "beijing"));

        String resultText = ((TextContent) mcpResult.content().get(0)).text();
        boolean isError = mcpResult.isError() != null && mcpResult.isError();

        System.out.println("  get_city_info({city: \"beijing\"})");
        System.out.println("    MCP result: " + resultText);
        System.out.println("    isError:    " + isError);

        // 找到 convert_currency 的 MCP spec，通过 handler 执行
        McpServerFeatures.SyncToolSpecification currencySpec = mcpSpecs.stream()
            .filter(s -> s.tool().name().equals("convert_currency"))
            .findFirst()
            .orElseThrow();

        CallToolResult currencyResult = currencySpec.handler().apply(null,
            Map.of("amount", 100.0, "from", "USD", "to", "CNY"));

        String currencyText = ((TextContent) currencyResult.content().get(0)).text();
        System.out.println("\n  convert_currency({amount: 100, from: \"USD\", to: \"CNY\"})");
        System.out.println("    MCP result: " + currencyText);

        // 对比：同一工具通过 ToolExecutor 本地执行
        System.out.println("\n[Compare] 同一工具通过 ToolExecutor 本地执行：\n");
        ToolExecutor executor = new ToolExecutor(registry);

        ToolResult localResult = executor.executeToolCall(
            new ToolCall("demo_1", "get_city_info", Map.of("city", "beijing")));
        System.out.println("  ToolExecutor:  " + localResult.getContent());
        System.out.println("  MCP handler:   " + resultText);
        System.out.println("  Match:         " + localResult.getContent().equals(resultText));

        // 提取 MCP 行为注解
        System.out.println("\n[Annotations] MCP 行为注解提取：\n");
        for (Tool tool : registry.getEnabled()) {
            Map<String, Boolean> annotations = McpToolAdapter.extractAnnotations(tool);
            if (!annotations.isEmpty()) {
                System.out.println("  " + tool.getName() + ": " + annotations);
            }
        }

        System.out.println();
    }

    // ================================================================
    //  [B] McpToolServer — 将工具暴露为 MCP 服务端
    //
    //  链路：
    //    ToolRegistry
    //      ↓ McpToolAdapter.toMcpTools()
    //    List<SyncToolSpecification>
    //      ↓ McpServer.sync(transport).tools(specs).build()
    //    McpSyncServer（监听 MCP 请求）
    //      ↓ 外部 AI Agent 连接
    //    Claude Desktop / Cursor 可以发现和调用所有工具
    // ================================================================

    static void demoMcpServerSetup() {
        System.out.println("--- [B] McpToolServer: 暴露工具给外部 AI Agent ---\n");

        // 准备 ToolRegistry
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());
        registry.scanAndRegister();

        // 构建 McpToolServer（不调用 start() 因为会阻塞在 STDIO）
        McpToolServer server = McpToolServer.builder()
            .serverName("demo-agent")
            .serverVersion("1.0.0")
            .toolRegistry(registry)
            .build();

        System.out.println("[Server] McpToolServer built successfully");
        System.out.println("  serverName:    demo-agent");
        System.out.println("  serverVersion: 1.0.0");
        System.out.println("  tools:         " + registry.enabledCount());
        System.out.println();

        // 说明如何真正启动
        System.out.println("  启动方式（会阻塞在 STDIO 监听）：");
        System.out.println("    server.start();");
        System.out.println();
        System.out.println("  Claude Desktop 配置 (claude_desktop_config.json)：");
        System.out.println("    {");
        System.out.println("      \"mcpServers\": {");
        System.out.println("        \"demo-agent\": {");
        System.out.println("          \"command\": \"java\",");
        System.out.println("          \"args\": [\"-cp\", \"agent-demo.jar\", \"com.lightweightai.demo.McpServerMain\"]");
        System.out.println("        }");
        System.out.println("      }");
        System.out.println("    }");
        System.out.println();
    }

    // ================================================================
    //  [C] McpToolClient — 连接外部 MCP 服务端，导入远程工具
    //
    //  链路：
    //    McpToolClient.builder()
    //        .serverName("xxx")
    //        .transport(transport)
    //        .build()
    //      ↓ client.initialize()       — MCP 握手
    //      ↓ client.discoverTools()    — 列出远程工具 (implements ToolSource)
    //      ↓ McpToolWrapper            — 包装为框架 Tool 接口
    //      ↓ registry.registerFrom(client)
    //    远程工具和本地工具一样：
    //      ToolExecutor 统一查找 → ToolCallingLoop 无需关心来源
    //      McpToolWrapper.execute() 内部通过 MCP 协议转发
    // ================================================================

    static void demoMcpClientSetup() {
        System.out.println("--- [C] McpToolClient: 连接外部 MCP 服务端 ---\n");

        System.out.println("  完整使用代码：\n");
        System.out.println("    // Step 1: 创建客户端");
        System.out.println("    McpToolClient client = McpToolClient.builder()");
        System.out.println("        .serverName(\"weather-server\")");
        System.out.println("        .transport(new StdioClientTransport(");
        System.out.println("            ServerParameters.builder(\"npx\")");
        System.out.println("                .args(\"-y\", \"@modelcontextprotocol/server-weather\")");
        System.out.println("                .build()))");
        System.out.println("        .build();\n");
        System.out.println("    // Step 2: MCP 握手");
        System.out.println("    client.initialize();\n");
        System.out.println("    // Step 3: 发现 + 注册（McpToolClient implements ToolSource）");
        System.out.println("    ToolRegistry registry = new ToolRegistry();");
        System.out.println("    registry.registerFrom(client);  // 一行搞定\n");
        System.out.println("    // 或者手动控制：");
        System.out.println("    List<McpToolWrapper> tools = client.discoverMcpTools();");
        System.out.println("    client.registerTools(registry, tool -> !tool.getName().equals(\"xxx\"));\n");
        System.out.println("    // Step 4: 注册后的 MCP 工具和本地工具完全一样");
        System.out.println("    ToolExecutor executor = new ToolExecutor(registry);");
        System.out.println("    executor.executeToolCall(new ToolCall(\"id\", \"get_forecast\", args));\n");
        System.out.println("    // Step 5: 关闭");
        System.out.println("    client.close();");
        System.out.println();

        // 展示 McpToolWrapper 的作用
        System.out.println("  McpToolWrapper 的作用：");
        System.out.println("    ┌─────────────────────────────────────────────┐");
        System.out.println("    │  McpToolWrapper implements Tool             │");
        System.out.println("    │                                             │");
        System.out.println("    │  getName()    → mcpTool.name()              │");
        System.out.println("    │  getSchema()  → 解析 mcpTool.inputSchema()  │");
        System.out.println("    │  execute(args)→ mcpClient.callTool(args)    │");
        System.out.println("    │                 ↑ 通过 MCP 协议转发到服务端  │");
        System.out.println("    └─────────────────────────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  示例工具
    // ================================================================

    public static class CityInfoTool {

        @ToolFunction(
            name = "get_city_info",
            description = "Get population and area information of a city",
            category = "geography",
            tags = {"city", "info"},
            readOnly = true
        )
        public String getCityInfo(
            @ToolParam(name = "city", description = "City name", required = true) String city
        ) {
            return switch (city.toLowerCase()) {
                case "beijing" -> "Beijing: population 21.5M, area 16,410 km²";
                case "shanghai" -> "Shanghai: population 24.9M, area 6,341 km²";
                case "tokyo" -> "Tokyo: population 13.9M, area 2,194 km²";
                default -> city + ": data not available (demo mode)";
            };
        }

        @ToolFunction(
            name = "convert_currency",
            description = "Convert amount between currencies",
            category = "finance",
            tags = {"currency", "conversion"},
            readOnly = true,
            idempotent = true
        )
        public String convertCurrency(
            @ToolParam(name = "amount", description = "Amount to convert", required = true) double amount,
            @ToolParam(name = "from", description = "Source currency code (e.g., USD)", required = true) String from,
            @ToolParam(name = "to", description = "Target currency code (e.g., CNY)", required = true) String to
        ) {
            double rate = 1.0;
            if ("USD".equalsIgnoreCase(from) && "CNY".equalsIgnoreCase(to)) rate = 7.25;
            if ("CNY".equalsIgnoreCase(from) && "USD".equalsIgnoreCase(to)) rate = 0.138;
            if ("USD".equalsIgnoreCase(from) && "JPY".equalsIgnoreCase(to)) rate = 149.5;

            double result = amount * rate;
            return String.format("%.2f %s = %.2f %s", amount, from.toUpperCase(), result, to.toUpperCase());
        }
    }
}
