package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpConfiguration.ServerConfig;
import com.lightweightai.mcp.McpToolClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M-3：MCP <b>生产装配</b>端到端接缝测试（巡检 C2/M-3）。
 *
 * <p>背景：{@code RunnerToMinionBodyMcpSmokeTest} 等用 {@code McpToolClient.builder()}
 * <b>手工重建</b>客户端，绕开了真正的 {@code application.yml → McpProperties 绑定 →
 * McpConfig.connectMcpServer()} 这条生产装配。若该装配漂移（YAML 键名、绑定、createTransport 选型），
 * fake smoke 抓不到。
 *
 * <p>本测试驱动**真生产链**，不手工重建客户端：
 * <ol>
 *   <li>Spring {@link Binder}（Spring Boot 启动时绑 {@code @ConfigurationProperties} 的同一机制）把
 *       {@code app.mcp.*} 属性绑进真 {@link McpProperties}——等价于 application.yml 装配；</li>
 *   <li>真 {@link McpConfig#tryConnectReturning}（{@code mcpToolRegistrar} @Bean 调的同一生产方法）
 *       → {@code connectMcpServer} → {@code ToolClient.Builder.createTransport} → 真 MCP SDK
 *       （{@code HttpClientStreamableHttpTransport} + {@code McpAsyncClient.initialize}）；</li>
 *   <li>对端是真网络 streamable_http MCP server（JDK {@link HttpServer}），断言其工具经此链
 *       注册进 {@link ToolRegistry}。</li>
 * </ol>
 *
 * <p>对端内联而非复用 agent-mcp 的 {@code MiniStreamableHttpMcpServer}：跨模块 test-jar 只在
 * {@code package} 阶段产出，会让 {@code mvn test} 解析不到——内联保持 {@code mvn clean test} 可用。
 * 协议形状沿用同版本 SDK 已验证的写法；形状漂移则真 SDK initialize 失败、测试红。
 */
@DisplayName("M-3 接缝: application.yml→McpProperties 绑定→connectMcpServer 生产装配端到端")
class McpProductionAssemblySeamAcceptanceTest {

    @Test
    @DisplayName("app.mcp.* 绑定 → 真 connectMcpServer 连上 fake Pi → 工具注册进 ToolRegistry")
    void yamlBindingDrivesConnectAndToolRegistration() throws Exception {
        try (FakeStreamableHttpMcp pi = new FakeStreamableHttpMcp().withTool("set_eyes")) {

            // 1) 真绑定：app.mcp.*（如 application.yml）→ McpProperties。
            Map<String, Object> yaml = new LinkedHashMap<>();
            yaml.put("app.mcp.enabled", "true");
            yaml.put("app.mcp.servers.minionpi.transport", "streamable_http");
            yaml.put("app.mcp.servers.minionpi.url", pi.url("/mcp"));
            yaml.put("app.mcp.servers.minionpi.enabled", "true");
            yaml.put("app.mcp.servers.minionpi.timeout-seconds", "10");

            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("test-yaml", yaml));
            McpProperties props = Binder.get(env)
                    .bind("app.mcp", Bindable.of(McpProperties.class))
                    .orElseThrow(() -> new AssertionError("app.mcp 未绑定成 McpProperties"));

            // 绑定结果就位（含 relaxed binding：timeout-seconds → timeoutSeconds）
            ServerConfig sc = props.getServers().get("minionpi");
            assertNotNull(sc, "app.mcp.servers.minionpi 应绑成 ServerConfig");
            assertEquals("streamable_http", sc.getTransport(), "transport 绑定");
            assertEquals(pi.url("/mcp"), sc.getUrl(), "url 绑定");
            assertTrue(sc.isEnabled());
            assertEquals(10, sc.getTimeoutSeconds(), "timeout-seconds → timeoutSeconds relaxed binding");

            // 2) 真生产 connect 路径（mcpToolRegistrar @Bean 调用的同一方法），不手工重建客户端。
            McpConfig config = new McpConfig(props, null);
            ToolRegistry registry = new ToolRegistry();
            McpToolClient client = config.tryConnectReturning("minionpi", sc, registry);

            // 3) 断载荷：连上 + 工具经真链（initialize→tools/list→registerTools）落进 registry。
            assertNotNull(client, "生产装配应连上 fake Pi 并返回 client（非 null=connect+initialize 成功）");
            assertTrue(registry.has("set_eyes"),
                    "fake Pi 的 set_eyes 应经 connectMcpServer→registerTools 注册进 ToolRegistry；实际: "
                            + registry.getAll().stream().map(t -> t.getName()).toList());
        }
    }

    @Test
    @DisplayName("绑定到不可达 url → connectMcpServer 快失败返回 null（不抛、不挂）")
    void unreachableServerFailsFastToNull() {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("app.mcp.servers.dead.transport", "streamable_http");
        yaml.put("app.mcp.servers.dead.url", "http://127.0.0.1:1/mcp"); // 端口 1，连不上
        yaml.put("app.mcp.servers.dead.enabled", "true");
        yaml.put("app.mcp.servers.dead.timeout-seconds", "2");
        env.getPropertySources().addFirst(new MapPropertySource("test-yaml", yaml));
        McpProperties props = Binder.get(env).bind("app.mcp", Bindable.of(McpProperties.class)).get();

        McpConfig config = new McpConfig(props, null);
        ToolRegistry registry = new ToolRegistry();
        McpToolClient client = config.tryConnectReturning("dead", props.getServers().get("dead"), registry);

        assertNull(client, "连不上时生产装配应快失败返回 null（仅记日志），不抛异常");
        assertEquals(0, registry.getAll().size(), "失败连接不应注册任何工具");
    }

    // ==================== 内联最小 streamable_http MCP 假对端（真网络 JDK HttpServer）====================

    /**
     * 依赖无关的最小 streamable_http MCP 测试服务端，协议形状沿用 agent-mcp
     * {@code MiniStreamableHttpMcpServer}（同版本 SDK 验证可用）：
     * 带 id 的 initialize/tools/list/tools/call → 200 + JSON-RPC；通知 → 202；GET → 405。
     */
    private static final class FakeStreamableHttpMcp implements AutoCloseable {
        private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
        private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
        private static final Pattern PROTO = Pattern.compile("\"protocolVersion\"\\s*:\\s*\"([^\"]*)\"");

        private final HttpServer server;
        private final CopyOnWriteArrayList<String> toolEntries = new CopyOnWriteArrayList<>();

        FakeStreamableHttpMcp() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/mcp", this::handle);
            this.server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "fake-http-mcp");
                t.setDaemon(true);
                return t;
            }));
            this.server.start();
        }

        FakeStreamableHttpMcp withTool(String name) {
            toolEntries.add("{\"name\":\"" + name + "\",\"description\":\"d\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}");
            return this;
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private void handle(HttpExchange ex) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, -1);
                    return;
                }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String method = group(METHOD, body);
                String id = group(ID, body);
                if (method == null || id == null) {
                    ex.sendResponseHeaders(202, -1);
                    return;
                }
                String json;
                switch (method) {
                    case "initialize" -> json = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{"
                            + "\"protocolVersion\":\"" + protoOrDefault(body) + "\","
                            + "\"capabilities\":{\"tools\":{\"listChanged\":true}},"
                            + "\"serverInfo\":{\"name\":\"fake-pi\",\"version\":\"1.0\"}}}";
                    case "tools/list" -> json = "{\"jsonrpc\":\"2.0\",\"id\":" + id
                            + ",\"result\":{\"tools\":[" + String.join(",", toolEntries) + "]}}";
                    case "tools/call" -> json = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{"
                            + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"isError\":false}}";
                    default -> {
                        ex.sendResponseHeaders(202, -1);
                        return;
                    }
                }
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (RuntimeException e) {
                try { ex.sendResponseHeaders(500, -1); } catch (IOException ignored) { }
            } finally {
                ex.close();
            }
        }

        private static String protoOrDefault(String body) {
            String p = group(PROTO, body);
            return p != null ? p : "2025-06-18";
        }

        private static String group(Pattern p, String s) {
            Matcher m = p.matcher(s);
            return m.find() ? m.group(1) : null;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
