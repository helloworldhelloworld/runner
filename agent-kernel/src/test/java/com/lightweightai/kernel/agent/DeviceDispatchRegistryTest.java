package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistry - 设备感知注册与分发")
class DeviceDispatchRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("普通注册向后兼容（无 deviceType）")
    void plainRegisterBackwardCompatible() {
        Tool tool = createTool("get_weather", "sunny");
        registry.register(tool);

        assertTrue(registry.has("get_weather"));
        assertSame(tool, registry.get("get_weather").orElse(null));
    }

    @Test
    @DisplayName("设备注册创建 DispatchingTool")
    void deviceRegisterCreatesDispatchingTool() {
        Tool carTool = createTool("get_position", "car-result");
        registry.register(carTool, "car", VersionRange.all());

        Tool registered = registry.get("get_position").orElse(null);
        assertInstanceOf(DispatchingTool.class, registered);
    }

    @Test
    @DisplayName("同名工具不同设备共存")
    void sameNameDifferentDevicesCoexist() {
        Tool carTool = createTool("get_position", "car");
        Tool phoneTool = createTool("get_position", "phone");
        registry.register(carTool, "car", VersionRange.all());
        registry.register(phoneTool, "phone", VersionRange.all());

        DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElse(null);
        assertNotNull(dt);
        assertEquals(2, dt.getBindingCount());

        assertSame(carTool, dt.resolve(DeviceContext.of("car")));
        assertSame(phoneTool, dt.resolve(DeviceContext.of("phone")));
    }

    @Test
    @DisplayName("普通工具注册后再设备注册，普通工具变 default")
    void plainThenDeviceUpgradesToDispatchingWithDefault() {
        Tool defaultTool = createTool("get_position", "default");
        Tool carTool = createTool("get_position", "car");

        // 先注册普通工具
        registry.register(defaultTool);
        assertSame(defaultTool, registry.get("get_position").orElse(null));

        // 再按设备注册
        registry.register(carTool, "car", VersionRange.all());

        DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElse(null);
        assertNotNull(dt);
        assertSame(defaultTool, dt.getDefaultTool());
        assertSame(carTool, dt.resolve(DeviceContext.of("car")));
        assertSame(defaultTool, dt.resolve(DeviceContext.of("unknown")));
    }

    @Test
    @DisplayName("设备注册后再普通注册，普通工具设为 default")
    void deviceThenPlainSetsDefault() {
        Tool carTool = createTool("get_position", "car");
        Tool defaultTool = createTool("get_position", "default");

        registry.register(carTool, "car", VersionRange.all());
        registry.register(defaultTool); // 应设为 DispatchingTool 的 default

        DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElse(null);
        assertNotNull(dt);
        assertSame(defaultTool, dt.getDefaultTool());
    }

    @Test
    @DisplayName("unregisterDevice 只移除指定设备")
    void unregisterDeviceRemovesOnlyTargetDevice() {
        Tool carTool = createTool("get_position", "car");
        Tool phoneTool = createTool("get_position", "phone");
        registry.register(carTool, "car", VersionRange.all());
        registry.register(phoneTool, "phone", VersionRange.all());

        registry.unregisterDevice("get_position", "car");

        DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElse(null);
        assertNotNull(dt);
        assertEquals(1, dt.getBindingCount());
    }

    @Test
    @DisplayName("unregisterDevice 全空后移除条目")
    void unregisterDeviceRemovesEntryWhenEmpty() {
        Tool carTool = createTool("get_position", "car");
        registry.register(carTool, "car", VersionRange.all());

        registry.unregisterDevice("get_position", "car");

        assertFalse(registry.has("get_position"));
    }

    @Test
    @DisplayName("版本范围注册和分发")
    void versionRangeDispatch() {
        Tool v2Tool = createTool("get_position", "v2");
        Tool v3Tool = createTool("get_position", "v3");
        registry.register(v2Tool, "car", VersionRange.parse(">=2.0.0,<3.0.0"));
        registry.register(v3Tool, "car", VersionRange.parse(">=3.0.0"));

        DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElse(null);
        assertNotNull(dt);

        assertSame(v2Tool, dt.resolve(DeviceContext.of("car", "2.5.0")));
        assertSame(v3Tool, dt.resolve(DeviceContext.of("car", "3.1.0")));
    }

    // ==================== Helper ====================

    private Tool createTool(String name, String resultContent) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " (" + resultContent + ")"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(resultContent);
            }
        };
    }
}
