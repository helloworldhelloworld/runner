package com.lightweightai.mcp;

import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolClient Builder、配置、静态工具方法的全面测试。
 *
 * 不建立实际连接——仅验证构建逻辑、元数据合并、连接状态判定等可本地测试的行为。
 */
@DisplayName("McpToolClient - Builder, config & utility methods")
class McpToolClientBuilderTest {

    private static WebSocketMcpClientTransport transport() {
        return WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
    }

    // ==================== Builder ====================

    @Test
    @DisplayName("Builder with transport succeeds and retains serverName")
    void builderWithTransportSucceeds() {
        McpToolClient client = McpToolClient.builder()
                .serverName("my-server")
                .transport(transport())
                .build();

        assertEquals("my-server", client.getServerName());
        assertNotNull(client.getProgressRouter());
        assertNotNull(client.getLoggingRouter());
    }

    @Test
    @DisplayName("Builder default serverName is 'mcp-server'")
    void builderDefaultServerName() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertEquals("mcp-server", client.getServerName());
    }

    @Test
    @DisplayName("Builder with custom timeout compiles (no runtime error)")
    void builderWithCustomTimeout() {
        McpToolClient client = McpToolClient.builder()
                .serverName("timeout-test")
                .transport(transport())
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Builder with sampling handler declares sampling capability")
    void builderWithSamplingHandler() {
        McpToolClient client = McpToolClient.builder()
                .serverName("sampling-test")
                .transport(transport())
                .samplingHandler(req -> McpSchema.CreateMessageResult.builder()
                        .role(McpSchema.Role.ASSISTANT)
                        .content(new McpSchema.TextContent("echo"))
                        .model("test-model")
                        .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                        .build())
                .build();

        assertNotNull(client.getAsyncClient().getClientCapabilities());
        assertNotNull(client.getAsyncClient().getClientCapabilities().sampling(),
                "sampling capability must be declared when handler is present");
    }

    // ==================== getDiscoveredTools before discover ====================

    @Test
    @DisplayName("getDiscoveredTools returns empty list before discovery")
    void discoveredToolsEmptyBeforeDiscover() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertTrue(client.getDiscoveredTools().isEmpty());
    }

    // ==================== isConnected ====================

    @Test
    @DisplayName("isConnected returns false for unconnected WebSocket transport")
    void isConnectedFalseForUnconnectedWebSocket() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertFalse(client.isConnected(),
                "WebSocket transport not yet connected should report false");
    }

    // ==================== setRequestMeta ====================

    @Test
    @DisplayName("setRequestMeta(null) clears to empty map — subsequent merge should be no-op")
    void setRequestMetaNullClearsToEmpty() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        client.setRequestMeta(Map.of("k", "v"));
        client.setRequestMeta(null);
        Map<String, String> merged = McpToolClient.mergeRequestMeta(Map.of(), Map.of());
        assertTrue(merged.isEmpty());
    }

    // ==================== mergeRequestMeta (static) ====================

    @Test
    @DisplayName("mergeRequestMeta: both null returns empty map")
    void mergeBothNullReturnsEmpty() {
        assertTrue(McpToolClient.mergeRequestMeta(null, null).isEmpty());
    }

    @Test
    @DisplayName("mergeRequestMeta: base null, perCall has values")
    void mergeBaseNullPerCallHasValues() {
        Map<String, String> result = McpToolClient.mergeRequestMeta(null, Map.of("a", "1"));
        assertEquals(Map.of("a", "1"), result);
    }

    @Test
    @DisplayName("mergeRequestMeta: base has values, perCall null")
    void mergeBaseHasValuesPerCallNull() {
        Map<String, String> result = McpToolClient.mergeRequestMeta(Map.of("a", "1"), null);
        assertEquals(Map.of("a", "1"), result);
    }

    @Test
    @DisplayName("mergeRequestMeta: perCall overrides base for same key")
    void mergePerCallOverridesBase() {
        Map<String, String> result = McpToolClient.mergeRequestMeta(
                Map.of("key", "base-val", "other", "keep"),
                Map.of("key", "call-val", "new", "added"));

        assertEquals("call-val", result.get("key"));
        assertEquals("keep", result.get("other"));
        assertEquals("added", result.get("new"));
    }

    // ==================== buildCallMeta (static) ====================

    @Test
    @DisplayName("buildCallMeta: both null/empty produces empty meta")
    void buildCallMetaBothEmpty() {
        Map<String, Object> meta = McpToolClient.buildCallMeta(null, Map.of());
        assertFalse(meta.containsKey("progressToken"));
        assertFalse(meta.containsKey(McpRequestMetaContext.META_FIELD));
    }

    @Test
    @DisplayName("buildCallMeta: only progressToken")
    void buildCallMetaOnlyProgressToken() {
        Map<String, Object> meta = McpToolClient.buildCallMeta("pt-1", Map.of());
        assertEquals("pt-1", meta.get("progressToken"));
        assertFalse(meta.containsKey(McpRequestMetaContext.META_FIELD));
    }

    @Test
    @DisplayName("buildCallMeta: only requestMeta")
    void buildCallMetaOnlyRequestMeta() {
        Map<String, Object> meta = McpToolClient.buildCallMeta(null, Map.of("h1", "v1"));
        assertNull(meta.get("progressToken"));
        assertTrue(meta.containsKey(McpRequestMetaContext.META_FIELD));
    }

    // ==================== onToolsChanged listener ====================

    @Test
    @DisplayName("onToolsChanged listener is registerable")
    void toolsChangedListenerIsRegisterable() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        AtomicReference<Set<String>> capturedAdded = new AtomicReference<>();
        AtomicReference<Set<String>> capturedRemoved = new AtomicReference<>();

        client.onToolsChanged((added, removed) -> {
            capturedAdded.set(added);
            capturedRemoved.set(removed);
        });

        assertNull(capturedAdded.get());
    }

    // ==================== close ====================

    @Test
    @DisplayName("close on freshly built client does not throw")
    void closeOnFreshClientDoesNotThrow() {
        McpToolClient client = McpToolClient.builder()
                .transport(transport())
                .build();

        assertDoesNotThrow(client::close);
    }
}
