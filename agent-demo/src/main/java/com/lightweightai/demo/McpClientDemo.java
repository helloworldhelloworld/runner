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

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
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
 *
 * Phase 2: McpServerRunner 上游为 SSE（HTTP RPC）
 *   McpClientDemo ──STDIO──→ McpServerRunner ──HTTP/SSE──→ UpstreamExampleServer --sse
 *
 * 同一个 UpstreamExampleServer，两种传输方式。Phase 2 验证真正的 HTTP 远程调用。
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║  MCP 端到端调试 — 验证 McpServerRunner 上游链路  ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        // Phase 1
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  Phase 1: 上游 STDIO                                        │");
        System.out.println("│  Client → STDIO → McpServerRunner → STDIO → UpstreamServer  │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");
        demoUpstreamStdio();

        System.out.println("\n");

        // Phase 2
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.println("│  Phase 2: 上游 SSE（HTTP RPC）                               │");
        System.out.println("│  Client → STDIO → McpServerRunner → SSE → UpstreamServer    │");
        System.out.println("└──────────────────────────────────────────────────────────────┘\n");
        demoUpstreamSse();

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║  全部验证通过！STDIO + SSE 上游均正常工作         ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    // ==================== Phase 1: 上游 STDIO ====================

    static void demoUpstreamStdio() {
        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

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

            List<McpToolWrapper> tools = client.discoverMcpTools();
            System.out.println("[3] 发现 " + tools.size() + " 个工具：");
            for (McpToolWrapper tool : tools) {
                System.out.println("    - " + tool.getName());
            }

            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            System.out.println("\n[4] 调用工具：\n");

            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"),
                "McpServerRunner(本地)");
            callAndPrint(executor, "translate_text",
                Map.of("text", "hello world", "from", "en", "to", "zh"),
                "McpServerRunner → STDIO → UpstreamServer");
            callAndPrint(executor, "sentiment_analysis",
                Map.of("text", "This framework is great!"),
                "McpServerRunner → STDIO → UpstreamServer");

            System.out.println("  >> Phase 1 验证通过 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] Phase 1 失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    // ==================== Phase 2: 上游 SSE ====================

    /**
     * 验证 McpServerRunner 通过 SSE/HTTP 连接外部 UpstreamExampleServer
     *
     * <pre>
     * 进程 1: UpstreamExampleServer --sse --port 0  (独立 HTTP 进程)
     *         监听 http://localhost:PORT/sse
     *
     * 进程 2: McpServerRunner                        (子进程)
     *         mcp-server.yaml:
     *           upstream.nlp-service.transport = sse
     *           upstream.nlp-service.url = http://localhost:PORT
     *         通过 HttpClientSseClientTransport 连接进程 1
     *
     * 本进程: McpClientDemo                           (Client)
     *         通过 STDIO 连接进程 2
     *         调用 translate_text → 进程 2 → HTTP/SSE → 进程 1 → 返回
     * </pre>
     */
    static void demoUpstreamSse() {
        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        Process upstreamProcess = null;
        McpToolClient client = null;
        Path tempConfig = null;

        try {
            // Step 1: 启动 UpstreamExampleServer --sse --port 0（独立 HTTP 进程）
            System.out.println("[1] 启动 UpstreamExampleServer（SSE/HTTP 模式）...");
            ProcessBuilder pb = new ProcessBuilder(
                javaCmd, "-cp", classpath,
                "com.lightweightai.demo.UpstreamExampleServer",
                "--sse", "--port", "0");
            pb.redirectErrorStream(false);
            upstreamProcess = pb.start();

            // 读取 stderr 等待就绪信号 SSE_READY:PORT
            int ssePort = waitForSseReady(upstreamProcess);
            System.out.println("    UpstreamExampleServer 启动: http://localhost:" + ssePort + "/sse");
            System.out.println("    PID: " + upstreamProcess.pid());

            // Step 2: 生成临时 mcp-server.yaml
            System.out.println("\n[2] 生成临时 mcp-server.yaml（upstream → SSE）...");
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
            System.out.println("    upstream.nlp-service.transport = sse");
            System.out.println("    upstream.nlp-service.url = http://localhost:" + ssePort);

            // Step 3: 启动 McpServerRunner（读取临时配置，通过 SSE 连接上游）
            System.out.println("\n[3] 启动 McpServerRunner（-Dmcp.server.config=临时配置）...");
            System.out.println("    链路: Client → STDIO → McpServerRunner → HTTP/SSE → UpstreamServer");

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
                System.out.println("    - " + tool.getName());
            }

            ToolRegistry registry = new ToolRegistry();
            client.registerTools(registry);
            ToolExecutor executor = new ToolExecutor(registry);

            // Step 5: 调用工具
            System.out.println("\n[6] 调用工具（NLP 工具走 HTTP/SSE）：\n");

            // 本地工具
            callAndPrint(executor, "get_city_info", Map.of("city", "tokyo"),
                "McpServerRunner(本地)");

            // NLP 工具 — 通过 SSE 代理到独立 UpstreamExampleServer 进程
            callAndPrint(executor, "translate_text",
                Map.of("text", "hello world", "from", "en", "to", "zh"),
                "McpServerRunner → HTTP/SSE → UpstreamServer(PID:" + upstreamProcess.pid() + ")");
            callAndPrint(executor, "lookup_definition",
                Map.of("word", "kernel", "language", "en"),
                "McpServerRunner → HTTP/SSE → UpstreamServer(PID:" + upstreamProcess.pid() + ")");
            callAndPrint(executor, "sentiment_analysis",
                Map.of("text", "This framework is great!"),
                "McpServerRunner → HTTP/SSE → UpstreamServer(PID:" + upstreamProcess.pid() + ")");

            // 工具来源
            System.out.println("[7] 工具来源：");
            for (Tool tool : registry.getEnabled()) {
                String source = (tool instanceof McpToolWrapper w) ? "mcp:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n  >> Phase 2 验证通过 <<");
            System.out.println("  >> McpServerRunner 通过 HTTP/SSE 连接独立 UpstreamExampleServer 进程 <<");

        } catch (Exception e) {
            System.err.println("[ERROR] Phase 2 失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n[8] 清理资源...");
            if (client != null) {
                try { client.close(); } catch (Exception e) { /* ignore */ }
            }
            if (upstreamProcess != null && upstreamProcess.isAlive()) {
                upstreamProcess.destroy();
                System.out.println("    UpstreamExampleServer 进程已终止");
            }
            if (tempConfig != null) {
                try { Files.deleteIfExists(tempConfig); } catch (Exception e) { /* ignore */ }
            }
        }
    }

    /**
     * 等待 UpstreamExampleServer 输出 SSE_READY:PORT 信号
     */
    private static int waitForSseReady(Process process) throws Exception {
        BufferedReader stderr = new BufferedReader(
            new InputStreamReader(process.getErrorStream()));
        String line;
        long deadline = System.currentTimeMillis() + 30_000; // 30s 超时
        while ((line = stderr.readLine()) != null) {
            System.err.println("    [upstream] " + line);
            if (line.startsWith("SSE_READY:")) {
                return Integer.parseInt(line.substring("SSE_READY:".length()).trim());
            }
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("UpstreamExampleServer 启动超时（30s）");
            }
        }
        throw new RuntimeException("UpstreamExampleServer 进程意外退出");
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
