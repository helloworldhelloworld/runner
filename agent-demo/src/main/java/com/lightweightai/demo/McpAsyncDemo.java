package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MCP Async Demo — 演示 Reactive 全链路流式工具调用
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li>Tool.executeReactive() — 所有工具的 Reactive 接口（default 方法）</li>
 *   <li>Flux&lt;ToolResultChunk&gt; — PROGRESS / LOG / COMPLETE / ERROR 统一流</li>
 *   <li>ToolExecutor.executeToolCallsReactive() — 多工具并行 Reactive 调用</li>
 *   <li>McpToolWrapper.executeReactive() — MCP 工具的进度/日志实时推送</li>
 *   <li>ProgressNotificationRouter — 按 progressToken 路由 MCP 进度通知</li>
 * </ul>
 *
 * <h2>演示内容</h2>
 * <pre>
 *   [A] 单工具 Reactive 调用（本地工具 → 退化为单个 COMPLETE 事件）
 *   [B] 模拟 MCP 工具 Reactive 调用（PROGRESS + LOG + COMPLETE 多事件流）
 *   [C] 多工具并行 Reactive 调用（Flux.merge 交错推送）
 *   [D] 同步 vs Reactive 对比
 * </pre>
 *
 * <h2>运行方式</h2>
 * <pre>
 * export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpAsyncDemo
 * </pre>
 */
public class McpAsyncDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  MCP Async Demo — Reactive 全链路流式工具调用        ║");
        System.out.println("║  Flux<ToolResultChunk>: PROGRESS + LOG + COMPLETE   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        // 注册工具：本地工具 + 模拟 MCP 工具
        ToolRegistry registry = new ToolRegistry();
        registry.scanAndRegister();  // SPI: MathTools, TimeTools, WebTools
        registry.register(new SimulatedMcpDeepWikiTool());  // 模拟 MCP 工具（带进度）

        ToolExecutor executor = new ToolExecutor(registry);

        System.out.println("[工具列表] " + registry.enabledCount() + " 个工具：");
        for (Tool tool : registry.getEnabled()) {
            String type = (tool instanceof SimulatedMcpDeepWikiTool) ? "mcp-reactive" : "local";
            System.out.println("    - " + tool.getName() + " [" + type + "]");
        }
        System.out.println();

        // [A] 单工具 Reactive 调用（本地工具）
        demoLocalToolReactive(registry);

        // [B] 模拟 MCP 工具 Reactive 调用（带 PROGRESS + LOG）
        demoMcpToolReactive(registry);

        // [C] 多工具并行 Reactive 调用
        demoParallelToolsReactive(executor);

        // [D] 同步 vs Reactive 对比
        demoSyncVsReactive(registry, executor);

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  MCP Async Demo 完成！                               ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    // ================================================================
    //  [A] 本地工具 Reactive 调用
    //
    //  本地工具通过 Tool 接口的 default executeReactive() 方法，
    //  自动退化为单个 COMPLETE 事件。不需要任何代码改动。
    //
    //  Tool.executeReactive(args) default 实现：
    //    Flux.defer(() -> {
    //        ToolResult result = execute(args);        // 调用同步方法
    //        return Flux.just(ToolResultChunk.complete(getName(), result));
    //    });
    // ================================================================

    static void demoLocalToolReactive(ToolRegistry registry) throws InterruptedException {
        System.out.println("--- [A] 本地工具 Reactive 调用 ---\n");
        System.out.println("  本地工具通过 default executeReactive() 自动获得 Reactive 能力");
        System.out.println("  退化为单个 COMPLETE 事件（无进度/日志）\n");

        Tool addTool = registry.get("add").orElse(null);
        if (addTool == null) {
            System.out.println("  [SKIP] add 工具不可用\n");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("  调用 add({a: 42, b: 58})：\n");

        addTool.executeReactive(Map.of("a", 42, "b", 58))
            .doOnNext(chunk -> {
                System.out.printf("    [%s] %s%n", chunk.getType(),
                    chunk.getType() == ToolResultChunk.ChunkType.COMPLETE
                        ? "result = " + chunk.getResult().getContent()
                        : chunk.getMessage());
            })
            .doOnComplete(() -> {
                System.out.println("    [DONE] Flux 完成（1 个事件）\n");
                latch.countDown();
            })
            .subscribe();

        latch.await(10, TimeUnit.SECONDS);
    }

    // ================================================================
    //  [B] MCP 工具 Reactive 调用（模拟进度推送）
    //
    //  真实 MCP 工具（McpToolWrapper）的 executeReactive() 实现：
    //    1. progressRouter.register(token) → Flux<ProgressNotification>
    //    2. loggingRouter.register(token)  → Flux<LoggingNotification>
    //    3. callToolReactive(name, args, token) → Mono<CallToolResult>
    //    4. Flux.merge(progress, logging, result) → Flux<ToolResultChunk>
    //
    //  这里用 SimulatedMcpDeepWikiTool 模拟这个过程：
    //    - 发射多个 PROGRESS 事件（模拟搜索进度）
    //    - 发射 LOG 事件（模拟日志输出）
    //    - 最后发射 COMPLETE 事件（最终结果）
    // ================================================================

    static void demoMcpToolReactive(ToolRegistry registry) throws InterruptedException {
        System.out.println("--- [B] MCP 工具 Reactive 调用（模拟进度推送） ---\n");
        System.out.println("  McpToolWrapper.executeReactive() 合并三路流：");
        System.out.println("    ├── progressRouter → Flux<PROGRESS>");
        System.out.println("    ├── loggingRouter  → Flux<LOG>");
        System.out.println("    └── callTool       → Mono<COMPLETE>");
        System.out.println("  合并为 Flux<ToolResultChunk>（实时推送）\n");

        Tool deepWiki = registry.get("ask_deepwiki").orElse(null);
        if (deepWiki == null) {
            System.out.println("  [SKIP] ask_deepwiki 工具不可用\n");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        int[] eventCount = {0};
        long startTime = System.currentTimeMillis();

        System.out.println("  调用 ask_deepwiki({repo: \"modelcontextprotocol/spec\", question: \"What is MCP?\"})：\n");

        deepWiki.executeReactive(Map.of(
                "repo", "modelcontextprotocol/spec",
                "question", "What is MCP?"))
            .doOnNext(chunk -> {
                eventCount[0]++;
                long elapsed = System.currentTimeMillis() - startTime;
                switch (chunk.getType()) {
                    case PROGRESS -> System.out.printf("    [%4dms] PROGRESS  %.0f%%  %s%n",
                        elapsed, chunk.getProgress() * 100, chunk.getMessage());
                    case LOG -> System.out.printf("    [%4dms] LOG       %s%n",
                        elapsed, chunk.getMessage());
                    case COMPLETE -> System.out.printf("    [%4dms] COMPLETE  %s%n",
                        elapsed, truncate(chunk.getResult().getContent(), 80));
                    case ERROR -> System.out.printf("    [%4dms] ERROR     %s%n",
                        elapsed, chunk.getMessage());
                }
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("%n    总耗时 %dms，收到 %d 个事件（PROGRESS + LOG + COMPLETE）%n%n",
                    elapsed, eventCount[0]);
                latch.countDown();
            })
            .subscribe();

        latch.await(30, TimeUnit.SECONDS);
    }

    // ================================================================
    //  [C] 多工具并行 Reactive 调用
    //
    //  ToolExecutor.executeToolCallsReactive(toolCalls)
    //    → Flux.merge(tool1.flux, tool2.flux, ...)
    //    → 进度事件交错推送（不同工具的事件可能交替出现）
    // ================================================================

    static void demoParallelToolsReactive(ToolExecutor executor) throws InterruptedException {
        System.out.println("--- [C] 多工具并行 Reactive 调用 ---\n");
        System.out.println("  ToolExecutor.executeToolCallsReactive() → Flux.merge()");
        System.out.println("  多个工具的事件交错推送\n");

        List<ToolCall> toolCalls = List.of(
            new ToolCall("call-1", "add", Map.of("a", 100, "b", 200)),
            new ToolCall("call-2", "ask_deepwiki",
                Map.of("repo", "spring-projects/spring-boot", "question", "How to start?")),
            new ToolCall("call-3", "multiply", Map.of("a", 7, "b", 8))
        );

        System.out.println("  并行调用 " + toolCalls.size() + " 个工具：");
        for (ToolCall tc : toolCalls) {
            System.out.println("    - " + tc.getName() + "(" + tc.getArguments() + ")");
        }
        System.out.println();

        CountDownLatch latch = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();
        int[] counts = {0, 0, 0, 0}; // progress, log, complete, error

        executor.executeToolCallsReactive(toolCalls)
            .doOnNext(chunk -> {
                long elapsed = System.currentTimeMillis() - startTime;
                switch (chunk.getType()) {
                    case PROGRESS -> {
                        counts[0]++;
                        System.out.printf("    [%4dms] PROGRESS  %-20s %.0f%%  %s%n",
                            elapsed, chunk.getToolName(),
                            chunk.getProgress() * 100, chunk.getMessage());
                    }
                    case LOG -> {
                        counts[1]++;
                        System.out.printf("    [%4dms] LOG       %-20s %s%n",
                            elapsed, chunk.getToolName(), chunk.getMessage());
                    }
                    case COMPLETE -> {
                        counts[2]++;
                        System.out.printf("    [%4dms] COMPLETE  %-20s %s%n",
                            elapsed, chunk.getToolName(),
                            truncate(chunk.getResult().getContent(), 60));
                    }
                    case ERROR -> {
                        counts[3]++;
                        System.out.printf("    [%4dms] ERROR     %-20s %s%n",
                            elapsed, chunk.getToolName(), chunk.getMessage());
                    }
                }
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("%n    全部完成，耗时 %dms（并行执行）%n", elapsed);
                System.out.printf("    事件统计: %d PROGRESS, %d LOG, %d COMPLETE, %d ERROR%n%n",
                    counts[0], counts[1], counts[2], counts[3]);
                latch.countDown();
            })
            .subscribe();

        latch.await(30, TimeUnit.SECONDS);
    }

    // ================================================================
    //  [D] 同步 vs Reactive 对比
    // ================================================================

    static void demoSyncVsReactive(ToolRegistry registry, ToolExecutor executor)
            throws InterruptedException {
        System.out.println("--- [D] 同步 vs Reactive 对比 ---\n");

        Map<String, Object> args = Map.of(
            "repo", "anthropics/claude-code",
            "question", "How does it work?");

        // 同步调用
        System.out.println("  [同步] tool.execute(args) — 阻塞等待最终结果");
        long syncStart = System.currentTimeMillis();
        ToolResult syncResult = executor.executeToolCall(
            new ToolCall("sync-1", "ask_deepwiki", args));
        long syncElapsed = System.currentTimeMillis() - syncStart;
        System.out.println("    结果: " + truncate(syncResult.getContent(), 80));
        System.out.println("    耗时: " + syncElapsed + "ms");
        System.out.println("    中间事件: 0（全部丢失）\n");

        // Reactive 调用
        System.out.println("  [Reactive] tool.executeReactive(args) — 非阻塞，实时推送");
        long reactiveStart = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        int[] eventCount = {0};

        Tool tool = registry.get("ask_deepwiki").orElse(null);
        if (tool == null) {
            System.out.println("    [SKIP]\n");
            return;
        }

        tool.executeReactive(args)
            .doOnNext(chunk -> {
                eventCount[0]++;
                long elapsed = System.currentTimeMillis() - reactiveStart;
                System.out.printf("    [%4dms] %s: %s%n", elapsed,
                    chunk.getType(),
                    chunk.getType() == ToolResultChunk.ChunkType.COMPLETE
                        ? truncate(chunk.getResult().getContent(), 60)
                        : chunk.getMessage());
            })
            .doOnComplete(() -> {
                long elapsed = System.currentTimeMillis() - reactiveStart;
                System.out.println("    耗时: " + elapsed + "ms");
                System.out.println("    中间事件: " + eventCount[0] + " 个（PROGRESS + LOG + COMPLETE）\n");
                latch.countDown();
            })
            .subscribe();

        latch.await(30, TimeUnit.SECONDS);

        // 对比表
        System.out.println("  ┌────────────────────┬─────────────────────────────────────┐");
        System.out.println("  │ 模式               │ 行为                                │");
        System.out.println("  ├────────────────────┼─────────────────────────────────────┤");
        System.out.println("  │ tool.execute()     │ 阻塞，只返回 ToolResult             │");
        System.out.println("  │                    │ 进度/日志信息丢失                   │");
        System.out.println("  ├────────────────────┼─────────────────────────────────────┤");
        System.out.println("  │ executeReactive()  │ 非阻塞，Flux<ToolResultChunk>       │");
        System.out.println("  │                    │ PROGRESS + LOG + COMPLETE 全链路    │");
        System.out.println("  │                    │ MCP ProgressNotification 实时路由   │");
        System.out.println("  └────────────────────┴─────────────────────────────────────┘");
        System.out.println();

        // 架构图
        System.out.println("  真实 MCP 工具的 Reactive 流式架构：");
        System.out.println("  ┌──────────────────────────────────────────────────────────┐");
        System.out.println("  │ McpToolWrapper.executeReactive(args)                    │");
        System.out.println("  │   │                                                      │");
        System.out.println("  │   ├── progressRouter.register(token) → Flux<PROGRESS>   │");
        System.out.println("  │   ├── loggingRouter.register(token)  → Flux<LOG>        │");
        System.out.println("  │   ├── callToolReactive(name, args, token) → Mono<Result>│");
        System.out.println("  │   │                                                      │");
        System.out.println("  │   └── Flux.merge(progress, logging, result)             │");
        System.out.println("  │         → Flux<ToolResultChunk>                         │");
        System.out.println("  │                                                          │");
        System.out.println("  │   McpAsyncClient 全局回调:                               │");
        System.out.println("  │     progressConsumer → ProgressNotificationRouter.route()│");
        System.out.println("  │     loggingConsumer  → LoggingNotificationRouter.route() │");
        System.out.println("  │     按 progressToken 分发到对应工具调用的 Flux sink      │");
        System.out.println("  └──────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  模拟 MCP DeepWiki 工具（带进度/日志推送）
    //
    //  真实场景中，这是 McpToolWrapper，进度来自 MCP ProgressNotification。
    //  这里 override executeReactive() 模拟多阶段流式事件。
    // ================================================================

    public static class SimulatedMcpDeepWikiTool implements Tool, ToolMetadata {

        @Override
        public String getName() {
            return "ask_deepwiki";
        }

        @Override
        public String getDescription() {
            return "Query DeepWiki for repository documentation (simulated MCP tool with progress)";
        }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.withRequired(Map.of(
                "repo", Map.of("type", "string", "description", "Repository name (e.g. owner/repo)"),
                "question", Map.of("type", "string", "description", "Question to ask")
            ), "repo", "question");
        }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            // 同步调用：block on reactive，但进度事件丢失
            return executeReactive(args)
                .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE)
                .next()
                .map(ToolResultChunk::getResult)
                .block();
        }

        @Override
        public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
            String repo = String.valueOf(args.getOrDefault("repo", "unknown"));
            String question = String.valueOf(args.getOrDefault("question", ""));
            String toolName = getName();

            // 模拟 MCP 工具的多阶段执行过程
            // 真实场景中，这些事件来自 ProgressNotificationRouter 和 LoggingNotificationRouter
            return Flux.create(sink -> {
                try {
                    // Phase 1: 搜索仓库
                    sink.next(ToolResultChunk.progress(toolName,
                        "Searching repository: " + repo, 0.0, 1.0));
                    Thread.sleep(100);

                    sink.next(ToolResultChunk.log(toolName, "INFO",
                        "Connected to DeepWiki API"));
                    Thread.sleep(50);

                    // Phase 2: 索引文档
                    sink.next(ToolResultChunk.progress(toolName,
                        "Indexing documentation...", 0.3, 1.0));
                    Thread.sleep(100);

                    sink.next(ToolResultChunk.log(toolName, "DEBUG",
                        "Found 42 relevant documents"));
                    Thread.sleep(50);

                    // Phase 3: 生成答案
                    sink.next(ToolResultChunk.progress(toolName,
                        "Generating answer for: " + truncateStatic(question, 30), 0.7, 1.0));
                    Thread.sleep(150);

                    sink.next(ToolResultChunk.log(toolName, "INFO",
                        "Answer generated, confidence: 0.95"));

                    // Phase 4: 完成
                    sink.next(ToolResultChunk.progress(toolName,
                        "Complete", 1.0, 1.0));

                    String answer = String.format(
                        "[DeepWiki] %s — Based on %s documentation: " +
                        "The repository implements a protocol specification for " +
                        "model context sharing between AI applications and tools.",
                        question, repo);
                    sink.next(ToolResultChunk.complete(toolName, ToolResult.success(answer)));
                    sink.complete();
                } catch (InterruptedException e) {
                    sink.next(ToolResultChunk.error(toolName, "Interrupted: " + e.getMessage()));
                    sink.complete();
                }
            });
        }

        @Override
        public String getCategory() { return "mcp:deepwiki"; }

        @Override
        public List<String> getTags() { return List.of("mcp", "reactive", "deepwiki"); }

        @Override
        public String getAuthor() { return "mcp-server:deepwiki"; }

        @Override
        public boolean isReadOnly() { return true; }

        private static String truncateStatic(String text, int maxLen) {
            if (text == null) return "";
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
