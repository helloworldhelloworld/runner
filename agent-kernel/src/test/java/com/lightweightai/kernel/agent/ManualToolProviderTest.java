package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - 手动注册工具提供者")
class ManualToolProviderTest {

    @Test
    @DisplayName("sourceType 为 manual")
    void shouldReturnManualSourceType() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("addTool + start 注册工具到 registry")
    void shouldRegisterToolsOnStart() {
        ManualToolProvider provider = new ManualToolProvider();
        Tool tool = simpleTool("greet", "say hello");
        provider.addTool(tool);

        ToolRegistry registry = new ToolRegistry();
        provider.start(registry);

        assertTrue(registry.has("greet"));
    }

    @Test
    @DisplayName("stop 注销已注册的工具")
    void shouldUnregisterToolsOnStop() {
        ManualToolProvider provider = new ManualToolProvider();
        Tool tool = simpleTool("greet", "say hello");
        provider.addTool(tool);

        ToolRegistry registry = new ToolRegistry();
        provider.start(registry);
        assertTrue(registry.has("greet"));

        provider.stop(registry);
        assertFalse(registry.has("greet"));
    }

    @Test
    @DisplayName("链式 addTool 调用")
    void shouldChainAddTool() {
        ManualToolProvider provider = new ManualToolProvider()
            .addTool(simpleTool("a", "tool a"))
            .addTool(simpleTool("b", "tool b"));

        ToolRegistry registry = new ToolRegistry();
        provider.start(registry);

        assertTrue(registry.has("a"));
        assertTrue(registry.has("b"));
    }

    @Test
    @DisplayName("空 provider start 不抛异常")
    void shouldHandleEmptyProvider() {
        ManualToolProvider provider = new ManualToolProvider();
        ToolRegistry registry = new ToolRegistry();
        assertDoesNotThrow(() -> provider.start(registry));
    }

    private Tool simpleTool(String name, String desc) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return desc; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
