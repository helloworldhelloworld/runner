package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.DeviceContext;
import com.lightweightai.kernel.agent.DeviceToolBinding;
import com.lightweightai.kernel.agent.DispatchingTool;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.agent.VersionRange;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolExecutor - Reactive 路径设备分发")
class ToolExecutorReactiveDeviceTest {

    private ToolRegistry registry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        executor = new ToolExecutor(registry);
    }

    @Test
    @DisplayName("DispatchingTool 根据 DeviceContext 选择正确实现 (reactive)")
    void reactiveDeviceDispatch() {
        DispatchingTool dispTool = new DispatchingTool("get_position",
            createTool("get_position", "default_position"));

        dispTool.addBinding(new DeviceToolBinding(
            "car", VersionRange.all(),
            createTool("get_position", "car_gps_position"), 10));

        dispTool.addBinding(new DeviceToolBinding(
            "phone", VersionRange.all(),
            createTool("get_position", "phone_position"), 10));

        registry.register(dispTool);

        ToolExecutionContext carCtx = new ToolExecutionContext(null, 0,
            DeviceContext.of("car", "2.0"));

        StepVerifier.create(executor.executeToolCallReactive(
                new ToolCall("tc1", "get_position", Map.of()), carCtx))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertTrue(chunk.getResult().getContent().contains("car_gps_position"),
                    "Should dispatch to car implementation, got: " + chunk.getResult().getContent());
                assertEquals("tc1", chunk.getToolCallId());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("DispatchingTool 没有匹配时 fallback 到 default (reactive)")
    void reactiveDefaultFallback() {
        DispatchingTool dispTool = new DispatchingTool("get_position",
            createTool("get_position", "default_impl"));
        registry.register(dispTool);

        ToolExecutionContext ctx = new ToolExecutionContext(null, 0,
            DeviceContext.of("unknown_device", "1.0"));

        StepVerifier.create(executor.executeToolCallReactive(
                new ToolCall("tc1", "get_position", Map.of()), ctx))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertTrue(chunk.getResult().getContent().contains("default_impl"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Reactive 批量设备分发 - 不同设备返回不同结果")
    void reactiveBatchDeviceDispatch() {
        DispatchingTool dispTool = new DispatchingTool("sensor",
            createTool("sensor", "generic"));
        dispTool.addBinding(new DeviceToolBinding(
            "car", VersionRange.all(),
            createTool("sensor", "car_sensor"), 10));
        registry.register(dispTool);

        ToolExecutionContext carCtx = new ToolExecutionContext(null, 0,
            DeviceContext.of("car"));

        StepVerifier.create(executor.executeToolCallsReactive(List.of(
                new ToolCall("a", "sensor", Map.of()),
                new ToolCall("b", "sensor", Map.of())
            ), carCtx))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    @DisplayName("没有 DeviceContext 时 reactive 路径走 default")
    void reactiveNoDeviceContext() {
        DispatchingTool dispTool = new DispatchingTool("action",
            createTool("action", "fallback_result"));
        registry.register(dispTool);

        StepVerifier.create(executor.executeToolCallReactive(
                new ToolCall("tc1", "action", Map.of())))
            .assertNext(chunk -> {
                assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunk.getType());
                assertTrue(chunk.getResult().getContent().contains("fallback_result"));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("collectAndJoin 最终结果包含 toolUseId")
    void collectReturnsToolUseId() {
        registry.register(createTool("echo", "hello"));

        StepVerifier.create(executor.executeToolCallsAndCollect(List.of(
                new ToolCall("tc1", "echo", Map.of()),
                new ToolCall("tc2", "echo", Map.of())
            )))
            .assertNext(results -> {
                assertEquals(2, results.size());
                assertTrue(results.stream().anyMatch(r -> "tc1".equals(r.getToolUseId())));
                assertTrue(results.stream().anyMatch(r -> "tc2".equals(r.getToolUseId())));
            })
            .verifyComplete();
    }

    private Tool createTool(String name, String resultContent) {
        return new Tool() {
            public String getName() { return name; }
            public String getDescription() { return "Tool " + name; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(resultContent);
            }
        };
    }
}
