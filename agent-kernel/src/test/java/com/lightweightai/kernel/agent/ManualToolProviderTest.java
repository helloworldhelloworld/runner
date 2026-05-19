package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — 手动注册 Provider 生命周期")
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
    @DisplayName("start 将 addTool 的工具注册到 registry")
    void startRegistersAddedTools() {
        Tool tool1 = noopTool("tool_a");
        Tool tool2 = noopTool("tool_b");

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(tool1)
                .addTool(tool2);

        assertEquals(0, registry.size());
        provider.start(registry);

        assertEquals(2, registry.size());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
    }

    @Test
    @DisplayName("stop 注销之前注册的工具")
    void stopUnregistersTools() {
        Tool tool = noopTool("removable");
        ManualToolProvider provider = new ManualToolProvider().addTool(tool);

        provider.start(registry);
        assertTrue(registry.has("removable"));

        provider.stop(registry);
        assertFalse(registry.has("removable"));
    }

    @Test
    @DisplayName("stop 后其他工具仍然保留")
    void stopOnlyRemovesOwnTools() {
        registry.register(noopTool("pre_existing"));

        ManualToolProvider provider = new ManualToolProvider().addTool(noopTool("dynamic"));
        provider.start(registry);
        assertEquals(2, registry.size());

        provider.stop(registry);
        assertEquals(1, registry.size());
        assertTrue(registry.has("pre_existing"));
        assertFalse(registry.has("dynamic"));
    }

    @Test
    @DisplayName("addTool 支持链式调用")
    void fluentApi() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("a"))
                .addTool(noopTool("b"))
                .addTool(noopTool("c"));

        provider.start(registry);
        assertEquals(3, registry.size());
    }

    @Test
    @DisplayName("payload 断言: start 后工具的 getToolDefinitions 包含正确 name 和 description")
    void startedToolsAppearInToolDefinitions() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("calc"));

        provider.start(registry);

        var defs = registry.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("calc", defs.get(0).get("name"));
        assertEquals("calc tool", defs.get(0).get("description"));
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
