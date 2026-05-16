package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyScanProvider - SPI 扫描工具来源")
class LegacyScanProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== sourceType ====================

    @Test
    @DisplayName("sourceType 返回 spi-scan")
    void sourceTypeReturnsSpiScan() {
        LegacyScanProvider provider = new LegacyScanProvider();
        assertEquals("spi-scan", provider.sourceType());
    }

    @Test
    @DisplayName("带 filter 构造时 sourceType 仍返回 spi-scan")
    void sourceTypeWithFilterStillReturnsSpiScan() {
        LegacyScanProvider provider = new LegacyScanProvider(tool -> true);
        assertEquals("spi-scan", provider.sourceType());
    }

    // ==================== start 无 filter ====================

    @Test
    @DisplayName("无 filter 的 start 通过 SPI 发现并注册工具")
    void startWithoutFilterRegistersSpiTools() {
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);

        assertTrue(registry.size() > 0, "应通过 SPI 发现至少一个工具");
        assertTrue(registry.has("test_echo"), "应发现 test_echo 工具");
    }

    @Test
    @DisplayName("无 filter 的 start 注册的工具可正常执行")
    void startWithoutFilterRegistersExecutableTools() {
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);

        Tool echoTool = registry.get("test_echo").orElse(null);
        assertNotNull(echoTool, "test_echo 应已注册到 registry");

        ToolResult result = echoTool.execute(Map.of("message", "hello"));
        assertFalse(result.isError());
        assertEquals("Echo: hello", result.getContent());
    }

    // ==================== start 带 filter ====================

    @Test
    @DisplayName("带 filter 的 start 只注册匹配的工具")
    void startWithFilterRegistersOnlyMatchingTools() {
        Predicate<Tool> acceptAll = tool -> true;
        LegacyScanProvider provider = new LegacyScanProvider(acceptAll);
        provider.start(registry);

        assertTrue(registry.has("test_echo"), "accept-all filter 应保留 test_echo");
    }

    @Test
    @DisplayName("filter 排除特定工具后 registry 不包含该工具")
    void startWithFilterExcludesRejectedTools() {
        Predicate<Tool> rejectEcho = tool -> !tool.getName().equals("test_echo");
        LegacyScanProvider provider = new LegacyScanProvider(rejectEcho);
        provider.start(registry);

        assertFalse(registry.has("test_echo"), "test_echo 应被 filter 排除");
    }

    @Test
    @DisplayName("filter 按名称前缀过滤只保留匹配工具")
    void startWithPrefixFilterKeepsOnlyMatchingTools() {
        Predicate<Tool> prefixFilter = tool -> tool.getName().startsWith("test_");
        LegacyScanProvider provider = new LegacyScanProvider(prefixFilter);
        provider.start(registry);

        // 所有注册的工具名称都以 test_ 开头
        registry.getAll().forEach(tool ->
            assertTrue(tool.getName().startsWith("test_"),
                "所有注册工具应匹配前缀 filter，但发现: " + tool.getName())
        );
    }

    @Test
    @DisplayName("filter 拒绝所有工具时 registry 为空")
    void startWithRejectAllFilterRegistersNothing() {
        Predicate<Tool> rejectAll = tool -> false;
        LegacyScanProvider provider = new LegacyScanProvider(rejectAll);
        provider.start(registry);

        assertEquals(0, registry.size(), "reject-all filter 应使 registry 为空");
    }

    // ==================== filter 谓词实际生效验证 ====================

    @Test
    @DisplayName("filter 谓词确实收到每个被扫描的工具进行判断")
    void filterPredicateIsInvokedForEachDiscoveredTool() {
        // 先用无 filter 扫描确认总数
        LegacyScanProvider unfilteredProvider = new LegacyScanProvider();
        unfilteredProvider.start(registry);
        int totalCount = registry.size();
        assertTrue(totalCount > 0, "SPI 应至少发现 1 个工具");

        // 用 reject-all filter 扫描，确认注册数为 0
        ToolRegistry filteredRegistry = new ToolRegistry();
        LegacyScanProvider filteredProvider = new LegacyScanProvider(tool -> false);
        filteredProvider.start(filteredRegistry);

        assertEquals(0, filteredRegistry.size(),
            "filter 应被实际调用并拦截所有 " + totalCount + " 个工具");
    }

    // ==================== stop 行为 ====================

    @Test
    @DisplayName("stop 是默认空操作，不移除已注册工具")
    void stopDoesNotRemoveRegisteredTools() {
        LegacyScanProvider provider = new LegacyScanProvider();
        provider.start(registry);
        int sizeAfterStart = registry.size();
        assertTrue(sizeAfterStart > 0, "start 后应有工具");

        provider.stop(registry);
        assertEquals(sizeAfterStart, registry.size(), "stop 不应移除任何工具");
        assertTrue(registry.has("test_echo"), "stop 后 test_echo 应仍存在");
    }

    // ==================== 默认构造 vs 显式 null ====================

    @Test
    @DisplayName("默认构造函数等价于 filter=null，走无过滤扫描路径")
    void defaultConstructorUsesNullFilter() {
        LegacyScanProvider defaultProvider = new LegacyScanProvider();
        defaultProvider.start(registry);
        int defaultCount = registry.size();

        ToolRegistry otherRegistry = new ToolRegistry();
        LegacyScanProvider nullFilterProvider = new LegacyScanProvider(null);
        nullFilterProvider.start(otherRegistry);
        int nullFilterCount = otherRegistry.size();

        assertEquals(defaultCount, nullFilterCount,
            "默认构造和显式 null filter 应注册相同数量的工具");
    }
}
