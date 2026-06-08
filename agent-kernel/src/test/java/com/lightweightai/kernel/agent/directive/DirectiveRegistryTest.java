package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ClientTool 注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    @Test
    @DisplayName("注册后可通过 has/get 查找")
    void registerAndGet() {
        DirectiveDescriptor desc = createDescriptor("myTool");
        registry.register(desc);

        assertTrue(registry.has("myTool"));
        Optional<DirectiveDescriptor> found = registry.get("myTool");
        assertTrue(found.isPresent());
        assertEquals("myTool", found.get().getTool());
    }

    @Test
    @DisplayName("未注册的 tool 返回 empty")
    void getUnregisteredReturnsEmpty() {
        assertFalse(registry.has("unknown"));
        assertTrue(registry.get("unknown").isEmpty());
    }

    @Test
    @DisplayName("unregister 移除已注册的 directive")
    void unregister() {
        registry.register(createDescriptor("tool1"));
        registry.unregister("tool1");

        assertFalse(registry.has("tool1"));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getAll 返回所有已注册的 directive")
    void getAll() {
        registry.register(createDescriptor("a"));
        registry.register(createDescriptor("b"));

        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("clear 清空所有 directive")
    void clear() {
        registry.register(createDescriptor("a"));
        registry.register(createDescriptor("b"));
        registry.clear();

        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("size 反映当前注册数量")
    void sizeTracksRegistrations() {
        assertEquals(0, registry.size());
        registry.register(createDescriptor("x"));
        assertEquals(1, registry.size());
    }

    private static DirectiveDescriptor createDescriptor(String toolName) {
        return new DirectiveDescriptor(toolName, "down_" + toolName, "up_" + toolName,
            "namespace", 5000, "1.0.0");
    }
}
