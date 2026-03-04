package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 独立 MCP Server 启动入口
 *
 * 将本地工具和<b>真实 RPC 工具</b>暴露为 MCP 服务端。
 * 外部 MCP 客户端（Claude Desktop、Cursor、其他 Agent）通过 MCP 协议发现并调用。
 *
 * <h2>工具分类</h2>
 * <ul>
 *   <li><b>本地工具</b> — CityInfoTools, MathCalcTools（纯本地计算）</li>
 *   <li><b>RPC 工具</b> — HttpRpcTools（真实 HTTP 远程调用）</li>
 *   <li><b>SPI 工具</b> — MathTools, TimeTools, WebTools（自动发现）</li>
 * </ul>
 *
 * <h2>启动方式</h2>
 * <pre>
 * # 方式一：Maven 直接运行
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpServerRunner
 *
 * # 方式二：先打包再运行
 * mvn package -DskipTests
 * java -cp agent-demo/target/classes:agent-mcp/target/classes:agent-kernel/target/classes:agent-tools/target/classes \
 *      com.lightweightai.demo.McpServerRunner
 * </pre>
 *
 * <h2>环境变量（RPC 工具需要）</h2>
 * <pre>
 * export ANTHROPIC_API_KEY=sk-ant-...   # llm_ask 工具需要
 * </pre>
 *
 * <h2>Claude Desktop 配置</h2>
 * <pre>
 * {
 *   "mcpServers": {
 *     "demo-agent": {
 *       "command": "java",
 *       "args": ["-cp", "path/to/classpath", "com.lightweightai.demo.McpServerRunner"]
 *     }
 *   }
 * }
 * </pre>
 */
public class McpServerRunner {

    public static void main(String[] args) {
        // 注册所有要暴露的工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTools());   // 本地工具
        registry.registerObject(new MathCalcTools());    // 本地工具
        registry.registerObject(new HttpRpcTools());     // ★ 真实 RPC 工具
        registry.scanAndRegister();                      // SPI 扫描

        System.err.println("========================================");
        System.err.println("  MCP Server: demo-agent");
        System.err.println("  Transport:  STDIO");
        System.err.println("  Tools:      " + registry.enabledCount());
        System.err.println("========================================");
        for (Tool tool : registry.getEnabled()) {
            String category = (tool instanceof ToolMetadata meta) ? meta.getCategory() : "default";
            System.err.println("  - " + tool.getName() + " [" + category + "]");
        }
        System.err.println("========================================");
        System.err.println("  ANTHROPIC_API_KEY: " +
            (System.getenv("ANTHROPIC_API_KEY") != null ? "set ✓" : "not set (llm_ask will fail)"));
        System.err.println("========================================");
        System.err.println("  Server is running. Waiting for MCP client...");
        System.err.println("  Press Ctrl+C to stop.");
        System.err.println("========================================");

        // 启动 MCP Server，阻塞直到 Ctrl+C
        McpToolServer server = McpToolServer.builder()
            .serverName("demo-agent")
            .serverVersion("0.1.0")
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
    }

    // ==================== 真实 RPC 工具 ====================

    /**
     * 真实 HTTP RPC 工具 — 通过 OkHttp 调用外部服务
     *
     * 这些工具在 MCP Server 内部发起真实的 HTTP 请求，
     * 完整链路：MCP Client → MCP Server → HTTP → 远程服务 → 返回
     */
    public static class HttpRpcTools {

        private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

        private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

        private final ObjectMapper objectMapper = new ObjectMapper();

        @ToolFunction(
            name = "http_fetch",
            description = "Fetch content from a URL via HTTP GET (real network call)",
            category = "rpc",
            tags = {"rpc", "http", "network", "external"},
            readOnly = true,
            idempotent = true
        )
        public String httpFetch(
            @ToolParam(name = "url", description = "The URL to fetch", required = true) String url,
            @ToolParam(name = "max_length", description = "Max response length to return (default 2000)", type = "integer") int maxLength
        ) {
            int limit = maxLength > 0 ? maxLength : 2000;

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "LightweightAI-McpServer/0.1")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";
                if (body.length() > limit) {
                    body = body.substring(0, limit) + "... (truncated)";
                }
                return String.format("HTTP %d\n%s", code, body);
            } catch (IOException e) {
                return "HTTP fetch failed: " + e.getMessage();
            }
        }

        @ToolFunction(
            name = "llm_ask",
            description = "Ask Claude AI a question via Anthropic API (real RPC call to LLM service)",
            category = "rpc",
            tags = {"rpc", "llm", "claude", "ai", "external"}
        )
        public String llmAsk(
            @ToolParam(name = "question", description = "The question to ask", required = true) String question,
            @ToolParam(name = "max_tokens", description = "Max tokens in response (default 256)", type = "integer") int maxTokens
        ) {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                return "Error: ANTHROPIC_API_KEY environment variable not set. "
                    + "Set it to enable real LLM RPC calls.";
            }
            int tokens = maxTokens > 0 ? maxTokens : 256;

            try {
                ObjectNode root = objectMapper.createObjectNode();
                root.put("model", "claude-sonnet-4-20250514");
                root.put("max_tokens", tokens);

                ArrayNode messages = root.putArray("messages");
                ObjectNode msg = messages.addObject();
                msg.put("role", "user");
                msg.put("content", question);

                String jsonBody = objectMapper.writeValueAsString(root);

                Request request = new Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON_TYPE))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        return "LLM API error (HTTP " + response.code() + "): " + body;
                    }
                    // 提取 content[0].text
                    JsonNode json = objectMapper.readTree(body);
                    JsonNode content = json.path("content");
                    if (content.isArray() && !content.isEmpty()) {
                        return content.get(0).path("text").asText();
                    }
                    return body;
                }
            } catch (Exception e) {
                return "LLM RPC call failed: " + e.getMessage();
            }
        }

        @ToolFunction(
            name = "json_api_call",
            description = "Call a JSON REST API endpoint with POST (real HTTP RPC call)",
            category = "rpc",
            tags = {"rpc", "http", "json", "api", "external"}
        )
        public String jsonApiCall(
            @ToolParam(name = "url", description = "API endpoint URL", required = true) String url,
            @ToolParam(name = "body", description = "JSON request body string", required = true) String body,
            @ToolParam(name = "headers", description = "Optional headers as JSON object, e.g. {\"Authorization\":\"Bearer xxx\"}") String headers
        ) {
            try {
                Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "LightweightAI-McpServer/0.1")
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON_TYPE));

                // 解析自定义 headers
                if (headers != null && !headers.isBlank()) {
                    JsonNode headerMap = objectMapper.readTree(headers);
                    headerMap.fields().forEachRemaining(entry ->
                        reqBuilder.header(entry.getKey(), entry.getValue().asText()));
                }

                try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (respBody.length() > 4000) {
                        respBody = respBody.substring(0, 4000) + "... (truncated)";
                    }
                    return String.format("HTTP %d\n%s", response.code(), respBody);
                }
            } catch (Exception e) {
                return "JSON API call failed: " + e.getMessage();
            }
        }
    }

    // ==================== 本地工具（纯本地计算，无网络） ====================

    /**
     * 城市信息查询工具（本地数据）
     */
    public static class CityInfoTools {

        @ToolFunction(
            name = "get_city_info",
            description = "Get population and area information of a city",
            category = "geography",
            tags = {"city", "info"},
            readOnly = true
        )
        public String getCityInfo(
            @ToolParam(name = "city", description = "City name", required = true) String city
        ) {
            return switch (city.toLowerCase()) {
                case "beijing", "北京" -> "Beijing: population 21.5M, area 16,410 km²";
                case "shanghai", "上海" -> "Shanghai: population 24.9M, area 6,341 km²";
                case "tokyo", "东京" -> "Tokyo: population 13.9M, area 2,194 km²";
                case "new york", "纽约" -> "New York: population 8.3M, area 783 km²";
                case "london", "伦敦" -> "London: population 8.9M, area 1,572 km²";
                default -> city + ": data not available";
            };
        }

        @ToolFunction(
            name = "get_weather",
            description = "Get current weather for a city (simulated)",
            category = "weather",
            tags = {"weather", "city"},
            readOnly = true
        )
        public String getWeather(
            @ToolParam(name = "city", description = "City name", required = true) String city
        ) {
            return switch (city.toLowerCase()) {
                case "beijing", "北京" -> city + ": 晴, 25°C, 湿度 45%";
                case "shanghai", "上海" -> city + ": 多云, 28°C, 湿度 72%";
                case "tokyo", "东京" -> city + ": 晴, 22°C, 湿度 55%";
                default -> city + ": 晴, 20°C, 湿度 50%";
            };
        }
    }

    /**
     * 数学计算工具（本地计算）
     */
    public static class MathCalcTools {

        @ToolFunction(
            name = "calculate",
            description = "Evaluate a simple math expression (add, subtract, multiply, divide)",
            category = "math",
            tags = {"math", "calculate"}
        )
        public String calculate(
            @ToolParam(name = "a", description = "First number", required = true) double a,
            @ToolParam(name = "op", description = "Operator: +, -, *, /", required = true) String op,
            @ToolParam(name = "b", description = "Second number", required = true) double b
        ) {
            double result = switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b != 0 ? a / b : Double.NaN;
                default -> throw new IllegalArgumentException("Unknown operator: " + op);
            };
            return String.format("%.2f %s %.2f = %.2f", a, op, b, result);
        }

        @ToolFunction(
            name = "convert_currency",
            description = "Convert amount between currencies (simulated rates)",
            category = "finance",
            tags = {"currency", "conversion"},
            readOnly = true,
            idempotent = true
        )
        public String convertCurrency(
            @ToolParam(name = "amount", description = "Amount to convert", required = true) double amount,
            @ToolParam(name = "from", description = "Source currency code (e.g., USD)", required = true) String from,
            @ToolParam(name = "to", description = "Target currency code (e.g., CNY)", required = true) String to
        ) {
            double rate = getRate(from.toUpperCase(), to.toUpperCase());
            double result = amount * rate;
            return String.format("%.2f %s = %.2f %s (rate: %.4f)", amount, from.toUpperCase(), result, to.toUpperCase(), rate);
        }

        private double getRate(String from, String to) {
            if (from.equals(to)) return 1.0;
            Map<String, Double> toUsd = Map.of(
                "USD", 1.0, "CNY", 0.138, "JPY", 0.0067, "EUR", 1.08, "GBP", 1.27);
            double fromRate = toUsd.getOrDefault(from, 1.0);
            double toRate = toUsd.getOrDefault(to, 1.0);
            return fromRate / toRate;
        }
    }
}
