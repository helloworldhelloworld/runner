package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - 手动工具注册")
class ManualToolProviderTest {

    @Test
    @DisplayName("sourceType 返回 manual")
    void sourceType() {
        assertEquals("manual", new ManualToolProvider().sourceType());
    }

    @Test
    @DisplayName("addTool 支持链式调用")
    void fluentAddTool() {
        ManualToolProvider provider = new ManualToolProvider()
            .addTool(stubTool("a"))
            .addTool(stubTool("b"));
        assertNotNull(provider);
    }

    @Test
    @DisplayName("start 时将 Tool 注册到 ToolRegistry")
    void startRegistersTools() {
        ToolRegistry registry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider()
            .addTool(stubTool("weather"))
            .addTool(stubTool("math"));

        provider.start(registry);

        assertTrue(registry.has("weather"));
        assertTrue(registry.has("math"));
    }

    @Test
    @DisplayName("stop 时通过名称注销 Tool")
    void stopUnregistersTools() {
        ToolRegistry registry = new ToolRegistry();

        ManualToolProvider provider = new ManualToolProvider()
            .addTool(stubTool("weather"));

        provider.start(registry);
        assertTrue(registry.has("weather"));

        provider.stop(registry);
        assertFalse(registry.has("weather"));
    }

    @Test
    @DisplayName("addAnnotated 注册带 @ToolFunction 注解的对象")
    void addAnnotatedRegistersObject() {
        ManualToolProvider provider = new ManualToolProvider()
            .addAnnotated(new Object());
        assertNotNull(provider);
    }

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public Map<String, Object> getParameterSchema() { return Map.of(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
