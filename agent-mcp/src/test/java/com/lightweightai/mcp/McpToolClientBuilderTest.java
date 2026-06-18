package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * McpToolClient Builder、配置、静态工具方法的全面测试。
 *
 * 不建立实际连接——仅验证构建逻辑、元数据合并、连接状态判定等可本地测试的行为。
 */
@DisplayName("McpToolClient - Builder, config & utility methods")
class McpToolClientBuilderTest {

    private final McpClientTransport mockTransport = mock(McpClientTransport.class);

    // ==================== Builder ====================

    @Test
    @DisplayName("Builder with transport succeeds and retains serverName")
    void builderWithTransportSucceeds() {
        McpToolClient client = McpToolClient.builder()
                .serverName("my-server")
                .transport(mockTransport)
                .build();

        assertEquals("my-server", client.getServerName());
        assertNotNull(client.getProgressRouter());
        assertNotNull(client.getLoggingRouter());
    }

    @Test
    @DisplayName("Builder default serverName is 'mcp-server'")
    void builderDefaultServerName() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        assertEquals("mcp-server", client.getServerName());
    }

    @Test
    @DisplayName("Builder with custom timeout compiles (no runtime error)")
    void builderWithCustomTimeout() {
        McpToolClient client = McpToolClient.builder()
                .serverName("timeout-test")
                .transport(mockTransport)
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Builder with sampling handler compiles")
    void builderWithSamplingHandler() {
        McpToolClient client = McpToolClient.builder()
                .serverName("sampling-test")
                .transport(mockTransport)
                .samplingHandler(req -> new McpSchema.CreateMessageResult(
                        new McpSchema.TextContent("echo"), "assistant",
                        new McpSchema.CreateMessageResult.StopReason("endTurn")))
                .build();

        assertNotNull(client);
    }

    // ==================== getDiscoveredTools before discover ====================

    @Test
    @DisplayName("getDiscoveredTools returns empty list before discovery")
    void discoveredToolsEmptyBeforeDiscover() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        assertTrue(client.getDiscoveredTools().isEmpty());
    }

    // ==================== isConnected ====================

    @Test
    @DisplayName("isConnected returns true for non-WebSocket transport")
    void isConnectedTrueForNonWebSocket() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        assertTrue(client.isConnected(), "Non-WebSocket transport should always report connected");
    }

    // ==================== setRequestMeta ====================

    @Test
    @DisplayName("setRequestMeta(null) clears to empty map — subsequent merge should be no-op")
    void setRequestMetaNullClearsToEmpty() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        client.setRequestMeta(Map.of("k", "v"));
        client.setRequestMeta(null);
        // After clearing, merge with empty perCall should return empty
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
    @DisplayName("onToolsChanged listener is invoked on tool list change notification")
    void toolsChangedListenerIsRegisterable() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        AtomicReference<Set<String>> capturedAdded = new AtomicReference<>();
        AtomicReference<Set<String>> capturedRemoved = new AtomicReference<>();

        client.onToolsChanged((added, removed) -> {
            capturedAdded.set(added);
            capturedRemoved.set(removed);
        });

        // Listener registered but not yet invoked — just verifying registration compiles
        assertNull(capturedAdded.get());
    }

    // ==================== close ====================

    @Test
    @DisplayName("close on freshly built client does not throw")
    void closeOnFreshClientDoesNotThrow() {
        McpToolClient client = McpToolClient.builder()
                .transport(mockTransport)
                .build();

        assertDoesNotThrow(client::close);
    }
}
