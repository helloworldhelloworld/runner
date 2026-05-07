package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - 手动工具注册 Provider")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    static class TestTool implements Tool {
        @Override public String getName() { return "test_tool"; }
        @Override public String getDescription() { return "A test tool"; }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("executed");
        }
    }

    static class AnotherTool implements Tool {
        @Override public String getName() { return "another_tool"; }
        @Override public String getDescription() { return "Another tool"; }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("done");
        }
    }

    static class AnnotatedTools {
        @ToolFunction(name = "greet", description = "Say hello")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hello, " + name + "!";
        }
    }

    @Test
    @DisplayName("sourceType → 'manual'")
    void sourceType() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("addTool + start → Tool 注册到 registry")
    void addToolRegisters() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new TestTool());

        provider.start(registry);

        assertTrue(registry.has("test_tool"));
        ToolResult result = registry.get("test_tool").get().execute(Map.of());
        assertEquals("executed", result.getContent());
    }

    @Test
    @DisplayName("多个 Tool → 全部注册")
    void multipleToolsRegistered() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new TestTool())
                .addTool(new AnotherTool());

        provider.start(registry);

        assertEquals(2, registry.size());
        assertTrue(registry.has("test_tool"));
        assertTrue(registry.has("another_tool"));
    }

    @Test
    @DisplayName("addAnnotated + start → 注解工具注册到 registry")
    void addAnnotatedRegisters() {
        ManualToolProvider provider = new ManualToolProvider()
                .addAnnotated(new AnnotatedTools());

        provider.start(registry);

        assertTrue(registry.has("greet"));
        ToolResult result = registry.get("greet").get().execute(Map.of("name", "World"));
        assertEquals("Hello, World!", result.getContent());
    }

    @Test
    @DisplayName("stop → 注销已注册的 Tool（非注解工具）")
    void stopUnregistersTools() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new TestTool());

        provider.start(registry);
        assertTrue(registry.has("test_tool"));

        provider.stop(registry);
        assertFalse(registry.has("test_tool"));
    }

    @Test
    @DisplayName("链式调用 — addTool 返回 this")
    void fluentApi() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new TestTool())
                .addTool(new AnotherTool())
                .addAnnotated(new AnnotatedTools());

        provider.start(registry);
        assertEquals(3, registry.size());
    }

    @Test
    @DisplayName("实现 ToolSourceProvider 接口")
    void implementsInterface() {
        ManualToolProvider provider = new ManualToolProvider();
        assertTrue(provider instanceof ToolSourceProvider);
    }
}
