package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider - SPI scan-based tool registration")
class LegacyScanProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== sourceType ====================

    @Test
    @DisplayName("Given a LegacyScanProvider, when sourceType is called, then it returns 'spi-scan'")
    void sourceTypeReturnsSpiScan() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();

        // When
        String type = provider.sourceType();

        // Then
        assertEquals("spi-scan", type);
    }

    // ==================== no-arg constructor + start ====================

    @Test
    @DisplayName("Given no-arg constructor, when start is called, then SPI tools are registered")
    void startWithNoFilterRegistersSpiTools() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();

        // When
        provider.start(registry);

        // Then - test_echo is declared in META-INF/services
        assertTrue(registry.has("test_echo"),
            "Should discover test_echo via SPI scan");
        assertTrue(registry.size() > 0,
            "Registry should contain at least one tool after SPI scan");
    }

    // ==================== constructor with filter + start ====================

    @Test
    @DisplayName("Given a filter that rejects test_echo, when start is called, then test_echo is not registered")
    void startWithFilterExcludesTool() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider(
            tool -> !tool.getName().equals("test_echo")
        );

        // When
        provider.start(registry);

        // Then
        assertFalse(registry.has("test_echo"),
            "test_echo should be excluded by filter");
    }

    @Test
    @DisplayName("Given a filter that accepts all, when start is called, then all SPI tools are registered")
    void startWithAcceptAllFilter() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider(tool -> true);

        // When
        provider.start(registry);

        // Then
        assertTrue(registry.has("test_echo"),
            "test_echo should be registered when filter accepts all");
    }

    @Test
    @DisplayName("Given a filter that rejects all, when start is called, then no tools are registered")
    void startWithRejectAllFilter() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider(tool -> false);

        // When
        provider.start(registry);

        // Then
        assertEquals(0, registry.size(),
            "No tools should be registered when filter rejects all");
    }

    // ==================== stop ====================

    @Test
    @DisplayName("Given LegacyScanProvider uses default stop, when stop is called, then tools remain in registry")
    void stopIsDefaultNoOp() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);
        int sizeBeforeStop = registry.size();
        assertTrue(sizeBeforeStop > 0, "Precondition: at least one tool should be registered");

        // When
        provider.stop(registry);

        // Then
        assertEquals(sizeBeforeStop, registry.size(),
            "stop should not remove any tools (default no-op from interface)");
        assertTrue(registry.has("test_echo"),
            "test_echo should still be present after stop");
    }

    // ==================== null filter treated as no filter ====================

    @Test
    @DisplayName("Given constructor with null filter, when start is called, then it behaves like no-arg constructor")
    void nullFilterBehavesLikeNoArg() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider(null);

        // When
        provider.start(registry);

        // Then
        assertTrue(registry.has("test_echo"),
            "null filter should trigger unfiltered scanAndRegister");
    }
}
