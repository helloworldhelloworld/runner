package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolServer;

import java.util.List;
import java.util.Map;

/**
 * 独立 MCP Server 启动入口
 *
 * 将本地工具暴露为 MCP 服务端，进程保持运行，等待外部 MCP 客户端连接。
 * 外部客户端（Claude Desktop、Cursor、其他 Agent）可以通过 MCP 协议发现并调用工具。
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
 * <h2>Claude Desktop 配置</h2>
 * 在 claude_desktop_config.json 中添加：
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
 *
 * <h2>Cursor 配置</h2>
 * 在 .cursor/mcp.json 中添加：
 * <pre>
 * {
 *   "mcpServers": {
 *     "demo-agent": {
 *       "command": "mvn",
 *       "args": ["-pl", "agent-demo", "exec:java",
 *                "-Dexec.mainClass=com.lightweightai.demo.McpServerRunner"]
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>框架内连接此 Server</h2>
 * <pre>
 * // mcp-config.yaml
 * mcp:
 *   servers:
 *     demo-agent:
 *       command: java
 *       args: ["-cp", "classpath", "com.lightweightai.demo.McpServerRunner"]
 *
 * // 代码
 * McpToolManager manager = McpToolManager.create()
 *     .fromConfig(config)
 *     .build();
 * </pre>
 */
public class McpServerRunner {

    public static void main(String[] args) {
        // 注册所有要暴露的工具
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTools());
        registry.registerObject(new MathCalcTools());
        registry.scanAndRegister();  // SPI 扫描 (MathTools, TimeTools, WebTools 等)

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

    // ==================== 暴露的工具 ====================

    /**
     * 城市信息查询工具
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
     * 数学计算工具
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
            // 简化汇率表
            Map<String, Double> toUsd = Map.of(
                "USD", 1.0, "CNY", 0.138, "JPY", 0.0067, "EUR", 1.08, "GBP", 1.27);
            double fromRate = toUsd.getOrDefault(from, 1.0);
            double toRate = toUsd.getOrDefault(to, 1.0);
            return fromRate / toRate;
        }
    }
}
