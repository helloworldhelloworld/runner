package com.lightweightai.mcp.transport;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WebSocketMcpClientTransport 端到端收发测试 —— 对接依赖无关的 {@link MiniWebSocketServer}。
 *
 * <p>验证最易出错的新代码：JDK WebSocket Listener 接线、JSON 序列化/反序列化、
 * connect handler 签名 {@code Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>>}。
 */
@DisplayName("WebSocketMcpClientTransport 端到端收发")
class WebSocketMcpClientTransportIntegrationTest {

    private static void await(BooleanSupplier cond, String desc) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("Timed out waiting for: " + desc);
    }

    @Test
    @DisplayName("connect 后 isConnected 为 true，sendMessage 序列化并被服务端收到")
    void connectAndSend() throws Exception {
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();

            CopyOnWriteArrayList<McpSchema.JSONRPCMessage> inbound = new CopyOnWriteArrayList<>();
            transport.connect(in -> in.doOnNext(inbound::add)).block(Duration.ofSeconds(5));

            await(transport::isConnected, "transport connected");
            assertTrue(transport.isConnected());

            McpSchema.JSONRPCMessage request = new McpSchema.JSONRPCRequest(
                McpSchema.JSONRPC_VERSION, "tools/call", "req-1", null);
            transport.sendMessage(request).block(Duration.ofSeconds(5));

            await(() -> !server.received.isEmpty(), "server received a frame");
            String raw = server.received.get(0);
            assertTrue(raw.contains("tools/call"), "server should receive tools/call: " + raw);
            assertTrue(raw.contains("\"req-1\""), "server should receive the id: " + raw);

            transport.closeGracefully().block(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("connect().block() 返回后立即 isConnected 为 true（无需 await） —— 复现 MCP SDK 初始化竞态")
    void connectMonoCompletesOnlyAfterOnOpen() throws Exception {
        // MCP SDK 的初始化是 connect().then(sendInitialize())，不会像测试那样插入 await(isConnected)。
        // 因此 connect() 返回的 Mono 完成时，connected 必须已为 true，否则紧接着的 sendMessage 会
        // 因 connected=false 抛 IllegalStateException，被 SDK 包成
        // "Client failed to initialize by explicit API call"。
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();

            transport.connect(in -> Mono.empty()).block(Duration.ofSeconds(5));

            // 关键断言：不插入 await，connect 完成即代表已连接
            assertTrue(transport.isConnected(),
                "connect().block() 返回后 isConnected 必须为 true（onOpen 已触发）");

            transport.closeGracefully().block(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("connect().then(sendMessage) 链式立即发送不抛 not connected —— 模拟 SDK initialize 路径")
    void connectThenSendImmediatelySucceeds() throws Exception {
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();

            McpSchema.JSONRPCMessage initialize = new McpSchema.JSONRPCRequest(
                McpSchema.JSONRPC_VERSION, "initialize", "init-1", null);

            // 完全复刻 SDK：connect 之后立刻在同一条反应式链上发送，不等待 isConnected
            transport.connect(in -> Mono.empty())
                .then(transport.sendMessage(initialize))
                .block(Duration.ofSeconds(5));

            await(() -> !server.received.isEmpty(), "server received initialize frame");
            assertTrue(server.received.get(0).contains("initialize"),
                "server should receive initialize: " + server.received.get(0));

            transport.closeGracefully().block(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("服务端推送的 text 帧被反序列化并交给 handler")
    void receivesAndDeserializes() throws Exception {
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();

            CopyOnWriteArrayList<McpSchema.JSONRPCMessage> inbound = new CopyOnWriteArrayList<>();
            transport.connect(in -> in.doOnNext(inbound::add)).block(Duration.ofSeconds(5));
            await(transport::isConnected, "transport connected");

            server.sendText("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}");

            await(() -> !inbound.isEmpty(), "handler received a message");
            McpSchema.JSONRPCMessage msg = inbound.get(0);
            assertInstanceOf(McpSchema.JSONRPCNotification.class, msg);
            McpSchema.JSONRPCNotification notification = (McpSchema.JSONRPCNotification) msg;
            assertTrue(notification.method().contains("list_changed"));

            transport.closeGracefully().block(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("连接被拒时 connect() 快速失败并带真实原因，而非被吞成超时")
    void connectFailureFailsFastWithRealCause() {
        // 契约（规则 3/5）：握手失败必须透出真实 cause、且远早于 connectTimeout 失败，
        // 不能被 .timeout() 吞成 TimeoutException 或挂到超时。localhost:1 无人监听 → 连接立即被拒。
        WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder("ws://localhost:1/mcp")
            .connectTimeout(Duration.ofSeconds(10))
            .pingInterval(Duration.ZERO)
            .build();

        long start = System.currentTimeMillis();
        Throwable ex = assertThrows(Throwable.class,
            () -> transport.connect(in -> Mono.empty()).block(Duration.ofSeconds(15)));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000,
            "连接被拒应快速失败（远早于 connectTimeout），实际耗时 " + elapsed + "ms");

        boolean hasConnRefusedCause = false;
        boolean swallowedAsTimeout = true;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.io.IOException) {
                hasConnRefusedCause = true;
            }
            if (t instanceof java.net.ConnectException || t instanceof java.io.IOException
                    || t instanceof java.nio.channels.ClosedChannelException) {
                swallowedAsTimeout = false;
            }
        }
        assertTrue(hasConnRefusedCause,
            "cause 链应包含真实连接错误 (ConnectException/IOException)，实际: " + ex);
        assertFalse(swallowedAsTimeout,
            "真实原因不应被 .timeout() 吞成 TimeoutException: " + ex);
        assertFalse(transport.isConnected());
    }

    @Test
    @DisplayName("服务端断连后不在 transport 层自动重连，sendMessage 立即失败上抛")
    void noAutoReconnectAfterServerClose() throws Exception {
        MiniWebSocketServer server = new MiniWebSocketServer();
        WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
            .pingInterval(Duration.ZERO)
            .build();
        transport.connect(in -> Mono.empty()).block(Duration.ofSeconds(5));
        await(transport::isConnected, "transport connected");

        // 服务端关闭连接 → 客户端 onClose 触发
        server.close();
        await(() -> !transport.isConnected(), "transport observed disconnect");

        // 选 1 语义：transport 不重连，断连后发送立即失败（由上层决定是否重建并重新 initialize）
        McpSchema.JSONRPCMessage msg = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION, "tools/call", "after-close", null);
        assertThrows(IllegalStateException.class,
            () -> transport.sendMessage(msg).block(Duration.ofSeconds(2)));

        // 给潜在的（不应存在的）重连留出时间窗口，确认仍未连接
        Thread.sleep(300);
        assertFalse(transport.isConnected(), "断连后不应自动重连");

        transport.closeGracefully().block(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("closeGracefully 后 isConnected 为 false")
    void closeStopsConnection() throws Exception {
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();
            transport.connect(in -> Mono.empty()).block(Duration.ofSeconds(5));
            await(transport::isConnected, "transport connected");

            transport.closeGracefully().block(Duration.ofSeconds(2));
            assertFalse(transport.isConnected());
        }
    }
}
