package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry — ClientTool directive registry")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String toolName) {
        return new DirectiveDescriptor(toolName, "down", "up", "ns", 5000, "1.0");
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void registerIncrementsSize() {
            assertEquals(0, registry.size());
            registry.register(descriptor("TakePhoto"));
            assertEquals(1, registry.size());
        }

        @Test
        void registerMakesToolAvailable() {
            registry.register(descriptor("TakePhoto"));
            assertTrue(registry.has("TakePhoto"));
        }

        @Test
        void registerSameNameOverwrites() {
            DirectiveDescriptor first = descriptor("ToolA");
            DirectiveDescriptor second = descriptor("ToolA");

            registry.register(first);
            registry.register(second);

            assertEquals(1, registry.size());
            assertSame(second, registry.get("ToolA").orElseThrow());
        }
    }

    @Nested
    @DisplayName("Unregistration")
    class Unregistration {

        @Test
        void unregisterRemovesTool() {
            registry.register(descriptor("ToolA"));
            registry.unregister("ToolA");
            assertFalse(registry.has("ToolA"));
            assertEquals(0, registry.size());
        }

        @Test
        void unregisterNonexistentIsNoop() {
            assertDoesNotThrow(() -> registry.unregister("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Retrieval")
    class Retrieval {

        @Test
        void getReturnsOptionalWithDescriptor() {
            DirectiveDescriptor desc = descriptor("ToolX");
            registry.register(desc);

            Optional<DirectiveDescriptor> result = registry.get("ToolX");
            assertTrue(result.isPresent());
            assertSame(desc, result.get());
        }

        @Test
        void getReturnsEmptyForMissing() {
            Optional<DirectiveDescriptor> result = registry.get("missing");
            assertTrue(result.isEmpty());
        }

        @Test
        void hasReturnsFalseForMissing() {
            assertFalse(registry.has("nope"));
        }
    }

    @Nested
    @DisplayName("getAll — returns copy")
    class GetAll {

        @Test
        void returnsAllRegistered() {
            registry.register(descriptor("A"));
            registry.register(descriptor("B"));
            registry.register(descriptor("C"));

            List<DirectiveDescriptor> all = registry.getAll();
            assertEquals(3, all.size());
        }

        @Test
        void returnsDefensiveCopy() {
            registry.register(descriptor("A"));
            List<DirectiveDescriptor> all = registry.getAll();
            all.clear();
            assertEquals(1, registry.size());
        }

        @Test
        void emptyRegistryReturnsEmptyList() {
            assertTrue(registry.getAll().isEmpty());
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        void removesAllEntries() {
            registry.register(descriptor("A"));
            registry.register(descriptor("B"));
            assertEquals(2, registry.size());

            registry.clear();
            assertEquals(0, registry.size());
            assertFalse(registry.has("A"));
            assertFalse(registry.has("B"));
        }
    }
}
