package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolServer;

import java.util.Map;

/**
 * 上游 MCP Server 示例 — 模拟外部真实 RPC 服务
 *
 * 这个 Server 模拟一个提供翻译和知识查询的外部服务。
 * McpServerRunner 通过 MCP SDK 连接此 Server，发现并代理其工具。
 *
 * <h2>完整调用链路</h2>
 * <pre>
 * 外部 Client ──MCP──→ McpServerRunner ──MCP──→ UpstreamExampleServer
 *                      (作为 Server)       (作为 Client)     (作为 Server)
 *                      暴露所有工具        发现+代理          执行真实逻辑
 * </pre>
 *
 * <h2>启动方式</h2>
 * <pre>
 * java -cp &lt;classpath&gt; com.lightweightai.demo.UpstreamExampleServer
 * </pre>
 *
 * <h2>提供的工具</h2>
 * <ul>
 *   <li>translate_text - 文本翻译</li>
 *   <li>lookup_definition - 词语释义查询</li>
 *   <li>sentiment_analysis - 情感分析</li>
 * </ul>
 */
public class UpstreamExampleServer {

    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new NlpTools());

        System.err.println("========================================");
        System.err.println("  Upstream MCP Server: nlp-service");
        System.err.println("  Transport:  STDIO");
        System.err.println("  Tools:      " + registry.enabledCount());
        System.err.println("========================================");
        for (Tool tool : registry.getEnabled()) {
            String category = (tool instanceof ToolMetadata meta) ? meta.getCategory() : "default";
            System.err.println("  - " + tool.getName() + " [" + category + "]");
        }
        System.err.println("========================================");

        McpToolServer server = McpToolServer.builder()
            .serverName("nlp-service")
            .serverVersion("0.1.0")
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
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
            // 模拟翻译服务（真实场景会调用 Google Translate / DeepL API）
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
            // 简单的情感分析模拟
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
