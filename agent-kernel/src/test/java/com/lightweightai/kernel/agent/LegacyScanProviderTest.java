package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider — SPI 扫描 Provider")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType 返回 spi-scan")
    void sourceTypeIsSpiScan() {
        assertEquals("spi-scan", new LegacyScanProvider().sourceType());
    }

    @Test
    @DisplayName("无 filter 构造 — start 不抛异常")
    void startWithoutFilter() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();
        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("带 filter 构造 — start 不抛异常")
    void startWithFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(tool -> true);
        ToolRegistry registry = new ToolRegistry();
        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("带 reject-all filter — 不注册任何工具")
    void filterRejectsAll() {
        LegacyScanProvider provider = new LegacyScanProvider(tool -> false);
        ToolRegistry registry = new ToolRegistry();
        provider.start(registry);
        assertTrue(registry.getAll().isEmpty() || registry.getAll().stream().noneMatch(
                t -> t.getName().startsWith("test_")),
                "reject-all filter should not register any matching tools");
    }
}
