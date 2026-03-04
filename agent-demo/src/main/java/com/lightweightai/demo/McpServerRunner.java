package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpConfiguration.McpServerConfig;
import com.lightweightai.mcp.McpConfiguration.ServerConfig;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 独立 MCP Server 启动入口 — 全部配置驱动
 *
 * 从 {@code mcp-server.yaml} 统一加载 Server 配置和上游 Server 配置。
 * 上游 Server 支持 SSE（HTTP 远程调用）和 STDIO（本地进程）两种传输。
 *
 * <h2>配置文件 (mcp-server.yaml)</h2>
 * <pre>
 * server:
 *   name: demo-agent
 *   version: 0.1.0
 *
 * upstream:
 *   servers:
 *     nlp-service:
 *       transport: sse
 *       url: "http://nlp-host:8080/sse"
 *     local-tools:
 *       transport: stdio
 *       command: java
 *       args: ["-cp", "...", "com.example.ToolServer"]
 * </pre>
 *
 * <h2>架构</h2>
 * <pre>
 * 外部 Client ──MCP──→ McpServerRunner ──SSE/HTTP──→ 远程 MCP Server
 *                      (mcp-server.yaml)  ──STDIO───→ 本地 MCP Server
 *                      ├── 本地工具
 *                      ├── SPI 工具
 *                      └── 上游工具（代理）
 * </pre>
 *
 * <h2>启动方式</h2>
 * <pre>
 * # 默认加载 classpath 下的 mcp-server.yaml
 * java -cp &lt;classpath&gt; com.lightweightai.demo.McpServerRunner
 *
 * # 指定配置文件
 * java -Dmcp.server.config=/path/to/config.yaml -cp &lt;classpath&gt; ...
 * </pre>
 */
public class McpServerRunner {

    private static final String DEFAULT_CONFIG = "mcp-server.yaml";
    private static final List<McpToolClient> upstreamClients = new ArrayList<>();

    public static void main(String[] args) {
        // 1. 加载配置
        Config config = loadConfig();

        // 2. 注册本地工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTools());
        registry.registerObject(new MathCalcTools());
        registry.scanAndRegister();

        // 3. 连接上游 MCP Server（从配置加载）
        if (config.upstream != null) {
            connectUpstreamServers(registry, config.upstream);
        }

        // 4. 打印工具清单
        System.err.println("========================================");
        System.err.println("  MCP Server: " + config.server.getName());
        System.err.println("  Version:    " + config.server.getVersion());
        System.err.println("  Transport:  STDIO");
        System.err.println("  Upstream:   " + upstreamClients.size() + " server(s)");
        System.err.println("  Tools:      " + registry.enabledCount());
        System.err.println("========================================");
        for (Tool tool : registry.getEnabled()) {
            String source;
            if (tool instanceof ToolMetadata meta) {
                String cat = meta.getCategory();
                source = cat.startsWith("mcp:") ? cat + " (upstream)" : cat;
            } else {
                source = "default";
            }
            System.err.println("  - " + tool.getName() + " [" + source + "]");
        }
        System.err.println("========================================");

        // 5. Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (McpToolClient client : upstreamClients) {
                try {
                    client.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }));

        // 6. 启动 MCP Server
        McpToolServer server = McpToolServer.builder()
            .serverName(config.server.getName())
            .serverVersion(config.server.getVersion())
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
    }

    // ==================== 配置加载 ====================

    /**
     * 统一配置结构：server + upstream
     */
    static class Config {
        McpServerConfig server = new McpServerConfig();
        McpConfiguration upstream;
    }

    /**
     * 从 YAML 加载统一配置
     *
     * 配置结构：
     * <pre>
     * server:
     *   name: demo-agent
     *   version: 0.1.0
     * upstream:
     *   servers:
     *     nlp-service:
     *       transport: sse
     *       url: http://host:8080/sse
     * </pre>
     */
    private static Config loadConfig() {
        Config config = new Config();
        String configPath = System.getProperty("mcp.server.config");

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            InputStream is;

            if (configPath != null && !configPath.isBlank()) {
                is = new java.io.FileInputStream(configPath);
                System.err.println("  Config: " + configPath);
            } else {
                is = McpServerRunner.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
                if (is == null) {
                    System.err.println("  Config: (default, no " + DEFAULT_CONFIG + " found)");
                    return config;
                }
                System.err.println("  Config: classpath:" + DEFAULT_CONFIG);
            }

            JsonNode root = yamlMapper.readTree(is);
            is.close();

            // server 配置
            JsonNode serverNode = root.get("server");
            if (serverNode != null) {
                config.server = yamlMapper.treeToValue(serverNode, McpServerConfig.class);
            }

            // upstream 配置（复用 McpConfiguration 结构）
            JsonNode upstreamNode = root.get("upstream");
            if (upstreamNode != null) {
                config.upstream = yamlMapper.treeToValue(upstreamNode, McpConfiguration.class);
            }

            return config;
        } catch (Exception e) {
            System.err.println("  Config load failed: " + e.getMessage());
            return config;
        }
    }

    // ==================== 上游 MCP Server 连接 ====================

    private static void connectUpstreamServers(ToolRegistry registry, McpConfiguration upstream) {
        Map<String, ServerConfig> enabled = upstream.getEnabledServers();
        if (enabled.isEmpty()) {
            System.err.println("  No enabled upstream servers");
            return;
        }

        for (Map.Entry<String, ServerConfig> entry : enabled.entrySet()) {
            String name = entry.getKey();
            ServerConfig serverConfig = entry.getValue();
            connectOneUpstream(registry, name, serverConfig);
        }
    }

    private static void connectOneUpstream(ToolRegistry registry, String name, ServerConfig serverConfig) {
        String transportType = serverConfig.getTransport();
        System.err.println("  Connecting upstream: " + name + " [" + transportType + "]");

        try {
            McpClientTransport transport = createTransport(name, serverConfig);

            McpToolClient client = McpToolClient.builder()
                .serverName(name)
                .transport(transport)
                .requestTimeout(Duration.ofSeconds(serverConfig.getTimeoutSeconds()))
                .build();

            client.initialize();
            int count = client.registerTools(registry);
            upstreamClients.add(client);

            System.err.println("  ✓ " + name + " → " + count + " tools [" + transportType + "]");
        } catch (Exception e) {
            System.err.println("  ✗ " + name + ": " + e.getMessage());
        }
    }

    /**
     * 根据配置创建传输层
     *
     * SSE：HttpClientSseClientTransport（旧版 SSE 协议）
     * Streamable HTTP：HttpClientStreamableHttpTransport（新版 HTTP 协议，推荐）
     * STDIO：StdioClientTransport（本地子进程，开发/测试用）
     */
    private static McpClientTransport createTransport(String name, ServerConfig config) {
        String transport = config.getTransport();

        // Streamable HTTP — 新版 MCP 传输协议（2025-03-26+）
        if ("streamable_http".equalsIgnoreCase(transport)
                || "streamable-http".equalsIgnoreCase(transport)
                || "http".equalsIgnoreCase(transport)) {
            if (config.getUrl() == null || config.getUrl().isBlank()) {
                throw new IllegalStateException("Upstream '" + name + "': Streamable HTTP requires 'url'");
            }
            return HttpClientStreamableHttpTransport.builder(config.getUrl()).build();
        }

        // SSE — 旧版传输协议
        if ("sse".equalsIgnoreCase(transport)) {
            if (config.getUrl() == null || config.getUrl().isBlank()) {
                throw new IllegalStateException("Upstream '" + name + "': SSE requires 'url'");
            }
            return HttpClientSseClientTransport.builder(config.getUrl())
                .sseEndpoint("/sse")
                .build();
        }

        // STDIO — 启动本地子进程
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalStateException("Upstream '" + name + "': STDIO requires 'command'");
        }

        List<String> resolvedArgs = resolveArgs(config.getArgs());
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(config.getCommand());
        if (!resolvedArgs.isEmpty()) {
            paramsBuilder.args(resolvedArgs.toArray(new String[0]));
        }
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            paramsBuilder.env(config.getEnv());
        }
        return new StdioClientTransport(paramsBuilder.build(),
            new JacksonMcpJsonMapper(new ObjectMapper()));
    }

    private static List<String> resolveArgs(List<String> args) {
        if (args == null) return List.of();
        return args.stream()
            .map(McpServerRunner::resolvePlaceholders)
            .toList();
    }

    private static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) return value;
        value = value.replace("${CLASSPATH}", System.getProperty("java.class.path", ""));
        // ${XXX} → System.getProperty or System.getenv
        while (value.contains("${")) {
            int start = value.indexOf("${");
            int end = value.indexOf("}", start);
            if (end < 0) break;
            String key = value.substring(start + 2, end);
            String resolved = System.getProperty(key);
            if (resolved == null) resolved = System.getenv(key);
            if (resolved == null) resolved = "";
            value = value.substring(0, start) + resolved + value.substring(end + 1);
        }
        return value;
    }

    // ==================== 本地工具 ====================

    public static class CityInfoTools {

        @ToolFunction(name = "get_city_info",
            description = "Get population and area information of a city",
            category = "geography", tags = {"city", "info"}, readOnly = true)
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

        @ToolFunction(name = "get_weather",
            description = "Get current weather for a city (simulated)",
            category = "weather", tags = {"weather", "city"}, readOnly = true)
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

    public static class MathCalcTools {

        @ToolFunction(name = "calculate",
            description = "Evaluate a simple math expression (add, subtract, multiply, divide)",
            category = "math", tags = {"math", "calculate"})
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

        @ToolFunction(name = "convert_currency",
            description = "Convert amount between currencies (simulated rates)",
            category = "finance", tags = {"currency", "conversion"},
            readOnly = true, idempotent = true)
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
