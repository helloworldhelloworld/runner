package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpToolClient - refresh、close lifecycle、toolsChanged listener")
class McpToolClientRefreshAndLifecycleTest {

    private static WebSocketMcpClientTransport transport() {
        return WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
    }

    @Test
    @DisplayName("close 后再次 close 不抛异常(幂等)")
    void closeIsIdempotent() {
        McpToolClient client = McpToolClient.builder()
                .serverName("idempotent")
                .transport(transport())
                .build();

        assertDoesNotThrow(() -> {
            client.close();
            client.close();
        });
    }

    @Test
    @DisplayName("getDiscoveredTools 在未调用 discover 前返回空列表")
    void discoveredToolsEmptyBeforeDiscovery() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertTrue(client.getDiscoveredTools().isEmpty());
    }

    @Test
    @DisplayName("getAsyncClient 返回有效的 McpAsyncClient 实例")
    void getAsyncClientNonNull() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertNotNull(client.getAsyncClient());
    }

    @Test
    @DisplayName("onToolsChanged 注册的 listener 可被设置")
    void toolsChangedListenerIsSettable() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        AtomicReference<Set<String>> addedCapture = new AtomicReference<>();
        AtomicReference<Set<String>> removedCapture = new AtomicReference<>();

        client.onToolsChanged((added, removed) -> {
            addedCapture.set(added);
            removedCapture.set(removed);
        });

        assertNull(addedCapture.get(), "listener 注册后不应立即被调用");
    }

    @Test
    @DisplayName("setRequestMeta 接受 non-null map 不抛异常")
    void setRequestMetaWithValues() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertDoesNotThrow(() -> client.setRequestMeta(java.util.Map.of("key", "value")));
    }

    @Test
    @DisplayName("setRequestMeta(null) 清空元数据不抛异常")
    void setRequestMetaNull() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        client.setRequestMeta(java.util.Map.of("key", "value"));
        assertDoesNotThrow(() -> client.setRequestMeta(null));
    }

    @Test
    @DisplayName("isConnected: WebSocket transport 未连接返回 false")
    void isConnectedWebSocketFalse() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("builder 不设 samplingHandler 时 sampling capability 为 null")
    void noSamplingHandlerNoCapability() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertNull(client.getAsyncClient().getClientCapabilities().sampling(),
                "未设 samplingHandler 时不应声明 sampling capability");
    }

    @Test
    @DisplayName("getProgressRouter 和 getLoggingRouter 不为 null")
    void routersNonNull() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertNotNull(client.getProgressRouter());
        assertNotNull(client.getLoggingRouter());
    }
}
