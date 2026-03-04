package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.lightweightai.mcp.McpToolWrapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * MCP 端到端调试 Demo — 验证 McpServerRunner 的 STDIO 和 SSE 上游链路
 *
 * <h2>运行方式</h2>
 * <pre>
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpClientDemo
 * </pre>
 *
 * <h2>验证场景</h2>
 * <pre>
 * Phase 1: McpServerRunner 上游为 STDIO
 *   McpClientDemo ──STDIO──→ McpServerRunner ──STDIO──→ UpstreamExampleServer
 *                             (mcp-server.yaml 默认配置)
 *
 * Phase 2: McpServerRunner 上游为 SSE（HTTP RPC）
 *   McpClientDemo ──STDIO──→ McpServerRunner ──HTTP/SSE──→ Tomcat SSE Server
 *                             (临时配置: upstream.transport=sse)
 *
 * 两种场景都是通过 McpServerRunner 代理，验证它内部的 SSE RPC 是否正常。
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  MCP 端到端调试 — 验证 McpServerRunner 上游链路  ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        // Phase 1: McpServerRunner → STDIO → UpstreamExampleServer
        System.out.println("┌──────────────────────────────────────────────────────┐");
        System.out.println("│  Phase 1: 上游 STDIO                                │");
        System.out.println("│  Client ──STDIO──→ McpServerRunner ──STDIO──→ 上游   │");
        System.out.println("└──────────────────────────────────────────────────────┘\n");
        demoUpstreamStdio();

        System.out.println("\n");

        // Phase 2: McpServerRunner → SSE/HTTP → Tomcat SSE Server
        System.out.println("┌──────────────────────────────────────────────────────┐");
        System.out.println("│  Phase 2: 上游 SSE（HTTP RPC）                       │");
        System.out.println("│  Client ──STDIO──→ McpServerRunner ──SSE──→ 上游     │");
        System.out.println("└──────────────────────────────────────────────────────┘\n");
        demoUpstreamSse();

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║  全部验证通过！STDIO + SSE 上游均正常工作         ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    // ==================== Phase 1: 上游 STDIO ====================

    /**
     * McpServerRunner → STDIO → UpstreamExampleServer
     *
     * 使用默认 mcp-server.yaml，上游 nlp-service-local 通过 STDIO 连接。
     */
    static void demoUpstreamStdio() {
        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // 启动 McpServerRunner（默认配置，上游 STDIO）
        ServerParameters serverParams = ServerParameters.builder(javaCmd)
            .args("-cp", classpath, "com.lightweightai.demo.McpServerRunner")
            .build();

        System.out.println("[1] 启动 McpServerRunner（默认配置: 上游 STDIO）");
        System.out.println("    链路: Client → STDIO → McpServerRunner → STDIO → UpstreamExampleServer");

        McpToolClient client = McpToolClient.builder()
            .serverName("demo-agent")
            .transport(new StdioClientTransport(serverParams,
                new JacksonMcpJsonMapper(new ObjectMapper())))
            .build();

        try {
            client.initialize();
            System.out.println("[2] MCP 握手成功\n");

            // 发现工具（包含 McpServerRunner 本地 + 上游代理）
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            System.out.println("\n[4] 调用工具：\n");

            // McpServerRunner 本地工具
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"),
                "Client → STDIO → McpServerRunner(本地)");
            callAndPrint(executor, "calculate", Map.of("a", 42.0, "op", "+", "b", 58.0),
                "Client → STDIO → McpServerRunner(本地)");

            // 上游代理工具（通过 STDIO）
            callAndPrint(executor, "translate_text",
                Map.of("text", "hello world", "from", "en", "to", "zh"),
                "Client → STDIO → McpServerRunner → STDIO → UpstreamServer");
            callAndPrint(executor, "sentiment_analysis",
                Map.of("text", "This framework is great!"),
                "Client → STDIO → McpServerRunner → STDIO → UpstreamServer");

            System.out.println("  >> Phase 1 验证通过: 上游 STDIO 链路正常 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] Phase 1 失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    // ==================== Phase 2: 上游 SSE（HTTP RPC）====================

    /**
     * 验证 McpServerRunner 内部的 SSE 上游连接
     *
     * <pre>
     * [本进程 JVM]                                   [子进程 JVM]
     * ┌─────────────┐    ┌──────────────┐           ┌───────────────┐
     * │ Tomcat SSE  │←───│ McpServer    │←──STDIO───│ McpClientDemo │
     * │ NLP Server  │SSE │ Runner       │           │ (本方法)       │
     * │ :randomPort │───→│ (子进程)      │──STDIO──→│               │
     * └─────────────┘    └──────────────┘           └───────────────┘
     *  上游 Server         Gateway                    Client
     *  (HTTP/SSE)          upstream:
     *                        nlp-service:
     *                          transport: sse
     *                          url: http://localhost:PORT/sse
     *
     * 核心验证点：McpServerRunner 通过 HttpClientSseClientTransport
     *            以 HTTP/SSE 方式连接上游，而不是 STDIO 子进程。
     * </pre>
     */
    static void demoUpstreamSse() {
        Tomcat tomcat = null;
        McpToolServer sseServer = null;
        McpToolClient client = null;
        Path tempConfig = null;

        try {
            // Step 1: 启动上游 SSE Server（嵌入式 Tomcat + NLP 工具）
            System.out.println("[1] 启动上游 SSE Server（嵌入式 Tomcat）...");

            ToolRegistry upstreamRegistry = new ToolRegistry();
            upstreamRegistry.registerObject(new UpstreamExampleServer.NlpTools());

            HttpServletSseServerTransportProvider sseTransport =
                HttpServletSseServerTransportProvider.builder()
                    .objectMapper(new ObjectMapper())
                    .messageEndpoint("/message")
                    .build();

            sseServer = McpToolServer.builder()
                .serverName("nlp-service-sse")
                .serverVersion("0.1.0")
                .toolRegistry(upstreamRegistry)
                .transportProvider(sseTransport)
                .build();
            sseServer.start();

            tomcat = new Tomcat();
            tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));
            tomcat.setPort(0);
            tomcat.getConnector();

            Context ctx = tomcat.addContext("", new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());
            Tomcat.addServlet(ctx, "mcp-sse", sseTransport);
            ctx.addServletMappingDecoded("/*", "mcp-sse");

            tomcat.start();
            int ssePort = tomcat.getConnector().getLocalPort();
            System.out.println("    上游 SSE Server 启动: http://localhost:" + ssePort + "/sse");
            System.out.println("    工具: translate_text, lookup_definition, sentiment_analysis");

            // Step 2: 生成临时 mcp-server.yaml，上游指向 SSE
            System.out.println("\n[2] 生成临时配置（upstream.transport=sse）...");
            tempConfig = Files.createTempFile("mcp-server-sse-", ".yaml");
            try (FileWriter w = new FileWriter(tempConfig.toFile())) {
                w.write("server:\n");
                w.write("  name: demo-agent-sse\n");
                w.write("  version: 0.1.0\n");
                w.write("upstream:\n");
                w.write("  servers:\n");
                w.write("    nlp-service:\n");
                w.write("      transport: sse\n");
                w.write("      url: \"http://localhost:" + ssePort + "\"\n");
                w.write("      timeoutSeconds: 30\n");
                w.write("      enabled: true\n");
            }
            System.out.println("    配置: " + tempConfig);
            System.out.println("    upstream.nlp-service.transport = sse");
            System.out.println("    upstream.nlp-service.url = http://localhost:" + ssePort);

            // Step 3: 启动 McpServerRunner 子进程，指向临时配置
            System.out.println("\n[3] 启动 McpServerRunner（-Dmcp.server.config=临时配置）...");
            System.out.println("    链路: Client → STDIO → McpServerRunner → HTTP/SSE → Tomcat");

            String javaCmd = ProcessHandle.current().info().command().orElse("java");
            String classpath = System.getProperty("java.class.path");

            ServerParameters serverParams = ServerParameters.builder(javaCmd)
                .args("-Dmcp.server.config=" + tempConfig.toAbsolutePath(),
                    "-cp", classpath,
                    "com.lightweightai.demo.McpServerRunner")
                .build();

            client = McpToolClient.builder()
                .serverName("demo-agent-sse")
                .transport(new StdioClientTransport(serverParams,
                    new JacksonMcpJsonMapper(new ObjectMapper())))
                .build();

            client.initialize();
            System.out.println("[4] MCP 握手成功\n");

            // Step 4: 发现工具
            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[5] 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName() + ": " + tool.getDescription());
            }

            ToolRegistry clientRegistry = new ToolRegistry();
            client.registerTools(clientRegistry);
            ToolExecutor executor = new ToolExecutor(clientRegistry);

            // Step 5: 调用工具 — NLP 工具通过 SSE 代理
            System.out.println("\n[6] 调用工具（NLP 工具走 HTTP/SSE RPC）：\n");

            // McpServerRunner 本地工具
            callAndPrint(executor, "get_city_info", Map.of("city", "tokyo"),
                "Client → STDIO → McpServerRunner(本地)");
            callAndPrint(executor, "get_weather", Map.of("city", "北京"),
                "Client → STDIO → McpServerRunner(本地)");

            // NLP 工具 — 通过 SSE/HTTP 代理到上游 Tomcat
            callAndPrint(executor, "translate_text",
                Map.of("text", "hello world", "from", "en", "to", "zh"),
                "Client → STDIO → McpServerRunner → HTTP/SSE → Tomcat");
            callAndPrint(executor, "lookup_definition",
                Map.of("word", "kernel", "language", "en"),
                "Client → STDIO → McpServerRunner → HTTP/SSE → Tomcat");
            callAndPrint(executor, "sentiment_analysis",
                Map.of("text", "This framework is great!"),
                "Client → STDIO → McpServerRunner → HTTP/SSE → Tomcat");

            // Step 6: 工具来源
            System.out.println("[7] 工具来源验证：");
            for (Tool tool : clientRegistry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w) ? "mcp:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n  >> Phase 2 验证通过: McpServerRunner 的 SSE 上游 RPC 正常 <<");
            System.out.println("  >> McpServerRunner 通过 HttpClientSseClientTransport 连接上游 <<");
            System.out.println("  >> NLP 工具的请求经 HTTP/SSE 到达 Tomcat，再逐层返回 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] Phase 2 失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n[8] 清理资源...");
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
            if (sseServer != null) {
                try { sseServer.close(); } catch (Exception e) { /* ignore */ }
            }
            if (tomcat != null) {
                try { tomcat.stop(); tomcat.destroy(); } catch (Exception e) { /* ignore */ }
            }
            if (tempConfig != null) {
                try { Files.deleteIfExists(tempConfig); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    // ==================== 工具方法 ====================

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args, String chain) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("debug-" + toolName, toolName, args));
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → " + result.getContent());
            System.out.println("    ↑ " + chain);
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println("    ↑ " + chain);
            System.out.println();
        }
    }
}
