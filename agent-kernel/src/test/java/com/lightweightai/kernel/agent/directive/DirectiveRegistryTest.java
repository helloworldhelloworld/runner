package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ClientTool registration and lookup")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor createDescriptor(String tool) {
        return new DirectiveDescriptor(tool, "DownAction", "UpAction", "TestNs", 5000, "1.0");
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("register adds descriptor and get retrieves it")
        void shouldRegisterAndRetrieve() {
            DirectiveDescriptor descriptor = createDescriptor("TakePhoto");
            registry.register(descriptor);

            Optional<DirectiveDescriptor> found = registry.get("TakePhoto");
            assertTrue(found.isPresent());
            assertEquals("TakePhoto", found.get().getTool());
            assertEquals("DownAction", found.get().getDownAction());
            assertEquals("UpAction", found.get().getUpAction());
            assertEquals("TestNs", found.get().getNamespace());
        }

        @Test
        @DisplayName("register overwrites existing descriptor with same tool name")
        void shouldOverwriteExisting() {
            registry.register(createDescriptor("TakePhoto"));
            DirectiveDescriptor updated = new DirectiveDescriptor(
                "TakePhoto", "NewDown", "NewUp", "CameraNs", 10000, "2.0");
            registry.register(updated);

            assertEquals(1, registry.size());
            DirectiveDescriptor found = registry.get("TakePhoto").orElseThrow();
            assertEquals("NewDown", found.getDownAction());
            assertEquals("2.0", found.getVersion());
        }

        @Test
        @DisplayName("register multiple descriptors increases size")
        void shouldTrackMultipleDescriptors() {
            registry.register(createDescriptor("ToolA"));
            registry.register(createDescriptor("ToolB"));
            registry.register(createDescriptor("ToolC"));

            assertEquals(3, registry.size());
        }
    }

    @Nested
    @DisplayName("Lookup")
    class Lookup {

        @Test
        @DisplayName("get returns empty for unregistered tool name")
        void shouldReturnEmptyForUnregistered() {
            Optional<DirectiveDescriptor> result = registry.get("NonExistent");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("has returns true for registered tool")
        void shouldReturnTrueForRegistered() {
            registry.register(createDescriptor("GPS"));
            assertTrue(registry.has("GPS"));
        }

        @Test
        @DisplayName("has returns false for unregistered tool")
        void shouldReturnFalseForUnregistered() {
            assertFalse(registry.has("GPS"));
        }

        @Test
        @DisplayName("getAll returns all registered descriptors")
        void shouldReturnAllDescriptors() {
            registry.register(createDescriptor("ToolA"));
            registry.register(createDescriptor("ToolB"));

            List<DirectiveDescriptor> all = registry.getAll();
            assertEquals(2, all.size());
        }

        @Test
        @DisplayName("getAll returns copy, not internal collection")
        void getAllShouldReturnCopy() {
            registry.register(createDescriptor("ToolA"));
            List<DirectiveDescriptor> all = registry.getAll();
            int sizeBefore = all.size();

            registry.register(createDescriptor("ToolB"));
            assertEquals(sizeBefore, all.size(), "Returned list should not reflect later changes");
        }
    }

    @Nested
    @DisplayName("Unregistration and clear")
    class Unregistration {

        @Test
        @DisplayName("unregister removes descriptor by tool name")
        void shouldRemoveByToolName() {
            registry.register(createDescriptor("ToolA"));
            registry.register(createDescriptor("ToolB"));

            registry.unregister("ToolA");

            assertFalse(registry.has("ToolA"));
            assertTrue(registry.has("ToolB"));
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("unregister non-existent tool is a no-op")
        void unregisterNonExistentShouldBeNoOp() {
            registry.register(createDescriptor("ToolA"));
            registry.unregister("NonExistent");
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("clear removes all descriptors")
        void shouldClearAll() {
            registry.register(createDescriptor("ToolA"));
            registry.register(createDescriptor("ToolB"));

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.has("ToolA"));
            assertFalse(registry.has("ToolB"));
        }
    }

    @Nested
    @DisplayName("Empty registry")
    class EmptyRegistry {

        @Test
        @DisplayName("New registry has size 0")
        void shouldStartEmpty() {
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("New registry getAll returns empty list")
        void shouldReturnEmptyList() {
            assertTrue(registry.getAll().isEmpty());
        }
    }
}
