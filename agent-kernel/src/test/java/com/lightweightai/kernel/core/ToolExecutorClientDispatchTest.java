package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolExecutor - 客户端工具调度 & 设备路由 & Reactive 带 Context 测试
 *
 * 覆盖关键路径：
 * - 客户端工具识别 (isClientSide)
 * - 客户端调度 (executeOnClient) 成功/超时/中断/异常
 * - DispatchingTool 设备分发
 * - Reactive 执行带 Context
 * - 工具禁用状态检查
 */
@DisplayName("ToolExecutor - 客户端调度与设备路由")
class ToolExecutorClientDispatchTest {

    private ToolExecutor executor;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        executor = new ToolExecutor(registry);
    }

    // ==================== 客户端工具调度 ====================

    @Nested
    @DisplayName("客户端工具调度 (executeOnClient)")
    class ClientDispatchTests {

        @Test
        @DisplayName("客户端工具成功调度并返回结果")
        void shouldDispatchToClientAndReturnResult() {
            // 注册一个标记为 clientSide 的工具
            registry.register(createClientSideTool("camera", "拍照工具"));

            // 创建 mock dispatcher，立即返回成功
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.completedFuture(ToolResult.success("photo_base64_data"));

            ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
            ToolCall call = new ToolCall("tc1", "camera", Map.of("mode", "photo"));

            ToolResult result = executor.executeToolCall(call, ctx);

            assertFalse(result.isError());
            assertEquals("tc1", result.getToolUseId());
            assertTrue(result.getContent().contains("photo_base64_data"));
        }

        @Test
        @DisplayName("客户端工具超时返回错误")
        void shouldReturnErrorOnClientTimeout() {
            registry.register(createClientSideTool("gps", "GPS定位"));

            // 创建永不完成的 dispatcher
            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                new CompletableFuture<>(); // never completes

            ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 100); // 100ms timeout
            ToolCall call = new ToolCall("tc2", "gps", Map.of());

            ToolResult result = executor.executeToolCall(call, ctx);

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("timed out"));
        }

        @Test
        @DisplayName("客户端工具调度异常返回错误")
        void shouldReturnErrorOnClientException() {
            registry.register(createClientSideTool("sign", "签名工具"));

            ClientToolDispatcher dispatcher = (callId, toolName, directive) ->
                CompletableFuture.failedFuture(new RuntimeException("WebSocket closed"));

            ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
            ToolCall call = new ToolCall("tc3", "sign", Map.of());

            ToolResult result = executor.executeToolCall(call, ctx);

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("failed"));
        }

        @Test
        @DisplayName("非客户端工具即使有 dispatcher 也在服务端执行")
        void shouldExecuteServerSideToolLocally() {
            Tool serverTool = createServerTool("calc", "计算器");
            registry.register(serverTool);

            // dispatcher 不应被调用
            ClientToolDispatcher dispatcher = (callId, toolName, directive) -> {
                fail("Should not dispatch server-side tool to client");
                return null;
            };

            ToolExecutionContext ctx = new ToolExecutionContext(dispatcher, 5000);
            ToolCall call = new ToolCall("tc4", "calc", Map.of("input", "1+1"));

            ToolResult result = executor.executeToolCall(call, ctx);

            assertFalse(result.isError());
            assertEquals("tc4", result.getToolUseId());
        }

        @Test
        @DisplayName("无 dispatcher 时客户端工具在服务端执行")
        void shouldFallbackToServerWithoutDispatcher() {
            registry.register(createClientSideTool("camera", "拍照"));

            ToolExecutionContext ctx = ToolExecutionContext.serverOnly();
            ToolCall call = new ToolCall("tc5", "camera", Map.of());

            // hasClientDispatcher() == false，走正常服务端执行
            ToolResult result = executor.executeToolCall(call, ctx);

            assertFalse(result.isError());
        }
    }

    // ==================== DispatchingTool 设备分发 ====================

    @Nested
    @DisplayName("DispatchingTool 设备分发")
    class DeviceDispatchTests {

        @Test
        @DisplayName("通过 DeviceContext 解析到正确的设备工具实现")
        void shouldResolveToDeviceSpecificTool() {
            // 创建 DispatchingTool 并注册设备绑定
            Tool phoneTool = createServerTool("phone_gps", "Phone GPS");
            Tool carTool = createServerTool("car_gps", "Car GPS");

            DispatchingTool dispatchingTool = new DispatchingTool("get_position");
            dispatchingTool.addBinding(new DeviceToolBinding("phone", VersionRange.all(), phoneTool, 10));
            dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool, 10));
            registry.register(dispatchingTool);

            // 带 phone 设备上下文执行
            DeviceContext phoneContext = DeviceContext.of("phone", "1.0.0");
            ToolExecutionContext ctx = new ToolExecutionContext(null, 0, phoneContext);
            ToolCall call = new ToolCall("tc1", "get_position", Map.of());

            ToolResult result = executor.executeToolCall(call, ctx);

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("phone_gps"),
                "Should resolve to phone tool");
        }

        @Test
        @DisplayName("DeviceContext.DEFAULT 使用默认工具")
        void shouldUseDefaultToolForDefaultContext() {
            Tool defaultTool = createServerTool("default_gps", "Default GPS");
            DispatchingTool dispatchingTool = new DispatchingTool("get_position", defaultTool);
            registry.register(dispatchingTool);

            ToolCall call = new ToolCall("tc2", "get_position", Map.of());

            // 无 context 使用默认路径
            ToolResult result = executor.executeToolCall(call);
            assertFalse(result.isError());
        }

        @Test
        @DisplayName("无匹配的设备类型回退到 default")
        void shouldFallbackToDefaultWhenNoDeviceMatch() {
            Tool defaultTool = createServerTool("default_gps", "Default GPS");
            Tool phoneTool = createServerTool("phone_gps", "Phone GPS");

            DispatchingTool dispatchingTool = new DispatchingTool("get_position", defaultTool);
            dispatchingTool.addBinding(new DeviceToolBinding("phone", VersionRange.all(), phoneTool, 10));
            registry.register(dispatchingTool);

            // 使用 car 上下文，无绑定，应 fallback 到 default
            DeviceContext carContext = DeviceContext.of("car", "1.0.0");
            ToolExecutionContext ctx = new ToolExecutionContext(null, 0, carContext);
            ToolCall call = new ToolCall("tc3", "get_position", Map.of());

            ToolResult result = executor.executeToolCall(call, ctx);

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("default_gps"));
        }
    }

    // ==================== Reactive 执行带 Context ====================

    @Nested
    @DisplayName("Reactive 执行带 Context")
    class ReactiveWithContextTests {

        @Test
        @DisplayName("单工具 reactive 执行带 context")
        void shouldExecuteReactiveWithContext() {
            registry.register(createServerTool("t1", "Tool 1"));
            ToolCall call = new ToolCall("tc1", "t1", Map.of("input", "rx"));

            StepVerifier.create(executor.executeToolCallReactive(call, ToolExecutionContext.serverOnly()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("tc1", chunk.getToolCallId());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("多工具 reactive 并行执行带 context")
        void shouldExecuteMultipleReactiveWithContext() {
            registry.register(createServerTool("t1", "Tool 1"));
            registry.register(createServerTool("t2", "Tool 2"));

            List<ToolCall> calls = List.of(
                new ToolCall("a", "t1", Map.of("input", "x")),
                new ToolCall("b", "t2", Map.of("input", "y"))
            );

            StepVerifier.create(executor.executeToolCallsReactive(calls, ToolExecutionContext.serverOnly()))
                .expectNextCount(2)
                .verifyComplete();
        }

        @Test
        @DisplayName("reactive 带 context 工具不存在返回 ERROR chunk")
        void reactiveWithContextShouldReturnErrorForMissingTool() {
            ToolCall call = new ToolCall("tc1", "missing", Map.of());

            StepVerifier.create(executor.executeToolCallReactive(call, ToolExecutionContext.serverOnly()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("Tool not found"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("reactive 带 context null ToolCall 返回错误流")
        void reactiveWithContextShouldErrorOnNull() {
            StepVerifier.create(executor.executeToolCallReactive(null, ToolExecutionContext.serverOnly()))
                .verifyError(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reactive 带 context 空列表返回空流")
        void reactiveWithContextEmptyList() {
            StepVerifier.create(executor.executeToolCallsReactive(null, ToolExecutionContext.serverOnly()))
                .verifyComplete();
        }

        @Test
        @DisplayName("DispatchingTool 通过 reactive 带 context 正确分发")
        void reactiveWithContextShouldResolveDispatchingTool() {
            Tool phoneTool = createServerTool("phone_impl", "Phone impl");
            DispatchingTool dt = new DispatchingTool("get_loc", phoneTool);
            registry.register(dt);

            DeviceContext phoneCtx = DeviceContext.of("phone", "1.0.0");
            ToolExecutionContext ctx = new ToolExecutionContext(null, 0, phoneCtx);
            ToolCall call = new ToolCall("tc1", "get_loc", Map.of());

            StepVerifier.create(executor.executeToolCallReactive(call, ctx))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                    assertEquals("tc1", chunk.getToolCallId());
                })
                .verifyComplete();
        }
    }

    // ==================== 工具禁用状态 ====================

    @Nested
    @DisplayName("工具禁用状态检查")
    class ToolDisabledTests {

        @Test
        @DisplayName("禁用的工具同步执行返回 not found")
        void disabledToolShouldReturnNotFound() {
            registry.register(createServerTool("t1", "Tool 1"));
            registry.disable("t1");

            ToolCall call = new ToolCall("tc1", "t1", Map.of());

            // 无 context 路径
            ToolResult result = executor.executeToolCall(call);
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Tool not found"));
        }

        @Test
        @DisplayName("禁用的工具 reactive 执行返回 ERROR chunk")
        void disabledToolReactiveShouldReturnError() {
            registry.register(createServerTool("t1", "Tool 1"));
            registry.disable("t1");

            ToolCall call = new ToolCall("tc1", "t1", Map.of());

            StepVerifier.create(executor.executeToolCallReactive(call))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                    assertTrue(chunk.getMessage().contains("Tool not found"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("禁用的工具带 context reactive 执行返回 ERROR chunk")
        void disabledToolReactiveWithContextShouldReturnError() {
            registry.register(createServerTool("t1", "Tool 1"));
            registry.disable("t1");

            ToolCall call = new ToolCall("tc1", "t1", Map.of());

            StepVerifier.create(executor.executeToolCallReactive(call, ToolExecutionContext.serverOnly()))
                .assertNext(chunk -> {
                    assertEquals(ToolResultChunk.ChunkType.ERROR, chunk.getType());
                })
                .verifyComplete();
        }
    }

    // ==================== 辅助方法 ====================

    private Tool createServerTool(String name, String desc) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return desc; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("Result from " + name + ": " + args);
            }
        };
    }

    /**
     * 创建标记为客户端执行的工具
     */
    private Tool createClientSideTool(String name, String desc) {
        return new ClientSideMockTool(name, desc);
    }

    private static class ClientSideMockTool implements Tool, ToolMetadata {
        private final String name;
        private final String desc;

        ClientSideMockTool(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return desc; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("server-fallback: " + name);
        }
        @Override public boolean isClientSide() { return true; }
    }
}
