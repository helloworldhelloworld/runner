package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DispatchingTool - resolve 设备分发逻辑")
class DispatchingToolResolveTest {

    @Test
    @DisplayName("无绑定无默认时 resolve 返回 null")
    void resolveReturnsNullWhenEmpty() {
        DispatchingTool dt = new DispatchingTool("test");
        assertNull(dt.resolve(DeviceContext.of("car", "1.0.0")));
        assertTrue(dt.isEmpty());
    }

    @Test
    @DisplayName("有默认工具时，不匹配设备类型回退到默认")
    void fallsBackToDefaultTool() {
        Tool defaultTool = namedTool("default_impl");
        DispatchingTool dt = new DispatchingTool("test", defaultTool);

        Tool resolved = dt.resolve(DeviceContext.of("unknown_device", "1.0.0"));
        assertSame(defaultTool, resolved);
    }

    @Test
    @DisplayName("精确设备类型匹配优先于默认工具")
    void exactDeviceMatchTakesPrecedence() {
        Tool defaultTool = namedTool("default_impl");
        Tool carTool = namedTool("car_impl");
        DispatchingTool dt = new DispatchingTool("test", defaultTool);
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        Tool resolved = dt.resolve(DeviceContext.of("car", "1.0.0"));
        assertSame(carTool, resolved);
    }

    @Test
    @DisplayName("多个匹配时按 priority 降序选择")
    void higherPriorityWins() {
        Tool lowPri = namedTool("low");
        Tool highPri = namedTool("high");
        DispatchingTool dt = new DispatchingTool("test");
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), lowPri, 1));
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), highPri, 10));

        Tool resolved = dt.resolve(DeviceContext.of("car", "1.0.0"));
        assertSame(highPri, resolved);
    }

    @Test
    @DisplayName("版本范围过滤生效：不匹配版本不被选中")
    void versionFilteringWorks() {
        Tool v2Tool = namedTool("v2_impl");
        Tool defaultTool = namedTool("default_impl");
        DispatchingTool dt = new DispatchingTool("test", defaultTool);
        dt.addBinding(new DeviceToolBinding("car", VersionRange.parse(">=2.0.0"), v2Tool));

        // v1 should fall back to default
        Tool resolved = dt.resolve(DeviceContext.of("car", "1.5.0"));
        assertSame(defaultTool, resolved);

        // v2 should match the binding
        Tool resolved2 = dt.resolve(DeviceContext.of("car", "2.1.0"));
        assertSame(v2Tool, resolved2);
    }

    @Test
    @DisplayName("DEFAULT context 使用默认工具")
    void defaultContextUsesDefaultTool() {
        Tool defaultTool = namedTool("default_impl");
        Tool carTool = namedTool("car_impl");
        DispatchingTool dt = new DispatchingTool("test", defaultTool);
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        Tool resolved = dt.resolve(DeviceContext.DEFAULT);
        assertSame(defaultTool, resolved);
    }

    @Test
    @DisplayName("null context 使用默认工具")
    void nullContextUsesDefaultTool() {
        Tool defaultTool = namedTool("default_impl");
        DispatchingTool dt = new DispatchingTool("test", defaultTool);

        Tool resolved = dt.resolve(null);
        assertSame(defaultTool, resolved);
    }

    @Test
    @DisplayName("无默认工具时 DEFAULT context 回退到第一个 binding")
    void defaultContextFallsToFirstBinding() {
        Tool carTool = namedTool("car_impl");
        DispatchingTool dt = new DispatchingTool("test");
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        Tool resolved = dt.resolve(DeviceContext.DEFAULT);
        assertSame(carTool, resolved);
    }

    @Test
    @DisplayName("removeBindingsForDevice 移除指定设备的绑定")
    void removeBindingsForDevice() {
        DispatchingTool dt = new DispatchingTool("test");
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), namedTool("car_v1")));
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), namedTool("car_v2")));
        dt.addBinding(new DeviceToolBinding("phone", VersionRange.all(), namedTool("phone")));
        assertEquals(3, dt.getBindingCount());

        dt.removeBindingsForDevice("car");
        assertEquals(1, dt.getBindingCount());
    }

    @Test
    @DisplayName("riskLevel 返回所有变体中最高风险等级")
    void riskLevelReturnsMaxAcrossVariants() {
        Tool safeTool = new Tool() {
            @Override public String getName() { return "safe"; }
            @Override public String getDescription() { return "safe"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return RiskLevel.SAFE; }
        };

        Tool systemTool = new Tool() {
            @Override public String getName() { return "system"; }
            @Override public String getDescription() { return "system"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return RiskLevel.SYSTEM; }
        };

        DispatchingTool dt = new DispatchingTool("test", safeTool);
        dt.addBinding(new DeviceToolBinding("car", VersionRange.all(), systemTool));

        assertEquals(RiskLevel.SYSTEM, dt.riskLevel());
    }

    @Test
    @DisplayName("getDescription / getSchema 委托给代表工具")
    void delegatesDescriptionAndSchema() {
        Tool primary = new Tool() {
            @Override public String getName() { return "primary"; }
            @Override public String getDescription() { return "Primary Tool Description"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };

        DispatchingTool dt = new DispatchingTool("test", primary);
        assertEquals("Primary Tool Description", dt.getDescription());
        assertNotNull(dt.getSchema());
    }

    @Test
    @DisplayName("execute 无上下文时走默认工具")
    void executeUsesDefaultTool() {
        Tool defaultTool = new Tool() {
            @Override public String getName() { return "default"; }
            @Override public String getDescription() { return "default"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("default executed");
            }
        };

        DispatchingTool dt = new DispatchingTool("test", defaultTool);
        ToolResult result = dt.execute(Map.of());
        assertEquals("default executed", result.getContent());
    }

    private static Tool namedTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success(name); }
        };
    }
}
