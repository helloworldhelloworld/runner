package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP HTTP Client Demo — 通过 Streamable HTTP 连接 MCP Server
 *
 * <h2>架构</h2>
 * <pre>
 * McpHttpClientDemo ──HTTP──→ agent-web:8080/mcp (McpServerConfig)
 *                                   │
 *                                   └── ToolRegistry（本地工具 + MCP 代理工具）
 * </pre>
 *
 * <h2>前置条件</h2>
 * <pre>
 * # 1. 启动 agent-web（开启 MCP HTTP Server）
 * MCP_SERVER_ENABLED=true mvn -pl agent-web spring-boot:run
 *
 * # 2. 运行本 Demo
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpHttpClientDemo
 *
 * # 3. 可选：指定服务器地址（默认 http://localhost:8080/mcp）
 * mvn -pl agent-demo exec:java \
 *     -Dexec.mainClass=com.lightweightai.demo.McpHttpClientDemo \
 *     -Dmcp.server.url=http://192.168.1.100:8080/mcp
 * </pre>
 */
public class McpHttpClientDemo {

    private static final String DEFAULT_URL = "http://localhost:8080/mcp";

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  MCP HTTP Client Demo — Streamable HTTP     ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        String url = System.getProperty("mcp.server.url", DEFAULT_URL);
        System.out.println("[config] MCP Server URL: " + url + "\n");

        // 通过 Streamable HTTP 连接 MCP Server
        McpToolClient client = McpToolClient.builder()
            .serverName("agent-web-http")
            .transport(HttpClientStreamableHttpTransport.builder(url).build())
            .requestTimeout(Duration.ofSeconds(30))
            .build();

        try {
            System.out.println("[1] 连接 MCP Server...");
            client.initialize();
            System.out.println("[2] MCP 握手成功\n");

            // 发现工具
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            if (tools.isEmpty()) {
                System.out.println("    （无工具，请确认 agent-web 已注册工具到 ToolRegistry）");
                return;
            }

            // 注册到 ToolRegistry，通过 ToolExecutor 调用
            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            System.out.println("\n[4] 调用工具：\n");

            // 尝试调用已知工具
            for (Tool tool : registry.getEnabled()) {
                System.out.println("  工具: " + tool.getName());
                System.out.println("  描述: " + tool.getDescription());

                // 根据工具名自动构造测试参数
                Map<String, Object> testArgs = guessTestArgs(tool.getName());
                if (testArgs != null) {
                    callAndPrint(executor, tool.getName(), testArgs);
                } else {
                    System.out.println("  （跳过，无测试参数）\n");
                }
            }

            // 工具来源
            System.out.println("[5] 工具来源：");
            for (Tool tool : registry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w)
                    ? "remote:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║  HTTP 连接验证通过！                          ║");
            System.out.println("╚══════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    /**
     * 根据工具名猜测测试参数
     */
    private static Map<String, Object> guessTestArgs(String toolName) {
        return switch (toolName) {
            case "get_city_info" -> Map.of("city", "beijing");
            case "get_weather" -> Map.of("city", "tokyo");
            case "calculate" -> Map.of("a", 12.5, "op", "+", "b", 7.3);
            case "get_current_time" -> Map.of();
            case "get_timezone_time" -> Map.of("timezone", "Asia/Shanghai");
            case "translate_text" -> Map.of("text", "hello", "from", "en", "to", "zh");
            case "web_search" -> Map.of("query", "MCP protocol");
            default -> null;
        };
    }

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("demo-" + toolName, toolName, args));
            System.out.println("  调用: " + toolName + "(" + args + ")");
            System.out.println("    → " + result.getContent());
            System.out.println();
        } catch (Exception e) {
            System.out.println("  调用: " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println();
        }
    }
}
