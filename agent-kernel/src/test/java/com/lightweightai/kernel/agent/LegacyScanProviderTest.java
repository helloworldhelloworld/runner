package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LegacyScanProvider 测试
 *
 * 验证 LegacyScanProvider 与 ToolScanner.scanAndRegister() 行为等价
 */
@DisplayName("LegacyScanProvider - 遗留扫描适配器")
class LegacyScanProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("start 应发现与 ToolScanner 一致的工具集")
    void shouldDiscoverSameToolsAsLegacyScan() {
        // Given — reference: what ToolScanner would find
        List<Tool> expectedTools = ToolScanner.scan();
        LegacyScanProvider provider = new LegacyScanProvider();

        // When
        provider.start(registry);

        // Then
        assertEquals(expectedTools.size(), registry.size());
        for (Tool tool : expectedTools) {
            assertTrue(registry.has(tool.getName()),
                "Missing tool: " + tool.getName());
        }
        assertTrue(provider.isRunning());
    }

    @Test
    @DisplayName("stop 应注销所有扫描注册的工具")
    void shouldUnregisterOnStop() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);
        assertTrue(registry.size() > 0, "Should have registered at least one tool via SPI");

        // When
        provider.stop(registry);

        // Then
        assertEquals(0, registry.size());
        assertFalse(provider.isRunning());
    }

    @Test
    @DisplayName("支持带过滤条件的扫描")
    void shouldSupportFilter() {
        // Given — filter out test_echo tool
        LegacyScanProvider provider = new LegacyScanProvider(
            tool -> !tool.getName().equals("test_echo")
        );

        // When
        provider.start(registry);

        // Then
        assertFalse(registry.has("test_echo"));
    }

    @Test
    @DisplayName("支持 stop 后重新 start")
    void shouldSupportRestart() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();

        // First cycle
        provider.start(registry);
        int firstSize = registry.size();
        assertTrue(firstSize > 0);

        provider.stop(registry);
        assertEquals(0, registry.size());

        // Second cycle
        provider.start(registry);
        assertEquals(firstSize, registry.size());
        assertTrue(provider.isRunning());
    }

    @Test
    @DisplayName("重复 start 应忽略")
    void shouldIgnoreDuplicateStart() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);
        int size = registry.size();

        // When — second start should be no-op
        provider.start(registry);

        // Then
        assertEquals(size, registry.size());
    }

    @Test
    @DisplayName("discoverTools 应返回与 ToolScanner.scan() 一致的结果")
    void shouldDiscoverToolsConsistentWithScanner() {
        // Given
        LegacyScanProvider provider = new LegacyScanProvider();

        // When
        List<Tool> discovered = provider.discoverTools();

        // Then
        List<Tool> fromScanner = ToolScanner.scan();
        assertEquals(fromScanner.size(), discovered.size());
    }

    @Test
    @DisplayName("getName 应返回 legacy-scan")
    void shouldReturnCorrectName() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("legacy-scan", provider.getName());
    }
}
