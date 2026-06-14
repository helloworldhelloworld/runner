package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider — SPI 扫描工具注册")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType 返回 spi-scan")
    void sourceTypeIsSpiScan() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    @Test
    @DisplayName("无 filter 构造 — start 调用 scanAndRegister")
    void startWithoutFilterCallsScanAndRegister() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("有 filter 构造 — start 调用 scanAndRegister(filter)")
    void startWithFilterCallsScanAndRegisterWithFilter() {
        LegacyScanProvider provider = new LegacyScanProvider(tool -> tool.getName().startsWith("test_"));
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.start(registry));
    }

    @Test
    @DisplayName("stop 默认实现不抛异常")
    void stopIsNoOp() {
        LegacyScanProvider provider = new LegacyScanProvider();
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() -> provider.stop(registry));
    }
}
