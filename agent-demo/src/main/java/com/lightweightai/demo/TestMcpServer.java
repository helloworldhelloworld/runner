package com.lightweightai.demo;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.mcp.McpToolServer;

/**
 * 最简独立 MCP Server — 用于验证上游连接
 *
 * 提供 echo 和 random_number 两个工具，通过 STDIO 传输。
 *
 * <pre>
 * # 在 mcp-server.yaml 中作为上游配置：
 * test-server:
 *   transport: stdio
 *   command: java
 *   args: ["-cp", "${CLASSPATH}", "com.lightweightai.demo.TestMcpServer"]
 * </pre>
 */
public class TestMcpServer {

    public static void main(String[] args) {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new TestTools());

        McpToolServer server = McpToolServer.builder()
            .serverName("test-mcp-server")
            .serverVersion("0.1.0")
            .toolRegistry(registry)
            .build();
        server.startAndBlock();
    }

    public static class TestTools {

        @ToolFunction(name = "echo",
            description = "Echo back the input text",
            category = "test", readOnly = true)
        public String echo(
            @ToolParam(name = "text", description = "Text to echo", required = true) String text
        ) {
            return text;
        }

        @ToolFunction(name = "random_number",
            description = "Generate a random number between min and max",
            category = "test", readOnly = true)
        public String randomNumber(
            @ToolParam(name = "min", description = "Minimum value", required = false) Integer min,
            @ToolParam(name = "max", description = "Maximum value", required = false) Integer max
        ) {
            int lo = (min != null) ? min : 1;
            int hi = (max != null) ? max : 100;
            int num = lo + (int) (Math.random() * (hi - lo + 1));
            return String.valueOf(num);
        }
    }
}
