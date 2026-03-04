package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;

import java.util.List;
import java.util.Map;

/**
 * MCP Client Demo — 验证 McpServerRunner 的端到端链路
 *
 * <h2>架构</h2>
 * <pre>
 * McpClientDemo ──STDIO──→ McpServerRunner ──MCP SDK──→ 外部已有 MCP Server
 *                          (mcp-server.yaml 配置)
 *
 * McpServerRunner 根据 mcp-server.yaml 中的 upstream 配置，
 * 通过官方 MCP SDK 连接外部已有的 MCP Server（SSE 或 STDIO）。
 * McpClientDemo 不需要关心上游传输方式，只负责连接 McpServerRunner 并验证工具。
 * </pre>
 *
 * <h2>运行方式</h2>
 * <pre>
 * # 1. 确保 mcp-server.yaml 中的上游 MCP Server 配置正确
 * #    - SSE: 外部 MCP Server 已在运行，url 配置正确
 * #    - STDIO: command/args 配置正确
 *
 * # 2. 运行 Demo
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpClientDemo
 *
 * # 3. 可选：指定自定义配置文件
 * mvn -pl agent-demo exec:java \
 *     -Dexec.mainClass=com.lightweightai.demo.McpClientDemo \
 *     -Dmcp.server.config=/path/to/custom-config.yaml
 * </pre>
 *
 * <h2>配置示例 (mcp-server.yaml)</h2>
 * <pre>
 * server:
 *   name: demo-agent
 *   version: 0.1.0
 * upstream:
 *   servers:
 *     nlp-service:
 *       transport: sse
 *       url: "http://remote-host:8081/sse"
 *       enabled: true
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  MCP Client Demo — 验证 McpServerRunner     ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // 构建 McpServerRunner 启动参数
        // McpServerRunner 自动读取 mcp-server.yaml（或 -Dmcp.server.config 指定的配置）
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(javaCmd);
        String configPath = System.getProperty("mcp.server.config");
        if (configPath != null && !configPath.isBlank()) {
            paramsBuilder.args("-Dmcp.server.config=" + configPath,
                "-cp", classpath, "com.lightweightai.demo.McpServerRunner");
            System.out.println("[config] " + configPath);
        } else {
            paramsBuilder.args("-cp", classpath, "com.lightweightai.demo.McpServerRunner");
            System.out.println("[config] classpath:mcp-server.yaml（默认）");
        }

        System.out.println("[1] 启动 McpServerRunner...");
        System.out.println("    McpServerRunner 根据配置连接上游 MCP Server（SSE/STDIO）\n");

        McpToolClient client = McpToolClient.builder()
            .serverName("demo-agent")
            .transport(new StdioClientTransport(paramsBuilder.build()))
            .build();

        try {
            client.initialize();
            System.out.println("[2] MCP 握手成功\n");

            // 发现工具（本地工具 + 上游代理工具）
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName());
            }

            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            // 调用工具
            System.out.println("\n[4] 调用工具：\n");

            // 本地工具（McpServerRunner 自带）
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));
            callAndPrint(executor, "get_weather", Map.of("city", "tokyo"));
            callAndPrint(executor, "calculate", Map.of("a", 12.5, "op", "+", "b", 7.3));

            // 上游代理工具（来自配置的外部 MCP Server）
            callAndPrint(executor, "translate_text",
                Map.of("text", "hello world", "from", "en", "to", "zh"));
            callAndPrint(executor, "sentiment_analysis",
                Map.of("text", "This framework is great!"));
            callAndPrint(executor, "lookup_definition",
                Map.of("word", "kernel", "language", "en"));

            // 工具来源
            System.out.println("[5] 工具来源：");
            for (Tool tool : registry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w)
                    ? "upstream:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║  验证通过！                                  ║");
            System.out.println("╚══════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("debug-" + toolName, toolName, args));
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → " + result.getContent());
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println();
        }
    }
}
