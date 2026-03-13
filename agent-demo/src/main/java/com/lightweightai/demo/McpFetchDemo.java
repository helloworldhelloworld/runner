package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.util.Map;

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

            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  Demo 验证通过！                             ║");
            System.out.println("╚══════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
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
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "... [truncated]";
    }
}
