package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ClientTool 注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    @Test
    @DisplayName("register + get 能正确存取 descriptor")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("TakePhoto", "camera.takePhoto",
                "camera.photoResult", "Camera", 30000, "1.0");

        registry.register(desc);

        assertTrue(registry.has("TakePhoto"));
        assertEquals(desc, registry.get("TakePhoto").orElse(null));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("unregister 移除后 has 返回 false")
    void unregisterRemovesEntry() {
        registry.register(descriptor("GetLocation", "gps.getLocation",
                "gps.locationResult", "GPS", 10000, "1.0"));

        registry.unregister("GetLocation");

        assertFalse(registry.has("GetLocation"));
        assertTrue(registry.get("GetLocation").isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("get 对不存在的 toolName 返回 empty")
    void getReturnsEmptyForUnknown() {
        assertTrue(registry.get("NonExistent").isEmpty());
    }

    @Test
    @DisplayName("getAll 返回所有已注册的 descriptor")
    void getAllReturnsAllRegistered() {
        DirectiveDescriptor d1 = descriptor("A", "a.down", "a.up", "NS1", 5000, "1.0");
        DirectiveDescriptor d2 = descriptor("B", "b.down", "b.up", "NS2", 5000, "2.0");

        registry.register(d1);
        registry.register(d2);

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(d1));
        assertTrue(all.contains(d2));
    }

    @Test
    @DisplayName("clear 清空所有注册项")
    void clearRemovesAll() {
        registry.register(descriptor("X", "x.down", "x.up", "NS", 1000, "1.0"));
        registry.register(descriptor("Y", "y.down", "y.up", "NS", 1000, "1.0"));

        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.has("X"));
        assertFalse(registry.has("Y"));
    }

    @Test
    @DisplayName("重复 register 相同 toolName 会覆盖旧值")
    void registerOverwritesSameToolName() {
        DirectiveDescriptor v1 = descriptor("Photo", "cam.photo", "cam.result", "Cam", 5000, "1.0");
        DirectiveDescriptor v2 = descriptor("Photo", "cam.photo", "cam.result", "Cam", 10000, "2.0");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        DirectiveDescriptor stored = registry.get("Photo").orElse(null);
        assertNotNull(stored);
        assertEquals("2.0", stored.getVersion());
        assertEquals(10000, stored.getTimeoutMs());
    }

    private static DirectiveDescriptor descriptor(String tool, String downAction, String upAction,
                                                   String namespace, long timeoutMs, String version) {
        return new DirectiveDescriptor(tool, downAction, upAction, namespace, timeoutMs, version);
    }
}
