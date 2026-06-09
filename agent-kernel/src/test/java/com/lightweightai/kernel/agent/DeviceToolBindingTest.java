package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding — device-specific tool variant binding")
class DeviceToolBindingTest {

    private static final Tool DUMMY_TOOL = new Tool() {
        @Override public String getName() { return "dummy"; }
        @Override public String getDescription() { return "dummy tool"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
    };

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        void rejectsNullDeviceType() {
            assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding(null, VersionRange.all(), DUMMY_TOOL));
        }

        @Test
        void rejectsNullVersionRange() {
            assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding("ios", null, DUMMY_TOOL));
        }

        @Test
        void rejectsNullTool() {
            assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding("ios", VersionRange.all(), null));
        }

        @Test
        void defaultPriorityIsZero() {
            DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), DUMMY_TOOL);
            assertEquals(0, binding.getPriority());
        }

        @Test
        void customPriorityIsPreserved() {
            DeviceToolBinding binding = new DeviceToolBinding("android", VersionRange.all(), DUMMY_TOOL, 10);
            assertEquals(10, binding.getPriority());
        }
    }

    @Nested
    @DisplayName("matches() — device context matching")
    class Matching {

        @Test
        void nullContextReturnsFalse() {
            DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), DUMMY_TOOL);
            assertFalse(binding.matches(null));
        }

        @Test
        void wildcardDeviceTypeMatchesAny() {
            DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), DUMMY_TOOL);
            assertTrue(binding.matches(DeviceContext.of("ios")));
            assertTrue(binding.matches(DeviceContext.of("android")));
            assertTrue(binding.matches(DeviceContext.of("web")));
        }

        @Test
        void specificDeviceTypeMustMatchExactly() {
            DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), DUMMY_TOOL);
            assertTrue(binding.matches(DeviceContext.of("ios")));
            assertFalse(binding.matches(DeviceContext.of("android")));
            assertFalse(binding.matches(DeviceContext.of("IOS")));
        }

        @Test
        void versionRangeIsChecked() {
            DeviceToolBinding binding = new DeviceToolBinding("ios",
                VersionRange.parse(">=2.0.0"), DUMMY_TOOL);
            assertTrue(binding.matches(DeviceContext.of("ios", "2.1.0")));
            assertTrue(binding.matches(DeviceContext.of("ios", "3.0.0")));
            assertFalse(binding.matches(DeviceContext.of("ios", "1.9.0")));
        }

        @Test
        void wildcardDeviceWithVersionRange() {
            DeviceToolBinding binding = new DeviceToolBinding("*",
                VersionRange.parse(">=1.0.0,<2.0.0"), DUMMY_TOOL);
            assertTrue(binding.matches(DeviceContext.of("ios", "1.5.0")));
            assertTrue(binding.matches(DeviceContext.of("android", "1.0.0")));
            assertFalse(binding.matches(DeviceContext.of("ios", "2.0.0")));
        }

        @Test
        void nullVersionOnContextWithNonAllRangeReturnsFalse() {
            DeviceToolBinding binding = new DeviceToolBinding("ios",
                VersionRange.exact("2.0.0"), DUMMY_TOOL);
            assertFalse(binding.matches(DeviceContext.of("ios")));
        }

        @Test
        void defaultContextMatchesWildcardAllBinding() {
            DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), DUMMY_TOOL);
            assertTrue(binding.matches(DeviceContext.DEFAULT));
        }
    }

    @Nested
    @DisplayName("Getters")
    class Getters {

        @Test
        void allFieldsAccessible() {
            VersionRange range = VersionRange.exact("1.0.0");
            DeviceToolBinding binding = new DeviceToolBinding("web", range, DUMMY_TOOL, 5);

            assertEquals("web", binding.getDeviceType());
            assertSame(range, binding.getVersionRange());
            assertSame(DUMMY_TOOL, binding.getTool());
            assertEquals(5, binding.getPriority());
        }
    }

    @Test
    @DisplayName("toString includes all fields")
    void toStringContainsFields() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), DUMMY_TOOL, 3);
        String s = binding.toString();
        assertTrue(s.contains("ios"));
        assertTrue(s.contains("dummy"));
        assertTrue(s.contains("3"));
    }
}
