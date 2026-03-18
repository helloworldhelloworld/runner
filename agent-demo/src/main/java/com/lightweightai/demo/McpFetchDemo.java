package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MCP Fetch Demo — 验证通过 McpServerRunner 代理外部免费 MCP Server
 *
 * <h2>架构</h2>
 * <pre>
 * McpFetchDemo ──STDIO──→ McpServerRunner ──HTTP Stream──→ DeepWiki (mcp.deepwiki.com/mcp)
 *                          (mcp-server.yaml) ──STDIO─────→ @modelcontextprotocol/server-fetch
 *                          ├── 本地工具 (get_city_info, get_weather, calculate...)
 *                          ├── HTTP Stream 上游 (deepwiki)
 *                          └── STDIO 上游 (fetch-server)
 * </pre>
 *
 * <h2>前置条件</h2>
 * <ul>
 *   <li>Node.js + npx 已安装（{@code npx --version}）</li>
 * </ul>
 *
 * <h2>运行方式</h2>
 * <pre>
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpFetchDemo
 * </pre>
 */
public class McpFetchDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  MCP Fetch Demo — DeepWiki + server-fetch   ║");
        System.out.println("║  via McpServerRunner (HTTP Stream + STDIO)  ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // 构建 McpServerRunner 启动参数
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

        System.out.println("[1] 启动 McpServerRunner（含 deepwiki + fetch-server 上游）...\n");

        StdioClientTransport transport = new StdioClientTransport(paramsBuilder.build(),
            new JacksonMcpJsonMapper(new ObjectMapper()));

        ToolClient client = ToolClient.create()
            .addMcpServer("demo-agent", transport)
            .build();

        try {
            System.out.println("[2] MCP 握手成功\n");

            ToolExecutor executor = client.getToolExecutor();

            // 列出所有发现的工具
            System.out.println("[3] 发现 " + client.getToolRegistry().enabledCount() + " 个工具：");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                String source = (tool instanceof McpToolWrapper w)
                    ? "upstream:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " [" + source + "]");
            }

            // 调用 DeepWiki 工具 — 查询仓库 wiki 结构
            System.out.println("\n[4] 调用 DeepWiki 工具（HTTP Stream 上游）：\n");
            callAndPrint(executor, "read_wiki_structure",
                Map.of("repoName", "modelcontextprotocol/specification"));

            // 调用 fetch 工具抓取网页
            System.out.println("[5] 调用 fetch 工具（STDIO 上游）：\n");
            callAndPrint(executor, "fetch", Map.of("url", "https://example.com"));

            // 本地工具验证
            System.out.println("[6] 验证本地工具仍可用：\n");
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));

            // ==================== [7] Reactive 异步调用（MCP 真实链路） ====================
            System.out.println("[7] Reactive 异步调用（MCP 真实链路）：\n");
            System.out.println("  通过 McpToolWrapper.executeReactive() 走真实 MCP 协议");
            System.out.println("  McpAsyncClient → ProgressNotificationRouter → Flux<ToolResultChunk>\n");

            // 7a: DeepWiki 工具 Reactive 调用
            Tool wikiTool = client.getToolRegistry().get("read_wiki_structure").orElse(null);
            if (wikiTool != null) {
                System.out.println("  [7a] read_wiki_structure — Reactive（真实 MCP → DeepWiki）：\n");
                callReactiveAndPrint(wikiTool, Map.of("repoName", "modelcontextprotocol/specification"));
            }

            // 7b: ask_question 工具 Reactive 调用（如果存在，更可能有进度通知）
            Tool askTool = client.getToolRegistry().get("ask_question").orElse(null);
            if (askTool != null) {
                System.out.println("  [7b] ask_question — Reactive（真实 MCP → DeepWiki）：\n");
                callReactiveAndPrint(askTool, Map.of(
                    "repoName", "modelcontextprotocol/specification",
                    "question", "What is MCP?"));
            }

            // 7c: 同步 vs Reactive 对比
            if (wikiTool != null) {
                System.out.println("  [7c] 同步 vs Reactive 对比（read_wiki_structure）：\n");
                Map<String, Object> wikiArgs = Map.of("repoName", "spring-projects/spring-boot");

                // 同步
                long syncStart = System.currentTimeMillis();
                ToolResult syncResult = executor.executeToolCall(
                    new ToolCall("cmp-sync", "read_wiki_structure", wikiArgs));
                long syncMs = System.currentTimeMillis() - syncStart;
                System.out.println("    同步: " + truncate(syncResult.getContent(), 80)
                    + " (" + syncMs + "ms)");

                // Reactive
                long reactStart = System.currentTimeMillis();
                int[] eventCount = {0};
                CountDownLatch cmpLatch = new CountDownLatch(1);
                wikiTool.executeReactive(wikiArgs)
                    .doOnNext(chunk -> {
                        eventCount[0]++;
                        long elapsed = System.currentTimeMillis() - reactStart;
                        System.out.printf("    异步 [%4dms] %s: %s%n", elapsed, chunk.getType(),
                            chunk.getType() == ToolResultChunk.ChunkType.COMPLETE
                                ? truncate(chunk.getResult().getContent(), 60)
                                : chunk.getMessage());
                    })
                    .doOnComplete(cmpLatch::countDown)
                    .doOnError(e -> cmpLatch.countDown())
                    .subscribe();
                cmpLatch.await(60, TimeUnit.SECONDS);
                long reactMs = System.currentTimeMillis() - reactStart;
                System.out.println("    异步总计: " + eventCount[0] + " 个事件, " + reactMs + "ms");
                System.out.println("    如果上游 MCP Server 支持 ProgressNotification，");
                System.out.println("    会在 COMPLETE 之前看到 PROGRESS/LOG 事件\n");
            }

            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  Demo 验证通过！（含 Reactive 异步链路）      ║");
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
        int[] eventCount = {0};

        System.out.println("    " + tool.getName() + "(" + args + ") → Flux<ToolResultChunk>:");

        tool.executeReactive(args)
            .doOnNext(chunk -> {
                eventCount[0]++;
                long elapsed = System.currentTimeMillis() - startTime;
                switch (chunk.getType()) {
                    case PROGRESS -> System.out.printf("      [%5dms] PROGRESS  %.0f%%  %s%n",
                        elapsed, chunk.getProgress() * 100, chunk.getMessage());
                    case LOG -> System.out.printf("      [%5dms] LOG       %s%n",
                        elapsed, chunk.getMessage());
                    case COMPLETE -> System.out.printf("      [%5dms] COMPLETE  %s%n",
                        elapsed, truncate(chunk.getResult().getContent(), 200));
                    case ERROR -> System.out.printf("      [%5dms] ERROR     %s%n",
                        elapsed, chunk.getMessage());
                }
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("      完成: %d 个事件, %dms%n%n", eventCount[0], elapsed);
                latch.countDown();
            })
            .doOnError(e -> {
                System.out.println("      ERROR: " + e.getMessage() + "\n");
                latch.countDown();
            })
            .subscribe();

        latch.await(60, TimeUnit.SECONDS);
    }

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("debug-" + toolName, toolName, args));
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → " + truncate(result.getContent(), 500));
            if (result.hasStructuredContent()) {
                System.out.println("    → structuredContent: "
                    + truncate(String.valueOf(result.getStructuredContent()), 300));
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println();
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "... [truncated]";
    }
}
