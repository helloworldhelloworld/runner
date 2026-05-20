package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission test: ToolExecutor → DispatchingTool → DeviceToolBinding → concrete Tool.
 *
 * Verifies the device-aware dispatch chain carries the correct payload
 * through to the right tool implementation based on DeviceContext.
 */
@DisplayName("ToolExecutor → DispatchingTool device dispatch transmission")
class ToolExecutorDeviceDispatchTransmissionTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        executor = new ToolExecutor(toolRegistry);

        DispatchingTool getPosition = new DispatchingTool("get_position",
                simpleTool("get_position", "generic: unknown"));

        getPosition.addBinding(new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"),
                simpleTool("get_position", "car: lat=31.2,lon=121.4"), 10));

        getPosition.addBinding(new DeviceToolBinding(
                "phone", VersionRange.all(),
                simpleTool("get_position", "phone: GPS(31.2,121.4)"), 5));

        toolRegistry.register(getPosition);
    }

    @Test
    @DisplayName("car context dispatches to car implementation")
    void carContextDispatchesToCar() {
        ToolExecutionContext ctx = new ToolExecutionContext(
                null, 5000, DeviceContext.of("car", "2.1.0"));

        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-1", "get_position", Map.of()), ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("car:"),
                "Expected car implementation, got: " + result.getContent());
    }

    @Test
    @DisplayName("phone context dispatches to phone implementation")
    void phoneContextDispatchesToPhone() {
        ToolExecutionContext ctx = new ToolExecutionContext(
                null, 5000, DeviceContext.of("phone", "1.0.0"));

        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-2", "get_position", Map.of()), ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("phone:"),
                "Expected phone implementation, got: " + result.getContent());
    }

    @Test
    @DisplayName("unknown device falls back to default tool")
    void unknownDeviceFallsToDefault() {
        ToolExecutionContext ctx = new ToolExecutionContext(
                null, 5000, DeviceContext.of("watch", "1.0.0"));

        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-3", "get_position", Map.of()), ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("generic:"),
                "Expected default implementation, got: " + result.getContent());
    }

    @Test
    @DisplayName("old car version falls back to default (version mismatch)")
    void oldCarVersionFallsToDefault() {
        ToolExecutionContext ctx = new ToolExecutionContext(
                null, 5000, DeviceContext.of("car", "1.5.0"));

        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-4", "get_position", Map.of()), ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("generic:"),
                "Expected default for old car, got: " + result.getContent());
    }

    @Test
    @DisplayName("null context (no device info) uses default tool")
    void nullContextUsesDefault() {
        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-5", "get_position", Map.of()));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("generic:"),
                "Expected default without context, got: " + result.getContent());
    }

    @Test
    @DisplayName("serverOnly context uses default tool")
    void serverOnlyContextUsesDefault() {
        ToolExecutionContext ctx = ToolExecutionContext.serverOnly();

        ToolResult result = executor.executeToolCall(
                new ToolCall("tc-6", "get_position", Map.of()), ctx);

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("generic:"),
                "Expected default for server-only, got: " + result.getContent());
    }

    @Test
    @DisplayName("reactive dispatch also respects device context")
    void reactiveDispatchRespectsDeviceContext() {
        ToolExecutionContext ctx = new ToolExecutionContext(
                null, 5000, DeviceContext.of("car", "3.0.0"));

        ToolResultChunk chunk = executor.executeToolCallReactive(
                        new ToolCall("tc-7", "get_position", Map.of()), ctx)
                .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE)
                .blockFirst();

        assertNotNull(chunk);
        assertTrue(chunk.getResult().getContent().contains("car:"),
                "Reactive path should also dispatch to car impl");
    }

    private Tool simpleTool(String name, String result) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(result);
            }
        };
    }
}
