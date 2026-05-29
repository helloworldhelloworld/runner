package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - Device-aware tool binding and matching")
class DeviceToolBindingTest {

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Dummy " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Creates binding with all fields")
        void shouldCreateWithAllFields() {
            Tool tool = dummyTool("test");
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.parse(">=2.0.0"), tool, 10);

            assertEquals("android", binding.getDeviceType());
            assertEquals(tool, binding.getTool());
            assertEquals(10, binding.getPriority());
        }

        @Test
        @DisplayName("Default priority is 0")
        void shouldDefaultPriorityToZero() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("test"));
            assertEquals(0, binding.getPriority());
        }

        @Test
        @DisplayName("Null deviceType throws NullPointerException")
        void shouldThrowOnNullDeviceType() {
            assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding(null, VersionRange.all(), dummyTool("test")));
        }

        @Test
        @DisplayName("Null versionRange throws NullPointerException")
        void shouldThrowOnNullVersionRange() {
            assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("android", null, dummyTool("test")));
        }

        @Test
        @DisplayName("Null tool throws NullPointerException")
        void shouldThrowOnNullTool() {
            assertThrows(NullPointerException.class, () ->
                new DeviceToolBinding("android", VersionRange.all(), null));
        }
    }

    @Nested
    @DisplayName("Device matching")
    class DeviceMatching {

        @Test
        @DisplayName("Exact device type match returns true")
        void shouldMatchExactDeviceType() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("android", "1.0.0")));
        }

        @Test
        @DisplayName("Different device type does not match")
        void shouldNotMatchDifferentDeviceType() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("test"));

            assertFalse(binding.matches(DeviceContext.of("ios", "1.0.0")));
        }

        @Test
        @DisplayName("Wildcard device type matches any device")
        void wildcardShouldMatchAnyDevice() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "*", VersionRange.all(), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("android", "1.0.0")));
            assertTrue(binding.matches(DeviceContext.of("ios", "2.0.0")));
            assertTrue(binding.matches(DeviceContext.of("car", "3.0.0")));
        }

        @Test
        @DisplayName("Null context does not match")
        void nullContextShouldNotMatch() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("test"));

            assertFalse(binding.matches(null));
        }
    }

    @Nested
    @DisplayName("Version matching")
    class VersionMatching {

        @Test
        @DisplayName("Version within range matches")
        void shouldMatchVersionInRange() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.parse(">=2.0.0,<3.0.0"), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("android", "2.5.0")));
        }

        @Test
        @DisplayName("Version outside range does not match")
        void shouldNotMatchVersionOutsideRange() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.parse(">=2.0.0,<3.0.0"), dummyTool("test"));

            assertFalse(binding.matches(DeviceContext.of("android", "3.1.0")));
        }

        @Test
        @DisplayName("Exact version match")
        void shouldMatchExactVersion() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.exact("2.1.0"), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("android", "2.1.0")));
            assertFalse(binding.matches(DeviceContext.of("android", "2.2.0")));
        }

        @Test
        @DisplayName("VersionRange.all() matches any version including null")
        void allVersionShouldMatchAny() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("android", "1.0.0")));
            assertTrue(binding.matches(DeviceContext.of("android")));
        }
    }

    @Nested
    @DisplayName("Combined device + version matching")
    class CombinedMatching {

        @Test
        @DisplayName("Both device and version must match")
        void shouldRequireBothDeviceAndVersionMatch() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), dummyTool("test"));

            assertTrue(binding.matches(DeviceContext.of("car", "2.5.0")));
            assertFalse(binding.matches(DeviceContext.of("car", "1.0.0")));
            assertFalse(binding.matches(DeviceContext.of("phone", "2.5.0")));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString includes device type and tool name")
        void shouldIncludeKeyInfo() {
            DeviceToolBinding binding = new DeviceToolBinding(
                "android", VersionRange.all(), dummyTool("photo_tool"), 5);

            String str = binding.toString();
            assertTrue(str.contains("android"));
            assertTrue(str.contains("photo_tool"));
            assertTrue(str.contains("5"));
        }
    }
}
