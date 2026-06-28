package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - 设备工具绑定匹配逻辑")
class DeviceToolBindingTest {

    @Test
    @DisplayName("精确设备类型 + 通配版本范围匹配")
    void matchesExactDeviceTypeWithWildcardVersion() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), noopTool("gps"));
        assertTrue(binding.matches(DeviceContext.of("car", "1.0.0")));
        assertTrue(binding.matches(DeviceContext.of("car")));
        assertFalse(binding.matches(DeviceContext.of("phone", "1.0.0")));
    }

    @Test
    @DisplayName("通配设备类型匹配所有设备")
    void wildcardDeviceTypeMatchesAll() {
        DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), noopTool("gps"));
        assertTrue(binding.matches(DeviceContext.of("car", "1.0.0")));
        assertTrue(binding.matches(DeviceContext.of("phone")));
        assertTrue(binding.matches(DeviceContext.DEFAULT));
    }

    @Test
    @DisplayName("版本范围过滤生效")
    void versionRangeFiltering() {
        DeviceToolBinding binding = new DeviceToolBinding(
                "car", VersionRange.parse(">=2.0.0"), noopTool("gps_v2"));
        assertTrue(binding.matches(DeviceContext.of("car", "2.0.0")));
        assertTrue(binding.matches(DeviceContext.of("car", "3.1.0")));
        assertFalse(binding.matches(DeviceContext.of("car", "1.9.9")));
    }

    @Test
    @DisplayName("null context 不匹配任何绑定")
    void nullContextNeverMatches() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), noopTool("gps"));
        assertFalse(binding.matches(null));
    }

    @Test
    @DisplayName("构造函数验证 null 参数")
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding(null, VersionRange.all(), noopTool("t")));
        assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding("car", null, noopTool("t")));
        assertThrows(NullPointerException.class,
                () -> new DeviceToolBinding("car", VersionRange.all(), null));
    }

    @Test
    @DisplayName("priority 从构造参数传入")
    void priorityPreserved() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), noopTool("gps"), 5);
        assertEquals(5, binding.getPriority());
    }

    @Test
    @DisplayName("默认 priority 为 0")
    void defaultPriorityIsZero() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), noopTool("gps"));
        assertEquals(0, binding.getPriority());
    }

    @Test
    @DisplayName("getter 返回构造时的值")
    void gettersReturnConstructionValues() {
        Tool tool = noopTool("my_tool");
        VersionRange range = VersionRange.exact("1.0.0");
        DeviceToolBinding binding = new DeviceToolBinding("phone", range, tool, 3);

        assertEquals("phone", binding.getDeviceType());
        assertEquals(range, binding.getVersionRange());
        assertSame(tool, binding.getTool());
        assertEquals(3, binding.getPriority());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsKeyInfo() {
        DeviceToolBinding binding = new DeviceToolBinding("car", VersionRange.all(), noopTool("gps"), 2);
        String s = binding.toString();
        assertTrue(s.contains("car"));
        assertTrue(s.contains("gps"));
        assertTrue(s.contains("2"));
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
