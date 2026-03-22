package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSourceProviderManager 测试
 */
@DisplayName("ToolSourceProviderManager - 提供者生命周期管理器")
class ToolSourceProviderManagerTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("构造时应启动所有提供者")
    void shouldStartAllProvidersOnConstruction() {
        // Given
        TrackingProvider p1 = new TrackingProvider("p1", List.of(createTool("tool_a")));
        TrackingProvider p2 = new TrackingProvider("p2", List.of(createTool("tool_b")));

        // When
        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of(p1, p2));

        // Then
        assertTrue(p1.isRunning());
        assertTrue(p2.isRunning());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertEquals(2, manager.getRunningCount());
    }

    @Test
    @DisplayName("close 应停止所有提供者")
    void shouldStopAllOnClose() {
        // Given
        TrackingProvider p1 = new TrackingProvider("p1", List.of(createTool("tool_a")));
        TrackingProvider p2 = new TrackingProvider("p2", List.of(createTool("tool_b")));
        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of(p1, p2));

        // When
        manager.close();

        // Then
        assertFalse(p1.isRunning());
        assertFalse(p2.isRunning());
        assertEquals(0, registry.size());
        assertEquals(0, manager.getRunningCount());
    }

    @Test
    @DisplayName("单个提供者启动失败不应影响其他")
    void shouldContinueIfOneProviderFails() {
        // Given
        TrackingProvider good1 = new TrackingProvider("good1", List.of(createTool("tool_a")));
        FailingProvider failing = new FailingProvider("failing");
        TrackingProvider good2 = new TrackingProvider("good2", List.of(createTool("tool_b")));

        // When
        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of(good1, failing, good2));

        // Then
        assertTrue(good1.isRunning());
        assertFalse(failing.isRunning());
        assertTrue(good2.isRunning());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
    }

    @Test
    @DisplayName("空提供者列表应正常工作")
    void shouldHandleEmptyProviderList() {
        // When
        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of());

        // Then
        assertEquals(0, manager.getProviders().size());
        assertEquals(0, manager.getRunningCount());

        // close should not throw
        assertDoesNotThrow(manager::close);
    }

    @Test
    @DisplayName("close 应逆序停止提供者")
    void shouldStopInReverseOrder() {
        // Given
        List<String> stopOrder = new ArrayList<>();
        OrderTrackingProvider p1 = new OrderTrackingProvider("first", stopOrder);
        OrderTrackingProvider p2 = new OrderTrackingProvider("second", stopOrder);
        OrderTrackingProvider p3 = new OrderTrackingProvider("third", stopOrder);

        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of(p1, p2, p3));

        // When
        manager.close();

        // Then
        assertEquals(List.of("third", "second", "first"), stopOrder);
    }

    @Test
    @DisplayName("getProviders 应返回不可修改列表")
    void shouldReturnUnmodifiableProviderList() {
        TrackingProvider p = new TrackingProvider("p", List.of(createTool("t")));
        ToolSourceProviderManager manager = new ToolSourceProviderManager(
            registry, List.of(p));

        assertThrows(UnsupportedOperationException.class,
            () -> manager.getProviders().add(p));
    }

    // ==================== 辅助类 ====================

    private Tool createTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class TrackingProvider implements ToolSourceProvider {
        private final String name;
        private final List<Tool> tools;
        private volatile boolean running = false;
        private List<String> registeredNames = List.of();

        TrackingProvider(String name, List<Tool> tools) {
            this.name = name;
            this.tools = tools;
        }

        @Override public String getName() { return name; }

        @Override
        public void start(ToolRegistry registry) {
            for (Tool tool : tools) { registry.register(tool); }
            registeredNames = tools.stream().map(Tool::getName).toList();
            running = true;
        }

        @Override
        public void stop(ToolRegistry registry) {
            for (String n : registeredNames) { registry.unregister(n); }
            registeredNames = List.of();
            running = false;
        }

        @Override public boolean isRunning() { return running; }
    }

    private static class FailingProvider implements ToolSourceProvider {
        private final String name;
        FailingProvider(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void start(ToolRegistry registry) {
            throw new RuntimeException("Simulated start failure");
        }
        @Override public void stop(ToolRegistry registry) {}
        @Override public boolean isRunning() { return false; }
    }

    private static class OrderTrackingProvider implements ToolSourceProvider {
        private final String name;
        private final List<String> stopOrder;
        private volatile boolean running = false;

        OrderTrackingProvider(String name, List<String> stopOrder) {
            this.name = name;
            this.stopOrder = stopOrder;
        }

        @Override public String getName() { return name; }
        @Override public void start(ToolRegistry registry) { running = true; }
        @Override public void stop(ToolRegistry registry) {
            stopOrder.add(name);
            running = false;
        }
        @Override public boolean isRunning() { return running; }
    }
}
