package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceToolBinding - 设备工具绑定")
class DeviceToolBindingTest {

    private final Tool mockTool = new SimpleTool("test-tool", "desc");

    @Test
    @DisplayName("通配符绑定匹配任意设备")
    void wildcardBindingShouldMatchAll() {
        DeviceToolBinding binding = new DeviceToolBinding("*", VersionRange.all(), mockTool);

        assertTrue(binding.matches(DeviceContext.of("ios", "2.0")));
        assertTrue(binding.matches(DeviceContext.of("android")));
        assertTrue(binding.matches(DeviceContext.DEFAULT));
    }

    @Test
    @DisplayName("精确设备类型匹配")
    void shouldMatchExactDeviceType() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), mockTool);

        assertTrue(binding.matches(DeviceContext.of("ios", "2.0")));
        assertFalse(binding.matches(DeviceContext.of("android", "2.0")));
    }

    @Test
    @DisplayName("null context 不匹配")
    void shouldNotMatchNullContext() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), mockTool);
        assertFalse(binding.matches(null));
    }

    @Test
    @DisplayName("构造器 null 检查")
    void shouldThrowOnNullParams() {
        assertThrows(NullPointerException.class,
            () -> new DeviceToolBinding(null, VersionRange.all(), mockTool));
        assertThrows(NullPointerException.class,
            () -> new DeviceToolBinding("ios", null, mockTool));
        assertThrows(NullPointerException.class,
            () -> new DeviceToolBinding("ios", VersionRange.all(), null));
    }

    @Test
    @DisplayName("getter 方法")
    void shouldReturnCorrectValues() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), mockTool, 10);

        assertEquals("ios", binding.getDeviceType());
        assertEquals(mockTool, binding.getTool());
        assertEquals(10, binding.getPriority());
    }

    @Test
    @DisplayName("默认优先级为0")
    void shouldHaveDefaultPriority() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), mockTool);
        assertEquals(0, binding.getPriority());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldHaveMeaningfulToString() {
        DeviceToolBinding binding = new DeviceToolBinding("ios", VersionRange.all(), mockTool, 5);
        String str = binding.toString();
        assertTrue(str.contains("ios"));
        assertTrue(str.contains("test-tool"));
    }

    // 简单 Tool 实现
    private static class SimpleTool implements Tool {
        private final String name;
        private final String desc;

        SimpleTool(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return desc; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("ok");
        }
    }
}
