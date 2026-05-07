package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider - SPI 扫描 Provider")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType → 'spi-scan'")
    void sourceType() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    @Test
    @DisplayName("无 filter 构造 → start 时调用 scanAndRegister()")
    void startWithoutFilter() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();

        // Should not throw; scans SPI (may find nothing in test env)
        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("带 filter 构造 → start 时调用 scanAndRegister(filter)")
    void startWithFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(
                tool -> tool.getName().startsWith("test_"));
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("null filter 等同于无 filter")
    void nullFilterIsNoFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(null);
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("stop 默认无操作")
    void stopIsNoOp() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.stop(registry));
    }

    @Test
    @DisplayName("实现 ToolSourceProvider 接口")
    void implementsInterface() {
        assertTrue(new LegacyScanProvider() instanceof ToolSourceProvider);
    }
}
