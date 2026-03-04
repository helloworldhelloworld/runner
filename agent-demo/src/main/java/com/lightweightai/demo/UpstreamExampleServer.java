package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.util.Map;

/**
 * 上游 MCP Server 示例 — 支持 STDIO 和 SSE 两种传输模式
 *
 * <h2>启动方式</h2>
 * <pre>
 * # STDIO 模式（默认，用于子进程通信）
 * java -cp &lt;classpath&gt; com.lightweightai.demo.UpstreamExampleServer
 *
 * # SSE/HTTP 模式（独立 HTTP 服务，真正的 RPC）
 * java -cp &lt;classpath&gt; com.lightweightai.demo.UpstreamExampleServer --sse --port 8081
 * java -cp &lt;classpath&gt; com.lightweightai.demo.UpstreamExampleServer --sse --port 0   # 随机端口
 * </pre>
 *
 * <h2>SSE 模式架构</h2>
 * <pre>
 * 外部 Client ──HTTP/SSE──→ Embedded Tomcat (:port)
 *                            │
 *                            └── HttpServletSseServerTransportProvider (Servlet)
 *                                    ↓
 *                               McpToolServer
 *                               ├── translate_text
 *                               ├── lookup_definition
 *                               └── sentiment_analysis
 *
 * SSE 协议：
 *   GET  /sse     → 建立 SSE 连接
 *   POST /message → JSON-RPC 消息
 * </pre>
 *
 * <h2>就绪信号</h2>
 * SSE 模式启动完成后，向 stderr 输出 {@code SSE_READY:PORT}，
 * 供父进程（如 McpClientDemo）获取实际监听端口。
 */
public class UpstreamExampleServer {

    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new NlpTools());

        // 解析命令行参数
        boolean sseMode = false;
        int port = 8081;
        for (int i = 0; i < args.length; i++) {
            if ("--sse".equals(args[i])) {
                sseMode = true;
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        if (sseMode) {
            startSseMode(registry, port);
        } else {
            startStdioMode(registry);
        }
    }

    // ==================== STDIO 模式 ====================

    private static void startStdioMode(ToolRegistry registry) {
        System.err.println("========================================");
        System.err.println("  Upstream MCP Server: nlp-service");
        System.err.println("  Transport:  STDIO");
        System.err.println("  Tools:      " + registry.enabledCount());
        System.err.println("========================================");
        printTools(registry);

        McpToolServer server = McpToolServer.builder()
            .serverName("nlp-service")
            .serverVersion("0.1.0")
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
    }

    // ==================== SSE/HTTP 模式 ====================

    private static void startSseMode(ToolRegistry registry, int port) {
        try {
            // 1. 创建 SSE 传输（Servlet）
            HttpServletSseServerTransportProvider sseTransport =
                HttpServletSseServerTransportProvider.builder()
                    .objectMapper(new ObjectMapper())
                    .messageEndpoint("/message")
                    .build();

            // 2. 创建 MCP Server（注册工具到 SSE 传输层）
            McpToolServer mcpServer = McpToolServer.builder()
                .serverName("nlp-service")
                .serverVersion("0.1.0")
                .toolRegistry(registry)
                .transportProvider(sseTransport)
                .build();
            mcpServer.start();

            // 3. 启动嵌入式 Tomcat
            Tomcat tomcat = new Tomcat();
            tomcat.setBaseDir(System.getProperty("java.io.tmpdir"));
            tomcat.setPort(port);
            tomcat.getConnector();

            Context ctx = tomcat.addContext("",
                new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());
            Tomcat.addServlet(ctx, "mcp-sse", sseTransport);
            ctx.addServletMappingDecoded("/*", "mcp-sse");

            tomcat.start();

            int actualPort = tomcat.getConnector().getLocalPort();

            System.err.println("========================================");
            System.err.println("  Upstream MCP Server: nlp-service");
            System.err.println("  Transport:  SSE/HTTP");
            System.err.println("  URL:        http://localhost:" + actualPort + "/sse");
            System.err.println("  Tools:      " + registry.enabledCount());
            System.err.println("========================================");
            printTools(registry);

            // 就绪信号 — 父进程通过读取此行获取端口
            System.err.println("SSE_READY:" + actualPort);

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                mcpServer.close();
                try { tomcat.stop(); } catch (Exception e) { /* ignore */ }
            }));

            // 阻塞等待
            tomcat.getServer().await();

        } catch (Exception e) {
            System.err.println("Failed to start SSE server: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void printTools(ToolRegistry registry) {
        for (Tool tool : registry.getEnabled()) {
            String category = (tool instanceof ToolMetadata meta) ? meta.getCategory() : "default";
            System.err.println("  - " + tool.getName() + " [" + category + "]");
        }
        System.err.println("========================================");
    }

    /**
     * NLP 工具集 — 模拟外部 NLP 服务的真实 API
     */
    public static class NlpTools {

        @ToolFunction(
            name = "translate_text",
            description = "Translate text between languages",
            category = "nlp",
            tags = {"nlp", "translation", "language"}
        )
        public String translateText(
            @ToolParam(name = "text", description = "Text to translate", required = true) String text,
            @ToolParam(name = "from", description = "Source language (e.g., en, zh, ja)", required = true) String from,
            @ToolParam(name = "to", description = "Target language (e.g., en, zh, ja)", required = true) String to
        ) {
            return switch (from.toLowerCase() + "->" + to.toLowerCase()) {
                case "en->zh" -> "[翻译] " + text + " → " + mockTranslateEnZh(text);
                case "zh->en" -> "[Translated] " + text + " → " + mockTranslateZhEn(text);
                case "en->ja" -> "[翻訳] " + text + " → " + mockTranslateEnJa(text);
                default -> "[Translation] " + text + " (" + from + " → " + to + ")";
            };
        }

        @ToolFunction(
            name = "lookup_definition",
            description = "Look up the definition of a word or phrase",
            category = "nlp",
            tags = {"nlp", "dictionary", "definition"},
            readOnly = true
        )
        public String lookupDefinition(
            @ToolParam(name = "word", description = "Word or phrase to look up", required = true) String word,
            @ToolParam(name = "language", description = "Language code (default: en)") String language
        ) {
            String lang = (language != null && !language.isBlank()) ? language : "en";
            return switch (word.toLowerCase()) {
                case "kernel" -> "kernel (n): The core or most essential part; in computing, the central component of an OS";
                case "orchestration" -> "orchestration (n): The coordination and arrangement of multiple components to work together";
                case "fusion" -> "fusion (n): The process of combining multiple elements into a unified whole";
                case "agent" -> "agent (n): An entity that acts autonomously on behalf of a user or system";
                default -> word + " (" + lang + "): definition not found in local dictionary";
            };
        }

        @ToolFunction(
            name = "sentiment_analysis",
            description = "Analyze the sentiment of text (positive/negative/neutral)",
            category = "nlp",
            tags = {"nlp", "sentiment", "analysis"},
            readOnly = true,
            idempotent = true
        )
        public String sentimentAnalysis(
            @ToolParam(name = "text", description = "Text to analyze", required = true) String text
        ) {
            String lower = text.toLowerCase();
            double score;
            String label;
            if (lower.contains("great") || lower.contains("excellent") || lower.contains("love")
                || lower.contains("好") || lower.contains("棒")) {
                score = 0.85;
                label = "positive";
            } else if (lower.contains("bad") || lower.contains("terrible") || lower.contains("hate")
                       || lower.contains("差") || lower.contains("糟")) {
                score = -0.72;
                label = "negative";
            } else {
                score = 0.1;
                label = "neutral";
            }
            return String.format("{\"sentiment\":\"%s\", \"score\":%.2f, \"text\":\"%s\"}", label, score, text);
        }

        private String mockTranslateEnZh(String text) {
            return switch (text.toLowerCase()) {
                case "hello" -> "你好";
                case "hello world" -> "你好世界";
                case "good morning" -> "早上好";
                case "thank you" -> "谢谢";
                default -> "(中文翻译: " + text + ")";
            };
        }

        private String mockTranslateZhEn(String text) {
            return switch (text) {
                case "你好" -> "Hello";
                case "谢谢" -> "Thank you";
                case "早上好" -> "Good morning";
                default -> "(English translation: " + text + ")";
            };
        }

        private String mockTranslateEnJa(String text) {
            return switch (text.toLowerCase()) {
                case "hello" -> "こんにちは";
                case "thank you" -> "ありがとう";
                default -> "(日本語翻訳: " + text + ")";
            };
        }
    }
}
