package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ConcurrentHashMap-backed directive descriptor registry")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    // ==================== register + get ====================

    @Test
    @DisplayName("Given a descriptor is registered, when get is called with its toolName, then it returns the descriptor")
    void registerAndGetDescriptor() {
        // Given
        DirectiveDescriptor descriptor = createDescriptor("Camera.TakePhoto");

        // When
        registry.register(descriptor);

        // Then
        Optional<DirectiveDescriptor> result = registry.get("Camera.TakePhoto");
        assertTrue(result.isPresent(), "Descriptor should be found after registration");
        assertEquals("Camera.TakePhoto", result.get().getTool());
        assertEquals("camera_takephoto_down", result.get().getDownAction());
        assertEquals("camera_takephoto_up", result.get().getUpAction());
    }

    @Test
    @DisplayName("Given multiple descriptors registered, when get is called for each, then each is returned correctly")
    void registerMultipleDescriptors() {
        // Given
        DirectiveDescriptor d1 = createDescriptor("Camera.TakePhoto");
        DirectiveDescriptor d2 = createDescriptor("GPS.GetLocation");

        // When
        registry.register(d1);
        registry.register(d2);

        // Then
        assertTrue(registry.get("Camera.TakePhoto").isPresent());
        assertTrue(registry.get("GPS.GetLocation").isPresent());
        assertEquals("Camera.TakePhoto", registry.get("Camera.TakePhoto").get().getTool());
        assertEquals("GPS.GetLocation", registry.get("GPS.GetLocation").get().getTool());
    }

    @Test
    @DisplayName("Given a descriptor is registered, when another with same toolName is registered, then it overwrites")
    void registerOverwritesExisting() {
        // Given
        DirectiveDescriptor original = new DirectiveDescriptor(
            "myTool", "down_v1", "up_v1", "ns", 5000, "1.0");
        DirectiveDescriptor replacement = new DirectiveDescriptor(
            "myTool", "down_v2", "up_v2", "ns", 10000, "2.0");
        registry.register(original);

        // When
        registry.register(replacement);

        // Then
        DirectiveDescriptor retrieved = registry.get("myTool").orElse(null);
        assertNotNull(retrieved);
        assertEquals("down_v2", retrieved.getDownAction(),
            "Should contain the replacement descriptor's downAction");
        assertEquals("2.0", retrieved.getVersion(),
            "Should contain the replacement descriptor's version");
    }

    // ==================== has ====================

    @Test
    @DisplayName("Given a descriptor is registered, when has is called with its name, then it returns true")
    void hasReturnsTrueForRegistered() {
        // Given
        registry.register(createDescriptor("myTool"));

        // When & Then
        assertTrue(registry.has("myTool"));
    }

    @Test
    @DisplayName("Given nothing is registered, when has is called, then it returns false")
    void hasReturnsFalseForUnregistered() {
        // When & Then
        assertFalse(registry.has("nonexistent"));
    }

    // ==================== get ====================

    @Test
    @DisplayName("Given no descriptor registered, when get is called, then it returns empty Optional")
    void getReturnsEmptyForUnregistered() {
        // When
        Optional<DirectiveDescriptor> result = registry.get("nonexistent");

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== unregister ====================

    @Test
    @DisplayName("Given a descriptor is registered, when unregister is called, then it is removed")
    void unregisterRemovesDescriptor() {
        // Given
        registry.register(createDescriptor("myTool"));
        assertTrue(registry.has("myTool"), "Precondition: tool should be registered");

        // When
        registry.unregister("myTool");

        // Then
        assertFalse(registry.has("myTool"), "Tool should not be present after unregister");
        assertTrue(registry.get("myTool").isEmpty());
    }

    @Test
    @DisplayName("Given unregister is called for non-existent tool, then no error is thrown")
    void unregisterNonExistentIsNoOp() {
        // When & Then (no exception)
        assertDoesNotThrow(() -> registry.unregister("nonexistent"));
    }

    @Test
    @DisplayName("Given multiple descriptors, when one is unregistered, then others remain")
    void unregisterDoesNotAffectOthers() {
        // Given
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        assertEquals(2, registry.size());

        // When
        registry.unregister("tool_a");

        // Then
        assertFalse(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"), "Other descriptors should remain");
        assertEquals(1, registry.size());
    }

    // ==================== getAll ====================

    @Test
    @DisplayName("Given descriptors are registered, when getAll is called, then all are returned")
    void getAllReturnsAllDescriptors() {
        // Given
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        registry.register(createDescriptor("tool_c"));

        // When
        List<DirectiveDescriptor> all = registry.getAll();

        // Then
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("Given empty registry, when getAll is called, then it returns empty list")
    void getAllReturnsEmptyListWhenEmpty() {
        // When
        List<DirectiveDescriptor> all = registry.getAll();

        // Then
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    @DisplayName("Given getAll is called, the returned list is a defensive copy")
    void getAllReturnsDefensiveCopy() {
        // Given
        registry.register(createDescriptor("tool_a"));
        List<DirectiveDescriptor> all = registry.getAll();

        // When - modify the returned list
        all.clear();

        // Then - registry is unchanged
        assertEquals(1, registry.size(), "Clearing the returned list should not affect the registry");
    }

    // ==================== size ====================

    @Test
    @DisplayName("Given empty registry, when size is called, then it returns 0")
    void sizeIsZeroWhenEmpty() {
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Given descriptors are registered, when size is called, then it returns the count")
    void sizeReflectsRegisteredCount() {
        // Given
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));

        // Then
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("Given a descriptor is unregistered, then size decreases")
    void sizeDecreasesAfterUnregister() {
        // Given
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        assertEquals(2, registry.size());

        // When
        registry.unregister("tool_a");

        // Then
        assertEquals(1, registry.size());
    }

    // ==================== clear ====================

    @Test
    @DisplayName("Given descriptors are registered, when clear is called, then registry is empty")
    void clearRemovesAllDescriptors() {
        // Given
        registry.register(createDescriptor("tool_a"));
        registry.register(createDescriptor("tool_b"));
        registry.register(createDescriptor("tool_c"));
        assertEquals(3, registry.size(), "Precondition");

        // When
        registry.clear();

        // Then
        assertEquals(0, registry.size());
        assertFalse(registry.has("tool_a"));
        assertFalse(registry.has("tool_b"));
        assertFalse(registry.has("tool_c"));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("Given empty registry, when clear is called, then it is a no-op")
    void clearOnEmptyRegistryIsNoOp() {
        // When & Then (no exception)
        assertDoesNotThrow(() -> registry.clear());
        assertEquals(0, registry.size());
    }

    // ==================== helper ====================

    private DirectiveDescriptor createDescriptor(String toolName) {
        return new DirectiveDescriptor(
            toolName,
            toolName.toLowerCase().replace(".", "_") + "_down",
            toolName.toLowerCase().replace(".", "_") + "_up",
            "test-ns",
            30000,
            "1.0"
        );
    }
}
