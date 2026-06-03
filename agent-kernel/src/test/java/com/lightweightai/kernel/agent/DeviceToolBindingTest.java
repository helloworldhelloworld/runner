package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - device/version-aware tool binding")
class DeviceToolBindingTest {

    // ==================== Helper ====================

    private static Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

    // ==================== Constructor Validation ====================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Rejects null deviceType with NullPointerException")
        void constructorRejectsNullDeviceType() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                    new DeviceToolBinding(null, VersionRange.all(), simpleTool("t")));
            assertEquals("deviceType cannot be null", ex.getMessage());
        }

        @Test
        @DisplayName("Rejects null versionRange with NullPointerException")
        void constructorRejectsNullVersionRange() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                    new DeviceToolBinding("car", null, simpleTool("t")));
            assertEquals("versionRange cannot be null", ex.getMessage());
        }

        @Test
        @DisplayName("Rejects null tool with NullPointerException")
        void constructorRejectsNullTool() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                    new DeviceToolBinding("car", VersionRange.all(), null));
            assertEquals("tool cannot be null", ex.getMessage());
        }

        @Test
        @DisplayName("3-arg constructor defaults priority to zero")
        void defaultPriorityIsZero() {
            DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), simpleTool("t"));
            assertEquals(0, binding.getPriority(), "3-arg constructor should set priority=0");
        }
    }

    // ==================== Getters ====================

    @Nested
    @DisplayName("Getters")
    class GetterTests {

        @Test
        @DisplayName("All getters return values passed to 4-arg constructor")
        void gettersReturnConstructorValues() {
            VersionRange range = VersionRange.parse(">=1.0.0");
            Tool tool = simpleTool("nav");
            int priority = 42;

            DeviceToolBinding binding = new DeviceToolBinding("car", range, tool, priority);

            assertEquals("car", binding.getDeviceType());
            assertSame(range, binding.getVersionRange());
            assertSame(tool, binding.getTool());
            assertEquals("nav", binding.getTool().getName());
            assertEquals(42, binding.getPriority());
        }
    }

    // ==================== Matching ====================

    @Nested
    @DisplayName("matches(DeviceContext)")
    class MatchingTests {

        @Test
        @DisplayName("Exact device type matches same type")
        void matchesExactDeviceType() {
            DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), simpleTool("t"));

            assertTrue(binding.matches(DeviceContext.of("car")),
                    "Binding for 'car' should match DeviceContext('car')");
        }

        @Test
        @DisplayName("Does not match a different device type")
        void doesNotMatchDifferentDeviceType() {
            DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), simpleTool("t"));

            assertFalse(binding.matches(DeviceContext.of("phone")),
                    "Binding for 'car' should NOT match DeviceContext('phone')");
        }

        @Test
        @DisplayName("Wildcard device type matches any device")
        void wildcardDeviceTypeMatchesAny() {
            DeviceToolBinding binding = new DeviceToolBinding(
                    DeviceContext.WILDCARD, VersionRange.all(), simpleTool("t"));

            assertTrue(binding.matches(DeviceContext.of("car")),
                    "Wildcard binding should match 'car'");
            assertTrue(binding.matches(DeviceContext.of("phone")),
                    "Wildcard binding should match 'phone'");
            assertTrue(binding.matches(DeviceContext.of("tablet")),
                    "Wildcard binding should match 'tablet'");
        }

        @Test
        @DisplayName("Version within range matches")
        void matchesVersionInRange() {
            DeviceToolBinding binding = new DeviceToolBinding(
                    "car", VersionRange.parse(">=1.0.0"), simpleTool("t"));

            assertTrue(binding.matches(DeviceContext.of("car", "1.5.0")),
                    "Version 1.5.0 should satisfy >=1.0.0");
        }

        @Test
        @DisplayName("Version outside range does not match")
        void doesNotMatchVersionOutOfRange() {
            DeviceToolBinding binding = new DeviceToolBinding(
                    "car", VersionRange.parse(">=1.0.0"), simpleTool("t"));

            assertFalse(binding.matches(DeviceContext.of("car", "0.5.0")),
                    "Version 0.5.0 should NOT satisfy >=1.0.0");
        }

        @Test
        @DisplayName("matches(null) returns false")
        void matchesNullContextReturnsFalse() {
            DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), simpleTool("t"));

            assertFalse(binding.matches(null),
                    "matches(null) must always return false");
        }

        @Test
        @DisplayName("Wildcard version range matches any version string")
        void matchesWithWildcardVersionRange() {
            DeviceToolBinding binding = new DeviceToolBinding(
                    "phone", VersionRange.parse("*"), simpleTool("t"));

            assertTrue(binding.matches(DeviceContext.of("phone", "0.0.1")),
                    "Wildcard version range should match 0.0.1");
            assertTrue(binding.matches(DeviceContext.of("phone", "99.99.99")),
                    "Wildcard version range should match 99.99.99");
            assertTrue(binding.matches(DeviceContext.of("phone")),
                    "Wildcard version range should match null version");
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains deviceType and tool name")
        void toStringContainsDeviceTypeAndToolName() {
            DeviceToolBinding binding = new DeviceToolBinding(
                    "car", VersionRange.parse(">=2.0.0"), simpleTool("navigator"), 5);

            String str = binding.toString();

            assertTrue(str.contains("car"),
                    "toString should contain the device type 'car', was: " + str);
            assertTrue(str.contains("navigator"),
                    "toString should contain the tool name 'navigator', was: " + str);
        }
    }
}
