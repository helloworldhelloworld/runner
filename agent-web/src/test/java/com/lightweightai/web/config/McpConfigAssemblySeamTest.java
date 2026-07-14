package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.McpConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产装配接缝测试：驱动 agent-web 启动时真正调用的 {@link McpConfig#mcpToolRegistrar} @Bean，
 * 走 YAML 绑定 → {@code connectMcpServer} → {@code ToolClient.Builder.createTransport(ws)} →
 * 真实 {@code McpToolClient.initialize()} → 真实 MCP SDK → WebSocket transport 的完整路径。
 *
 * <p>守护 issue #195 在<b>端到端装配层</b>的价值（接缝单测 {@code WebSocketMcpInitializeFailFastTest}
 * 覆盖不到的那一面）：一个连不上的 WS MCP 上游<b>不能拖垮 app 启动</b>。修复前，每个坏上游会在
 * {@code initialize()} 同步阻塞到 requestTimeout / SDK init 超时（约 20s），N 个上游 = N×20s 的
 * 启动停顿；修复后握手一被拒即快速失败，装配循环继续、启动不被拖住。
 *
 * <p>注：这里断言的是「装配循环的时间上界 + 优雅跳过」，而非异常原因——原因/时间阈值由接缝单测精确验证。
 * 两者互补（一个管「原因/时间」，一个管「启动不被拖垮」）。
 */
@DisplayName("MCP 生产装配：坏的 WS 上游不拖垮启动（issue #195）")
class McpConfigAssemblySeamTest {

    /** 完成 TCP，但用 HTTP 400 拒绝 WebSocket 升级 → 模拟"连得上端口但不是 WS/不可初始化"的上游。 */
    private static final class UpgradeRejectingServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;

        UpgradeRejectingServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.acceptThread = new Thread(this::run, "ws-upgrade-reject-web");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        String wsUrl(String path) {
            return "ws://localhost:" + serverSocket.getLocalPort() + path;
        }

        private void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = in.read()) != -1) {
                        sb.append((char) c);
                        if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) {
                            break;
                        }
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 400 Bad Request\r\n"
                        + "Connection: close\r\n"
                        + "Content-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    return;
                }
            }
        }

        @Override
        public void close() {
            acceptThread.interrupt();
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static McpConfiguration.ServerConfig wsServer(String url) {
        McpConfiguration.ServerConfig cfg = new McpConfiguration.ServerConfig();
        cfg.setTransport("ws");
        cfg.setUrl(url);
        cfg.setEnabled(true);
        cfg.setTimeoutSeconds(10);      // 坏上游若阻塞，会撞到这个上限；快速失败则远早于此
        cfg.setPingIntervalSeconds(0);  // 关心跳，聚焦 initialize 时序
        return cfg;
    }

    @Test
    @DisplayName("两个连不上的 WS 上游 → mcpToolRegistrar 快速返回（不 ~20s×N 阻塞），Bean 正常产出")
    void badWsUpstreamsDoNotStallStartup() throws Exception {
        try (UpgradeRejectingServer server = new UpgradeRejectingServer()) {
            Map<String, McpConfiguration.ServerConfig> servers = new LinkedHashMap<>();
            servers.put("ws-health-mcp-upstream", wsServer(server.wsUrl("/mcp")));
            servers.put("ws-health-mcp-upstream-2", wsServer(server.wsUrl("/mcp")));

            McpProperties props = new McpProperties();
            props.setServers(servers);

            McpConfig config = new McpConfig(props, null);
            ToolRegistry registry = new ToolRegistry();

            long start = System.currentTimeMillis();
            // 这正是 Spring 启动时执行的 @Bean 方法
            McpConfig.McpToolRegistrar registrar = config.mcpToolRegistrar(registry);
            long elapsed = System.currentTimeMillis() - start;

            // 装配不因坏上游而中止：Bean 仍然产出（McpConfig 对每个 server try/catch 后继续）
            assertNotNull(registrar, "坏上游不应导致装配中止，mcpToolRegistrar 仍应产出 Bean");
            assertTrue(registrar.getClients().isEmpty(),
                "连不上的上游不应被计入已连接客户端，实际: " + registrar.getClients().size());

            // 核心：两个坏上游合计仍快速返回，而非 2×(requestTimeout/SDK init 超时)≈2×10~20s 的启动停顿
            assertTrue(elapsed < 5000,
                "坏的 WS 上游不应拖垮启动：两个上游的装配应快速失败，实际耗时 " + elapsed + "ms");
        }
    }
}
