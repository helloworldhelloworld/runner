package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GatewayStreamHandler}.
 *
 * Focuses on the interface's default methods and the {@code simple()} factory,
 * which are the executable parts a protocol adapter relies on.
 */
class GatewayStreamHandlerTest {

    @Test
    void simple_onDelta_delegatesToConsumer() {
        List<String> deltas = new ArrayList<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add,
            resp -> {},
            err -> {}
        );

        handler.onDelta("hello");
        handler.onDelta("world");

        assertEquals(List.of("hello", "world"), deltas);
    }

    @Test
    void simple_onComplete_delegatesToConsumer() {
        AtomicReference<GatewayResponse> captured = new AtomicReference<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {},
            captured::set,
            err -> {}
        );
        GatewayResponse resp = GatewayResponse.builder()
            .requestId("req-1")
            .sessionId("sess-1")
            .text("final text")
            .build();

        handler.onComplete(resp);

        assertSame(resp, captured.get());
    }

    @Test
    void simple_onError_delegatesToConsumer() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {},
            r -> {},
            captured::set
        );
        Throwable err = new RuntimeException("boom");

        handler.onError(err);

        assertSame(err, captured.get());
    }

    @Test
    void onDeltaWithMetadata_defaultDelegatesToOnDelta() {
        // Handler from simple() only implements the 1-arg form; the 2-arg default
        // must fall back to onDelta(delta).
        List<String> deltas = new ArrayList<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add, r -> {}, e -> {});

        handler.onDelta("hello", Map.of("emotion", "happy"));

        assertEquals(List.of("hello"), deltas);
    }

    @Test
    void toolEventCallbacks_haveNoOpDefaults() {
        // An adapter that doesn't care about tool events should still be
        // safe to call — defaults are empty.
        GatewayStreamHandler handler = new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onComplete(GatewayResponse response) {}
            @Override public void onError(Throwable error) {}
        };

        // None of these should throw
        assertDoesNotThrow(() -> {
            handler.onToolCallStart(new ToolCall("id", "name", Map.of()));
            handler.onToolProgress(null);
            handler.onToolLog(null);
            handler.onToolResult(null);
            handler.onToolError(null);
            handler.onPostProcessData("cat", Map.of());
        });
    }

    @Test
    void customHandler_canOverrideToolEvents() {
        AtomicBoolean startCalled = new AtomicBoolean(false);
        AtomicReference<String> lastCategory = new AtomicReference<>();

        GatewayStreamHandler handler = new GatewayStreamHandler() {
            @Override public void onDelta(String delta) {}
            @Override public void onComplete(GatewayResponse response) {}
            @Override public void onError(Throwable error) {}

            @Override
            public void onToolCallStart(ToolCall toolCall) {
                startCalled.set(true);
            }

            @Override
            public void onPostProcessData(String category, Map<String, Object> data) {
                lastCategory.set(category);
            }
        };

        handler.onToolCallStart(new ToolCall("id-1", "tool-x", Map.of()));
        handler.onPostProcessData("cards", Map.of("k", "v"));

        assertTrue(startCalled.get());
        assertEquals("cards", lastCategory.get());
    }

    // ==================== SessionManager.SessionContext ====================

    @Test
    void sessionContext_simple_setsOnlyUserId() {
        SessionManager.SessionContext ctx = SessionManager.SessionContext.simple("user-1");

        assertEquals("user-1", ctx.getUserId());
        assertNull(ctx.getDeviceId());
        assertNull(ctx.getChannel());
        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    void sessionContext_full_retainsAllFields() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "user-2", "dev-99", "web", Map.of("k", "v"));

        assertEquals("user-2", ctx.getUserId());
        assertEquals("dev-99", ctx.getDeviceId());
        assertEquals("web", ctx.getChannel());
        assertEquals("v", ctx.getExtra().get("k"));
    }

    @Test
    void sessionContext_nullExtra_becomesEmptyMap() {
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "user", null, null, null);

        assertNotNull(ctx.getExtra());
        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    void sessionContext_extraIsDefensivelyCopied() {
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("key", "value");
        SessionManager.SessionContext ctx = new SessionManager.SessionContext(
            "u", "d", "c", source);

        // Mutating source must not affect the stored extras
        source.put("new-key", "new-value");

        assertEquals(1, ctx.getExtra().size());
        assertFalse(ctx.getExtra().containsKey("new-key"));
    }
}
