package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — 手动注册工具 Provider")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("start 时注册的工具可在 registry 中找到")
    void registersToolsOnStart() {
        Tool echo = new StubTool("echo", "Echo tool");

        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(echo);
        provider.start(registry);

        assertTrue(registry.has("echo"));
        assertSame(echo, registry.get("echo").orElse(null));
    }

    @Test
    @DisplayName("addTool 支持链式调用")
    void addToolReturnsSelf() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(new StubTool("a", "A"));
        assertSame(provider, returned);
    }

    @Test
    @DisplayName("多个工具全部注册到 registry")
    void registersMultipleTools() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new StubTool("tool1", "Tool 1"))
                .addTool(new StubTool("tool2", "Tool 2"))
                .addTool(new StubTool("tool3", "Tool 3"));

        provider.start(registry);

        assertTrue(registry.has("tool1"));
        assertTrue(registry.has("tool2"));
        assertTrue(registry.has("tool3"));
    }

    @Test
    @DisplayName("stop 时注销已注册的工具")
    void unregistersToolsOnStop() {
        Tool echo = new StubTool("echo", "Echo tool");

        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(echo);
        provider.start(registry);
        assertTrue(registry.has("echo"));

        provider.stop(registry);
        assertFalse(registry.has("echo"));
    }

    @Test
    @DisplayName("sourceType 返回 manual")
    void sourceTypeIsManual() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("stop 不影响其他 Provider 注册的工具")
    void stopDoesNotAffectOtherTools() {
        Tool otherTool = new StubTool("other", "Other tool");
        registry.register(otherTool);

        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(new StubTool("mine", "My tool"));
        provider.start(registry);
        provider.stop(registry);

        assertTrue(registry.has("other"), "Other tool should still be in registry");
        assertFalse(registry.has("mine"), "Own tool should be removed");
    }

    private static class StubTool implements Tool {
        private final String name;
        private final String description;

        StubTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("stub result");
        }
    }
}
