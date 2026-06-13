package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - Directive 元信息")
class DirectiveDescriptorTest {

    @Nested
    @DisplayName("构造器验证")
    class ConstructorValidation {

        @Test
        @DisplayName("正常构造并获取全部字段")
        void normalConstruction() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "ExecOsApi", "execute_down", "execute_up",
                "SystemApi", 10000, "2.0.0");

            assertEquals("ExecOsApi", desc.getTool());
            assertEquals("execute_down", desc.getDownAction());
            assertEquals("execute_up", desc.getUpAction());
            assertEquals("SystemApi", desc.getNamespace());
            assertEquals(10000, desc.getTimeoutMs());
            assertEquals("2.0.0", desc.getVersion());
        }

        @Test
        @DisplayName("null tool 抛出 NullPointerException")
        void nullToolThrows() {
            assertThrows(NullPointerException.class,
                () -> new DirectiveDescriptor(null, "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("空 tool 抛出 IllegalArgumentException")
        void emptyToolThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new DirectiveDescriptor("  ", "down", "up", "ns", 5000, "1.0"));
        }

        @Test
        @DisplayName("timeout <= 0 默认为 60000")
        void negativeTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", -1, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }

        @Test
        @DisplayName("timeout = 0 默认为 60000")
        void zeroTimeoutDefaultsTo60s() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 0, "1.0");
            assertEquals(60_000, desc.getTimeoutMs());
        }
    }

    @Nested
    @DisplayName("别名处理")
    class AliasHandling {

        @Test
        @DisplayName("无别名时 getAllToolNames 只包含主名")
        void noAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 5000, "1.0");
            assertEquals(List.of("tool"), desc.getAllToolNames());
            assertTrue(desc.getAliases().isEmpty());
        }

        @Test
        @DisplayName("带别名时 getAllToolNames 包含主名和别名")
        void withAliases() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "ExecOsApi", List.of("ExecuteOSAPI", "OsApiInvoke"),
                "down", "up", "ns", 5000, "1.0");

            List<String> allNames = desc.getAllToolNames();
            assertEquals(3, allNames.size());
            assertEquals("ExecOsApi", allNames.get(0));
            assertTrue(allNames.contains("ExecuteOSAPI"));
            assertTrue(allNames.contains("OsApiInvoke"));
        }

        @Test
        @DisplayName("别名去重：与主名相同的别名被过滤")
        void aliasSameAsPrimaryFiltered() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", List.of("tool", "alias1"),
                "down", "up", "ns", 5000, "1.0");

            assertEquals(1, desc.getAliases().size());
            assertEquals("alias1", desc.getAliases().get(0));
        }

        @Test
        @DisplayName("null 和空别名被过滤")
        void nullAndEmptyAliasesFiltered() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", Arrays.asList(null, "", "  ", "valid"),
                "down", "up", "ns", 5000, "1.0");

            assertEquals(1, desc.getAliases().size());
            assertEquals("valid", desc.getAliases().get(0));
        }

        @Test
        @DisplayName("重复别名去重")
        void duplicateAliasesDeduped() {
            DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", List.of("alias", "alias", "other"),
                "down", "up", "ns", 5000, "1.0");

            assertEquals(2, desc.getAliases().size());
        }
    }

    @Test
    @DisplayName("toToolName 返回主名")
    void toToolName() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "myTool", "down", "up", "ns", 5000, "1.0");
        assertEquals("myTool", desc.toToolName());
    }

    @Test
    @DisplayName("toDownlinkToolName 返回 downAction")
    void toDownlinkToolName() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", "execute_command", "up", "ns", 5000, "1.0");
        assertEquals("execute_command", desc.toDownlinkToolName());
    }
}
