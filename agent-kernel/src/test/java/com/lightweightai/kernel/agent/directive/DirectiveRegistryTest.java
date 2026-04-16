package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - 客户端工具注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    @Test
    @DisplayName("初始状态为空")
    void shouldStartEmpty() {
        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("注册 Descriptor 后可通过 toolName 查找")
    void shouldRegisterAndGet() {
        DirectiveDescriptor desc = createDescriptor("get_position");
        registry.register(desc);

        assertTrue(registry.has("get_position"));
        Optional<DirectiveDescriptor> found = registry.get("get_position");
        assertTrue(found.isPresent());
        assertEquals("get_position", found.get().getTool());
    }

    @Test
    @DisplayName("has 对未注册的工具返回 false")
    void shouldReturnFalseForUnregistered() {
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    @DisplayName("get 对未注册的工具返回 empty")
    void shouldReturnEmptyForUnregistered() {
        assertEquals(Optional.empty(), registry.get("nonexistent"));
    }

    @Test
    @DisplayName("注册多个 Descriptor")
    void shouldRegisterMultiple() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        registry.register(createDescriptor("tool_c"));

        assertEquals(3, registry.size());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertTrue(registry.has("tool_c"));
    }

    @Test
    @DisplayName("getAll 返回所有已注册的 Descriptor")
    void shouldGetAll() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("unregister 移除指定工具")
    void shouldUnregister() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));

        registry.unregister("tool_a");

        assertFalse(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("unregister 不存在的工具不报错")
    void shouldSilentlyUnregisterNonExistent() {
        assertDoesNotThrow(() -> registry.unregister("nonexistent"));
    }

    @Test
    @DisplayName("clear 清空所有注册")
    void shouldClear() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));

        registry.clear();

        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("重复注册同名工具覆盖旧的")
    void shouldOverrideOnDuplicateRegister() {
        DirectiveDescriptor v1 = new DirectiveDescriptor("tool_a", "down_v1", "up_v1", "ns", 5000, "1.0");
        DirectiveDescriptor v2 = new DirectiveDescriptor("tool_a", "down_v2", "up_v2", "ns", 10000, "2.0");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        assertEquals("2.0", registry.get("tool_a").get().getVersion());
    }

    @Test
    @DisplayName("getAll 返回的列表是快照（修改不影响注册表）")
    void shouldReturnDefensiveCopy() {
        registry.register(createDescriptor("tool_a"));
        List<DirectiveDescriptor> all = registry.getAll();
        all.clear();

        assertEquals(1, registry.size());
    }

    private DirectiveDescriptor createDescriptor(String toolName) {
        return new DirectiveDescriptor(toolName, "down_" + toolName, "up_" + toolName,
                "default", 60000, "1.0");
    }
}
