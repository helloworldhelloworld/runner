package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DispatchingTool - 设备分发工具")
class DispatchingToolTest {

    private DispatchingTool dispatchingTool;

    @BeforeEach
    void setUp() {
        dispatchingTool = new DispatchingTool("get_position");
    }

    @Test
    @DisplayName("无绑定无默认时 execute 返回错误")
    void executeWithNoBindingsReturnsError() {
        ToolResult result = dispatchingTool.execute(Map.of());
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("有默认工具时走默认")
    void resolveDefaultWithNoDeviceMatch() {
        Tool defaultTool = createTool("get_position", "default-result");
        dispatchingTool.setDefaultTool(defaultTool);

        Tool resolved = dispatchingTool.resolve(DeviceContext.of("unknown_device"));
        assertSame(defaultTool, resolved);
    }

    @Test
    @DisplayName("设备匹配优先于默认")
    void resolveDeviceMatchOverDefault() {
        Tool defaultTool = createTool("get_position", "default-result");
        Tool carTool = createTool("get_position", "car-result");
        dispatchingTool.setDefaultTool(defaultTool);
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        Tool resolved = dispatchingTool.resolve(DeviceContext.of("car", "2.0.0"));
        assertSame(carTool, resolved);
    }

    @Test
    @DisplayName("版本不匹配时回退到默认")
    void fallbackToDefaultWhenVersionMismatch() {
        Tool defaultTool = createTool("get_position", "default-result");
        Tool carV3Tool = createTool("get_position", "car-v3-result");
        dispatchingTool.setDefaultTool(defaultTool);
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.parse(">=3.0.0"), carV3Tool));

        Tool resolved = dispatchingTool.resolve(DeviceContext.of("car", "2.0.0"));
        assertSame(defaultTool, resolved);
    }

    @Test
    @DisplayName("多设备分发到各自实现")
    void multiDeviceDispatch() {
        Tool carTool = createTool("get_position", "car-result");
        Tool phoneTool = createTool("get_position", "phone-result");
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));
        dispatchingTool.addBinding(new DeviceToolBinding("phone", VersionRange.all(), phoneTool));

        assertSame(carTool, dispatchingTool.resolve(DeviceContext.of("car", "1.0.0")));
        assertSame(phoneTool, dispatchingTool.resolve(DeviceContext.of("phone", "1.0.0")));
    }

    @Test
    @DisplayName("高优先级绑定优先匹配")
    void higherPriorityWins() {
        Tool lowPriTool = createTool("get_position", "low");
        Tool highPriTool = createTool("get_position", "high");
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), lowPriTool, 0));
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), highPriTool, 10));

        Tool resolved = dispatchingTool.resolve(DeviceContext.of("car", "1.0.0"));
        assertSame(highPriTool, resolved);
    }

    @Test
    @DisplayName("DEFAULT context 走默认工具")
    void defaultContextUsesDefaultTool() {
        Tool defaultTool = createTool("get_position", "default");
        Tool carTool = createTool("get_position", "car");
        dispatchingTool.setDefaultTool(defaultTool);
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        assertSame(defaultTool, dispatchingTool.resolve(DeviceContext.DEFAULT));
    }

    @Test
    @DisplayName("null context 走默认工具")
    void nullContextUsesDefaultTool() {
        Tool defaultTool = createTool("get_position", "default");
        dispatchingTool.setDefaultTool(defaultTool);

        assertSame(defaultTool, dispatchingTool.resolve(null));
    }

    @Test
    @DisplayName("removeBindingsForDevice 只移除指定设备")
    void removeBindingsForDevice() {
        Tool carTool = createTool("get_position", "car");
        Tool phoneTool = createTool("get_position", "phone");
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));
        dispatchingTool.addBinding(new DeviceToolBinding("phone", VersionRange.all(), phoneTool));

        dispatchingTool.removeBindingsForDevice("car");

        assertEquals(1, dispatchingTool.getBindingCount());
        assertSame(phoneTool, dispatchingTool.resolve(DeviceContext.of("phone", "1.0.0")));
        // car 已移除，无匹配 → 走第一个 binding（phone）
        assertSame(phoneTool, dispatchingTool.resolve(DeviceContext.of("car", "1.0.0")));
    }

    @Test
    @DisplayName("isEmpty 正确判断")
    void isEmpty() {
        assertTrue(dispatchingTool.isEmpty());

        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(),
            createTool("get_position", "car")));
        assertFalse(dispatchingTool.isEmpty());

        dispatchingTool.removeBindingsForDevice("car");
        assertTrue(dispatchingTool.isEmpty());
    }

    @Test
    @DisplayName("Schema 和 Description 取 default 或第一个 binding")
    void schemaAndDescriptionFromRepresentative() {
        Tool carTool = createTool("get_position", "car");
        dispatchingTool.addBinding(new DeviceToolBinding("car", VersionRange.all(), carTool));

        assertEquals("get_position tool", dispatchingTool.getDescription());
        assertNotNull(dispatchingTool.getSchema());
    }

    // ==================== Helper ====================

    private Tool createTool(String name, String resultContent) {
        return new Tool() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return name + " tool"; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(resultContent);
            }
        };
    }
}
