package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — 手动注册 Provider 生命周期")
class ManualToolProviderTest {

    private ManualToolProvider provider;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        provider = new ManualToolProvider();
        registry = new ToolRegistry();
    }

    @Nested
    @DisplayName("sourceType")
    class SourceTypeTests {

        @Test
        @DisplayName("sourceType 返回 'manual'")
        void sourceTypeIsManual() {
            assertEquals("manual", provider.sourceType());
        }
    }

    @Nested
    @DisplayName("addTool + start/stop 生命周期")
    class ToolLifecycleTests {

        @Test
        @DisplayName("start 注册所有已 add 的工具")
        void startRegistersTools() {
            Tool echo = new SimpleTool("echo", "Echo input");
            Tool greet = new SimpleTool("greet", "Greet user");

            provider.addTool(echo).addTool(greet);
            provider.start(registry);

            assertTrue(registry.has("echo"), "echo 应已注册");
            assertTrue(registry.has("greet"), "greet 应已注册");
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("stop 注销所有已注册的工具")
        void stopUnregistersTools() {
            Tool echo = new SimpleTool("echo", "Echo input");
            provider.addTool(echo);
            provider.start(registry);
            assertTrue(registry.has("echo"));

            provider.stop(registry);
            assertFalse(registry.has("echo"), "echo 应已被注销");
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("start 后注册的工具可通过 registry.get 获取并执行")
        void registeredToolIsExecutable() {
            provider.addTool(new SimpleTool("calc", "Calculate"));
            provider.start(registry);

            Tool resolved = registry.get("calc").orElseThrow();
            ToolResult result = resolved.execute(Map.of());
            assertEquals("executed", result.getContent());
        }

        @Test
        @DisplayName("addTool 支持链式调用（fluent）")
        void fluentBuilder() {
            ManualToolProvider result = provider
                    .addTool(new SimpleTool("a", "A"))
                    .addTool(new SimpleTool("b", "B"));

            assertSame(provider, result, "addTool 应返回自身以支持链式调用");
        }
    }

    @Nested
    @DisplayName("addAnnotated + start")
    class AnnotatedRegistrationTests {

        @Test
        @DisplayName("start 注册带 @ToolFunction 注解的对象方法")
        void startRegistersAnnotatedMethods() {
            provider.addAnnotated(new AnnotatedToolsFixture());
            provider.start(registry);

            assertTrue(registry.has("hello"), "hello 方法应被注册为工具");
        }

        @Test
        @DisplayName("addAnnotated 支持链式调用")
        void fluentAnnotated() {
            ManualToolProvider result = provider.addAnnotated(new AnnotatedToolsFixture());
            assertSame(provider, result);
        }
    }

    @Nested
    @DisplayName("混合注册")
    class MixedRegistrationTests {

        @Test
        @DisplayName("同时 addTool 和 addAnnotated 全部注册成功")
        void mixedRegistration() {
            provider.addTool(new SimpleTool("manual-tool", "Manual"))
                    .addAnnotated(new AnnotatedToolsFixture());
            provider.start(registry);

            assertTrue(registry.has("manual-tool"));
            assertTrue(registry.has("hello"));
        }
    }

    // ==================== Fixtures ====================

    private static class SimpleTool implements Tool {
        private final String name;
        private final String description;

        SimpleTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("executed");
        }
    }

    public static class AnnotatedToolsFixture {
        @ToolFunction(name = "hello", description = "Say hello")
        public String hello(@ToolParam(name = "name") String name) {
            return "Hello, " + name;
        }
    }
}
