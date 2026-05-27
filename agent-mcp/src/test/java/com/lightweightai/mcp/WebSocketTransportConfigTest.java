package com.lightweightai.mcp;

import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebSocket transport 配置与工厂")
class WebSocketTransportConfigTest {

    @Test
    @DisplayName("ServerConfig.ws() 设置 transport=ws 且 WebSocket 字段有默认值")
    void wsFactoryAndDefaults() {
        McpConfiguration.ServerConfig config =
            McpConfiguration.ServerConfig.ws("ws://host:6646/mcp");

        assertEquals("ws", config.getTransport());
        assertEquals("ws://host:6646/mcp", config.getUrl());
        assertEquals(30, config.getPingIntervalSeconds());
        assertEquals(10, config.getMaxReconnectAttempts());
        assertEquals(1000, config.getReconnectBaseDelayMs());
        assertEquals(300, config.getToolRefreshIntervalSeconds());
    }

    @Test
    @DisplayName("createTransport 为 ws/websocket 返回 WebSocketMcpClientTransport")
    void createTransportReturnsWebSocket() {
        McpConfiguration.ServerConfig ws = McpConfiguration.ServerConfig.ws("ws://host:6646/mcp");
        McpClientTransport t1 = ToolClient.Builder.createTransport("s1", ws, null);
        assertInstanceOf(WebSocketMcpClientTransport.class, t1);

        McpConfiguration.ServerConfig wss = new McpConfiguration.ServerConfig();
        wss.setTransport("websocket");
        wss.setUrl("https://host:6646/mcp"); // http(s) 自动转换为 ws(s)
        McpClientTransport t2 = ToolClient.Builder.createTransport("s2", wss, null);
        assertInstanceOf(WebSocketMcpClientTransport.class, t2);
    }

    @Test
    @DisplayName("ws transport 缺少 url 抛异常")
    void wsRequiresUrl() {
        McpConfiguration.ServerConfig config = new McpConfiguration.ServerConfig();
        config.setTransport("ws");
        config.setUrl("");
        assertThrows(IllegalStateException.class,
            () -> ToolClient.Builder.createTransport("s", config, null));
    }

    @Test
    @DisplayName("已有 streamable_http / stdio 行为不受影响")
    void existingTransportsUnaffected() {
        McpConfiguration.ServerConfig http =
            McpConfiguration.ServerConfig.streamableHttp("http://host:8080/mcp");
        assertTrue(ToolClient.Builder.createTransport("h", http, null) != null);

        McpConfiguration.ServerConfig stdio =
            McpConfiguration.ServerConfig.stdio("echo", "hi");
        assertTrue(ToolClient.Builder.createTransport("s", stdio, null) != null);
    }
}
