package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - Thread-safe directive CRUD")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String tool) {
        return new DirectiveDescriptor(tool, "down", "up", "ns", 5000, "v1");
    }

    @Test
    @DisplayName("register and retrieve descriptor")
    void shouldRegisterAndGet() {
        DirectiveDescriptor desc = descriptor("camera_photo");
        registry.register(desc);

        assertTrue(registry.has("camera_photo"));
        Optional<DirectiveDescriptor> found = registry.get("camera_photo");
        assertTrue(found.isPresent());
        assertEquals("camera_photo", found.get().getTool());
    }

    @Test
    @DisplayName("get returns empty for unknown tool")
    void shouldReturnEmptyForUnknown() {
        assertFalse(registry.has("nonexistent"));
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("unregister removes descriptor")
    void shouldUnregister() {
        registry.register(descriptor("tool_a"));
        assertEquals(1, registry.size());

        registry.unregister("tool_a");
        assertFalse(registry.has("tool_a"));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getAll returns all registered descriptors")
    void shouldGetAll() {
        registry.register(descriptor("tool_a"));
        registry.register(descriptor("tool_b"));
        registry.register(descriptor("tool_c"));

        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("clear removes everything")
    void shouldClear() {
        registry.register(descriptor("tool_a"));
        registry.register(descriptor("tool_b"));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("register overwrites existing descriptor with same tool name")
    void shouldOverwriteExisting() {
        DirectiveDescriptor v1 = new DirectiveDescriptor("tool_x", "downA", "upA", "nsA", 1000, "v1");
        DirectiveDescriptor v2 = new DirectiveDescriptor("tool_x", "downB", "upB", "nsB", 2000, "v2");

        registry.register(v1);
        registry.register(v2);

        assertEquals(1, registry.size());
        assertEquals("v2", registry.get("tool_x").orElseThrow().getVersion());
        assertEquals("downB", registry.get("tool_x").orElseThrow().getDownAction());
    }
}
