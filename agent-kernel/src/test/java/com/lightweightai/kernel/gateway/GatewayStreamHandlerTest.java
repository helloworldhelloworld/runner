package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayStreamHandler - 流式响应处理器")
class GatewayStreamHandlerTest {

    // ==================== simple() Factory ====================

    @Test
    @DisplayName("simple 工厂方法创建 handler 并接收 delta")
    void shouldCreateSimpleHandlerAndReceiveDelta() {
        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> errored = new AtomicReference<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add,
            completed::set,
            errored::set
        );

        handler.onDelta("Hello ");
        handler.onDelta("World");

        assertEquals(2, deltas.size());
        assertEquals("Hello ", deltas.get(0));
        assertEquals("World", deltas.get(1));
    }

    @Test
    @DisplayName("simple handler onComplete 传递响应")
    void shouldReceiveCompleteResponse() {
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {},
            completed::set,
            e -> {}
        );

        GatewayResponse response = GatewayResponse.builder()
            .text("Done")
            .build();
        handler.onComplete(response);

        assertNotNull(completed.get());
        assertEquals("Done", completed.get().getText());
    }

    @Test
    @DisplayName("simple handler onError 传递异常")
    void shouldReceiveError() {
        AtomicReference<Throwable> errored = new AtomicReference<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {},
            r -> {},
            errored::set
        );

        handler.onError(new RuntimeException("network error"));

        assertNotNull(errored.get());
        assertEquals("network error", errored.get().getMessage());
    }

    // ==================== Default Method Behaviors ====================

    @Test
    @DisplayName("onDelta(delta, metadata) 默认委托到 onDelta(delta)")
    void shouldDelegateMetadataDeltaToSimpleDelta() {
        List<String> deltas = new ArrayList<>();
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add,
            r -> {},
            e -> {}
        );

        handler.onDelta("text", Map.of("emotion", "happy"));

        assertEquals(1, deltas.size());
        assertEquals("text", deltas.get(0));
    }

    @Test
    @DisplayName("onToolCallStart 默认空实现不抛异常")
    void shouldHaveNoOpToolCallStart() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});
        ToolCall call = new ToolCall("id", "tool", Map.of());

        assertDoesNotThrow(() -> handler.onToolCallStart(call));
    }

    @Test
    @DisplayName("onToolProgress 默认空实现不抛异常")
    void shouldHaveNoOpToolProgress() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});
        ToolResultChunk chunk = ToolResultChunk.progress("tool", "50%", 50, 100);

        assertDoesNotThrow(() -> handler.onToolProgress(chunk));
    }

    @Test
    @DisplayName("onToolLog 默认空实现不抛异常")
    void shouldHaveNoOpToolLog() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});
        ToolResultChunk chunk = ToolResultChunk.log("tool", "INFO", "msg");

        assertDoesNotThrow(() -> handler.onToolLog(chunk));
    }

    @Test
    @DisplayName("onToolResult 默认空实现不抛异常")
    void shouldHaveNoOpToolResult() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});
        ToolResultChunk chunk = ToolResultChunk.complete("tool", ToolResult.success("ok"));

        assertDoesNotThrow(() -> handler.onToolResult(chunk));
    }

    @Test
    @DisplayName("onToolError 默认空实现不抛异常")
    void shouldHaveNoOpToolError() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});
        ToolResultChunk chunk = ToolResultChunk.error("tool", "failed");

        assertDoesNotThrow(() -> handler.onToolError(chunk));
    }

    @Test
    @DisplayName("onPostProcessData 默认空实现不抛异常")
    void shouldHaveNoOpPostProcessData() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(d -> {}, r -> {}, e -> {});

        assertDoesNotThrow(() -> handler.onPostProcessData("card", Map.of("type", "weather")));
    }

    // ==================== Override ====================

    @Test
    @DisplayName("可以覆盖 onDelta(delta, metadata) 获取 metadata")
    void shouldAllowOverridingMetadataDelta() {
        List<Map<String, Object>> receivedMeta = new ArrayList<>();

        GatewayStreamHandler handler = new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {}

            @Override
            public void onDelta(String delta, Map<String, Object> metadata) {
                receivedMeta.add(metadata);
            }

            @Override
            public void onComplete(GatewayResponse response) {}

            @Override
            public void onError(Throwable error) {}
        };

        handler.onDelta("text", Map.of("confidence", 0.9));

        assertEquals(1, receivedMeta.size());
        assertEquals(0.9, receivedMeta.get(0).get("confidence"));
    }
}
