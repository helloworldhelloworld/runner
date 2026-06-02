package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry — CRUD for DirectiveDescriptors")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String name) {
        return new DirectiveDescriptor(
                name,
                Collections.emptyList(),
                "down:" + name,
                "up:" + name,
                "test",
                5000,
                "1.0"
        );
    }

    @Nested
    @DisplayName("Register & lookup")
    class RegisterAndLookup {

        @Test
        @DisplayName("register and retrieve by tool name")
        void registerAndGet() {
            DirectiveDescriptor desc = descriptor("navigate");
            registry.register(desc);

            assertTrue(registry.has(desc.toToolName()));
            Optional<DirectiveDescriptor> found = registry.get(desc.toToolName());
            assertTrue(found.isPresent());
            assertEquals("down:navigate", found.get().getDownAction());
            assertEquals("up:navigate", found.get().getUpAction());
        }

        @Test
        @DisplayName("has returns false for unregistered name")
        void hasReturnsFalse() {
            assertFalse(registry.has("nonexistent"));
        }

        @Test
        @DisplayName("get returns empty for unregistered name")
        void getReturnsEmpty() {
            assertTrue(registry.get("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("register overwrites existing entry with same tool name")
        void overwrite() {
            DirectiveDescriptor first = new DirectiveDescriptor(
                    "nav", Collections.emptyList(), "old_down", "old_up", "ns", 1000, "1.0");
            DirectiveDescriptor second = new DirectiveDescriptor(
                    "nav", Collections.emptyList(), "new_down", "new_up", "ns", 2000, "2.0");

            registry.register(first);
            registry.register(second);

            assertEquals(1, registry.size());
            assertEquals("new_down", registry.get(second.toToolName()).get().getDownAction());
        }
    }

    @Nested
    @DisplayName("Unregister")
    class Unregister {

        @Test
        @DisplayName("unregister removes existing entry")
        void unregisterExisting() {
            DirectiveDescriptor desc = descriptor("test");
            registry.register(desc);

            registry.unregister(desc.toToolName());

            assertFalse(registry.has(desc.toToolName()));
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("unregister nonexistent does nothing")
        void unregisterNonexistent() {
            registry.unregister("ghost");
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("Bulk operations")
    class BulkOperations {

        @Test
        @DisplayName("getAll returns all registered descriptors")
        void getAll() {
            registry.register(descriptor("a"));
            registry.register(descriptor("b"));
            registry.register(descriptor("c"));

            List<DirectiveDescriptor> all = registry.getAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("getAll returns empty list when registry is empty")
        void getAllEmpty() {
            assertTrue(registry.getAll().isEmpty());
        }

        @Test
        @DisplayName("clear removes all entries")
        void clear() {
            registry.register(descriptor("x"));
            registry.register(descriptor("y"));

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.has("x"));
        }

        @Test
        @DisplayName("size reflects current count")
        void size() {
            assertEquals(0, registry.size());

            registry.register(descriptor("one"));
            assertEquals(1, registry.size());

            registry.register(descriptor("two"));
            assertEquals(2, registry.size());

            registry.unregister(descriptor("one").toToolName());
            assertEquals(1, registry.size());
        }
    }
}
