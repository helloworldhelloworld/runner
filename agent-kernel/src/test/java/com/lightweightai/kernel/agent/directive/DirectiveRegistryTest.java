package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - Client tool directive CRUD")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    @Test
    @DisplayName("register and retrieve by tool name")
    void registerAndGet() {
        DirectiveDescriptor desc = descriptor("play_music");
        registry.register(desc);

        assertTrue(registry.has("play_music"));
        Optional<DirectiveDescriptor> found = registry.get("play_music");
        assertTrue(found.isPresent());
        assertEquals("play_music", found.get().getTool());
    }

    @Test
    @DisplayName("has returns false for unregistered tool")
    void hasReturnsFalseForMissing() {
        assertFalse(registry.has("nonexistent"));
    }

    @Test
    @DisplayName("get returns empty for unregistered tool")
    void getReturnsEmptyForMissing() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("unregister removes the directive")
    void unregister() {
        registry.register(descriptor("to_remove"));
        assertTrue(registry.has("to_remove"));

        registry.unregister("to_remove");
        assertFalse(registry.has("to_remove"));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getAll returns all registered directives")
    void getAll() {
        registry.register(descriptor("tool_a"));
        registry.register(descriptor("tool_b"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("size tracks count correctly")
    void sizeTracksCount() {
        assertEquals(0, registry.size());
        registry.register(descriptor("t1"));
        assertEquals(1, registry.size());
        registry.register(descriptor("t2"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("clear removes all directives")
    void clear() {
        registry.register(descriptor("t1"));
        registry.register(descriptor("t2"));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertFalse(registry.has("t1"));
    }

    @Test
    @DisplayName("re-register overwrites existing directive")
    void reRegisterOverwrites() {
        registry.register(descriptor("tool_a"));
        assertEquals(1, registry.size());

        registry.register(descriptor("tool_a"));
        assertEquals(1, registry.size());
    }

    private static DirectiveDescriptor descriptor(String toolName) {
        return new DirectiveDescriptor(
                toolName, "execute", "result",
                "test", 5000, "1.0.0"
        );
    }
}
