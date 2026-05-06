package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - device-specific tool matching")
class DeviceToolBindingTest {

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    @Test
    @DisplayName("exact device type match")
    void shouldMatchExactDeviceType() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), dummyTool("nav"));

        assertTrue(binding.matches(DeviceContext.of("car")));
        assertFalse(binding.matches(DeviceContext.of("phone")));
    }

    @Test
    @DisplayName("wildcard device type matches everything")
    void wildcardShouldMatchAllDevices() {
        DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), dummyTool("universal"));

        assertTrue(binding.matches(DeviceContext.of("car")));
        assertTrue(binding.matches(DeviceContext.of("phone")));
        assertTrue(binding.matches(DeviceContext.DEFAULT));
    }

    @Test
    @DisplayName("version range constrains matching")
    void shouldRespectVersionRange() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), dummyTool("nav_v2"));

        assertTrue(binding.matches(DeviceContext.of("car", "2.0.0")));
        assertTrue(binding.matches(DeviceContext.of("car", "3.0.0")));
        assertFalse(binding.matches(DeviceContext.of("car", "1.9.0")));
    }

    @Test
    @DisplayName("null version fails non-wildcard version range")
    void nullVersionShouldFailNonWildcardRange() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.exact("2.0.0"), dummyTool("nav"));

        assertFalse(binding.matches(DeviceContext.of("car")));
    }

    @Test
    @DisplayName("null context returns false")
    void nullContextShouldNotMatch() {
        DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), dummyTool("x"));
        assertFalse(binding.matches(null));
    }

    @Test
    @DisplayName("default priority is 0")
    void defaultPriorityShouldBeZero() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), dummyTool("x"));
        assertEquals(0, binding.getPriority());
    }

    @Test
    @DisplayName("custom priority is preserved")
    void customPriorityShouldBePreserved() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), dummyTool("x"), 10);
        assertEquals(10, binding.getPriority());
    }

    @Test
    @DisplayName("getters return constructor values")
    void gettersShouldReturnConstructorValues() {
        Tool tool = dummyTool("test_tool");
        VersionRange range = VersionRange.parse(">=1.0.0,<2.0.0");
        DeviceToolBinding binding = new DeviceToolBinding("phone", range, tool, 5);

        assertEquals("phone", binding.getDeviceType());
        assertEquals(range, binding.getVersionRange());
        assertSame(tool, binding.getTool());
        assertEquals(5, binding.getPriority());
    }

    @Test
    @DisplayName("rejects null deviceType")
    void shouldRejectNullDeviceType() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding(null, VersionRange.all(), dummyTool("x")));
    }

    @Test
    @DisplayName("rejects null versionRange")
    void shouldRejectNullVersionRange() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("car", null, dummyTool("x")));
    }

    @Test
    @DisplayName("rejects null tool")
    void shouldRejectNullTool() {
        assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("car", VersionRange.all(), null));
    }

    @Test
    @DisplayName("toString includes tool name and device type")
    void toStringShouldIncludeKeyInfo() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), dummyTool("nav"));
        String s = binding.toString();
        assertTrue(s.contains("car"));
        assertTrue(s.contains("nav"));
    }
}
