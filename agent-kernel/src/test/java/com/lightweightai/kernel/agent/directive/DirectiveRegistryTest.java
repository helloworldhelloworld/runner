package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry — CRUD and thread safety")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String toolName) {
        return new DirectiveDescriptor(toolName, "down_" + toolName, "up_" + toolName,
                "ns", 5000, "1.0");
    }

    @Test
    @DisplayName("register and retrieve by tool name")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("myTool");
        registry.register(desc);

        Optional<DirectiveDescriptor> found = registry.get("myTool");
        assertTrue(found.isPresent());
        assertEquals("myTool", found.get().getTool());
        assertEquals("down_myTool", found.get().getDownAction());
        assertEquals("up_myTool", found.get().getUpAction());
    }

    @Test
    @DisplayName("has() returns true for registered, false for unregistered")
    void hasReturnsCorrectly() {
        registry.register(descriptor("exists"));

        assertTrue(registry.has("exists"));
        assertFalse(registry.has("missing"));
    }

    @Test
    @DisplayName("get() returns empty for unregistered tool")
    void getReturnsEmptyForMissing() {
        assertTrue(registry.get("nope").isEmpty());
    }

    @Test
    @DisplayName("unregister removes the directive")
    void unregisterRemoves() {
        registry.register(descriptor("temp"));
        assertTrue(registry.has("temp"));

        registry.unregister("temp");
        assertFalse(registry.has("temp"));
        assertTrue(registry.get("temp").isEmpty());
    }

    @Test
    @DisplayName("unregister nonexistent name does not throw")
    void unregisterNonexistentDoesNotThrow() {
        assertDoesNotThrow(() -> registry.unregister("ghost"));
    }

    @Test
    @DisplayName("getAll() returns all registered directives")
    void getAllReturnsAll() {
        registry.register(descriptor("a"));
        registry.register(descriptor("b"));
        registry.register(descriptor("c"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("getAll() returns defensive copy")
    void getAllReturnsDefensiveCopy() {
        registry.register(descriptor("x"));
        List<DirectiveDescriptor> list = registry.getAll();

        list.clear();
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("size() reflects registration count")
    void sizeReflectsCount() {
        assertEquals(0, registry.size());
        registry.register(descriptor("a"));
        assertEquals(1, registry.size());
        registry.register(descriptor("b"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("clear() removes all entries")
    void clearRemovesAll() {
        registry.register(descriptor("a"));
        registry.register(descriptor("b"));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertFalse(registry.has("a"));
    }

    @Test
    @DisplayName("re-register overwrites existing entry")
    void reRegisterOverwrites() {
        registry.register(new DirectiveDescriptor("tool", "down1", "up1", "ns", 1000, "1.0"));
        registry.register(new DirectiveDescriptor("tool", "down2", "up2", "ns", 2000, "2.0"));

        assertEquals(1, registry.size());
        DirectiveDescriptor desc = registry.get("tool").orElseThrow();
        assertEquals("down2", desc.getDownAction());
        assertEquals("2.0", desc.getVersion());
    }
}
