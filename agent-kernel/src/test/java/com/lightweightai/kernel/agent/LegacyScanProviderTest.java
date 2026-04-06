package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider - SPI扫描Provider")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType 为 spi-scan")
    void shouldReturnCorrectSourceType() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    @Test
    @DisplayName("无 filter 时调用 scanAndRegister()")
    void shouldCallScanAndRegisterWithoutFilter() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();

        // 不应抛异常
        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("有 filter 时调用 scanAndRegister(filter)")
    void shouldCallScanAndRegisterWithFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(tool -> true);
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("null filter 等同于无 filter")
    void shouldHandleNullFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(null);
        ToolRegistry registry = new ToolRegistry();

        // null filter 走 else 分支 (registry.scanAndRegister())
        assertDoesNotThrow(() -> provider.start(registry));
    }
}
