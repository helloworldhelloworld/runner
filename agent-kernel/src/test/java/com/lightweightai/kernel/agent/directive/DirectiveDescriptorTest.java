package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DirectiveDescriptor} — immutable value object for client tool metadata.
 *
 * Covers: validation, timeout defaults, accessor methods, toToolName/toDownlinkToolName.
 */
@DisplayName("DirectiveDescriptor - Directive 元信息")
class DirectiveDescriptorTest {

    // ==================== Constructor Validation ====================

    @Nested
    @DisplayName("构造函数校验")
    class ConstructorValidation {

        @Test
        @DisplayName("所有参数有效 — 创建成功")
        void valid_createsInstance() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "GetPosition", "GetPosition", "PositionInfo", "GeoInfo", 8000, "1.1"
            );

            assertEquals("GetPosition", desc.getTool());
            assertEquals("GetPosition", desc.getDownAction());
            assertEquals("PositionInfo", desc.getUpAction());
            assertEquals("GeoInfo", desc.getNamespace());
            assertEquals(8000, desc.getTimeoutMs());
            assertEquals("1.1", desc.getVersion());
        }

        @Test
        @DisplayName("null tool — 抛出 NullPointerException")
        void nullTool_throws() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("空 tool — 抛出 IllegalArgumentException")
        void emptyTool_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("", "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("空白 tool — 抛出 IllegalArgumentException")
        void blankTool_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("   ", "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("null namespace — 抛出 NullPointerException")
        void nullNamespace_throws() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", null, 5000, "1.0"));
        }

        @Test
        @DisplayName("null version — 抛出 NullPointerException")
        void nullVersion_throws() {
            assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", "up", "ns", 5000, null));
        }
    }

    // ==================== Timeout Defaults ====================

    @Nested
    @DisplayName("超时默认值")
    class TimeoutDefaults {

        @Test
        @DisplayName("正数超时 — 保留原值")
        void positiveTimeout_preserved() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 3000, "1.0");
            assertEquals(3000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("零超时 — 默认为 60000")
        void zeroTimeout_defaults() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 0, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("负数超时 — 默认为 60000")
        void negativeTimeout_defaults() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", -100, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }
    }

    // ==================== Convenience Methods ====================

    @Nested
    @DisplayName("便捷方法")
    class ConvenienceMethods {

        @Test
        @DisplayName("toToolName 返回 tool 字段")
        void toToolName_returnsTool() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "MyTool", "DownAction", "UpAction", "NS", 5000, "1.0");
            assertEquals("MyTool", desc.toToolName());
        }

        @Test
        @DisplayName("toDownlinkToolName 返回 downAction 字段")
        void toDownlinkToolName_returnsDownAction() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "MyTool", "DownAction", "UpAction", "NS", 5000, "1.0");
            assertEquals("DownAction", desc.toDownlinkToolName());
        }
    }

    // ==================== Trimming ====================

    @Test
    @DisplayName("字符串参数自动 trim")
    void values_areTrimmed() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "  tool  ", "  down  ", "  up  ", "  ns  ", 5000, "  1.0  ");

        assertEquals("tool", desc.getTool());
        assertEquals("down", desc.getDownAction());
        assertEquals("up", desc.getUpAction());
        assertEquals("ns", desc.getNamespace());
        assertEquals("1.0", desc.getVersion());
    }
}
