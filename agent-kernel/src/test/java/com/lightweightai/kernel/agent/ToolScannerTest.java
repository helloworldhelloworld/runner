package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolScanner 测试
 *
 * 验证 Java SPI 自动扫描机制：
 * - 通过 META-INF/services 自动发现 Tool 实现
 * - 按分类/标签过滤
 * - 扫描并注册到 ToolRegistry
 */
@DisplayName("ToolScanner - SPI 自动扫描")
class ToolScannerTest {

    @Test
    @DisplayName("扫描发现 SPI 注册的工具")
    void shouldDiscoverToolsViaSPI() {
        List<Tool> tools = ToolScanner.scan();

        assertFalse(tools.isEmpty(), "Should discover at least one tool via SPI");
        assertTrue(
            tools.stream().anyMatch(t -> "test_echo".equals(t.getName())),
            "Should discover TestEchoTool"
        );
    }

    @Test
    @DisplayName("扫描结果包含正确的工具信息")
    void shouldContainCorrectToolInfo() {
        List<Tool> tools = ToolScanner.scan();
        Tool echoTool = tools.stream()
            .filter(t -> "test_echo".equals(t.getName()))
            .findFirst()
            .orElse(null);

        assertNotNull(echoTool, "TestEchoTool should be found");
        assertEquals("Echo the input (test tool)", echoTool.getDescription());
        assertNotNull(echoTool.getSchema());
    }

    @Test
    @DisplayName("按条件过滤扫描结果")
    void shouldFilterByPredicate() {
        List<Tool> filtered = ToolScanner.scan(tool -> tool.getName().startsWith("test_"));

        assertFalse(filtered.isEmpty());
        assertTrue(filtered.stream().allMatch(t -> t.getName().startsWith("test_")));
    }

    @Test
    @DisplayName("按分类扫描工具")
    void shouldScanByCategory() {
        List<Tool> testTools = ToolScanner.scanByCategory("test");

        assertTrue(
            testTools.stream().anyMatch(t -> "test_echo".equals(t.getName())),
            "Should find TestEchoTool in 'test' category"
        );
    }

    @Test
    @DisplayName("按标签扫描工具")
    void shouldScanByTag() {
        List<Tool> echoTools = ToolScanner.scanByTag("echo");

        assertTrue(
            echoTools.stream().anyMatch(t -> "test_echo".equals(t.getName())),
            "Should find TestEchoTool by 'echo' tag"
        );
    }

    @Test
    @DisplayName("按不存在的分类扫描返回空列表")
    void shouldReturnEmptyForUnknownCategory() {
        List<Tool> tools = ToolScanner.scanByCategory("nonexistent_category");

        assertTrue(tools.isEmpty() || tools.stream().noneMatch(t ->
            t instanceof ToolMetadata && "nonexistent_category".equals(((ToolMetadata) t).getCategory())
        ));
    }

    @Test
    @DisplayName("扫描并注册到 ToolRegistry")
    void shouldScanAndRegister() {
        ToolRegistry registry = new ToolRegistry();

        int count = ToolScanner.scanAndRegister(registry);

        assertTrue(count > 0, "Should register at least one tool");
        assertTrue(registry.has("test_echo"), "Registry should contain TestEchoTool");
        assertEquals(count, registry.size());
    }

    @Test
    @DisplayName("扫描并按条件过滤后注册")
    void shouldScanAndRegisterWithFilter() {
        ToolRegistry registry = new ToolRegistry();

        int count = ToolScanner.scanAndRegister(registry, tool -> "test_echo".equals(tool.getName()));

        assertEquals(1, count);
        assertTrue(registry.has("test_echo"));
    }

    @Test
    @DisplayName("ToolRegistry.scanAndRegister() 便捷方法")
    void shouldUseRegistryScanAndRegister() {
        ToolRegistry registry = new ToolRegistry();

        int count = registry.scanAndRegister();

        assertTrue(count > 0);
        assertTrue(registry.has("test_echo"));
    }

    @Test
    @DisplayName("ToolRegistry.scanAndRegister(filter) 便捷方法")
    void shouldUseRegistryScanAndRegisterWithFilter() {
        ToolRegistry registry = new ToolRegistry();

        int count = registry.scanAndRegister(tool -> tool.getName().startsWith("test_"));

        assertTrue(count > 0);
        assertTrue(registry.has("test_echo"));
    }

    @Test
    @DisplayName("扫描的工具可以正常执行")
    void shouldExecuteScannedTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.scanAndRegister();

        Tool tool = registry.get("test_echo").orElse(null);
        assertNotNull(tool);

        var result = tool.execute(java.util.Map.of("message", "Hello SPI"));
        assertFalse(result.isError());
        assertEquals("Echo: Hello SPI", result.getContent());
    }

    @Test
    @DisplayName("获取扫描到的工具名称列表")
    void shouldGetScannedToolNames() {
        List<String> names = ToolScanner.scanToolNames();

        assertFalse(names.isEmpty());
        assertTrue(names.contains("test_echo"));
    }

    @Test
    @DisplayName("使用指定 ClassLoader 扫描")
    void shouldScanWithCustomClassLoader() {
        List<Tool> tools = ToolScanner.scan(Thread.currentThread().getContextClassLoader());

        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().anyMatch(t -> "test_echo".equals(t.getName())));
    }

    @Test
    @DisplayName("null filter 应抛出异常")
    void shouldRejectNullFilter() {
        assertThrows(NullPointerException.class, () -> ToolScanner.scan((java.util.function.Predicate<Tool>) null));
    }

    @Test
    @DisplayName("null registry 应抛出异常")
    void shouldRejectNullRegistry() {
        assertThrows(NullPointerException.class, () -> ToolScanner.scanAndRegister(null));
    }
}
