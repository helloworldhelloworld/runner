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
    @DisplayName("sourceType 返回 manual")
    void sourceTypeIsManual() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("start 将 addTool 注册的工具添加到 registry")
    void startRegistersTool() {
        Tool echo = new SimpleTool("echo", "Echo tool");
        ManualToolProvider provider = new ManualToolProvider().addTool(echo);

        provider.start(registry);

        assertTrue(registry.has("echo"));
        assertEquals("Echo tool", registry.get("echo").orElseThrow().getDescription());
    }

    @Test
    @DisplayName("start 注册多个工具")
    void startRegistersMultipleTools() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("t1", "Tool 1"))
                .addTool(new SimpleTool("t2", "Tool 2"));

        provider.start(registry);

        assertEquals(2, registry.size());
        assertTrue(registry.has("t1"));
        assertTrue(registry.has("t2"));
    }

    @Test
    @DisplayName("stop 从 registry 注销已注册的工具")
    void stopUnregistersTools() {
        Tool echo = new SimpleTool("echo", "Echo tool");
        ManualToolProvider provider = new ManualToolProvider().addTool(echo);

        provider.start(registry);
        assertTrue(registry.has("echo"));

        provider.stop(registry);
        assertFalse(registry.has("echo"));
    }

    @Test
    @DisplayName("addTool 支持链式调用")
    void addToolReturnsSelf() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(new SimpleTool("a", "A"));
        assertSame(provider, returned);
    }

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
            return ToolResult.success("ok");
        }
    }
}
