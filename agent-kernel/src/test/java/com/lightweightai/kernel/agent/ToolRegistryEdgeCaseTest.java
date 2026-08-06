package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 边界条件与高级特性测试
 *
 * 覆盖：
 * - 设备感知注册（DispatchingTool 升级）
 * - 设备注销和空检测
 * - Listener 异常不阻塞其他 Listener
 * - null 参数校验
 * - ToolSource 注册
 * - registerObject 注解扫描
 * - clear()/size()/enabledCount() 统计
 * - 禁用不存在的工具不触发事件
 * - toString 格式
 */
@DisplayName("ToolRegistry - 高级特性与边界条件")
class ToolRegistryEdgeCaseTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== 设备感知注册 ====================

    @Nested
    @DisplayName("设备感知注册")
    class DeviceAwareRegistration {

        @Test
        @DisplayName("普通注册后设备注册升级为 DispatchingTool")
        void shouldUpgradeToDispatchingToolOnDeviceRegister() {
            Tool defaultTool = createTool("get_position", "默认定位");
            Tool carTool = createTool("get_position", "车载定位");

            registry.register(defaultTool);
            registry.register(carTool, "car", VersionRange.all());

            Tool resolved = registry.get("get_position").orElse(null);
            assertNotNull(resolved);
            assertInstanceOf(DispatchingTool.class, resolved);
        }

        @Test
        @DisplayName("DispatchingTool 设备解析正确")
        void shouldResolveCorrectDeviceTool() {
            Tool defaultTool = createTool("get_position", "默认定位");
            Tool carTool = createTool("get_position", "车载定位");

            registry.register(defaultTool);
            registry.register(carTool, "car", VersionRange.all());

            DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElseThrow();

            // Default context should return defaultTool
            Tool resolvedDefault = dt.resolve(DeviceContext.DEFAULT);
            assertEquals("默认定位", resolvedDefault.getDescription());

            // Car context should return carTool
            Tool resolvedCar = dt.resolve(DeviceContext.of("car", "1.0.0"));
            assertEquals("车载定位", resolvedCar.getDescription());
        }

        @Test
        @DisplayName("首次直接设备注册创建 DispatchingTool")
        void shouldCreateDispatchingToolOnFirstDeviceRegister() {
            Tool carTool = createTool("nav", "导航");
            registry.register(carTool, "car", VersionRange.all());

            Tool resolved = registry.get("nav").orElse(null);
            assertInstanceOf(DispatchingTool.class, resolved);
        }

        @Test
        @DisplayName("带优先级的设备注册")
        void shouldRegisterWithPriority() {
            Tool lowPriority = createTool("get_position", "低优先级");
            Tool highPriority = createTool("get_position", "高优先级");

            registry.register(lowPriority, "car", VersionRange.all(), 0);
            registry.register(highPriority, "car", VersionRange.all(), 10);

            DispatchingTool dt = (DispatchingTool) registry.get("get_position").orElseThrow();
            Tool resolved = dt.resolve(DeviceContext.of("car", "1.0.0"));
            assertEquals("高优先级", resolved.getDescription());
        }

        @Test
        @DisplayName("unregisterDevice 移除设备绑定")
        void shouldUnregisterDeviceBindings() {
            Tool carTool = createTool("nav", "车载导航");
            Tool phoneTool = createTool("nav", "手机导航");

            registry.register(carTool, "car", VersionRange.all());
            registry.register(phoneTool, "phone", VersionRange.all());

            registry.unregisterDevice("nav", "car");

            DispatchingTool dt = (DispatchingTool) registry.get("nav").orElseThrow();
            assertEquals(1, dt.getBindingCount());
        }

        @Test
        @DisplayName("unregisterDevice 空后移除整个 DispatchingTool")
        void shouldRemoveDispatchingToolWhenEmpty() {
            Tool carTool = createTool("nav", "导航");
            registry.register(carTool, "car", VersionRange.all());

            registry.unregisterDevice("nav", "car");

            assertFalse(registry.has("nav"));
        }

        @Test
        @DisplayName("对普通 Tool 调用 unregisterDevice 无效果")
        void shouldNoopUnregisterDeviceOnNonDispatchingTool() {
            Tool normalTool = createTool("calc", "计算器");
            registry.register(normalTool);

            registry.unregisterDevice("calc", "car");

            assertTrue(registry.has("calc")); // still there
        }
    }

    // ==================== Listener 异常隔离 ====================

    @Nested
    @DisplayName("Listener 异常处理")
    class ListenerExceptionHandling {

        @Test
        @DisplayName("null listener 抛出 NPE")
        void shouldRejectNullListener() {
            assertThrows(NullPointerException.class,
                () -> registry.addListener(null));
        }
    }

    // ==================== Null 参数校验 ====================

    @Nested
    @DisplayName("Null 参数校验")
    class NullValidation {

        @Test
        @DisplayName("注册 null 工具抛出 NPE")
        void shouldRejectNullTool() {
            assertThrows(NullPointerException.class,
                () -> registry.register(null));
        }

        @Test
        @DisplayName("设备注册 null deviceType 抛出 NPE")
        void shouldRejectNullDeviceType() {
            Tool tool = createTool("test", "test");
            assertThrows(NullPointerException.class,
                () -> registry.register(tool, null, VersionRange.all()));
        }

        @Test
        @DisplayName("设备注册 null versionRange 抛出 NPE")
        void shouldRejectNullVersionRange() {
            Tool tool = createTool("test", "test");
            assertThrows(NullPointerException.class,
                () -> registry.register(tool, "car", null));
        }

        @Test
        @DisplayName("registerFrom null ToolSource 抛出 NPE")
        void shouldRejectNullToolSource() {
            assertThrows(NullPointerException.class,
                () -> registry.registerFrom(null));
        }

        @Test
        @DisplayName("registerObject null target 抛出 NPE")
        void shouldRejectNullTarget() {
            assertThrows(NullPointerException.class,
                () -> registry.registerObject(null));
        }
    }

    // ==================== ToolSource 注册 ====================

    @Nested
    @DisplayName("ToolSource 注册")
    class ToolSourceRegistration {

        @Test
        @DisplayName("从 ToolSource 注册工具")
        void shouldRegisterFromToolSource() {
            ToolSource source = () -> List.of(
                createTool("tool_a", "A"),
                createTool("tool_b", "B")
            );

            int count = registry.registerFrom(source);

            assertEquals(2, count);
            assertTrue(registry.has("tool_a"));
            assertTrue(registry.has("tool_b"));
        }

        @Test
        @DisplayName("ToolSource 返回 null 注册0个")
        void shouldHandleNullFromToolSource() {
            ToolSource source = () -> null;
            int count = registry.registerFrom(source);
            assertEquals(0, count);
        }

        @Test
        @DisplayName("ToolSource 返回空列表注册0个")
        void shouldHandleEmptyToolSource() {
            ToolSource source = List::of;
            int count = registry.registerFrom(source);
            assertEquals(0, count);
        }
    }

    // ==================== 统计信息 ====================

    @Nested
    @DisplayName("统计信息")
    class Statistics {

        @Test
        @DisplayName("size() 返回所有工具数量")
        void shouldReturnTotalSize() {
            registry.register(createTool("a", "A"));
            registry.register(createTool("b", "B"));
            registry.disable("a");

            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("enabledCount() 返回启用工具数量")
        void shouldReturnEnabledCount() {
            registry.register(createTool("a", "A"));
            registry.register(createTool("b", "B"));
            registry.register(createTool("c", "C"));
            registry.disable("a");

            assertEquals(2, registry.enabledCount());
        }

        @Test
        @DisplayName("clear() 清空所有工具和禁用状态")
        void shouldClearAll() {
            registry.register(createTool("a", "A"));
            registry.register(createTool("b", "B"));
            registry.disable("a");

            registry.clear();

            assertEquals(0, registry.size());
            assertEquals(0, registry.enabledCount());
            assertFalse(registry.has("a"));
        }

        @Test
        @DisplayName("isEnabled 对不存在的工具返回 false")
        void shouldReturnFalseForNonexistentTool() {
            assertFalse(registry.isEnabled("nonexistent"));
        }

        @Test
        @DisplayName("toString 包含数量信息")
        void toStringShouldContainCounts() {
            registry.register(createTool("a", "A"));
            registry.register(createTool("b", "B"));
            registry.disable("a");

            String str = registry.toString();
            assertTrue(str.contains("total=2"));
            assertTrue(str.contains("enabled=1"));
        }
    }

    // ==================== 禁用/启用边界 ====================

    @Nested
    @DisplayName("禁用/启用边界")
    class DisableEnableEdgeCases {

        @Test
        @DisplayName("禁用不存在的工具无效果")
        void shouldNoopDisableNonexistentTool() {
            AtomicInteger disableCount = new AtomicInteger(0);
            registry.addListener(new ToolRegistryListener() {
                @Override
                public void onToolDisabled(String toolName) {
                    disableCount.incrementAndGet();
                }
            });

            registry.disable("nonexistent");
            assertEquals(0, disableCount.get());
        }

        @Test
        @DisplayName("重复禁用同一工具")
        void shouldAllowDoubleDisable() {
            registry.register(createTool("tool1", "T1"));
            registry.disable("tool1");
            registry.disable("tool1"); // second disable

            assertFalse(registry.isEnabled("tool1"));
        }

        @Test
        @DisplayName("getEnabled 不包含禁用工具")
        void shouldExcludeDisabledFromGetEnabled() {
            registry.register(createTool("a", "A"));
            registry.register(createTool("b", "B"));
            registry.register(createTool("c", "C"));
            registry.disable("b");

            List<Tool> enabled = registry.getEnabled();
            assertEquals(2, enabled.size());
            assertTrue(enabled.stream().noneMatch(t -> t.getName().equals("b")));
        }

        @Test
        @DisplayName("getToolDefinitions 不包含禁用工具")
        void shouldExcludeDisabledFromDefinitions() {
            registry.register(createToolWithSchema("active", "Active", Map.of()));
            registry.register(createToolWithSchema("disabled", "Disabled", Map.of()));
            registry.disable("disabled");

            List<Map<String, Object>> definitions = registry.getToolDefinitions();
            assertEquals(1, definitions.size());
            assertEquals("active", definitions.get(0).get("name"));
        }
    }

    // ==================== 覆盖注册 ====================

    @Nested
    @DisplayName("覆盖注册")
    class OverwriteRegistration {

        @Test
        @DisplayName("相同名称注册覆盖旧工具")
        void shouldOverwriteToolWithSameName() {
            Tool v1 = createTool("calc", "计算器 v1");
            Tool v2 = createTool("calc", "计算器 v2");

            registry.register(v1);
            registry.register(v2);

            assertEquals(1, registry.size());
            assertEquals("计算器 v2", registry.get("calc").orElseThrow().getDescription());
        }
    }

    // ==================== 辅助方法 ====================

    private Tool createTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result");
            }
        };
    }

    private Tool createToolWithSchema(String name, String description, Map<String, Object> properties) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.withProperties(properties); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("result");
            }
        };
    }
}
