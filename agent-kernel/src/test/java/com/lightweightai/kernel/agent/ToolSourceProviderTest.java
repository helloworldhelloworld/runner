package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSourceProvider 接口契约测试
 */
@DisplayName("ToolSourceProvider - 工具来源提供者接口")
class ToolSourceProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("start 应注册工具到 registry")
    void shouldRegisterToolsOnStart() {
        // Given
        ToolSourceProvider provider = new SimpleTestProvider("test-provider",
            List.of(createTool("tool_a"), createTool("tool_b")));

        // When
        provider.start(registry);

        // Then
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertEquals(2, registry.size());
        assertTrue(provider.isRunning());
    }

    @Test
    @DisplayName("stop 应注销工具并释放资源")
    void shouldUnregisterToolsOnStop() {
        // Given
        ToolSourceProvider provider = new SimpleTestProvider("test-provider",
            List.of(createTool("tool_a"), createTool("tool_b")));
        provider.start(registry);

        // When
        provider.stop(registry);

        // Then
        assertFalse(registry.has("tool_a"));
        assertFalse(registry.has("tool_b"));
        assertEquals(0, registry.size());
        assertFalse(provider.isRunning());
    }

    @Test
    @DisplayName("isRunning 应反映生命周期状态")
    void shouldReportRunningState() {
        // Given
        ToolSourceProvider provider = new SimpleTestProvider("test-provider",
            List.of(createTool("tool_a")));

        // Then
        assertFalse(provider.isRunning());

        provider.start(registry);
        assertTrue(provider.isRunning());

        provider.stop(registry);
        assertFalse(provider.isRunning());
    }

    @Test
    @DisplayName("两个 provider 注册同名工具时应后者覆盖")
    void shouldOverwriteOnConflict() {
        // Given
        Tool toolV1 = createTool("shared_tool");
        Tool toolV2 = createTool("shared_tool");
        ToolSourceProvider provider1 = new SimpleTestProvider("provider-1", List.of(toolV1));
        ToolSourceProvider provider2 = new SimpleTestProvider("provider-2", List.of(toolV2));

        // When
        provider1.start(registry);
        provider2.start(registry);

        // Then — last write wins
        assertTrue(registry.has("shared_tool"));
        assertSame(toolV2, registry.get("shared_tool").orElse(null));
    }

    @Test
    @DisplayName("默认 discoverTools 返回空列表")
    void shouldReturnEmptyDiscoverToolsByDefault() {
        // Given
        ToolSourceProvider provider = new SimpleTestProvider("test", List.of(createTool("t")));

        // When — default discoverTools from interface
        // SimpleTestProvider overrides it, but interface default returns empty
        ToolSourceProvider minimal = new ToolSourceProvider() {
            @Override public String getName() { return "minimal"; }
            @Override public void start(ToolRegistry registry) {}
            @Override public void stop(ToolRegistry registry) {}
            @Override public boolean isRunning() { return false; }
        };

        // Then
        assertTrue(minimal.discoverTools().isEmpty());
    }

    // ==================== 辅助方法 ====================

    private Tool createTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test tool: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    /**
     * 简单测试用 ToolSourceProvider 实现
     */
    private static class SimpleTestProvider implements ToolSourceProvider {
        private final String name;
        private final List<Tool> tools;
        private volatile boolean running = false;
        private List<String> registeredNames = List.of();

        SimpleTestProvider(String name, List<Tool> tools) {
            this.name = name;
            this.tools = tools;
        }

        @Override public String getName() { return name; }

        @Override
        public void start(ToolRegistry registry) {
            for (Tool tool : tools) {
                registry.register(tool);
            }
            registeredNames = tools.stream().map(Tool::getName).toList();
            running = true;
        }

        @Override
        public void stop(ToolRegistry registry) {
            for (String n : registeredNames) {
                registry.unregister(n);
            }
            registeredNames = List.of();
            running = false;
        }

        @Override public boolean isRunning() { return running; }
    }
}
