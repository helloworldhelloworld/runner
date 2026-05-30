package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — 手动工具注册 Provider")
class ManualToolProviderTest {

    private ToolRegistry registry;
    private ManualToolProvider provider;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        provider = new ManualToolProvider();
    }

    @Test
    @DisplayName("sourceType 返回 manual")
    void sourceTypeIsManual() {
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("addTool + start 后工具注册到 registry 并可执行")
    void addToolRegistersOnStartAndToolExecutes() {
        Tool dummy = new SimpleTool("dummy", "ok");
        provider.addTool(dummy);

        assertFalse(registry.has("dummy"), "tool should not exist before start");
        provider.start(registry);
        assertTrue(registry.has("dummy"), "tool should exist after start");

        Tool resolved = registry.get("dummy");
        assertNotNull(resolved);
        ToolResult result = resolved.execute(Map.of());
        assertEquals("ok", result.getContent(),
                "registered tool should be executable with correct payload");
    }

    @Test
    @DisplayName("stop 注销 addTool 注册的工具")
    void stopUnregistersTools() {
        provider.addTool(new SimpleTool("dummy", "ok"));
        provider.start(registry);
        assertTrue(registry.has("dummy"));

        provider.stop(registry);
        assertFalse(registry.has("dummy"), "tool should be removed after stop");
    }

    @Test
    @DisplayName("链式调用 addTool 返回 this")
    void fluentAddTool() {
        ManualToolProvider result = provider
                .addTool(new SimpleTool("a", "r1"))
                .addTool(new SimpleTool("b", "r2"));
        assertSame(provider, result, "addTool should return this for chaining");

        provider.start(registry);
        assertTrue(registry.has("a"));
        assertTrue(registry.has("b"));
    }

    @Test
    @DisplayName("addAnnotated 返回 this 用于链式调用")
    void fluentAddAnnotated() {
        ManualToolProvider result = provider.addAnnotated(new Object());
        assertSame(provider, result);
    }

    private static class SimpleTool implements Tool {
        private final String name;
        private final String resultContent;
        SimpleTool(String name, String resultContent) {
            this.name = name; this.resultContent = resultContent;
        }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return name; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success(resultContent);
        }
    }
}
