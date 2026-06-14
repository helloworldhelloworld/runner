package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — 手动工具注册生命周期")
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
    @DisplayName("addTool + start → 工具注册到 registry")
    void startRegistersTool() {
        Tool tool = simpleTool("greet", "Say hello");
        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(tool);

        assertFalse(registry.has("greet"));
        provider.start(registry);
        assertTrue(registry.has("greet"));
    }

    @Test
    @DisplayName("addAnnotated + start → 注解工具注册到 registry")
    void startRegistersAnnotatedTools() {
        ManualToolProvider provider = new ManualToolProvider();
        provider.addAnnotated(new SampleTools());

        provider.start(registry);
        assertTrue(registry.has("echo"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("start 同时注册 tool 和 annotated")
    void startRegistersBothTypes() {
        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(simpleTool("t1", "Tool 1"));
        provider.addAnnotated(new SampleTools());

        provider.start(registry);
        assertTrue(registry.has("t1"));
        assertTrue(registry.has("echo"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("stop → 卸载已注册的 tool")
    void stopUnregistersTool() {
        Tool tool = simpleTool("greet", "Say hello");
        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(tool);

        provider.start(registry);
        assertTrue(registry.has("greet"));

        provider.stop(registry);
        assertFalse(registry.has("greet"));
    }

    @Test
    @DisplayName("链式调用 addTool 返回 this")
    void addToolReturnsSelf() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(simpleTool("a", "A"));
        assertSame(provider, returned);
    }

    @Test
    @DisplayName("链式调用 addAnnotated 返回 this")
    void addAnnotatedReturnsSelf() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addAnnotated(new SampleTools());
        assertSame(provider, returned);
    }

    @Test
    @DisplayName("注册的工具可执行并返回正确结果 — 传输链验证")
    void registeredToolExecuteReturnsPayload() {
        Tool tool = simpleTool("calc", "Calculator");
        ManualToolProvider provider = new ManualToolProvider();
        provider.addTool(tool);
        provider.start(registry);

        Tool retrieved = registry.get("calc").orElseThrow();
        ToolResult result = retrieved.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("42", result.getContent());
    }

    // ==================== Helper ====================

    static class SampleTools {
        @ToolFunction(name = "echo", description = "Echo input")
        public String echo(@ToolParam(name = "text", required = true) String text) {
            return text;
        }
    }

    private Tool simpleTool(String name, String description) {
        return new Tool() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return description; }
            @Override
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("42");
            }
        };
    }
}
