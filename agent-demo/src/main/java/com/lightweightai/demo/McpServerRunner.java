package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpConfiguration.ServerConfig;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 独立 MCP Server 启动入口 — 配置驱动的上游 MCP Server 代理
 *
 * 既是 MCP Server（暴露工具给外部 Client），也是 MCP Client（连接上游 MCP Server）。
 * 上游 Server 在 {@code mcp-upstream.yaml} 中配置，启动时自动连接、发现、代理。
 *
 * <h2>架构</h2>
 * <pre>
 * 外部 Client ──MCP──→ McpServerRunner ──MCP──→ 上游 MCP Server
 *                      (Server + Client)         (mcp-upstream.yaml 配置)
 *                      ├── 本地工具
 *                      ├── SPI 工具
 *                      └── 上游 MCP 工具（代理）
 * </pre>
 *
 * <h2>上游配置 (mcp-upstream.yaml)</h2>
 * <pre>
 * mcp:
 *   servers:
 *     nlp-service:
 *       transport: stdio
 *       command: java
 *       args: ["-cp", "${CLASSPATH}", "com.lightweightai.demo.UpstreamExampleServer"]
 *       timeoutSeconds: 30
 *
 *     filesystem:
 *       transport: stdio
 *       command: npx
 *       args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *       enabled: false
 * </pre>
 *
 * <h2>启动方式</h2>
 * <pre>
 * # 默认加载 classpath 下的 mcp-upstream.yaml
 * java -cp &lt;classpath&gt; com.lightweightai.demo.McpServerRunner
 *
 * # 指定自定义配置文件
 * java -Dmcp.upstream.config=/path/to/upstream.yaml -cp &lt;classpath&gt; com.lightweightai.demo.McpServerRunner
 *
 * # Maven 运行
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpServerRunner
 * </pre>
 *
 * <h2>Claude Desktop / Cursor 配置</h2>
 * <pre>
 * {
 *   "mcpServers": {
 *     "demo-agent": {
 *       "command": "java",
 *       "args": ["-cp", "classpath", "com.lightweightai.demo.McpServerRunner"]
 *     }
 *   }
 * }
 * </pre>
 */
public class McpServerRunner {

    private static final String DEFAULT_CONFIG = "mcp-upstream.yaml";
    private static final List<McpToolClient> upstreamClients = new ArrayList<>();

    public static void main(String[] args) {
        // 1. 注册本地工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTools());
        registry.registerObject(new MathCalcTools());
        registry.scanAndRegister();  // SPI 扫描

        // 2. 从配置文件加载上游 MCP Server 并连接
        connectUpstreamFromConfig(registry);

        // 3. 打印工具清单
        System.err.println("========================================");
        System.err.println("  MCP Server: demo-agent");
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
        System.err.println("  Server is running. Waiting for MCP client...");
        System.err.println("  Press Ctrl+C to stop.");
        System.err.println("========================================");

        // 4. 注册 shutdown hook 清理上游连接
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (McpToolClient client : upstreamClients) {
                try {
                    client.close();
                    System.err.println("  Closed upstream: " + client.getServerName());
                } catch (Exception e) {
                    System.err.println("  Failed to close upstream " + client.getServerName() + ": " + e.getMessage());
                }
            }
        }));

        // 5. 启动 MCP Server，阻塞直到 Ctrl+C
        McpToolServer server = McpToolServer.builder()
            .serverName("demo-agent")
            .serverVersion("0.1.0")
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
    }

    // ==================== 上游 MCP Server 配置加载 ====================

    /**
     * 从配置文件加载上游 MCP Server 并连接
     *
     * 配置文件查找顺序：
     * 1. 系统属性 mcp.upstream.config 指定的路径
     * 2. classpath 下的 mcp-upstream.yaml
     *
     * 对每个 enabled 的 Server 执行：
     *   创建 Transport → McpToolClient → initialize → discoverTools → registerTools
     */
    private static void connectUpstreamFromConfig(ToolRegistry registry) {
        McpConfiguration config = loadUpstreamConfig();
        if (config == null || config.getServers() == null || config.getServers().isEmpty()) {
            System.err.println("  No upstream MCP servers configured");
            return;
        }

        Map<String, ServerConfig> enabledServers = config.getEnabledServers();
        if (enabledServers.isEmpty()) {
            System.err.println("  No enabled upstream MCP servers");
            return;
        }

        for (Map.Entry<String, ServerConfig> entry : enabledServers.entrySet()) {
            String name = entry.getKey();
            ServerConfig serverConfig = entry.getValue();
            connectOneUpstream(registry, name, serverConfig);
        }
    }

    /**
     * 加载上游配置文件
     */
    private static McpConfiguration loadUpstreamConfig() {
        // 优先使用系统属性指定的配置文件
        String configPath = System.getProperty("mcp.upstream.config");

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            InputStream is;

            if (configPath != null && !configPath.isBlank()) {
                is = new java.io.FileInputStream(configPath);
                System.err.println("  Loading upstream config: " + configPath);
            } else {
                is = McpServerRunner.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
                if (is == null) {
                    System.err.println("  No " + DEFAULT_CONFIG + " found on classpath");
                    return null;
                }
                System.err.println("  Loading upstream config: classpath:" + DEFAULT_CONFIG);
            }

            // 读取顶层 { mcp: { servers: ... } } 结构
            var root = yamlMapper.readTree(is);
            is.close();

            var mcpNode = root.get("mcp");
            if (mcpNode == null) {
                return null;
            }
            return yamlMapper.treeToValue(mcpNode, McpConfiguration.class);
        } catch (Exception e) {
            System.err.println("  Failed to load upstream config: " + e.getMessage());
            return null;
        }
    }

    /**
     * 连接单个上游 MCP Server
     *
     * 支持 ${CLASSPATH} 占位符，运行时替换为当前进程的 java.class.path
     */
    private static void connectOneUpstream(ToolRegistry registry, String name, ServerConfig serverConfig) {
        System.err.println("  Connecting upstream: " + name + " (" + serverConfig + ")");

        try {
            // 解析 args 中的 ${CLASSPATH} 占位符
            List<String> resolvedArgs = resolveArgs(serverConfig.getArgs());

            ServerParameters.Builder paramsBuilder = ServerParameters.builder(serverConfig.getCommand());
            if (!resolvedArgs.isEmpty()) {
                paramsBuilder.args(resolvedArgs.toArray(new String[0]));
            }
            if (serverConfig.getEnv() != null && !serverConfig.getEnv().isEmpty()) {
                paramsBuilder.env(serverConfig.getEnv());
            }

            McpToolClient client = McpToolClient.builder()
                .serverName(name)
                .transport(new StdioClientTransport(paramsBuilder.build(),
                    new JacksonMcpJsonMapper(new ObjectMapper())))
                .requestTimeout(Duration.ofSeconds(serverConfig.getTimeoutSeconds()))
                .build();

            client.initialize();
            int count = client.registerTools(registry);
            upstreamClients.add(client);

            System.err.println("  ✓ Connected upstream: " + name + " (" + count + " tools)");
        } catch (Exception e) {
            System.err.println("  ✗ Failed upstream: " + name + ": " + e.getMessage());
        }
    }

    /**
     * 解析 args 中的占位符
     *
     * 支持：
     *   ${CLASSPATH} → System.getProperty("java.class.path")
     *   ${env.XXX}   → System.getenv("XXX")
     *   ${XXX}       → System.getProperty("XXX") 或 System.getenv("XXX")
     */
    private static List<String> resolveArgs(List<String> args) {
        if (args == null) return List.of();
        return args.stream()
            .map(McpServerRunner::resolvePlaceholders)
            .toList();
    }

    private static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) return value;

        // ${CLASSPATH} → java.class.path
        value = value.replace("${CLASSPATH}", System.getProperty("java.class.path", ""));

        // ${env.XXX} → System.getenv("XXX")
        while (value.contains("${env.")) {
            int start = value.indexOf("${env.");
            int end = value.indexOf("}", start);
            if (end < 0) break;
            String envKey = value.substring(start + 6, end);
            String envVal = System.getenv(envKey);
            value = value.substring(0, start) + (envVal != null ? envVal : "") + value.substring(end + 1);
        }

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
