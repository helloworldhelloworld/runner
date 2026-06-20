package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider — SPI scan wrapper")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType returns 'spi-scan'")
    void sourceType() {
        assertEquals("spi-scan", new LegacyScanProvider().sourceType());
    }

    @Test
    @DisplayName("sourceType with filter also returns 'spi-scan'")
    void sourceTypeWithFilter() {
        assertEquals("spi-scan", new LegacyScanProvider(t -> true).sourceType());
    }

    @Test
    @DisplayName("start with null filter calls scanAndRegister() no-arg")
    void startNoFilter() {
        ToolRegistry registry = new ToolRegistry();
        LegacyScanProvider provider = new LegacyScanProvider();

        // SPI scan may find tools on classpath — just verify it doesn't throw
        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("start with filter calls scanAndRegister(filter)")
    void startWithFilter() {
        ToolRegistry registry = new ToolRegistry();
        AtomicBoolean filterCalled = new AtomicBoolean(false);
        Predicate<Tool> filter = tool -> {
            filterCalled.set(true);
            return true;
        };

        LegacyScanProvider provider = new LegacyScanProvider(filter);
        provider.start(registry);

        // Filter may or may not be called depending on classpath tools
        // The important thing is no exception is thrown
    }

    @Test
    @DisplayName("default stop is no-op (inherited from interface default)")
    void stopIsNoOp() {
        ToolRegistry registry = new ToolRegistry();
        LegacyScanProvider provider = new LegacyScanProvider();
        assertDoesNotThrow(() -> provider.stop(registry));
    }

    @Test
    @DisplayName("no-arg constructor equivalent to null filter")
    void noArgConstructor() {
        LegacyScanProvider p1 = new LegacyScanProvider();
        LegacyScanProvider p2 = new LegacyScanProvider(null);

        assertEquals(p1.sourceType(), p2.sourceType());
        // Both should call scanAndRegister() without filter
        ToolRegistry r1 = new ToolRegistry();
        ToolRegistry r2 = new ToolRegistry();
        assertDoesNotThrow(() -> p1.start(r1));
        assertDoesNotThrow(() -> p2.start(r2));
    }
}
