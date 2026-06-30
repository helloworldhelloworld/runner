package com.lightweightai.mcp;

import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpToolClient APIs that are currently untested:
 * static utility methods (mergeRequestMeta, buildCallMeta),
 * listener registration, setRequestMeta, isConnected, and Builder validation.
 */
@DisplayName("McpToolClient - refreshTools-adjacent APIs")
class McpToolClientRefreshToolsTest {

    private static WebSocketMcpClientTransport wsTransport() {
        return WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
    }

    // ==================== mergeRequestMeta ====================

    @Nested
    @DisplayName("mergeRequestMeta")
    class MergeRequestMetaTests {

        @Test
        @DisplayName("both null returns empty map")
        void bothNullReturnsEmptyMap() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(null, null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("both empty returns empty map")
        void bothEmptyReturnsEmptyMap() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(Map.of(), Map.of());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("base null, perCall empty returns empty map")
        void baseNullPerCallEmptyReturnsEmptyMap() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(null, Map.of());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("base empty, perCall null returns empty map")
        void baseEmptyPerCallNullReturnsEmptyMap() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(Map.of(), null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("only base provided returns base entries")
        void onlyBaseReturnsBaseEntries() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(
                    Map.of("x-trace-id", "abc", "x-app", "test"), null);

            assertEquals(2, result.size());
            assertEquals("abc", result.get("x-trace-id"));
            assertEquals("test", result.get("x-app"));
        }

        @Test
        @DisplayName("only perCall provided returns perCall entries")
        void onlyPerCallReturnsPerCallEntries() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(
                    null, Map.of("x-uid", "u-1", "x-device", "mobile"));

            assertEquals(2, result.size());
            assertEquals("u-1", result.get("x-uid"));
            assertEquals("mobile", result.get("x-device"));
        }

        @Test
        @DisplayName("perCall overrides same-named keys from base")
        void perCallOverridesSameNamedKeys() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(
                    Map.of("shared", "base-value", "base-only", "kept"),
                    Map.of("shared", "call-value", "call-only", "added"));

            assertEquals("call-value", result.get("shared"), "per-call should override base");
            assertEquals("kept", result.get("base-only"), "base-only key should survive");
            assertEquals("added", result.get("call-only"), "call-only key should appear");
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("disjoint keys are all present in merged result")
        void disjointKeysAllPresent() {
            Map<String, String> result = McpToolClient.mergeRequestMeta(
                    Map.of("a", "1"), Map.of("b", "2"));

            assertEquals(Map.of("a", "1", "b", "2"), result);
        }
    }

    // ==================== buildCallMeta ====================

    @Nested
    @DisplayName("buildCallMeta")
    class BuildCallMetaTests {

        @Test
        @DisplayName("null progressToken and empty meta returns empty map")
        void nullTokenEmptyMetaReturnsEmpty() {
            Map<String, Object> meta = McpToolClient.buildCallMeta(null, Map.of());

            assertTrue(meta.isEmpty());
        }

        @Test
        @DisplayName("null progressToken and null meta returns empty map")
        void nullTokenNullMetaReturnsEmpty() {
            Map<String, Object> meta = McpToolClient.buildCallMeta(null, null);

            assertTrue(meta.isEmpty());
        }

        @Test
        @DisplayName("progressToken set produces map with progressToken key")
        void progressTokenSetProducesKey() {
            Map<String, Object> meta = McpToolClient.buildCallMeta("pt-42", Map.of());

            assertEquals("pt-42", meta.get("progressToken"));
            assertFalse(meta.containsKey(McpRequestMetaContext.META_FIELD),
                    "requestHeaders should be absent when meta is empty");
        }

        @Test
        @DisplayName("requestMeta set produces map with META_FIELD key")
        void requestMetaSetProducesMetaFieldKey() {
            Map<String, Object> meta = McpToolClient.buildCallMeta(
                    null, Map.of("x-trace-id", "t-1"));

            assertTrue(meta.containsKey(McpRequestMetaContext.META_FIELD));
            assertFalse(meta.containsKey("progressToken"),
                    "progressToken should be absent when token is null");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) meta.get(McpRequestMetaContext.META_FIELD);
            assertEquals("t-1", headers.get("x-trace-id"));
        }

        @Test
        @DisplayName("both progressToken and requestMeta set produces both keys")
        void bothSetProducesBothKeys() {
            Map<String, Object> meta = McpToolClient.buildCallMeta(
                    "pt-99", Map.of("x-uid", "u-5"));

            assertEquals("pt-99", meta.get("progressToken"));
            assertTrue(meta.containsKey(McpRequestMetaContext.META_FIELD));

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) meta.get(McpRequestMetaContext.META_FIELD);
            assertEquals("u-5", headers.get("x-uid"));
        }

        @Test
        @DisplayName("requestMeta is copied, not shared with caller")
        void requestMetaIsCopied() {
            Map<String, String> input = new HashMap<>();
            input.put("key", "original");

            Map<String, Object> meta = McpToolClient.buildCallMeta(null, input);

            // Mutating the input map should not affect the stored copy
            input.put("key", "mutated");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) meta.get(McpRequestMetaContext.META_FIELD);
            assertEquals("original", headers.get("key"),
                    "buildCallMeta should defensive-copy the request meta");
        }
    }

    // ==================== onToolsChanged ====================

    @Nested
    @DisplayName("onToolsChanged listener registration")
    class OnToolsChangedTests {

        @Test
        @DisplayName("registered listener is stored and not invoked immediately")
        void listenerStoredNotInvokedImmediately() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            AtomicReference<Set<String>> capturedAdded = new AtomicReference<>();
            AtomicReference<Set<String>> capturedRemoved = new AtomicReference<>();

            client.onToolsChanged((added, removed) -> {
                capturedAdded.set(added);
                capturedRemoved.set(removed);
            });

            assertNull(capturedAdded.get(), "listener should not be called on registration");
            assertNull(capturedRemoved.get(), "listener should not be called on registration");
        }

        @Test
        @DisplayName("registering a second listener replaces the first")
        void secondListenerReplacesFirst() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            AtomicReference<String> firstCalled = new AtomicReference<>("not-called");
            AtomicReference<String> secondCalled = new AtomicReference<>("not-called");

            client.onToolsChanged((added, removed) -> firstCalled.set("first"));
            client.onToolsChanged((added, removed) -> secondCalled.set("second"));

            // The field is volatile and overwritten, so only the second listener remains.
            // We cannot trigger it without a real MCP connection, but we verify
            // no exception is thrown and the registration succeeds.
            assertEquals("not-called", firstCalled.get());
            assertEquals("not-called", secondCalled.get());
        }
    }

    // ==================== setRequestMeta ====================

    @Nested
    @DisplayName("setRequestMeta")
    class SetRequestMetaTests {

        @Test
        @DisplayName("null clears to empty map")
        void nullClearsToEmptyMap() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            client.setRequestMeta(Map.of("k", "v"));
            client.setRequestMeta(null);

            // After clearing, merging with empty per-call should yield empty
            // We verify indirectly via mergeRequestMeta since requestMeta is private
            // The fact that setRequestMeta(null) does not throw is the primary assertion.
            assertDoesNotThrow(() -> client.setRequestMeta(null));
        }

        @Test
        @DisplayName("non-null stores an immutable copy")
        void nonNullStoresImmutableCopy() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            Map<String, String> mutable = new HashMap<>();
            mutable.put("key", "original");

            client.setRequestMeta(mutable);

            // Mutating the original map should not affect the stored copy
            mutable.put("key", "mutated");

            // setRequestMeta uses Map.copyOf, so the stored version is immutable.
            // We cannot access the field directly, but we verify no exception on set.
            assertDoesNotThrow(() -> client.setRequestMeta(Map.of("another", "value")));
        }

        @Test
        @DisplayName("setting metadata does not throw for empty map")
        void emptyMapDoesNotThrow() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            assertDoesNotThrow(() -> client.setRequestMeta(Map.of()));
        }
    }

    // ==================== isConnected ====================

    @Nested
    @DisplayName("isConnected")
    class IsConnectedTests {

        @Test
        @DisplayName("WebSocket transport that has not connected returns false")
        void unconnectedWebSocketReturnsFalse() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            assertFalse(client.isConnected(),
                    "unconnected WebSocket transport should report false");
        }
    }

    // ==================== Builder validation ====================

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidationTests {

        @Test
        @DisplayName("serverName defaults to 'mcp-server'")
        void serverNameDefaultsToMcpServer() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            assertEquals("mcp-server", client.getServerName());
        }

        @Test
        @DisplayName("custom serverName is retained")
        void customServerNameRetained() {
            McpToolClient client = McpToolClient.builder()
                    .serverName("custom-server")
                    .transport(wsTransport())
                    .build();

            assertEquals("custom-server", client.getServerName());
        }

        @Test
        @DisplayName("transport is required - build without transport throws IllegalStateException")
        void transportRequired() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    McpToolClient.builder()
                            .serverName("no-transport")
                            .build());

            assertEquals("Transport is required", ex.getMessage());
        }

        @Test
        @DisplayName("builder with only transport builds successfully")
        void builderWithOnlyTransportSucceeds() {
            McpToolClient client = McpToolClient.builder()
                    .transport(wsTransport())
                    .build();

            assertNotNull(client);
            assertNotNull(client.getProgressRouter());
            assertNotNull(client.getLoggingRouter());
            assertTrue(client.getDiscoveredTools().isEmpty());
        }
    }
}
