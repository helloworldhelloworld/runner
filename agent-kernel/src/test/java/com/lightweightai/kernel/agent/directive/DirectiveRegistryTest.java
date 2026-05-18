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
    @DisplayName("注册后可通过 toolName 查询到")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("Camera.TakePhoto");
        registry.register(desc);

        assertTrue(registry.has("Camera.TakePhoto"));
        Optional<DirectiveDescriptor> found = registry.get("Camera.TakePhoto");
        assertTrue(found.isPresent());
        assertEquals("Camera.TakePhoto", found.get().getTool());
    }

    @Test
    @DisplayName("注册后 size 增加")
    void registerIncreasesSize() {
        assertEquals(0, registry.size());
        registry.register(descriptor("GPS.GetLocation"));
        assertEquals(1, registry.size());
        registry.register(descriptor("NFC.ReadTag"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("unregister 移除指定工具")
    void unregisterRemoves() {
        registry.register(descriptor("Camera.TakePhoto"));
        registry.register(descriptor("GPS.GetLocation"));

        registry.unregister("Camera.TakePhoto");

        assertFalse(registry.has("Camera.TakePhoto"));
        assertTrue(registry.has("GPS.GetLocation"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("unregister 不存在的 key 不报错")
    void unregisterNonexistent() {
        assertDoesNotThrow(() -> registry.unregister("nonexistent"));
    }

    @Test
    @DisplayName("get 不存在的 key 返回 empty")
    void getReturnsEmpty() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("has 不存在的 key 返回 false")
    void hasReturnsFalse() {
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    @DisplayName("getAll 返回所有已注册描述符的快照")
    void getAllReturnsAll() {
        registry.register(descriptor("A.a"));
        registry.register(descriptor("B.b"));
        registry.register(descriptor("C.c"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("getAll 返回的是副本，修改不影响注册表")
    void getAllReturnsDefensiveCopy() {
        registry.register(descriptor("A.a"));
        List<DirectiveDescriptor> all = registry.getAll();

        all.clear();

        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("clear 清空所有注册")
    void clearRemovesAll() {
        registry.register(descriptor("A.a"));
        registry.register(descriptor("B.b"));

        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.has("A.a"));
        assertFalse(registry.has("B.b"));
    }

    @Test
    @DisplayName("注册相同 toolName 会覆盖")
    void registerOverwritesSameName() {
        DirectiveDescriptor v1 = new DirectiveDescriptor("cam", "down1", "up1", "ns1", 1000, "1.0");
        DirectiveDescriptor v2 = new DirectiveDescriptor("cam", "down2", "up2", "ns2", 2000, "2.0");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        DirectiveDescriptor found = registry.get("cam").orElseThrow();
        assertEquals("down2", found.getDownAction());
        assertEquals("2.0", found.getVersion());
    }

    @Test
    @DisplayName("toToolName 用于注册的 key 和 get 的 key 一致")
    void registryKeyIsToToolName() {
        DirectiveDescriptor desc = descriptor("GPS.GetLocation");
        String expectedKey = desc.toToolName();

        registry.register(desc);

        assertTrue(registry.has(expectedKey));
        assertEquals(desc, registry.get(expectedKey).orElse(null));
    }

    private DirectiveDescriptor descriptor(String tool) {
        return new DirectiveDescriptor(tool, "down_" + tool, "up_" + tool, "ns", 5000, "1.0");
    }
}
