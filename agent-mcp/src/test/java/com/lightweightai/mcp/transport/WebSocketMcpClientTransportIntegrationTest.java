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
