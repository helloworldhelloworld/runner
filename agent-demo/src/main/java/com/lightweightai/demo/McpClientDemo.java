package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.util.List;
import java.util.Map;

/**
 * MCP 端到端调试 Demo
 *
 * 自动完成：启动 McpServerRunner 子进程 → MCP 握手 → 发现工具 → 调用工具 → 打印结果 → 关闭
 *
 * <h2>运行方式</h2>
 * <pre>
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpClientDemo
 * </pre>
 *
 * <h2>调试流程</h2>
 * <pre>
 *   McpClientDemo (本进程)
 *       │
 *       │ 1. ServerParameters → StdioClientTransport
 *       │    (自动 fork McpServerRunner 子进程)
 *       │
 *       ├──── stdin/stdout ────→ McpServerRunner (子进程)
 *       │                         │
 *       │ 2. initialize()         │ MCP 握手
 *       │ 3. listTools()          │ 返回工具列表
 *       │ 4. callTool()           │ 执行工具
 *       │ 5. close()              │ 子进程退出
 *       │
 *   调试完成
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  MCP 端到端调试 Demo");
        System.out.println("  Client → Server (STDIO) → 调用工具");
        System.out.println("========================================\n");

        // Step 1: 构建 ServerParameters，启动 McpServerRunner 作为子进程
        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        ServerParameters serverParams = ServerParameters.builder(javaCmd)
            .args("-cp", classpath, "com.lightweightai.demo.McpServerRunner")
            .build();

        System.out.println("[1] 启动 MCP Server 子进程...");
        System.out.println("    command: " + javaCmd);
        System.out.println("    mainClass: com.lightweightai.demo.McpServerRunner");

        // Step 2: 创建 MCP Client，连接到子进程
        McpToolClient client = McpToolClient.builder()
            .serverName("demo-agent")
            .transport(new StdioClientTransport(serverParams,
                new JacksonMcpJsonMapper(new ObjectMapper())))
            .build();

        try {
            client.initialize();
            System.out.println("[2] MCP 握手成功\n");

            // Step 3: 发现工具
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个远程工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
                System.out.println("      schema: " + tool.getSchema().toMap());
            }

            // Step 4: 注册到 ToolRegistry，通过 ToolExecutor 统一调用
            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            System.out.println("\n[4] 通过 ToolExecutor 调用远程工具（和本地工具代码完全一样）：\n");

            // 调用 get_city_info
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));

            // 调用 get_weather
            callAndPrint(executor, "get_weather", Map.of("city", "上海"));

            // 调用 calculate
            callAndPrint(executor, "calculate", Map.of("a", 42.0, "op", "+", "b", 58.0));

            // 调用 convert_currency
            callAndPrint(executor, "convert_currency",
                Map.of("amount", 100.0, "from", "USD", "to", "CNY"));

            // 测试不存在的城市
            callAndPrint(executor, "get_city_info", Map.of("city", "mars"));

            System.out.println("========================================");
            System.out.println("  调试完成！所有工具通过 MCP 协议调用成功");
            System.out.println("========================================\n");

            // 列出工具来源，证明是 MCP 远程工具
            System.out.println("[5] 工具来源验证：");
            for (Tool tool : registry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w) ? "mcp:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] MCP 调试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Step 5: 关闭连接（子进程自动退出）
            System.out.println("\n[6] 关闭 MCP 连接...");
            client.close();
            System.out.println("    MCP Server 子进程已退出");
        }
    }

    private static void callAndPrint(ToolExecutor executor, String toolName, Map<String, Object> args) {
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
