package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - 指令注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    @Test
    @DisplayName("初始为空")
    void shouldStartEmpty() {
        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("注册和查找 Directive")
    void shouldRegisterAndFind() {
        DirectiveDescriptor desc = createDescriptor("take_photo");
        registry.register(desc);

        assertTrue(registry.has("take_photo"));
        assertTrue(registry.get("take_photo").isPresent());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("注销 Directive")
    void shouldUnregister() {
        DirectiveDescriptor desc = createDescriptor("take_photo");
        registry.register(desc);
        registry.unregister("take_photo");

        assertFalse(registry.has("take_photo"));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("注销不存在的 key 不抛异常")
    void shouldNotThrowOnUnregisterMissing() {
        assertDoesNotThrow(() -> registry.unregister("nonexistent"));
    }

    @Test
    @DisplayName("查找不存在的返回 empty")
    void shouldReturnEmptyForMissing() {
        assertFalse(registry.has("nonexistent"));
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("getAll 返回所有已注册")
    void shouldReturnAll() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));

        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("clear 清空所有")
    void shouldClearAll() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        registry.clear();

        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("重复注册覆盖旧值")
    void shouldOverwriteOnDuplicate() {
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_a"));

        assertEquals(1, registry.size());
    }

    private DirectiveDescriptor createDescriptor(String toolName) {
        return new DirectiveDescriptor(
            toolName, "down_action", "up_action",
            "namespace", 30000, "1.0.0"
        );
    }
}
