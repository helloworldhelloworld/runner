package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeviceToolBinding — device-context matching for tool dispatch.
 *
 * Part of the ToolExecutor dispatch chain:
 *   ToolCall → ToolExecutor → DispatchingTool → DeviceToolBinding.matches() → concrete Tool
 */
@DisplayName("DeviceToolBinding — device context matching")
class DeviceToolBindingTest {

    @Test
    @DisplayName("matches exact device type and version in range")
    void matchesExactDeviceAndVersion() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), simpleTool("car_gps"));

        assertTrue(binding.matches(new DeviceContext("car", "2.1.0")));
        assertTrue(binding.matches(new DeviceContext("car", "3.0.0")));
    }

    @Test
    @DisplayName("rejects wrong device type")
    void rejectsWrongDevice() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.any(), simpleTool("car_gps"));

        assertFalse(binding.matches(new DeviceContext("phone", "1.0.0")));
    }

    @Test
    @DisplayName("rejects version outside range")
    void rejectsVersionOutOfRange() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), simpleTool("car_gps"));

        assertFalse(binding.matches(new DeviceContext("car", "1.9.0")));
    }

    @Test
    @DisplayName("wildcard device type matches any device")
    void wildcardMatchesAnyDevice() {
        DeviceToolBinding binding = new DeviceToolBinding(
                DeviceContext.WILDCARD, VersionRange.any(), simpleTool("generic"));

        assertTrue(binding.matches(new DeviceContext("car", "1.0.0")));
        assertTrue(binding.matches(new DeviceContext("phone", "2.0.0")));
        assertTrue(binding.matches(new DeviceContext("watch", "3.0.0")));
    }

    @Test
    @DisplayName("returns false for null context")
    void nullContextReturnsFalse() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.any(), simpleTool("car_gps"));

        assertFalse(binding.matches(null));
    }

    @Test
    @DisplayName("priority is preserved")
    void priorityPreserved() {
        DeviceToolBinding low = new DeviceToolBinding(
                "car", VersionRange.any(), simpleTool("low"), 1);
        DeviceToolBinding high = new DeviceToolBinding(
                "car", VersionRange.any(), simpleTool("high"), 10);

        assertEquals(1, low.getPriority());
        assertEquals(10, high.getPriority());
    }

    @Test
    @DisplayName("default priority is 0")
    void defaultPriorityIsZero() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.any(), simpleTool("test"));

        assertEquals(0, binding.getPriority());
    }

    @Test
    @DisplayName("getTool() returns the bound tool")
    void getToolReturnsBound() {
        Tool tool = simpleTool("my_tool");
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.any(), tool);

        assertSame(tool, binding.getTool());
    }

    @Test
    @DisplayName("getDeviceType() returns the device type")
    void getDeviceTypeWorks() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "phone", VersionRange.any(), simpleTool("test"));

        assertEquals("phone", binding.getDeviceType());
    }

    @Test
    @DisplayName("null deviceType throws NPE")
    void nullDeviceTypeThrows() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding(null, VersionRange.any(), simpleTool("t")));
    }

    @Test
    @DisplayName("null versionRange throws NPE")
    void nullVersionRangeThrows() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("car", null, simpleTool("t")));
    }

    @Test
    @DisplayName("null tool throws NPE")
    void nullToolThrows() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("car", VersionRange.any(), null));
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(name + " result");
            }
        };
    }
}
