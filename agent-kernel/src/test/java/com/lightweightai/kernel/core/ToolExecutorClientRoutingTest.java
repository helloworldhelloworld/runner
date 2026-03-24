package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutor 客户端工具路由和禁用工具测试
 *
 * 覆盖关键路径：
 * - 客户端工具路由（isClientSide + ClientToolDispatcher）
 * - 客户端工具超时
 * - 禁用工具的处理
 * - Reactive 路径中禁用工具的处理
 */
@DisplayName("ToolExecutor - 客户端路由与禁用工具")
class ToolExecutorClientRoutingTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        executor = new ToolExecutor(toolRegistry);
    }

    // ==================== 客户端工具路由测试 ====================

    @Test
    @DisplayName("客户端工具通过 ClientDispatcher 派发")
    void shouldDispatchClientToolViaDispatcher() {
        // 注册客户端工具
        toolRegistry.register(createClientSideTool("camera"));

        // 创建 mock dispatcher
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        future.complete(ToolResult.success("photo_data"));
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> future;

        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
        ToolCall call = new ToolCall("tc1", "camera", Map.of("mode", "photo"));

        ToolResult result = executor.executeToolCall(call, ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("photo_data"));
        assertEquals("tc1", result.getToolUseId());
    }

    @Test
    @DisplayName("非客户端工具即使有 dispatcher 也走服务端执行")
    void shouldExecuteServerSideToolNormally() {
        toolRegistry.register(createServerTool("calc"));

        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            fail("Should not dispatch server-side tool to client");
            return null;
        };

        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
        ToolCall call = new ToolCall("tc1", "calc", Map.of("input", "1+1"));

        ToolResult result = executor.executeToolCall(call, ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("calc"));
    }

    @Test
    @DisplayName("客户端工具超时返回错误")
    void shouldHandleClientToolTimeout() {
        toolRegistry.register(createClientSideTool("gps"));

        // 永远不会完成的 future
        CompletableFuture<ToolResult> neverComplete = new CompletableFuture<>();
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> neverComplete;

        // 使用很短的超时
        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 100);
        ToolCall call = new ToolCall("tc1", "gps", Map.of());

        ToolResult result = executor.executeToolCall(call, ctx);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("timed out"));
    }

    @Test
    @DisplayName("客户端 dispatcher 异常返回错误")
    void shouldHandleClientDispatcherException() {
        toolRegistry.register(createClientSideTool("sign"));

        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
            throw new RuntimeException("Connection lost");
        };

        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
        ToolCall call = new ToolCall("tc1", "sign", Map.of());

        ToolResult result = executor.executeToolCall(call, ctx);

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("failed"));
    }

    // ==================== 禁用工具测试 ====================

    @Test
    @DisplayName("禁用的工具不执行，fallback 到 legacy")
    void shouldNotExecuteDisabledTool() {
        toolRegistry.register(createServerTool("weather"));
        toolRegistry.disable("weather");

        ToolCall call = new ToolCall("tc1", "weather", Map.of());
        ToolResult result = executor.executeToolCall(call);

        // Tool is disabled so it won't be found in registry, falls through to legacy
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("Tool not found"));
    }

    @Test
    @DisplayName("Reactive 路径 - 禁用工具返回错误 chunk")
    void reactiveDisabledToolReturnsError() {
        toolRegistry.register(createServerTool("weather"));
        toolRegistry.disable("weather");

        ToolCall call = new ToolCall("tc1", "weather", Map.of());

        StepVerifier.create(executor.executeToolCallReactive(call))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                assertTrue(chunk.getMessage().contains("Tool not found"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Reactive 路径 - 启用后的工具可正常执行")
    void reactiveEnabledToolExecutes() {
        toolRegistry.register(createServerTool("weather"));
        toolRegistry.disable("weather");
        toolRegistry.enable("weather");

        ToolCall call = new ToolCall("tc1", "weather", Map.of("input", "test"));

        StepVerifier.create(executor.executeToolCallReactive(call))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertEquals("tc1", chunk.getToolCallId());
            })
            .verifyComplete();
    }

    // ==================== 批量执行 with context ====================

    @Test
    @DisplayName("带 context 的批量执行 - 混合客户端和服务端工具")
    void shouldHandleMixedToolBatch() {
        toolRegistry.register(createClientSideTool("camera"));
        toolRegistry.register(createServerTool("calc"));

        CompletableFuture<ToolResult> clientResult = CompletableFuture.completedFuture(
            ToolResult.success("photo"));
        ClientToolDispatcher dispatcher = (callId, toolName, directive) -> clientResult;
        ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);

        List<ToolResult> results = executor.executeToolCalls(List.of(
            new ToolCall("tc1", "camera", Map.of()),
            new ToolCall("tc2", "calc", Map.of("input", "x"))
        ), ctx);

        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    @Test
    @DisplayName("带 context 的异步批量执行 - 空列表")
    void asyncEmptyListWithContext() throws Exception {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
        List<ToolResult> results = executor.executeToolCallsAsync(List.of(), ctx).get();
        assertTrue(results.isEmpty());
    }

    // ==================== 辅助方法 ====================

    private Tool createServerTool(String name) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return "Server tool " + name; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("Result from " + name + ": " + args);
            }
        };
    }

    private Tool createClientSideTool(String name) {
        return new ClientTool(name);
    }

    /**
     * 实现 ToolMetadata.isClientSide() 的客户端工具
     */
    private static class ClientTool implements Tool, ToolMetadata {
        private final String name;
        ClientTool(String name) { this.name = name; }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "Client tool " + name; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("server-fallback");
        }
        @Override public boolean isClientSide() { return true; }
    }
}
