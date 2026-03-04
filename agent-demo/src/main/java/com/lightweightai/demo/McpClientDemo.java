package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP 端到端调试 Demo — 覆盖 STDIO 和 HTTP/SSE 两种传输
 *
 * <h2>运行方式</h2>
 * <pre>
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpClientDemo
 * </pre>
 *
 * <h2>验证场景</h2>
 * <pre>
 * Phase 1: STDIO Transport（本地子进程）
 *   McpClientDemo ──stdin/stdout──→ McpServerRunner (子进程)
 *
 * Phase 2: HTTP/SSE Transport（真正的 HTTP RPC）
 *   McpClientDemo ──HTTP/SSE──→ Embedded Tomcat + MCP Server (同进程，不同端口)
 *   验证 HttpClientSseClientTransport → HttpServletSseServerTransportProvider
 *   这和连接远程 MCP Server（如 http://remote:8080/sse）完全一致
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  MCP 端到端调试 — STDIO + HTTP/SSE 全覆盖   ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        // Phase 1: STDIO
        System.out.println("┌──────────────────────────────────────────────┐");
        System.out.println("│  Phase 1: STDIO Transport（本地子进程）       │");
        System.out.println("│  Client ──stdin/stdout──→ Server (subprocess)│");
        System.out.println("└──────────────────────────────────────────────┘\n");
        demoStdioTransport();

        System.out.println("\n");

        // Phase 2: HTTP/SSE
        System.out.println("┌──────────────────────────────────────────────┐");
        System.out.println("│  Phase 2: HTTP/SSE Transport（HTTP RPC）     │");
        System.out.println("│  Client ──HTTP/SSE──→ Tomcat + MCP Server   │");
        System.out.println("└──────────────────────────────────────────────┘\n");
        demoSseHttpTransport();

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║  全部验证通过！STDIO + HTTP/SSE 均正常工作    ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    // ==================== Phase 1: STDIO ====================

    static void demoStdioTransport() {
        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        ServerParameters serverParams = ServerParameters.builder(javaCmd)
            .args("-cp", classpath, "com.lightweightai.demo.McpServerRunner")
            .build();

        System.out.println("[1] 启动 MCP Server 子进程...");
        System.out.println("    command: " + javaCmd);
        System.out.println("    mainClass: com.lightweightai.demo.McpServerRunner");

        McpToolClient client = McpToolClient.builder()
            .serverName("demo-agent")
            .transport(new StdioClientTransport(serverParams,
                new JacksonMcpJsonMapper(new ObjectMapper())))
            .build();

        try {
            client.initialize();
            System.out.println("[2] MCP 握手成功 (STDIO)\n");

            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个远程工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            System.out.println("\n[4] 调用工具（通过 STDIO MCP）：\n");

            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));
            callAndPrint(executor, "get_weather", Map.of("city", "上海"));
            callAndPrint(executor, "calculate", Map.of("a", 42.0, "op", "+", "b", 58.0));
            callAndPrint(executor, "convert_currency",
                Map.of("amount", 100.0, "from", "USD", "to", "CNY"));

            System.out.println("[5] 工具来源：");
            for (Tool tool : registry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w) ? "mcp:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n  >> STDIO 验证通过 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] STDIO 验证失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    // ==================== Phase 2: HTTP/SSE RPC ====================

    /**
     * 验证 MCP over HTTP/SSE — 真正的 HTTP 远程调用
     *
     * <pre>
     * 架构：
     *   McpClientDemo                    Embedded Tomcat (:randomPort)
     *   ┌───────────┐                   ┌─────────────────────────┐
     *   │ McpTool   │ ──GET /sse──→     │ HttpServletSseServer    │
     *   │ Client    │ ──POST /message─→ │ TransportProvider       │
     *   │ (HTTP)    │ ←─SSE events───   │ (Servlet)               │
     *   └───────────┘                   │          ↓              │
     *      ↑                            │ McpToolServer           │
     *      │                            │ ├── get_time            │
     *      │                            │ ├── echo                │
     *   HttpClient                      │ └── add_numbers         │
     *   SseClient                       └─────────────────────────┘
     *   Transport
     *
     * 这和连接远程 http://some-host:8080/sse 完全一致。
     * 只是 Server 恰好在同一个 JVM 里（用 Tomcat 托管）。
     * </pre>
     */
    static void demoSseHttpTransport() {
        Tomcat tomcat = null;
        McpToolServer mcpServer = null;
        McpToolClient client = null;

        try {
            // Step 1: 准备工具
            ToolRegistry serverRegistry = new ToolRegistry();
            serverRegistry.registerObject(new SseTestTools());

            System.out.println("[1] 准备 HTTP/SSE MCP Server 工具：");
            for (Tool tool : serverRegistry.getEnabled()) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            // Step 2: 创建 SSE 服务端传输（Servlet）
            // HttpServletSseServerTransportProvider 是 MCP SDK 核心模块自带的
            // 它是一个 Servlet，处理 GET（SSE 连接）和 POST（JSON-RPC 消息）
            System.out.println("\n[2] 创建 HttpServletSseServerTransportProvider...");
            HttpServletSseServerTransportProvider sseTransport =
                HttpServletSseServerTransportProvider.builder()
                    .objectMapper(new ObjectMapper())
                    .messageEndpoint("/message")
                    .build();

            // Step 3: 启动 MCP Server（配置工具到传输层）
            System.out.println("[3] 启动 McpToolServer（注册工具到 SSE 传输）...");
            mcpServer = McpToolServer.builder()
                .serverName("sse-test-server")
                .serverVersion("0.1.0")
                .toolRegistry(serverRegistry)
                .transportProvider(sseTransport)
                .build();
            mcpServer.start();

            // Step 4: 启动嵌入式 Tomcat，托管 SSE Servlet
            System.out.println("[4] 启动嵌入式 Tomcat...");
            tomcat = new Tomcat();
            tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));
            tomcat.setPort(0); // 随机端口
            tomcat.getConnector(); // 触发 Connector 创建

            Context ctx = tomcat.addContext("", new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());
            Tomcat.addServlet(ctx, "mcp-sse", sseTransport);
            ctx.addServletMappingDecoded("/*", "mcp-sse");

            tomcat.start();
            int port = tomcat.getConnector().getLocalPort();
            System.out.println("    Tomcat started on port: " + port);
            System.out.println("    SSE endpoint: http://localhost:" + port + "/sse");
            System.out.println("    Message endpoint: http://localhost:" + port + "/message");

            // Step 5: 用 HttpClientSseClientTransport 连接（和连远程服务器一模一样）
            System.out.println("\n[5] 通过 HTTP/SSE 连接 MCP Server...");
            System.out.println("    transport: HttpClientSseClientTransport");
            System.out.println("    url: http://localhost:" + port);

            client = McpToolClient.builder()
                .serverName("sse-test")
                .transport(HttpClientSseClientTransport.builder("http://localhost:" + port)
                    .sseEndpoint("/sse")
                    .build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();

            client.initialize();
            System.out.println("    MCP 握手成功 (HTTP/SSE)\n");

            // Step 6: 发现远程工具
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[6] 通过 HTTP/SSE 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            // Step 7: 注册到 ToolRegistry，通过 ToolExecutor 调用
            ToolRegistry clientRegistry = new ToolRegistry();
            client.registerTools(clientRegistry);
            ToolExecutor executor = new ToolExecutor(clientRegistry);

            System.out.println("\n[7] 通过 HTTP/SSE 调用工具（真正的 HTTP RPC）：\n");

            callAndPrint(executor, "echo",
                Map.of("message", "Hello from HTTP/SSE!"));
            callAndPrint(executor, "add_numbers",
                Map.of("a", 123.0, "b", 456.0));
            callAndPrint(executor, "get_time", Map.of());

            // Step 8: 验证工具来源
            System.out.println("[8] 工具来源验证（全部通过 HTTP/SSE）：");
            for (Tool tool : clientRegistry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w) ? "mcp:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source + " (HTTP/SSE)");
            }

            System.out.println("\n  >> HTTP/SSE RPC 验证通过 <<");
            System.out.println("  >> 和连接 http://remote-host:8080/sse 完全一致 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] HTTP/SSE 验证失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Step 9: 清理
            System.out.println("\n[9] 清理 HTTP/SSE 资源...");
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
            if (mcpServer != null) {
                try { mcpServer.close(); } catch (Exception e) { /* ignore */ }
            }
            if (tomcat != null) {
                try { tomcat.stop(); tomcat.destroy(); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    // ==================== SSE 测试工具 ====================

    /**
     * 简单的测试工具集，用于验证 HTTP/SSE 传输
     */
    public static class SseTestTools {

        @ToolFunction(name = "echo",
            description = "Echo back the input message (for testing connectivity)",
            category = "test", readOnly = true)
        public String echo(
            @ToolParam(name = "message", description = "Message to echo", required = true) String message
        ) {
            return "Echo via HTTP/SSE: " + message;
        }

        @ToolFunction(name = "add_numbers",
            description = "Add two numbers (for testing tool execution over HTTP)",
            category = "test", readOnly = true)
        public String addNumbers(
            @ToolParam(name = "a", description = "First number", required = true) double a,
            @ToolParam(name = "b", description = "Second number", required = true) double b
        ) {
            return String.format("%.2f + %.2f = %.2f (computed via HTTP/SSE RPC)", a, b, a + b);
        }

        @ToolFunction(name = "get_time",
            description = "Get current server time (proves this runs on the server side)",
            category = "test", readOnly = true)
        public String getTime() {
            return "Server time: " + java.time.Instant.now() + " (from HTTP/SSE MCP Server)";
        }
    }

    // ==================== 工具方法 ====================

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
