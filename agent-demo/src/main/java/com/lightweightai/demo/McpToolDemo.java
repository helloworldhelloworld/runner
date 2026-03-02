package com.lightweightai.demo;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.mcp.McpToolAdapter;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;

/**
 * Demo 3: MCP 工具集成
 *
 * 演示两个方向的 MCP 集成：
 *
 * 方向 A：作为 MCP 客户端 — 连接外部 MCP 服务端，导入远程工具
 *   外部 MCP Server (Python/Node.js)
 *       ↓ MCP 协议 (STDIO/SSE)
 *   McpToolClient.discoverTools()
 *       ↓ McpToolWrapper (implements Tool)
 *   ToolRegistry.registerFrom(client)
 *       ↓
 *   LLM 可以调用远程工具，就像本地工具一样
 *
 * 方向 B：作为 MCP 服务端 — 将本地工具暴露给外部 AI Agent
 *   ToolRegistry（本地注册的工具）
 *       ↓ McpToolAdapter 转换
 *   McpToolServer.start()
 *       ↓ MCP 协议 (STDIO/SSE)
 *   Claude Desktop / Cursor / 其他 AI Agent 可以调用
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.McpToolDemo
 */
public class McpToolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 3: MCP Tool Integration");
        System.out.println("========================================\n");

        demoMcpClientUsage();
        demoMcpServerUsage();
        demoMcpAdapterUsage();
    }

    // ================================================================
    //  方向 A：作为 MCP 客户端 — 连接外部 MCP 服务端
    // ================================================================

    static void demoMcpClientUsage() {
        System.out.println("--- [A] MCP Client: 连接外部 MCP 服务端 ---\n");
        System.out.println("// 使用方式（需要外部 MCP 服务端运行）：");
        System.out.println("//");
        System.out.println("// import com.lightweightai.mcp.McpToolClient;");
        System.out.println("// import io.modelcontextprotocol.client.transport.StdioClientTransport;");
        System.out.println("// import io.modelcontextprotocol.spec.McpSchema.ServerParameters;");
        System.out.println("//");
        System.out.println("// // Step 1: 创建 MCP 客户端，指定传输方式和服务端启动命令");
        System.out.println("// McpToolClient client = McpToolClient.builder()");
        System.out.println("//     .serverName(\"weather-server\")");
        System.out.println("//     .transport(new StdioClientTransport(");
        System.out.println("//         ServerParameters.builder(\"npx\")");
        System.out.println("//             .args(\"-y\", \"@modelcontextprotocol/server-weather\")");
        System.out.println("//             .build()))");
        System.out.println("//     .build();");
        System.out.println("//");
        System.out.println("// // Step 2: 初始化（MCP 握手）");
        System.out.println("// client.initialize();");
        System.out.println("//");
        System.out.println("// // Step 3: 发现并注册工具（两种方式均可）");
        System.out.println("// registry.registerFrom(client);          // 方式一：ToolSource 接口");
        System.out.println("// client.registerTools(registry);         // 方式二：手动注册");
        System.out.println("//");
        System.out.println("// // 注册后的 MCP 工具和本地工具完全一样：");
        System.out.println("// //   - ToolExecutor 统一查找和执行");
        System.out.println("// //   - ToolCallingLoop 无需关心来源");
        System.out.println("// //   - McpToolWrapper 内部通过 MCP 协议转发调用");
        System.out.println("//");
        System.out.println("// // Step 4: 使用完毕关闭");
        System.out.println("// client.close();");
        System.out.println();
    }

    // ================================================================
    //  方向 B：作为 MCP 服务端 — 暴露工具给外部 AI Agent
    // ================================================================

    static void demoMcpServerUsage() {
        System.out.println("--- [B] MCP Server: 暴露工具给外部 AI Agent ---\n");

        // Step 1: 构建 ToolRegistry，注册本地工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());
        registry.scanAndRegister();

        System.out.println("ToolRegistry contains " + registry.enabledCount() + " tools");
        System.out.println("These tools can be exposed via MCP Server:");
        registry.getEnabled().forEach(t ->
            System.out.println("  - " + t.getName() + ": " + t.getDescription()));

        // Step 2: McpToolServer 将 ToolRegistry 暴露为 MCP 服务端
        //   注意：实际启动需要 STDIO transport，这里只展示构建过程
        System.out.println("\n// 启动 MCP 服务端：");
        System.out.println("// McpToolServer server = McpToolServer.builder()");
        System.out.println("//     .serverName(\"my-agent\")");
        System.out.println("//     .serverVersion(\"1.0.0\")");
        System.out.println("//     .toolRegistry(registry)");
        System.out.println("//     .build();");
        System.out.println("// server.start();  // 开始监听 MCP 请求");
        System.out.println("//");
        System.out.println("// 外部 AI Agent（Claude Desktop、Cursor 等）可以通过 MCP 协议");
        System.out.println("// 发现和调用 registry 中的所有工具。");
        System.out.println("//");
        System.out.println("// Claude Desktop 配置示例 (claude_desktop_config.json)：");
        System.out.println("// {");
        System.out.println("//   \"mcpServers\": {");
        System.out.println("//     \"my-agent\": {");
        System.out.println("//       \"command\": \"java\",");
        System.out.println("//       \"args\": [\"-jar\", \"agent-demo.jar\"]");
        System.out.println("//     }");
        System.out.println("//   }");
        System.out.println("// }");
        System.out.println();
    }

    // ================================================================
    //  McpToolAdapter — Tool ↔ MCP 格式转换
    // ================================================================

    static void demoMcpAdapterUsage() {
        System.out.println("--- [C] McpToolAdapter: Tool ↔ MCP 格式转换 ---\n");

        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());

        // McpToolAdapter 将框架 Tool 转为 MCP SyncToolSpecification
        List<McpServerFeatures.SyncToolSpecification> mcpTools = McpToolAdapter.toMcpTools(registry);
        System.out.println("Converted " + mcpTools.size() + " tools to MCP format:");
        mcpTools.forEach(spec ->
            System.out.println("  - " + spec.tool().name() + ": " + spec.tool().description()));
        System.out.println();
    }

    // ================================================================
    //  示例工具 — 用于 MCP 暴露演示
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
            // 模拟汇率
            double rate = 1.0;
            if ("USD".equalsIgnoreCase(from) && "CNY".equalsIgnoreCase(to)) rate = 7.25;
            if ("CNY".equalsIgnoreCase(from) && "USD".equalsIgnoreCase(to)) rate = 0.138;
            if ("USD".equalsIgnoreCase(from) && "JPY".equalsIgnoreCase(to)) rate = 149.5;

            double result = amount * rate;
            return String.format("%.2f %s = %.2f %s", amount, from.toUpperCase(), result, to.toUpperCase());
        }
    }
}
