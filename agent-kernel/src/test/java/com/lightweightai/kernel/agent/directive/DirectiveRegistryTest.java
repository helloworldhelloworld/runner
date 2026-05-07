package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ClientTool 注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String tool) {
        return new DirectiveDescriptor(tool, "down_" + tool, "up_" + tool, "ns", 60000, "1.0");
    }

    @Test
    @DisplayName("register + get → 注册后可查询")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("camera");
        registry.register(desc);

        Optional<DirectiveDescriptor> found = registry.get("camera");
        assertTrue(found.isPresent());
        assertEquals("camera", found.get().getTool());
        assertEquals("down_camera", found.get().getDownAction());
    }

    @Test
    @DisplayName("has → 判断工具是否已注册")
    void hasDetectsRegistration() {
        assertFalse(registry.has("camera"));

        registry.register(descriptor("camera"));

        assertTrue(registry.has("camera"));
    }

    @Test
    @DisplayName("unregister → 注销后不可查询")
    void unregisterRemoves() {
        registry.register(descriptor("gps"));
        assertTrue(registry.has("gps"));

        registry.unregister("gps");

        assertFalse(registry.has("gps"));
        assertTrue(registry.get("gps").isEmpty());
    }

    @Test
    @DisplayName("getAll → 返回所有已注册 descriptor")
    void getAllReturnsRegistered() {
        registry.register(descriptor("camera"));
        registry.register(descriptor("gps"));
        registry.register(descriptor("sign"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("size → 正确反映注册数量")
    void sizeTracksCount() {
        assertEquals(0, registry.size());

        registry.register(descriptor("a"));
        assertEquals(1, registry.size());

        registry.register(descriptor("b"));
        assertEquals(2, registry.size());

        registry.unregister("a");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("clear → 清空所有注册")
    void clearRemovesAll() {
        registry.register(descriptor("a"));
        registry.register(descriptor("b"));
        assertEquals(2, registry.size());

        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.has("a"));
        assertFalse(registry.has("b"));
    }

    @Test
    @DisplayName("重复注册相同 tool → 覆盖旧值")
    void duplicateRegistrationOverwrites() {
        DirectiveDescriptor v1 = new DirectiveDescriptor("cam", "down_v1", "up_v1", "ns", 5000, "1.0");
        DirectiveDescriptor v2 = new DirectiveDescriptor("cam", "down_v2", "up_v2", "ns", 10000, "2.0");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        assertEquals("down_v2", registry.get("cam").get().getDownAction());
    }

    @Test
    @DisplayName("get 不存在的 tool → 返回 empty Optional")
    void getMissingReturnsEmpty() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }
}
