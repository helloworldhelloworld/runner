package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolSourceProvider 测试")
class ToolSourceProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Nested
    @DisplayName("LegacyScanProvider")
    class LegacyScanProviderTest {

        @Test
        @DisplayName("sourceType 返回 spi-scan")
        void sourceType() {
            assertEquals("spi-scan", new LegacyScanProvider().sourceType());
        }

        @Test
        @DisplayName("start 调用 scanAndRegister 注册工具")
        void startRegistersTools() {
            LegacyScanProvider provider = new LegacyScanProvider();
            provider.start(registry);
            // TestEchoTool is in test SPI
            assertTrue(registry.has("test_echo"), "Should discover test_echo via SPI");
        }

        @Test
        @DisplayName("带过滤器的 start 可排除工具")
        void startWithFilter() {
            LegacyScanProvider provider = new LegacyScanProvider(
                tool -> !tool.getName().equals("test_echo")
            );
            provider.start(registry);
            assertFalse(registry.has("test_echo"), "test_echo should be filtered out");
        }

        @Test
        @DisplayName("stop 是空操作（SPI 工具无法卸载）")
        void stopIsNoOp() {
            LegacyScanProvider provider = new LegacyScanProvider();
            provider.start(registry);
            int before = registry.size();
            provider.stop(registry);
            assertEquals(before, registry.size(), "stop should not remove tools");
        }
    }

    @Nested
    @DisplayName("ManualToolProvider")
    class ManualToolProviderTest {

        @Test
        @DisplayName("sourceType 返回 manual")
        void sourceType() {
            assertEquals("manual", new ManualToolProvider().sourceType());
        }

        @Test
        @DisplayName("addTool + start 注册工具")
        void addToolAndStart() {
            Tool dummyTool = createDummyTool("dummy_tool");

            ManualToolProvider provider = new ManualToolProvider()
                .addTool(dummyTool);
            provider.start(registry);

            assertTrue(registry.has("dummy_tool"));
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("addTool 多个工具")
        void addMultipleTools() {
            ManualToolProvider provider = new ManualToolProvider()
                .addTool(createDummyTool("tool_a"))
                .addTool(createDummyTool("tool_b"));
            provider.start(registry);

            assertTrue(registry.has("tool_a"));
            assertTrue(registry.has("tool_b"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("stop 注销已注册的工具")
        void stopUnregistersTools() {
            Tool toolA = createDummyTool("tool_a");
            Tool toolB = createDummyTool("tool_b");

            ManualToolProvider provider = new ManualToolProvider()
                .addTool(toolA)
                .addTool(toolB);

            provider.start(registry);
            assertEquals(2, registry.size());

            provider.stop(registry);
            assertFalse(registry.has("tool_a"));
            assertFalse(registry.has("tool_b"));
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("stop 不影响其他 provider 注册的工具")
        void stopOnlyAffectsOwnTools() {
            // 另一个 provider 先注册
            registry.register(createDummyTool("other_tool"));

            ManualToolProvider provider = new ManualToolProvider()
                .addTool(createDummyTool("my_tool"));
            provider.start(registry);
            assertEquals(2, registry.size());

            provider.stop(registry);
            assertTrue(registry.has("other_tool"), "Should not affect other provider's tools");
            assertFalse(registry.has("my_tool"));
        }
    }

    @Nested
    @DisplayName("多 Provider 组合")
    class MultiProviderTest {

        @Test
        @DisplayName("多个 Provider 顺序启动")
        void multipleProvidersStart() {
            LegacyScanProvider spiProvider = new LegacyScanProvider();
            ManualToolProvider manualProvider = new ManualToolProvider()
                .addTool(createDummyTool("custom_tool"));

            spiProvider.start(registry);
            manualProvider.start(registry);

            assertTrue(registry.has("test_echo"), "SPI tool should be present");
            assertTrue(registry.has("custom_tool"), "Manual tool should be present");
        }
    }

    // ==================== Helper ====================

    private static Tool createDummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Dummy tool: " + name; }
            @Override public ToolSchema getSchema() {
                return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(name, "ok");
            }
        };
    }
}
