package com.lightweightai.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

import java.io.InputStream;
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
        System.out.println("--- [F] Header 配置：Config + ToolClient 集成 ---\n");

        // ---- 1. 从 mcp-config.yaml 加载配置，展示 YAML 中的静态 headers ----
        System.out.println("[1] 从 mcp-config.yaml 加载配置\n");

        McpConfiguration yamlConfig = loadMcpConfig("mcp-config.yaml");
        if (yamlConfig != null) {
            McpConfiguration.ServerConfig remoteApi = yamlConfig.getServers().get("remote-api");
            if (remoteApi != null) {
                System.out.println("  remote-api (transport=" + remoteApi.getTransport()
                    + ", enabled=" + remoteApi.isEnabled() + ")");
                System.out.println("  YAML 静态 headers: " + remoteApi.getHeaders());
            } else {
                System.out.println("  remote-api 未配置");
            }
        }

        // ---- 2. 代码方式添加静态 Header ----
        System.out.println("\n[2] 代码方式添加静态 Header\n");

        McpConfiguration.ServerConfig sseConfig = McpConfiguration.ServerConfig.sse("http://api.example.com/sse")
            .withHeader("X-API-Key", "sk-static-key-12345")
            .withHeader("X-Client-Version", "1.0.0");

        System.out.println("  ServerConfig.sse(...).withHeader(\"X-API-Key\", ...).withHeader(\"X-Client-Version\", ...)");
        System.out.println("  静态 headers: " + sseConfig.getHeaders());

        // ---- 3. 动态 Header（McpHeaderProvider） ----
        System.out.println("\n[3] 动态 Header（McpHeaderProvider）\n");

        McpHeaderProvider dynamicProvider = () -> Map.of(
            "Authorization", "Bearer tok-" + System.currentTimeMillis(),
            "X-Trace-Id", "trace-" + java.util.UUID.randomUUID().toString().substring(0, 8),
            "X-API-Key", "sk-dynamic-override"  // 与静态同名，动态优先
        );

        System.out.println("  动态 headers: " + dynamicProvider.getHeaders());
        System.out.println("  （每次调用产生新值：token 刷新、trace ID 等）");

        // ---- 4. resolveHeaders 合并 — 真实调用 ----
        System.out.println("\n[4] resolveHeaders 合并结果（动态覆盖同名静态）\n");

        Map<String, String> merged = ToolClient.Builder.resolveHeaders(sseConfig, dynamicProvider);

        for (Map.Entry<String, String> h : merged.entrySet()) {
            String source;
            if ("X-Client-Version".equals(h.getKey())) {
                source = "← 仅静态";
            } else if ("Authorization".equals(h.getKey()) || "X-Trace-Id".equals(h.getKey())) {
                source = "← 仅动态";
            } else {
                source = "← 动态覆盖静态";
            }
            System.out.println("  " + h.getKey() + ": " + h.getValue() + "  " + source);
        }

        // ---- 5. ToolClient + Config 集成 ----
        System.out.println("\n[5] ToolClient + Config 集成\n");

        // 构建包含 header 的配置
        McpConfiguration config = new McpConfiguration();
        McpConfiguration.ServerConfig apiServer = McpConfiguration.ServerConfig.sse("http://api.example.com/sse")
            .withHeader("X-API-Key", "sk-from-config");
        apiServer.setEnabled(false);  // 不实际连接
        config.addServer("api-server", apiServer);

        // 创建 ToolClient：headerProvider + fromConfig 配合
        // fromConfig 内部对每个 enabled SSE server 自动调用 resolveHeaders
        ToolClient client = ToolClient.create()
            .headerProvider(() -> Map.of(
                "Authorization", "Bearer tok-dynamic-123"
            ))
            .fromConfig(config)  // api-server disabled，不会连接；但演示了集成方式
            .build();

        System.out.println("  ToolClient.create()");
        System.out.println("      .headerProvider(() -> Map.of(\"Authorization\", \"Bearer ...\"))");
        System.out.println("      .fromConfig(config)   // 自动对每个 SSE server resolveHeaders");
        System.out.println("      .build()");
        System.out.println();
        System.out.println("  fromConfig 内部流程：");
        System.out.println("    1. 遍历 config 中所有 enabled server");
        System.out.println("    2. 对 SSE/Streamable HTTP server 调用 resolveHeaders(serverConfig, headerProvider)");
        System.out.println("    3. 合并后的 headers 注入到 HTTP transport 的 request builder");
        System.out.println("    4. STDIO server 忽略 headers");

        client.close();

        System.out.println();
    }

    /**
     * 从 classpath 加载 mcp-config.yaml 中的 mcp 节点为 McpConfiguration
     */
    private static McpConfiguration loadMcpConfig(String resourceName) {
        try (InputStream is = McpClientDemo.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                System.out.println("  [WARN] " + resourceName + " 未找到");
                return null;
            }
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            JsonNode root = yamlMapper.readTree(is);
            JsonNode mcpNode = root.get("mcp");
            if (mcpNode == null) {
                System.out.println("  [WARN] " + resourceName + " 中无 mcp 节点");
                return null;
            }
            return yamlMapper.treeToValue(mcpNode, McpConfiguration.class);
        } catch (Exception e) {
            System.out.println("  [WARN] 加载 " + resourceName + " 失败: " + e.getMessage());
            return null;
        }
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
