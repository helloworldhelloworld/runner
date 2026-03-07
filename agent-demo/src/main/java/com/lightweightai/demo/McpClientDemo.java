package com.lightweightai.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpHeaderProvider;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import java.util.Map;
import java.util.Set;

/**
 * MCP Client Demo — 验证 McpServerRunner 的端到端链路
 *
 * <h2>架构</h2>
 * <pre>
 * McpClientDemo ──STDIO──→ McpServerRunner ──MCP SDK──→ 外部已有 MCP Server
 *                          (mcp-server.yaml 配置)
 *
 * McpServerRunner 根据 mcp-server.yaml 中的 upstream 配置，
 * 通过官方 MCP SDK 连接外部已有的 MCP Server（SSE 或 STDIO）。
 * McpClientDemo 不需要关心上游传输方式，只负责连接 McpServerRunner 并验证工具。
 * </pre>
 *
 * <h2>运行方式</h2>
 * <pre>
 * # 1. 确保 mcp-server.yaml 中的上游 MCP Server 配置正确
 * #    - SSE: 外部 MCP Server 已在运行，url 配置正确
 * #    - STDIO: command/args 配置正确
 *
 * # 2. 运行 Demo
 * mvn -pl agent-demo exec:java -Dexec.mainClass=com.lightweightai.demo.McpClientDemo
 *
 * # 3. 可选：指定自定义配置文件
 * mvn -pl agent-demo exec:java \
 *     -Dexec.mainClass=com.lightweightai.demo.McpClientDemo \
 *     -Dmcp.server.config=/path/to/custom-config.yaml
 * </pre>
 *
 * <h2>配置示例 (mcp-server.yaml)</h2>
 * <pre>
 * server:
 *   name: demo-agent
 *   version: 0.1.0
 * upstream:
 *   servers:
 *     nlp-service:
 *       transport: sse
 *       url: "http://remote-host:8081/sse"
 *       enabled: true
 * </pre>
 */
public class McpClientDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  MCP Client Demo — 验证 McpServerRunner     ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        demoHeaderConfiguration();

        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // 构建 McpServerRunner 启动参数
        // McpServerRunner 自动读取 mcp-server.yaml（或 -Dmcp.server.config 指定的配置）
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

        System.out.println("[1] 启动 McpServerRunner...");
        System.out.println("    McpServerRunner 根据配置连接上游 MCP Server（SSE/STDIO）\n");

        // 使用 ToolClient 统一管理：addMcpServer 会自动 initialize + discover + register
        StdioClientTransport transport = new StdioClientTransport(paramsBuilder.build(),
            new JacksonMcpJsonMapper(new ObjectMapper()));

        ToolClient client = ToolClient.create()
            .addMcpServer("demo-agent", transport)
            .build();

        try {
            System.out.println("[2] MCP 握手成功\n");

            // 通过 ToolClient 获取统一的 ToolExecutor
            ToolExecutor executor = client.getToolExecutor();

            // 发现的工具
            System.out.println("[3] 发现 " + client.getToolRegistry().enabledCount() + " 个工具：");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                System.out.println("    - " + tool.getName());
            }

            // 调用工具
            System.out.println("\n[4] 调用工具：\n");

            // 本地工具（McpServerRunner 自带）
            callAndPrint(executor, "get_city_info", Map.of("city", "beijing"));
            callAndPrint(executor, "get_weather", Map.of("city", "tokyo"));
            callAndPrint(executor, "calculate", Map.of("a", 12.5, "op", "+", "b", 7.3));

            // 上游代理工具 — 带参数的示例调用
            callAndPrint(executor, "goldPrice/search",
                Map.of("query", "黄金价格", "extraInfo", Map.of()));

            // 上游代理工具（动态发现，来自配置的外部 MCP Server）
            Set<String> localTools = Set.of("get_city_info", "get_weather", "calculate",
                "goldPrice/search");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                if (tool instanceof McpToolWrapper w && !localTools.contains(tool.getName())) {
                    System.out.println("  [upstream:" + w.getServerName() + "] " + tool.getName());
                    System.out.println("    schema: " + tool.getSchema().toMap());
                    callAndPrint(executor, tool.getName(), Map.of());
                }
            }

            // 工具来源
            System.out.println("[5] 工具来源：");
            for (Tool tool : client.getToolRegistry().getEnabled()) {
                String source = (tool instanceof McpToolWrapper w)
                    ? "upstream:" + w.getServerName() : "local";
                System.out.println("    - " + tool.getName() + " → " + source);
            }

            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║  验证通过！                                  ║");
            System.out.println("╚══════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    // ================================================================
    //  Header 配置 — 静态 + 动态 Header 合并
    //
    //  MCP 通过 SSE / Streamable HTTP 连接远程 Server 时，
    //  常需要附加认证、链路追踪等 HTTP Header。
    //
    //  框架提供两种方式，可单独使用也可组合：
    //    1. 静态 Header   — YAML 或 ServerConfig.withHeader() 配置
    //    2. 动态 Header   — McpHeaderProvider 函数式接口，每次请求调用
    //
    //  合并规则：动态 Header 覆盖同名静态 Header。
    // ================================================================

    static void demoHeaderConfiguration() {
        System.out.println("--- [F] Header 配置：静态 + 动态合并 ---\n");

        // ---- 1. 静态 Header（ServerConfig / YAML） ----
        System.out.println("[1] 静态 Header（ServerConfig.withHeader / YAML headers:）\n");

        McpConfiguration.ServerConfig sseConfig = McpConfiguration.ServerConfig.sse("http://api.example.com/sse")
            .withHeader("X-API-Key", "sk-static-key-12345")
            .withHeader("X-Client-Version", "1.0.0");

        System.out.println("  ServerConfig.sse(\"http://api.example.com/sse\")");
        System.out.println("      .withHeader(\"X-API-Key\", \"sk-static-key-12345\")");
        System.out.println("      .withHeader(\"X-Client-Version\", \"1.0.0\")");
        System.out.println("  静态 headers: " + sseConfig.getHeaders());

        // ---- 2. 动态 Header（McpHeaderProvider） ----
        System.out.println("\n[2] 动态 Header（McpHeaderProvider）\n");

        // 模拟：每次调用返回新的 token 和 trace ID
        McpHeaderProvider dynamicProvider = () -> Map.of(
            "Authorization", "Bearer " + "tok-" + System.currentTimeMillis(),
            "X-Trace-Id", "trace-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "X-API-Key", "sk-dynamic-override"  // 与静态同名，动态优先
        );

        System.out.println("  McpHeaderProvider provider = () -> Map.of(");
        System.out.println("      \"Authorization\", \"Bearer \" + tokenService.getToken(),");
        System.out.println("      \"X-Trace-Id\",    \"trace-\" + TraceContext.traceId(),");
        System.out.println("      \"X-API-Key\",     \"sk-dynamic-override\"  // 覆盖静态值");
        System.out.println("  );");
        System.out.println("  动态 headers: " + dynamicProvider.getHeaders());

        // ---- 3. 合并结果 ----
        System.out.println("\n[3] 合并结果（resolveHeaders）\n");

        Map<String, String> merged = ToolClient.Builder.resolveHeaders(sseConfig, dynamicProvider);

        System.out.println("  resolveHeaders(staticConfig, dynamicProvider):");
        for (Map.Entry<String, String> h : merged.entrySet()) {
            String source;
            if ("X-Client-Version".equals(h.getKey())) {
                source = "← 仅静态";
            } else if ("Authorization".equals(h.getKey()) || "X-Trace-Id".equals(h.getKey())) {
                source = "← 仅动态";
            } else {
                source = "← 动态覆盖静态";
            }
            System.out.println("    " + h.getKey() + ": " + h.getValue() + "  " + source);
        }

        // ---- 4. ToolClient 集成用法 ----
        System.out.println("\n[4] ToolClient 集成用法\n");

        System.out.println("  ToolClient client = ToolClient.create()");
        System.out.println("      .headerProvider(() -> Map.of(                // 全局动态 Header");
        System.out.println("          \"Authorization\", \"Bearer \" + getToken()))");
        System.out.println("      .fromConfig(config)                          // YAML 中可含静态 headers");
        System.out.println("      .build();");
        System.out.println();
        System.out.println("  // YAML 配置示例：");
        System.out.println("  // mcp:");
        System.out.println("  //   servers:");
        System.out.println("  //     remote-api:");
        System.out.println("  //       transport: sse");
        System.out.println("  //       url: \"http://api.example.com/sse\"");
        System.out.println("  //       headers:");
        System.out.println("  //         X-API-Key: \"sk-static-key\"");
        System.out.println("  //         X-Client-Version: \"1.0.0\"");

        System.out.println();
        System.out.println("  ✓ 静态 Header：YAML headers 或 withHeader()，适合固定配置");
        System.out.println("  ✓ 动态 Header：McpHeaderProvider，适合 Token 刷新、链路追踪");
        System.out.println("  ✓ 合并规则：动态覆盖同名静态 Header");
        System.out.println("  ✓ 仅 SSE / Streamable HTTP 生效，STDIO 忽略");
        System.out.println();
    }

    private static void callAndPrint(ToolExecutor executor, String toolName,
                                     Map<String, Object> args) {
        try {
            ToolResult result = executor.executeToolCall(
                new ToolCall("debug-" + toolName, toolName, args));
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → " + result.getContent());
            if (result.hasStructuredContent()) {
                System.out.println("    → structuredContent: " + result.getStructuredContent());
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("  " + toolName + "(" + args + ")");
            System.out.println("    → ERROR: " + e.getMessage());
            System.out.println();
        }
    }
}
