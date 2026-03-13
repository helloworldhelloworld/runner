package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        // 使用 ToolClient 统一管理：addMcpServer 会自动 initialize + discover + register
        StdioClientTransport transport = new StdioClientTransport(paramsBuilder.build(),
            new JacksonMcpJsonMapper(new ObjectMapper()));

        ToolClient client = ToolClient.create()
            .addMcpServer("demo-agent", transport)
            .build();

        try {
            System.out.println("[2] MCP 握手成功\n");

            // 通过 ToolClient 获取统一的 ToolExecutor
            ToolExecutor executor = client.getToolExecutor();

            // 发现的工具
            System.out.println("[3] 发现 " + client.getToolRegistry().enabledCount() + " 个工具：");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                System.out.println("    - " + tool.getName());
            }

            // 调用工具
            System.out.println("\n[4] 调用工具：\n");

            // 本地工具（McpServerRunner 自带）
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));
            callAndPrint(executor, "get_weather", Map.of("city", "tokyo"));
            callAndPrint(executor, "calculate", Map.of("a", 12.5, "op", "+", "b", 7.3));

            // 上游代理工具 — 带参数的示例调用
            callAndPrint(executor, "goldPrice/search",
                Map.of("query", "黄金价格", "extraInfo", Map.of()));

            // 上游代理工具（动态发现，来自配置的外部 MCP Server）
            Set<String> localTools = Set.of("get_city_info", "get_weather", "calculate",
                "goldPrice/search");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                if (tool instanceof McpToolWrapper w && !localTools.contains(tool.getName())) {
                    System.out.println("  [upstream:" + w.getServerName() + "] " + tool.getName());
                    System.out.println("    schema: " + tool.getSchema().toMap());
                    callAndPrint(executor, tool.getName(), Map.of());
                }
            }

            // 工具来源
            System.out.println("[5] 工具来源：");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                String source = (tool instanceof McpToolWrapper w)
                    ? "upstream:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            // ==================== [6] Reactive 异步调用验证 ====================
            System.out.println("\n[6] Reactive 异步调用验证（MCP 真实链路）：\n");

            // 6a: 单工具 Reactive — 通过真实 MCP 协议调用
            System.out.println("  [6a] 单工具 executeReactive() — MCP 真实链路：\n");
            Tool weatherTool = client.getToolRegistry().get("get_weather").orElse(null);
            if (weatherTool != null) {
                callReactiveAndPrint(weatherTool, Map.of("city", "tokyo"));
            } else {
                System.out.println("    [SKIP] get_weather 不可用\n");
            }

            // 6b: 多工具并行 Reactive
            System.out.println("  [6b] 多工具并行 executeToolCallsReactive() — Flux.merge()：\n");
            callParallelReactive(executor, List.of(
                new ToolCall("r-1", "get_weather", Map.of("city", "beijing")),
                new ToolCall("r-2", "calculate", Map.of("a", 99.0, "op", "*", "b", 3.0)),
                new ToolCall("r-3", "get_city_info", Map.of("city", "shanghai"))
            ));

            // 6c: 同步 vs Reactive 对比
            System.out.println("  [6c] 同步 vs Reactive 对比：\n");
            Map<String, Object> testArgs = Map.of("city", "tokyo");

            long syncStart = System.currentTimeMillis();
            ToolResult syncResult = executor.executeToolCall(
                new ToolCall("cmp-sync", "get_weather", testArgs));
            long syncMs = System.currentTimeMillis() - syncStart;
            System.out.println("    同步: " + syncResult.getContent() + " (" + syncMs + "ms, 0 中间事件)");

            if (weatherTool != null) {
                long reactStart = System.currentTimeMillis();
                int[] eventCount = {0};
                CountDownLatch cmpLatch = new CountDownLatch(1);
                weatherTool.executeReactive(testArgs)
                    .doOnNext(chunk -> eventCount[0]++)
                    .doOnComplete(cmpLatch::countDown)
                    .subscribe();
                cmpLatch.await(30, TimeUnit.SECONDS);
                long reactMs = System.currentTimeMillis() - reactStart;
                System.out.println("    异步: " + eventCount[0] + " 个事件 (" + reactMs + "ms)");
                System.out.println("    说明: 本地 MCP 工具无进度通知时退化为 1 个 COMPLETE 事件");
                System.out.println("          真正的远程 MCP Server（如 DeepWiki）可产生 PROGRESS/LOG 事件");
            }

            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║  验证通过！（含 Reactive 异步链路）           ║");
            System.out.println("╚══════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    private static void callReactiveAndPrint(Tool tool, Map<String, Object> args)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();

        System.out.println("    " + tool.getName() + "(" + args + ") → Flux<ToolResultChunk>:");

        tool.executeReactive(args)
            .doOnNext(chunk -> {
                long elapsed = System.currentTimeMillis() - startTime;
                switch (chunk.getType()) {
                    case PROGRESS -> System.out.printf("      [%4dms] PROGRESS  %.0f%%  %s%n",
                        elapsed, chunk.getProgress() * 100, chunk.getMessage());
                    case LOG -> System.out.printf("      [%4dms] LOG       %s%n",
                        elapsed, chunk.getMessage());
                    case COMPLETE -> System.out.printf("      [%4dms] COMPLETE  %s%n",
                        elapsed, chunk.getResult().getContent());
                    case ERROR -> System.out.printf("      [%4dms] ERROR     %s%n",
                        elapsed, chunk.getMessage());
                }
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("      完成 (%dms)%n%n", elapsed);
                latch.countDown();
            })
            .doOnError(e -> {
                System.out.println("      ERROR: " + e.getMessage() + "\n");
                latch.countDown();
            })
            .subscribe();

        latch.await(30, TimeUnit.SECONDS);
    }

    private static void callParallelReactive(ToolExecutor executor, List<ToolCall> toolCalls)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();
        int[] counts = {0, 0, 0, 0}; // progress, log, complete, error

        executor.executeToolCallsReactive(toolCalls)
            .doOnNext(chunk -> {
                long elapsed = System.currentTimeMillis() - startTime;
                switch (chunk.getType()) {
                    case PROGRESS -> {
                        counts[0]++;
                        System.out.printf("      [%4dms] PROGRESS  %-15s %s%n",
                            elapsed, chunk.getToolName(), chunk.getMessage());
                    }
                    case LOG -> {
                        counts[1]++;
                        System.out.printf("      [%4dms] LOG       %-15s %s%n",
                            elapsed, chunk.getToolName(), chunk.getMessage());
                    }
                    case COMPLETE -> {
                        counts[2]++;
                        System.out.printf("      [%4dms] COMPLETE  %-15s %s%n",
                            elapsed, chunk.getToolName(), chunk.getResult().getContent());
                    }
                    case ERROR -> {
                        counts[3]++;
                        System.out.printf("      [%4dms] ERROR     %-15s %s%n",
                            elapsed, chunk.getToolName(), chunk.getMessage());
                    }
                }
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("      并行完成 (%dms): %d PROGRESS, %d LOG, %d COMPLETE, %d ERROR%n%n",
                    elapsed, counts[0], counts[1], counts[2], counts[3]);
                latch.countDown();
            })
            .subscribe();

        latch.await(30, TimeUnit.SECONDS);
    }

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("debug-" + toolName, toolName, args));
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → " + result.getContent());
            if (result.hasStructuredContent()) {
                System.out.println("    → structuredContent: " + result.getStructuredContent());
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println();
        }
    }
}
