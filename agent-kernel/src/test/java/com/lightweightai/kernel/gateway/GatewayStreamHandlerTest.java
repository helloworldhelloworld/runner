package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GatewayStreamHandler - 流式响应处理器")
class GatewayStreamHandlerTest {

    @Test
    @DisplayName("simple 工厂方法创建 Lambda 处理器")
    void simpleFactoryMethod() {
        List<String> deltas = new ArrayList<>();
        AtomicReference<GatewayResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> errored = new AtomicReference<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            deltas::add,
            completed::set,
            errored::set
        );

        handler.onDelta("Hello");
        handler.onDelta(" World");
        assertEquals(List.of("Hello", " World"), deltas);

        GatewayResponse resp = GatewayResponse.builder()
            .requestId("r1")
            .sessionId("s1")
            .text("Hello World")
            .build();
        handler.onComplete(resp);
        assertEquals("Hello World", completed.get().getText());

        RuntimeException ex = new RuntimeException("fail");
        handler.onError(ex);
        assertEquals("fail", errored.get().getMessage());
    }

    @Test
    @DisplayName("onDelta(delta, metadata) 默认委托到 onDelta(delta)")
    void onDeltaWithMetadataDefaultDelegatesToPlain() {
        List<String> received = new ArrayList<>();

        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            received::add,
            r -> {},
            e -> {}
        );

        handler.onDelta("test", Map.of("emotion", "happy"));
        assertEquals(List.of("test"), received);
    }

    @Test
    @DisplayName("工具事件回调默认为空操作（不抛异常）")
    void toolEventCallbacksDefaultToNoOp() {
        GatewayStreamHandler handler = GatewayStreamHandler.simple(
            d -> {}, r -> {}, e -> {}
        );

        // All default tool event methods should not throw
        assertDoesNotThrow(() ->
            handler.onToolCallStart(new ToolCall("c1", "search", Map.of())));

        assertDoesNotThrow(() ->
            handler.onToolProgress(ToolResultChunk.progress("tool", "msg", 1, 2)));

        assertDoesNotThrow(() ->
            handler.onToolLog(ToolResultChunk.log("tool", "INFO", "msg")));

        assertDoesNotThrow(() ->
            handler.onToolResult(ToolResultChunk.complete("tool",
                com.lightweightai.kernel.llm.ToolResult.success("ok"))));

        assertDoesNotThrow(() ->
            handler.onToolError(ToolResultChunk.error("tool", "err")));

        assertDoesNotThrow(() ->
            handler.onPostProcessData("card", Map.of("type", "weather")));
    }

    @Test
    @DisplayName("自定义实现可覆盖工具回调")
    void customImplementationCanOverrideToolCallbacks() {
        List<String> toolCalls = new ArrayList<>();
        List<String> results = new ArrayList<>();

        GatewayStreamHandler handler = new GatewayStreamHandler() {
            @Override
            public void onDelta(String delta) {}

            @Override
            public void onComplete(GatewayResponse response) {}

            @Override
            public void onError(Throwable error) {}

            @Override
            public void onToolCallStart(ToolCall toolCall) {
                toolCalls.add(toolCall.getName());
            }

            @Override
            public void onToolResult(ToolResultChunk chunk) {
                results.add(chunk.getResult().getContent());
            }
        };

        handler.onToolCallStart(new ToolCall("c1", "search", Map.of()));
        handler.onToolResult(ToolResultChunk.complete("search",
            com.lightweightai.kernel.llm.ToolResult.success("found 3 results")));

        assertEquals(List.of("search"), toolCalls);
        assertEquals(List.of("found 3 results"), results);
    }
}
