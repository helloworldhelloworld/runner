package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry — register, lookup, and lifecycle of client tool descriptors")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String tool) {
        return new DirectiveDescriptor(tool, "down", "up", "TestNS", 5000, "1.0");
    }

    @Test
    @DisplayName("register and lookup by tool name")
    void registerAndLookup() {
        DirectiveDescriptor desc = descriptor("take_photo");
        registry.register(desc);

        assertTrue(registry.has("take_photo"));
        Optional<DirectiveDescriptor> found = registry.get("take_photo");
        assertTrue(found.isPresent());
        assertEquals("take_photo", found.get().getTool());
    }

    @Test
    @DisplayName("get returns empty for unregistered tool")
    void getUnregisteredReturnsEmpty() {
        assertFalse(registry.has("nonexistent"));
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("unregister removes the descriptor")
    void unregister() {
        registry.register(descriptor("cam"));
        assertTrue(registry.has("cam"));

        registry.unregister("cam");
        assertFalse(registry.has("cam"));
    }

    @Test
    @DisplayName("size tracks registered descriptors")
    void sizeTracking() {
        assertEquals(0, registry.size());

        registry.register(descriptor("a"));
        registry.register(descriptor("b"));
        assertEquals(2, registry.size());

        registry.unregister("a");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("getAll returns snapshot of all descriptors")
    void getAllReturnsSnapshot() {
        registry.register(descriptor("x"));
        registry.register(descriptor("y"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("clear removes all descriptors")
    void clear() {
        registry.register(descriptor("a"));
        registry.register(descriptor("b"));
        registry.clear();

        assertEquals(0, registry.size());
        assertFalse(registry.has("a"));
    }

    @Test
    @DisplayName("re-registering same tool name overwrites previous")
    void reRegisterOverwrites() {
        DirectiveDescriptor v1 = new DirectiveDescriptor("cam", "downV1", "upV1", "NS1", 1000, "1.0");
        DirectiveDescriptor v2 = new DirectiveDescriptor("cam", "downV2", "upV2", "NS2", 2000, "2.0");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        DirectiveDescriptor found = registry.get("cam").orElseThrow();
        assertEquals("NS2", found.getNamespace());
        assertEquals("2.0", found.getVersion());
    }
}
