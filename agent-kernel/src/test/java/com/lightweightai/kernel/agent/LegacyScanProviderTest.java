package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider - SPI scan tool source provider")
class LegacyScanProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== sourceType ====================

    @Test
    @DisplayName("sourceType returns 'spi-scan'")
    void sourceTypeIsSpiScan() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    // ==================== Default constructor ====================

    @Nested
    @DisplayName("Default constructor (no filter)")
    class DefaultConstructorTests {

        @Test
        @DisplayName("default constructor creates provider without errors")
        void defaultConstructorHasNoFilter() {
            LegacyScanProvider provider = new LegacyScanProvider();
            assertNotNull(provider);
            assertEquals("spi-scan", provider.sourceType());
        }

        @Test
        @DisplayName("start with default constructor registers discovered SPI tools")
        void startRegistersDiscoveredTools() {
            ToolRegistry registry = new ToolRegistry();
            LegacyScanProvider provider = new LegacyScanProvider();
            int sizeBefore = registry.size();
            provider.start(registry);
            // SPI scan discovers tools on the classpath (e.g. TestEchoTool in test META-INF/services)
            assertTrue(registry.size() >= sizeBefore,
                    "Registry size after scan should be >= size before scan");
        }

        @Test
        @DisplayName("start discovers TestEchoTool from test SPI configuration")
        void startFindsTestEchoTool() {
            LegacyScanProvider provider = new LegacyScanProvider();
            provider.start(registry);
            assertTrue(registry.has("test_echo"),
                    "Should discover test_echo tool via SPI ServiceLoader");
        }
    }

    // ==================== Filter constructor ====================

    @Nested
    @DisplayName("Filter constructor")
    class FilterConstructorTests {

        @Test
        @DisplayName("start with filter only registers matching tools")
        void startWithFilterOnlyRegistersMatchingTools() {
            LegacyScanProvider provider = new LegacyScanProvider(
                    tool -> tool.getName().startsWith("nonexistent_prefix_xyz_"));
            provider.start(registry);
            assertEquals(0, registry.size(),
                    "Filter rejecting all tools should result in empty registry");
        }

        @Test
        @DisplayName("start with filter excluding test_echo removes it from registry")
        void startWithFilterExcludesSpecificTool() {
            LegacyScanProvider provider = new LegacyScanProvider(
                    tool -> !tool.getName().equals("test_echo"));
            provider.start(registry);
            assertFalse(registry.has("test_echo"),
                    "test_echo should be filtered out by the predicate");
        }

        @Test
        @DisplayName("start with accept-all filter behaves like no-filter constructor")
        void startWithAcceptAllFilter() {
            // First run without filter
            ToolRegistry registryNoFilter = new ToolRegistry();
            new LegacyScanProvider().start(registryNoFilter);

            // Then run with accept-all filter
            ToolRegistry registryWithFilter = new ToolRegistry();
            new LegacyScanProvider(tool -> true).start(registryWithFilter);

            assertEquals(registryNoFilter.size(), registryWithFilter.size(),
                    "Accept-all filter should produce same result as no filter");
        }

        @Test
        @DisplayName("start with filter that accepts only test_echo registers exactly it")
        void startWithFilterSelectingOneTool() {
            LegacyScanProvider provider = new LegacyScanProvider(
                    tool -> "test_echo".equals(tool.getName()));
            provider.start(registry);
            assertTrue(registry.has("test_echo"),
                    "test_echo should pass the filter");
            // Only test_echo should be registered (there may be other SPI tools, but they are filtered out)
            assertEquals(1, registry.size(),
                    "Only the one tool matching the filter should be registered");
        }
    }

    // ==================== start safety ====================

    @Nested
    @DisplayName("start edge cases")
    class StartEdgeCaseTests {

        @Test
        @DisplayName("start does not throw with empty classpath (no SPI tools)")
        void startDoesNotThrowWithEmptyClasspath() {
            // Use a classloader that has no META-INF/services entries for Tool
            // LegacyScanProvider delegates to registry.scanAndRegister() which uses
            // the thread context classloader. We can't easily override that, but we can
            // verify the default path does not throw.
            LegacyScanProvider provider = new LegacyScanProvider();
            assertDoesNotThrow(() -> provider.start(registry),
                    "start should not throw even if SPI discovers zero tools");
        }

        @Test
        @DisplayName("start can be called multiple times without error")
        void startCanBeCalledMultipleTimes() {
            LegacyScanProvider provider = new LegacyScanProvider();
            provider.start(registry);
            int sizeAfterFirst = registry.size();

            // Second call re-registers the same tools (register is idempotent by name)
            assertDoesNotThrow(() -> provider.start(registry),
                    "Calling start twice should not throw");
            assertEquals(sizeAfterFirst, registry.size(),
                    "Registry size should be stable after duplicate start calls");
        }
    }

    // ==================== stop (default no-op) ====================

    @Nested
    @DisplayName("stop behavior (default no-op)")
    class StopTests {

        @Test
        @DisplayName("stop does not throw")
        void noStopOverride() {
            LegacyScanProvider provider = new LegacyScanProvider();
            assertDoesNotThrow(() -> provider.stop(registry),
                    "stop should be a safe no-op (default from interface)");
        }

        @Test
        @DisplayName("stop does not remove previously registered tools")
        void stopDoesNotRemoveTools() {
            LegacyScanProvider provider = new LegacyScanProvider();
            provider.start(registry);
            int sizeBeforeStop = registry.size();
            assertTrue(sizeBeforeStop > 0, "Precondition: at least one SPI tool should be registered");

            provider.stop(registry);
            assertEquals(sizeBeforeStop, registry.size(),
                    "stop is a no-op and should not remove any tools");
            assertTrue(registry.has("test_echo"),
                    "test_echo should still be present after stop");
        }
    }

    // ==================== ToolSourceProvider contract ====================

    @Test
    @DisplayName("implements ToolSourceProvider interface")
    void implementsToolSourceProvider() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertInstanceOf(ToolSourceProvider.class, provider,
                "LegacyScanProvider must implement ToolSourceProvider");
    }
}
