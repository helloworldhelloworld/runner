package com.lightweightai.kernel.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayStreamHandler - 流式响应处理器")
class GatewayStreamHandlerTest {

    @Test
    @DisplayName("simple() 工厂创建可用的 handler")
    void shouldCreateSimpleHandler() {
        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> errors = new AtomicReference<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add,
            completed::set,
            errors::set
        );

        handler.onDelta("hello");
        handler.onDelta(" world");

        assertEquals(2, deltas.size());
        assertEquals("hello", deltas.get(0));
        assertEquals(" world", deltas.get(1));
    }

    @Test
    @DisplayName("simple() handler 处理 onComplete")
    void shouldHandleComplete() {
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {}, completed::set, e -> {}
        );

        GatewayResponse response = GatewayResponse.builder()
            .text("full response")
            .build();
        handler.onComplete(response);

        assertNotNull(completed.get());
        assertEquals("full response", completed.get().getText());
    }

    @Test
    @DisplayName("simple() handler 处理 onError")
    void shouldHandleError() {
        AtomicReference<Throwable> errors = new AtomicReference<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {}, r -> {}, errors::set
        );

        RuntimeException ex = new RuntimeException("boom");
        handler.onError(ex);

        assertSame(ex, errors.get());
    }

    @Test
    @DisplayName("onDelta(delta, metadata) 默认实现委托 onDelta(delta)")
    void shouldDelegateMetadataDelta() {
        List<String> deltas = new ArrayList<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add, r -> {}, e -> {}
        );

        handler.onDelta("test", Map.of("emotion", "happy"));

        assertEquals(1, deltas.size());
        assertEquals("test", deltas.get(0));
    }

    @Test
    @DisplayName("默认工具事件回调为空操作")
    void shouldHaveNoOpDefaultToolCallbacks() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {}, r -> {}, e -> {}
        );

        // 所有默认回调都不应抛异常
        assertDoesNotThrow(() -> handler.onToolCallStart(null));
        assertDoesNotThrow(() -> handler.onToolProgress(null));
        assertDoesNotThrow(() -> handler.onToolLog(null));
        assertDoesNotThrow(() -> handler.onToolResult(null));
        assertDoesNotThrow(() -> handler.onToolError(null));
        assertDoesNotThrow(() -> handler.onPostProcessData("test", Map.of()));
    }
}
