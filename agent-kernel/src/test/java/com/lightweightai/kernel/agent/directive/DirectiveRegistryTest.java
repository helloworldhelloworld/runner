package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DirectiveRegistry — ClientTool directive registry")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String toolName) {
        DirectiveDescriptor desc = mock(DirectiveDescriptor.class);
        when(desc.toToolName()).thenReturn(toolName);
        return desc;
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void registerIncrementsSize() {
            assertEquals(0, registry.size());
            registry.register(descriptor("Camera.TakePhoto"));
            assertEquals(1, registry.size());
        }

        @Test
        void registerMakesToolAvailable() {
            registry.register(descriptor("Camera.TakePhoto"));
            assertTrue(registry.has("Camera.TakePhoto"));
        }

        @Test
        void registerSameNameOverwrites() {
            DirectiveDescriptor first = descriptor("Tool.Action");
            DirectiveDescriptor second = descriptor("Tool.Action");

            registry.register(first);
            registry.register(second);

            assertEquals(1, registry.size());
            assertSame(second, registry.get("Tool.Action").orElseThrow());
        }
    }

    @Nested
    @DisplayName("Unregistration")
    class Unregistration {

        @Test
        void unregisterRemovesTool() {
            registry.register(descriptor("Tool.A"));
            registry.unregister("Tool.A");
            assertFalse(registry.has("Tool.A"));
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
            DirectiveDescriptor desc = descriptor("Tool.X");
            registry.register(desc);

            Optional<DirectiveDescriptor> result = registry.get("Tool.X");
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
