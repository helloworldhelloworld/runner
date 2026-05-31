package com.lightweightai.mcp.transport;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebSocketMcpClientTransport 纯逻辑单元测试（无需连接）")
class WebSocketMcpClientTransportTest {

    @Test
    @DisplayName("builder 要求非空 url")
    void builderRequiresUrl() {
        assertThrows(IllegalArgumentException.class,
            () -> WebSocketMcpClientTransport.builder(""));
        assertThrows(IllegalArgumentException.class,
            () -> WebSocketMcpClientTransport.builder(null));
    }

    @Test
    @DisplayName("未连接时 isConnected 返回 false")
    void notConnectedInitially() {
        WebSocketMcpClientTransport transport =
            WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
        assertFalse(transport.isConnected());
    }

    @Test
    @DisplayName("未连接时 sendMessage 返回 error")
    void sendMessageBeforeConnectErrors() {
        WebSocketMcpClientTransport transport =
            WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
        McpSchema.JSONRPCMessage msg =
            new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, "ping", null);

        assertThrows(IllegalStateException.class,
            () -> transport.sendMessage(msg).block(Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("unmarshalFrom 委托 jsonMapper 转换")
    void unmarshalFromConverts() {
        WebSocketMcpClientTransport transport =
            WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();

        Map<String, Object> data = Map.of("progressToken", "tok-1", "progress", 0.5);
        Map<String, Object> converted =
            transport.unmarshalFrom(data, new TypeRef<Map<String, Object>>() {});

        assertEquals("tok-1", converted.get("progressToken"));
    }

    @Test
    @DisplayName("connect 后未连接的 transport 不会阻塞 sendMessage 的 lazy 求值")
    void sendMessageIsLazy() {
        WebSocketMcpClientTransport transport =
            WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
        McpSchema.JSONRPCMessage msg =
            new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, "ping", null);
        // 仅构建 Mono 不订阅，不应抛错
        Mono<Void> mono = transport.sendMessage(msg);
        assertTrue(mono != null);
    }
}
