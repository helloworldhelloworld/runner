package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - device-specific tool matching")
class DeviceToolBindingTest {

    @Test
    @DisplayName("matches exact device type and version")
    void matchesExactDeviceAndVersion() {
        Tool tool = dummyTool("nav");
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.parse("*"), tool);

        assertTrue(binding.matches(new DeviceContext("car", "2.0.0")));
    }

    @Test
    @DisplayName("does not match different device type")
    void noMatchDifferentDevice() {
        Tool tool = dummyTool("nav");
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.parse("*"), tool);

        assertFalse(binding.matches(new DeviceContext("phone", "2.0.0")));
    }

    @Test
    @DisplayName("wildcard device type matches any device")
    void wildcardMatchesAny() {
        Tool tool = dummyTool("common");
        DeviceToolBinding binding = new DeviceToolBinding(
                DeviceContext.WILDCARD, VersionRange.parse("*"), tool);

        assertTrue(binding.matches(new DeviceContext("car", "1.0.0")));
        assertTrue(binding.matches(new DeviceContext("phone", "3.0.0")));
    }

    @Test
    @DisplayName("version range restricts matching")
    void versionRangeRestricts() {
        Tool tool = dummyTool("nav");
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), tool);

        assertTrue(binding.matches(new DeviceContext("car", "2.0.0")));
        assertTrue(binding.matches(new DeviceContext("car", "3.0.0")));
        assertFalse(binding.matches(new DeviceContext("car", "1.0.0")));
    }

    @Test
    @DisplayName("null context returns false")
    void nullContextReturnsFalse() {
        Tool tool = dummyTool("nav");
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.parse("*"), tool);

        assertFalse(binding.matches(null));
    }

    @Test
    @DisplayName("constructor rejects null deviceType")
    void rejectsNullDeviceType() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding(null, VersionRange.parse("*"), dummyTool("t")));
    }

    @Test
    @DisplayName("constructor rejects null tool")
    void rejectsNullTool() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("car", VersionRange.parse("*"), null));
    }

    @Test
    @DisplayName("priority defaults to 0")
    void defaultPriority() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse("*"), dummyTool("t"));
        assertEquals(0, binding.getPriority());
    }

    @Test
    @DisplayName("custom priority is preserved")
    void customPriority() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse("*"), dummyTool("t"), 10);
        assertEquals(10, binding.getPriority());
    }

    @Test
    @DisplayName("getters return constructed values")
    void gettersReturnValues() {
        Tool tool = dummyTool("nav");
        VersionRange range = VersionRange.parse(">=1.0.0");
        DeviceToolBinding binding = new DeviceToolBinding("car", range, tool, 5);

        assertEquals("car", binding.getDeviceType());
        assertSame(range, binding.getVersionRange());
        assertSame(tool, binding.getTool());
        assertEquals(5, binding.getPriority());
    }

    @Test
    @DisplayName("toString includes device type and tool name")
    void toStringFormat() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse("*"), dummyTool("navigate"));

        String str = binding.toString();
        assertTrue(str.contains("car"));
        assertTrue(str.contains("navigate"));
    }

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
