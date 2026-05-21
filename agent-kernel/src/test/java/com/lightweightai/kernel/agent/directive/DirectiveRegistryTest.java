package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry — thread-safe directive storage")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor desc(String name) {
        return new DirectiveDescriptor(name, "Down", "Up", "NS", 1000, "1.0");
    }

    @Test
    @DisplayName("register and retrieve by tool name")
    void registerAndGet() {
        DirectiveDescriptor d = desc("Foo");
        registry.register(d);

        assertTrue(registry.has("Foo"));
        assertEquals("Foo", registry.get("Foo").orElseThrow().getTool());
    }

    @Test
    @DisplayName("get returns empty for unregistered name")
    void getReturnsEmpty() {
        assertFalse(registry.has("Unknown"));
        assertTrue(registry.get("Unknown").isEmpty());
    }

    @Test
    @DisplayName("unregister removes descriptor")
    void unregister() {
        registry.register(desc("Bar"));
        assertTrue(registry.has("Bar"));

        registry.unregister("Bar");
        assertFalse(registry.has("Bar"));
    }

    @Test
    @DisplayName("unregister nonexistent name is a no-op")
    void unregisterNonexistent() {
        assertDoesNotThrow(() -> registry.unregister("Ghost"));
    }

    @Test
    @DisplayName("getAll returns all registered descriptors")
    void getAll() {
        registry.register(desc("A"));
        registry.register(desc("B"));
        registry.register(desc("C"));

        List<DirectiveDescriptor> all = registry.getAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("size reflects current registration count")
    void sizeTracker() {
        assertEquals(0, registry.size());
        registry.register(desc("X"));
        assertEquals(1, registry.size());
        registry.register(desc("Y"));
        assertEquals(2, registry.size());
        registry.unregister("X");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("clear removes all registrations")
    void clearRemovesAll() {
        registry.register(desc("A"));
        registry.register(desc("B"));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertFalse(registry.has("A"));
    }

    @Test
    @DisplayName("re-registering same name overwrites previous descriptor")
    void reRegisterOverwrites() {
        DirectiveDescriptor first = new DirectiveDescriptor(
            "Tool", "Down1", "Up1", "NS1", 1000, "1.0");
        DirectiveDescriptor second = new DirectiveDescriptor(
            "Tool", "Down2", "Up2", "NS2", 2000, "2.0");

        registry.register(first);
        registry.register(second);

        assertEquals(1, registry.size());
        DirectiveDescriptor stored = registry.get("Tool").orElseThrow();
        assertEquals("Down2", stored.getDownAction());
        assertEquals("NS2", stored.getNamespace());
    }
}
