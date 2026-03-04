package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 独立 MCP Server 启动入口 — 支持上游 MCP Server 代理
 *
 * 既是 MCP Server（暴露工具给外部 Client），也是 MCP Client（连接上游 MCP Server）。
 * 上游 MCP Server 的工具通过 MCP SDK 自动发现、注册，和本地工具一起暴露。
 *
 * <h2>架构</h2>
 * <pre>
 * 外部 Client ──MCP──→ McpServerRunner ──MCP──→ 上游 MCP Server
 *                      (Server + Client)         (如 nlp-service, filesystem 等)
 *                      ├── 本地工具
 *                      ├── SPI 工具
 *                      └── 上游 MCP 工具（代理）
 * </pre>
 *
 * <h2>启动方式</h2>
 * <pre>
 * # 不带上游 Server（仅本地工具）
 * java -cp &lt;classpath&gt; com.lightweightai.demo.McpServerRunner
 *
 * # 带上游 MCP Server（代理模式）
 * java -cp &lt;classpath&gt; com.lightweightai.demo.McpServerRunner \
 *     --upstream nlp-service:java:-cp:&lt;classpath&gt;:com.lightweightai.demo.UpstreamExampleServer
 *
 * # Maven 运行
 * mvn -pl agent-demo exec:java \
 *     -Dexec.mainClass=com.lightweightai.demo.McpServerRunner \
 *     -Dexec.args="--upstream nlp-service:java:-cp:CLASSPATH:com.lightweightai.demo.UpstreamExampleServer"
 * </pre>
 *
 * <h2>--upstream 参数格式</h2>
 * <pre>
 * --upstream name:command:arg1:arg2:...
 * </pre>
 * 可以指定多个 --upstream 参数连接多个上游 Server。
 *
 * <h2>Claude Desktop / Cursor 配置</h2>
 * <pre>
 * {
 *   "mcpServers": {
 *     "demo-agent": {
 *       "command": "java",
 *       "args": ["-cp", "classpath", "com.lightweightai.demo.McpServerRunner",
 *                "--upstream", "nlp:java:-cp:classpath:com.lightweightai.demo.UpstreamExampleServer"]
 *     }
 *   }
 * }
 * </pre>
 */
public class McpServerRunner {

    private static final List<McpToolClient> upstreamClients = new ArrayList<>();

    public static void main(String[] args) {
        // 1. 注册本地工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTools());
        registry.registerObject(new MathCalcTools());
        registry.scanAndRegister();  // SPI 扫描

        // 2. 连接上游 MCP Server，发现并注册其工具（代理模式）
        connectUpstreamServers(registry, args);

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

    // ==================== 上游 MCP Server 连接 ====================

    /**
     * 解析 --upstream 参数，连接上游 MCP Server
     *
     * 格式：--upstream name:command:arg1:arg2:...
     * 例如：--upstream nlp-service:java:-cp:classpath:com.lightweightai.demo.UpstreamExampleServer
     *
     * 流程：
     * 1. 创建 StdioClientTransport（启动上游进程）
     * 2. McpToolClient 握手
     * 3. 发现上游工具
     * 4. 注册到本地 ToolRegistry（McpToolWrapper 自动代理）
     */
    private static void connectUpstreamServers(ToolRegistry registry, String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--upstream".equals(args[i]) && i + 1 < args.length) {
                String spec = args[++i];
                connectOneUpstream(registry, spec);
            }
        }
    }

    private static void connectOneUpstream(ToolRegistry registry, String spec) {
        String[] parts = spec.split(":", 2);
        if (parts.length < 2) {
            System.err.println("  ✗ Invalid upstream spec: " + spec + " (expected name:command:args...)");
            return;
        }

        String name = parts[0];
        String[] cmdParts = parts[1].split(":");
        String command = cmdParts[0];
        String[] cmdArgs = new String[cmdParts.length - 1];
        System.arraycopy(cmdParts, 1, cmdArgs, 0, cmdArgs.length);

        System.err.println("  Connecting upstream: " + name + " (" + command + " " + String.join(" ", cmdArgs) + ")");

        try {
            ServerParameters serverParams = ServerParameters.builder(command)
                .args(cmdArgs)
                .build();

            McpToolClient client = McpToolClient.builder()
                .serverName(name)
                .transport(new StdioClientTransport(serverParams,
                    new JacksonMcpJsonMapper(new ObjectMapper())))
                .build();

            client.initialize();
            int count = client.registerTools(registry);
            upstreamClients.add(client);

            System.err.println("  ✓ Connected upstream: " + name + " (" + count + " tools discovered)");
        } catch (Exception e) {
            System.err.println("  ✗ Failed upstream: " + name + ": " + e.getMessage());
        }
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
