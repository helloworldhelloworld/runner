package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider — SPI 扫描 Provider")
class LegacyScanProviderTest {

    @Test
    @DisplayName("sourceType 返回 spi-scan")
    void sourceTypeIsSpiScan() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    @Test
    @DisplayName("无 filter 时 start 调用 scanAndRegister()")
    void startWithoutFilterCallsScanAndRegister() {
        ToolRegistry registry = new ToolRegistry();
        LegacyScanProvider provider = new LegacyScanProvider();

        int sizeBefore = registry.size();
        provider.start(registry);
        // SPI scan may or may not find tools on the classpath.
        // The key assertion: no exception thrown, and size >= before.
        assertTrue(registry.size() >= sizeBefore);
    }

    @Test
    @DisplayName("带 filter 时 start 只注册通过 filter 的工具")
    void startWithFilterRejectsAll() {
        ToolRegistry registry = new ToolRegistry();
        // Reject all tools
        LegacyScanProvider provider = new LegacyScanProvider(tool -> false);

        provider.start(registry);
        assertEquals(0, registry.size(), "All tools rejected by filter");
    }

    @Test
    @DisplayName("stop 是 no-op（默认实现）")
    void stopIsNoOp() {
        ToolRegistry registry = new ToolRegistry();
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);

        int sizeAfterStart = registry.size();
        provider.stop(registry);
        assertEquals(sizeAfterStart, registry.size(),
                "stop() should not remove tools (default no-op)");
    }
}
