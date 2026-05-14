package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectiveRegistry 单元测试
 */
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        ClientToolAliasRegistry.reset();
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String toolName) {
        return new DirectiveDescriptor(toolName, "down", "up", "ns", 5000, "1.0");
    }

    @Test
    @DisplayName("register + get - 注册后通过工具名能获取描述符")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("myTool");
        registry.register(desc);

        Optional<DirectiveDescriptor> result = registry.get("myTool");

        assertTrue(result.isPresent());
        assertEquals("myTool", result.get().getTool());
        assertEquals("down", result.get().getDownAction());
        assertEquals("up", result.get().getUpAction());
        assertEquals("ns", result.get().getNamespace());
        assertEquals(5000, result.get().getTimeoutMs());
    }

    @Test
    @DisplayName("has 注册后返回 true")
    void hasReturnsTrueAfterRegister() {
        registry.register(descriptor("myTool"));

        assertTrue(registry.has("myTool"));
    }

    @Test
    @DisplayName("has 未注册时返回 false")
    void hasReturnsFalseForUnregistered() {
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    @DisplayName("unregister 移除已注册条目")
    void unregisterRemovesEntry() {
        registry.register(descriptor("myTool"));
        assertTrue(registry.has("myTool"));

        registry.unregister("myTool");

        assertFalse(registry.has("myTool"));
        assertTrue(registry.get("myTool").isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getAll 返回所有已注册描述符")
    void getAllReturnsAllRegistered() {
        registry.register(descriptor("tool1"));
        registry.register(descriptor("tool2"));
        registry.register(descriptor("tool3"));

        List<DirectiveDescriptor> all = registry.getAll();

        assertEquals(3, all.size());
        // Verify all tool names are present (order not guaranteed in ConcurrentHashMap)
        List<String> names = all.stream().map(DirectiveDescriptor::getTool).sorted().toList();
        assertEquals(List.of("tool1", "tool2", "tool3"), names);
    }

    @Test
    @DisplayName("size 返回正确的注册数量")
    void sizeReturnsCorrectCount() {
        assertEquals(0, registry.size());

        registry.register(descriptor("tool1"));
        assertEquals(1, registry.size());

        registry.register(descriptor("tool2"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("clear 清空所有注册条目")
    void clearEmptiesEverything() {
        registry.register(descriptor("tool1"));
        registry.register(descriptor("tool2"));
        assertEquals(2, registry.size());

        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.has("tool1"));
        assertFalse(registry.has("tool2"));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("get 未注册工具返回 empty Optional")
    void getReturnsEmptyForUnregistered() {
        Optional<DirectiveDescriptor> result = registry.get("nonexistent");

        assertTrue(result.isEmpty());
    }
}
