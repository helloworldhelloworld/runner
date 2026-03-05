package com.lightweightai.demo;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpToolAdapter;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolManager;
import com.lightweightai.mcp.McpToolServer;
import com.lightweightai.mcp.McpToolWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo 3: MCP 工具集成 — 透明化设计
 *
 * 核心理念：调用方（ToolExecutor / ToolCallingLoop / Agent）完全不需要知道
 * 某个工具是本地的还是来自 MCP 服务端。框架自动路由。
 *
 * 演示五部分：
 *
 * [A] 透明调用 — 本地工具和 MCP 工具统一执行（核心设计）
 *     ToolExecutor.executeToolCall() 不区分工具来源
 *     ToolCallingLoop 使用完全相同的代码处理两种工具
 *
 * [B] McpToolManager — 一站式管理本地+MCP工具生命周期
 *     create() → registerLocal() → addMcpServer() → build()
 *     自动 connect → discover → register → close
 *
 * [C] McpToolAdapter — 将本地工具暴露为 MCP 服务端
 *     McpToolServer 让 Claude Desktop / Cursor / 其他 Agent 发现并调用工具
 *
 * [D] Agent 集成 — Agent 使用 ToolRegistry，无感调用所有工具
 *
 * [E] 远程 MCP Server 配置 — 通过 McpConfiguration 配置驱动
 *     支持 STDIO 和 SSE 两种传输方式，可代码配置或 YAML 配置
 *
 * 运行方式：
 *   mvn exec:java -pl agent-demo -Dexec.mainClass=com.lightweightai.demo.McpToolDemo
 */
public class McpToolDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Demo 3: MCP Tool Integration");
        System.out.println("  核心：本地工具与 MCP 工具透明统一");
        System.out.println("========================================\n");

        demoTransparentExecution();
        demoMcpToolManager();
        demoRemoteMcpConfig();
        demoMcpServerExpose();
        demoAgentIntegration();
    }

    // ================================================================
    //  [A] 透明调用 — ToolExecutor 不区分本地/MCP
    //
    //  设计原理：
    //    Tool 是统一接口，McpToolWrapper 和本地 Tool 都实现它。
    //    ToolExecutor 调用 tool.execute(args)，由多态决定走本地还是 MCP。
    //    ToolCallingLoop 只依赖 ToolExecutor，完全无感。
    //
    //  调用链路（调用方看到的）：
    //    ToolExecutor.executeToolCall(ToolCall)
    //      → ToolRegistry.get(name) → Tool
    //      → tool.execute(args) → ToolResult
    //
    //  实际执行（调用方不可见）：
    //    本地 Tool:     直接执行 Java 方法
    //    MCP Tool:      McpToolWrapper.execute()
    //                     → mcpClient.callTool()
    //                     → MCP 协议 → 远程服务端
    //                     → CallToolResult → ToolResult
    // ================================================================

    static void demoTransparentExecution() {
        System.out.println("--- [A] 透明调用：ToolExecutor 不区分本地/MCP ---\n");

        // Step 1: 统一注册（本地工具 + 模拟的 MCP 工具）
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());   // 本地 @ToolFunction
        registry.scanAndRegister();                     // SPI: MathTools, TimeTools, WebTools

        // 模拟：如果有 MCP 服务端连接，McpToolWrapper 也会注册到同一个 registry
        // registry.registerFrom(mcpClient);  // McpToolWrapper implements Tool
        // 这里用一个本地 Tool 模拟 MCP 工具，证明调用方代码完全一样
        registry.register(new SimulatedMcpWeatherTool());

        System.out.println("[Registry] " + registry.enabledCount() + " tools (本地+MCP混合):");
        for (Tool tool : registry.getEnabled()) {
            String source = (tool instanceof ToolMetadata meta)
                ? meta.getCategory() : "default";
            System.out.println("  - " + tool.getName() + " [" + source + "]");
        }

        // Step 2: ToolExecutor — 统一执行入口，不区分来源
        ToolExecutor executor = new ToolExecutor(registry);
        System.out.println("\n[ToolExecutor] 统一调用，调用方代码完全相同：\n");

        // 调用本地工具
        ToolResult r1 = executor.executeToolCall(
            new ToolCall("1", "get_city_info", Map.of("city", "beijing")));
        System.out.println("  get_city_info({city:\"beijing\"})  → " + r1.getContent());
        System.out.println("    ↑ 本地 @ToolFunction 直接执行");

        ToolResult r2 = executor.executeToolCall(
            new ToolCall("2", "add", Map.of("a", 42, "b", 58)));
        System.out.println("  add({a:42, b:58})                → " + r2.getContent());
        System.out.println("    ↑ SPI 扫描注册的本地工具");

        // 调用 "MCP" 工具（实际是模拟的，但代码完全一样）
        ToolResult r3 = executor.executeToolCall(
            new ToolCall("3", "get_weather_forecast", Map.of("city", "Shanghai")));
        System.out.println("  get_weather_forecast({city:\"Shanghai\"}) → " + r3.getContent());
        System.out.println("    ↑ MCP 工具（McpToolWrapper.execute() → mcpClient.callTool()）");

        // 关键点：上面三次调用，代码完全一样！调用方不知道也不需要知道工具来源。
        System.out.println("\n  ✓ 三次调用代码完全相同，调用方无感工具来源");

        // Step 3: ToolCallingLoop — 完全透明
        System.out.println("\n[ToolCallingLoop] LLM 自动调用工具，无需关心来源：\n");

        // Mock LLM 模拟调用 MCP 工具 get_weather_forecast
        LLMProvider mockLlm = new MockLLMProvider(
            "get_weather_forecast",
            Map.of("city", "Tokyo"));

        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(mockLlm)
            .toolExecutor(executor)
            .maxIterations(5)
            .build();

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent("What's the weather in Tokyo?")
            .build());

        LLMResponse response = loop.executeWithTools(messages,
            LLMOptions.builder()
                .toolDefinitions(executor.getToolDefinitions())
                .build());

        System.out.println("  Final: " + response.getMessage().getTextContent());
        System.out.println("  ↑ ToolCallingLoop 自动调用了 MCP 工具，代码零改动\n");
    }

    // ================================================================
    //  [B] McpToolManager — 一站式管理
    //
    //  真实使用时，只需：
    //    McpToolManager.create()
    //      .registerLocal(...)      // 本地工具
    //      .addMcpServer(name, t)   // MCP 自动 connect+discover+register
    //      .build()
    //
    //  然后 manager.getToolExecutor() 即可统一调用所有工具
    // ================================================================

    static void demoMcpToolManager() {
        System.out.println("--- [B] McpToolManager: 一站式工具管理 ---\n");

        System.out.println("  // 真实使用代码（需要 MCP 服务端运行时连接）：");
        System.out.println("  McpToolManager manager = McpToolManager.create()");
        System.out.println("      .registerLocal(new MathTools())");
        System.out.println("      .registerLocal(new CityInfoTool())");
        System.out.println("      .addMcpServer(\"weather\",              // 自动 connect+discover+register");
        System.out.println("          new StdioClientTransport(weatherParams))");
        System.out.println("      .addMcpServer(\"filesystem\",");
        System.out.println("          new StdioClientTransport(fsParams))");
        System.out.println("      .autoScan()                            // SPI 扫描");
        System.out.println("      .build();");
        System.out.println();
        System.out.println("  // 使用：完全不需要知道哪个是 MCP，哪个是本地");
        System.out.println("  ToolExecutor executor = manager.getToolExecutor();");
        System.out.println("  executor.executeToolCall(new ToolCall(\"1\", \"add\", args));");
        System.out.println("  executor.executeToolCall(new ToolCall(\"2\", \"get_forecast\", args));");
        System.out.println("  executor.executeToolCall(new ToolCall(\"3\", \"read_file\", args));");
        System.out.println();
        System.out.println("  // 或者直接给 Agent 使用");
        System.out.println("  Agent agent = Agent.builder()");
        System.out.println("      .claude(apiKey)");
        System.out.println("      .tools(manager.getToolRegistry())  // 统一注册");
        System.out.println("      .build();");
        System.out.println("  agent.chat(\"Tokyo 天气怎么样？\");  // 自动调用 MCP 的 weather 工具");
        System.out.println();
        System.out.println("  manager.close();  // 自动关闭所有 MCP 连接");
        System.out.println();

        // 设计图
        System.out.println("  架构图：");
        System.out.println("  ┌─────────────────────────────────────────────────┐");
        System.out.println("  │           McpToolManager                        │");
        System.out.println("  │                                                 │");
        System.out.println("  │  ┌───────────────┐  ┌───────────────────────┐  │");
        System.out.println("  │  │ ToolRegistry   │  │ MCP Client Lifecycle │  │");
        System.out.println("  │  │               │  │                       │  │");
        System.out.println("  │  │ add (local)   │  │ weather-server ──┐    │  │");
        System.out.println("  │  │ multiply      │  │ filesystem     ──┤    │  │");
        System.out.println("  │  │ get_time      │  │ ...            ──┘    │  │");
        System.out.println("  │  │ get_forecast  │←─│ connect+discover      │  │");
        System.out.println("  │  │ read_file     │  │ +register             │  │");
        System.out.println("  │  └───────────────┘  └───────────────────────┘  │");
        System.out.println("  │         ↓                                       │");
        System.out.println("  │    ToolExecutor ← ToolCallingLoop ← Agent      │");
        System.out.println("  │    (统一执行，不区分来源)                         │");
        System.out.println("  └─────────────────────────────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  [E] 调用远程 MCP 工具 — 真实 MCP 协议端到端
    //
    //  流程：
    //    1. 启动 McpServerRunner 子进程（STDIO MCP Server）
    //    2. MCP 握手 → 发现远程工具
    //    3. 注册到 ToolRegistry
    //    4. 通过 ToolExecutor 调用远程工具（和本地工具代码完全一样）
    //    5. 关闭连接
    // ================================================================

    static void demoRemoteMcpConfig() {
        System.out.println("--- [E] MCP Server 代理上游 MCP Server（配置驱动 + MCP SDK） ---\n");

        //
        // 架构：
        //   Demo [E]  ──MCP──→  McpServerRunner  ──MCP SDK──→  外部已有 MCP Server
        //   (Client)            (mcp-server.yaml)               (只需配置，不用实现)
        //                       ├── server: name, version
        //                       ├── 本地工具
        //                       └── upstream.servers:
        //                             nlp-service → SSE   → http://host:8081
        //                             filesystem  → STDIO → npx @modelcontextprotocol/server-filesystem
        //
        // mcp-server.yaml 示例：
        //   server:
        //     name: demo-agent
        //     version: 0.1.0
        //   upstream:
        //     servers:
        //       nlp-service:
        //         transport: sse
        //         url: http://host:8081
        //       filesystem:
        //         transport: stdio
        //         command: npx
        //         args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
        //

        String javaCmd = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // Step 1: 启动 McpServerRunner 子进程
        // McpServerRunner 自动从 mcp-server.yaml 加载 server 配置和 upstream 配置
        ServerParameters serverParams = ServerParameters.builder(javaCmd)
            .args("-cp", classpath, "com.lightweightai.demo.McpServerRunner")
            .build();

        System.out.println("[Step 1] 启动 McpServerRunner（自动加载 mcp-server.yaml）");
        System.out.println("  server 配置 → name, version");
        System.out.println("  upstream 配置 → 上游 MCP Server（SSE 或 STDIO）");
        System.out.println("  链路: Demo → MCP → McpServerRunner → MCP SDK → 外部 MCP Server\n");

        McpToolClient client = McpToolClient.builder()
            .serverName("demo-agent")
            .transport(new StdioClientTransport(serverParams,
                new JacksonMcpJsonMapper(new ObjectMapper())))
            .build();

        try {
            // Step 2: MCP 握手
            client.initialize();
            System.out.println("[Step 2] MCP 握手成功\n");

            // Step 3: 发现远程工具（包含 McpServerRunner 本地 + 上游代理的工具）
            List<McpToolWrapper> remoteTools = client.discoverMcpTools();
            System.out.println("[Step 3] 发现 " + remoteTools.size() + " 个远程工具：");
            for (McpToolWrapper tool : remoteTools) {
                System.out.println("  - " + tool.getName() + ": " + tool.getDescription());
            }

            // Step 4: 注册到 ToolRegistry
            ToolRegistry registry = new ToolRegistry();
            registry.registerObject(new CityInfoTool());   // 本地工具
            client.registerTools(registry);                 // MCP 远程工具

            ToolExecutor executor = new ToolExecutor(registry);

            // ---- 4a: 调用 McpServerRunner 的本地工具 ----
            System.out.println("\n[Step 4a] 调用 McpServerRunner 本地工具：\n");

            ToolResult r1 = executor.executeToolCall(
                new ToolCall("1", "get_weather", Map.of("city", "上海")));
            System.out.println("  get_weather({city:\"上海\"})");
            System.out.println("    → " + r1.getContent());
            System.out.println("    ↑ Demo → MCP → McpServerRunner（本地计算） → MCP → Demo\n");

            ToolResult r2 = executor.executeToolCall(
                new ToolCall("2", "calculate", Map.of("a", 42.0, "op", "+", "b", 58.0)));
            System.out.println("  calculate({a:42, op:\"+\", b:58})");
            System.out.println("    → " + r2.getContent());
            System.out.println("    ↑ Demo → MCP → McpServerRunner（本地计算） → MCP → Demo\n");

            // ---- 4b: 调用上游 MCP Server 的工具（通过 McpServerRunner 代理） ----
            System.out.println("[Step 4b] 调用上游 MCP Server 工具（MCP-to-MCP 代理）：\n");

            ToolResult r3 = executor.executeToolCall(
                new ToolCall("3", "translate_text",
                    Map.of("text", "hello world", "from", "en", "to", "zh")));
            System.out.println("  translate_text({text:\"hello world\", from:\"en\", to:\"zh\"})");
            System.out.println("    → " + r3.getContent());
            System.out.println("    ↑ Demo → MCP → McpServerRunner → MCP SDK → 外部 MCP Server → 逐层返回\n");

            ToolResult r4 = executor.executeToolCall(
                new ToolCall("4", "lookup_definition",
                    Map.of("word", "kernel", "language", "en")));
            System.out.println("  lookup_definition({word:\"kernel\"})");
            System.out.println("    → " + r4.getContent());
            System.out.println("    ↑ Demo → MCP → McpServerRunner → MCP SDK → 外部 MCP Server → 逐层返回\n");

            ToolResult r5 = executor.executeToolCall(
                new ToolCall("5", "sentiment_analysis",
                    Map.of("text", "This framework is great!")));
            System.out.println("  sentiment_analysis({text:\"This framework is great!\"})");
            System.out.println("    → " + r5.getContent());
            System.out.println("    ↑ Demo → MCP → McpServerRunner → MCP SDK → 外部 MCP Server → 逐层返回\n");

            // ---- 4c: 调用本地工具（对比） ----
            System.out.println("[Step 4c] 本地工具调用（对比）：\n");

            ToolResult r6 = executor.executeToolCall(
                new ToolCall("6", "get_city_info", Map.of("city", "beijing")));
            System.out.println("  get_city_info({city:\"beijing\"})");
            System.out.println("    → " + r6.getContent());
            System.out.println("    ↑ 本地执行（代码和上面完全一样，调用方无感）\n");

            // Step 5: 工具来源总览
            System.out.println("[Step 5] 工具来源总览：");
            for (Tool tool : registry.getEnabled()) {
                String source;
                if (tool instanceof McpToolWrapper w) {
                    source = "mcp:" + w.getServerName();
                } else {
                    source = "local";
                }
                System.out.println("  - " + tool.getName() + " → " + source);
            }

            System.out.println("\n  ✓ 全部配置驱动：mcp-server.yaml 定义 server + upstream");
            System.out.println("  ✓ upstream 支持 SSE（HTTP 远程调用）和 STDIO（本地进程）");
            System.out.println("  ✓ SSE 使用 HttpClientSseClientTransport（MCP SDK，JDK HttpClient）");
            System.out.println("  ✓ 完整链路：Client → MCP → Server → MCP/SSE → Upstream → 逐层返回");
            System.out.println("  ✓ 调用方完全无感，本地/远程/代理工具通过 ToolExecutor 统一调用");

        } catch (Exception e) {
            System.out.println("[ERROR] MCP 远程调用失败: " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            // Step 6: 关闭（McpServerRunner 的 shutdown hook 会自动关闭上游连接）
            System.out.println("\n[Step 6] 关闭 MCP 连接，McpServerRunner 及上游子进程退出");
            client.close();
        }

        System.out.println();
    }

    // ================================================================
    //  [C] McpToolServer — 反方向：将本地工具暴露为 MCP 服务端
    // ================================================================

    static void demoMcpServerExpose() {
        System.out.println("--- [C] McpToolServer: 暴露工具给外部 AI Agent ---\n");

        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new CityInfoTool());
        registry.scanAndRegister();

        // 转换为 MCP 格式
        List<McpServerFeatures.SyncToolSpecification> mcpSpecs = McpToolAdapter.toMcpTools(registry);
        System.out.println("[Adapter] " + mcpSpecs.size() + " tools → MCP SyncToolSpecification:");
        for (McpServerFeatures.SyncToolSpecification spec : mcpSpecs) {
            System.out.println("  - " + spec.tool().name());
        }

        // 通过 MCP handler 验证执行
        McpServerFeatures.SyncToolSpecification citySpec = mcpSpecs.stream()
            .filter(s -> s.tool().name().equals("get_city_info"))
            .findFirst()
            .orElseThrow();

        McpSchema.CallToolResult mcpResult = citySpec.call().apply(null, Map.of("city", "beijing"));
        String resultText = ((McpSchema.TextContent) mcpResult.content().get(0)).text();
        System.out.println("\n[MCP Handler] get_city_info({city:\"beijing\"}) → " + resultText);

        // 对比本地执行
        ToolExecutor executor = new ToolExecutor(registry);
        ToolResult localResult = executor.executeToolCall(
            new ToolCall("1", "get_city_info", Map.of("city", "beijing")));
        System.out.println("[Local]       get_city_info({city:\"beijing\"}) → " + localResult.getContent());
        System.out.println("[Match]       " + localResult.getContent().equals(resultText));

        System.out.println("\n  启动 MCP 服务端：");
        System.out.println("    McpToolServer server = McpToolServer.builder()");
        System.out.println("        .serverName(\"demo-agent\")");
        System.out.println("        .toolRegistry(registry)");
        System.out.println("        .build();");
        System.out.println("    server.start();  // STDIO 监听\n");
    }

    // ================================================================
    //  [D] Agent 集成 — ToolRegistry 统一管理
    // ================================================================

    static void demoAgentIntegration() {
        System.out.println("--- [D] Agent 集成: tools(ToolRegistry) ---\n");

        System.out.println("  // 方式一：直接用 ToolRegistry");
        System.out.println("  ToolRegistry registry = new ToolRegistry();");
        System.out.println("  registry.registerObject(new MathTools());");
        System.out.println("  registry.registerFrom(mcpClient);  // MCP 工具也注册进来");
        System.out.println();
        System.out.println("  Agent agent = Agent.builder()");
        System.out.println("      .claude(apiKey)");
        System.out.println("      .tools(registry)  // ← 统一注册表");
        System.out.println("      .build();");
        System.out.println();
        System.out.println("  agent.chat(\"42 + 58 = ?\");        // 自动调用本地 add 工具");
        System.out.println("  agent.chat(\"Tokyo 天气如何？\");    // 自动调用 MCP weather 工具");
        System.out.println("  // Agent 内部 ToolCallingLoop 完全透明\n");

        System.out.println("  // 方式二：用 McpToolManager（推荐，自动管理 MCP 生命周期）");
        System.out.println("  McpToolManager manager = McpToolManager.create()");
        System.out.println("      .registerLocal(new MathTools())");
        System.out.println("      .addMcpServer(\"weather\", transport)");
        System.out.println("      .build();");
        System.out.println();
        System.out.println("  Agent agent = Agent.builder()");
        System.out.println("      .claude(apiKey)");
        System.out.println("      .tools(manager.getToolRegistry())");
        System.out.println("      .build();");
        System.out.println();
        System.out.println("  agent.chat(\"...\");  // 本地和MCP工具无感调用");
        System.out.println("  manager.close();    // 清理所有 MCP 连接\n");

        // 关键设计说明
        System.out.println("========================================");
        System.out.println("  透明设计总结");
        System.out.println("========================================\n");
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.println("  │  调用方（Agent/ToolCallingLoop/ToolExecutor）        │");
        System.out.println("  │  只看到: Tool.execute(args) → ToolResult            │");
        System.out.println("  │  不知道也不需要知道工具来源                            │");
        System.out.println("  └─────────────────────┬───────────────────────────────┘");
        System.out.println("                        │ tool.execute(args)");
        System.out.println("              ┌─────────┴─────────┐");
        System.out.println("              ↓                   ↓");
        System.out.println("  ┌────────────────────┐ ┌────────────────────────┐");
        System.out.println("  │ 本地 Tool          │ │ McpToolWrapper         │");
        System.out.println("  │ (直接 Java 执行)   │ │ (implements Tool)      │");
        System.out.println("  │                    │ │                        │");
        System.out.println("  │ return result;     │ │ mcpClient.callTool()   │");
        System.out.println("  │                    │ │   → MCP 协议           │");
        System.out.println("  │                    │ │   → 远程服务端执行      │");
        System.out.println("  │                    │ │   → CallToolResult     │");
        System.out.println("  │                    │ │   → ToolResult         │");
        System.out.println("  └────────────────────┘ └────────────────────────┘");
        System.out.println();
    }

    // ================================================================
    //  模拟的 MCP 天气工具
    //
    //  真实场景中，这是 McpToolWrapper（通过 mcpClient.callTool() 调用远程服务）
    //  这里用本地实现模拟，证明 ToolExecutor 调用代码完全相同
    // ================================================================

    public static class SimulatedMcpWeatherTool implements Tool, ToolMetadata {

        @Override
        public String getName() {
            return "get_weather_forecast";
        }

        @Override
        public String getDescription() {
            return "Get weather forecast for a city (simulated MCP tool)";
        }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.withRequired(Map.of(
                "city", Map.of("type", "string", "description", "City name")
            ), "city");
        }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            // 真实的 McpToolWrapper 这里会调用 mcpClient.callTool()
            // 走 MCP 协议到远程服务端。调用方看不到区别。
            String city = (String) args.get("city");
            return ToolResult.success("Forecast for " + city + ": sunny, 25°C, humidity 60%");
        }

        @Override
        public String getCategory() {
            return "mcp:weather-server";  // McpToolWrapper 自动设置
        }

        @Override
        public List<String> getTags() {
            return List.of("mcp", "remote", "weather-server");
        }

        @Override
        public String getAuthor() {
            return "mcp-server:weather-server";
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }
    }

    // ================================================================
    //  示例工具
    // ================================================================

    public static class CityInfoTool {

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
                case "beijing" -> "Beijing: population 21.5M, area 16,410 km²";
                case "shanghai" -> "Shanghai: population 24.9M, area 6,341 km²";
                case "tokyo" -> "Tokyo: population 13.9M, area 2,194 km²";
                default -> city + ": data not available (demo mode)";
            };
        }

        @ToolFunction(
            name = "convert_currency",
            description = "Convert amount between currencies",
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
            double rate = 1.0;
            if ("USD".equalsIgnoreCase(from) && "CNY".equalsIgnoreCase(to)) rate = 7.25;
            if ("CNY".equalsIgnoreCase(from) && "USD".equalsIgnoreCase(to)) rate = 0.138;
            if ("USD".equalsIgnoreCase(from) && "JPY".equalsIgnoreCase(to)) rate = 149.5;

            double result = amount * rate;
            return String.format("%.2f %s = %.2f %s", amount, from.toUpperCase(), result, to.toUpperCase());
        }
    }

    // ================================================================
    //  Mock LLM — 模拟 LLM 调用工具
    // ================================================================

    static class MockLLMProvider implements LLMProvider {
        private final String toolName;
        private final Map<String, Object> toolArgs;
        private int callCount = 0;

        MockLLMProvider(String toolName, Map<String, Object> toolArgs) {
            this.toolName = toolName;
            this.toolArgs = toolArgs;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            callCount++;
            if (callCount == 1) {
                System.out.println("  [LLM] Round 1 → tool_use: " + toolName + "(" + toolArgs + ")");
                ToolCall call = new ToolCall("call_001", toolName, toolArgs);
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("")
                        .metadata(Map.of("tool_calls", List.of(call)))
                        .build())
                    .toolCalls(List.of(call))
                    .build();
            }
            String toolResult = messages.stream()
                .filter(m -> m.getRole() == MessageRole.TOOL)
                .reduce((a, b) -> b)
                .map(ConversationMessage::getTextContent)
                .orElse("(no result)");
            System.out.println("  [LLM] Round 2 ← tool result: " + toolResult);
            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT)
                    .textContent("Based on the forecast: " + toolResult)
                    .build())
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            return completeAsync(messages, options);
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "mock"; }
    }
}
