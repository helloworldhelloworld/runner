package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ManualToolProviderTest {

    private ManualToolProvider provider;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        provider = new ManualToolProvider();
        registry = new ToolRegistry();
    }

    @Test
    void sourceTypeIsManual() {
        assertEquals("manual", provider.sourceType());
    }

    @Test
    void addToolReturnsSelfForChaining() {
        Tool tool = simpleTool("alpha");
        ManualToolProvider result = provider.addTool(tool);
        assertSame(provider, result, "addTool should return the same ManualToolProvider instance for fluent chaining");
    }

    @Test
    void addAnnotatedReturnsSelfForChaining() {
        Object annotated = new AnnotatedTools();
        ManualToolProvider result = provider.addAnnotated(annotated);
        assertSame(provider, result, "addAnnotated should return the same ManualToolProvider instance for fluent chaining");
    }

    @Test
    void startRegistersSingleTool() {
        Tool tool = simpleTool("my_tool");
        provider.addTool(tool);

        provider.start(registry);

        assertTrue(registry.has("my_tool"), "Registry should contain the registered tool by name");
        assertEquals("my_tool", registry.get("my_tool").orElseThrow().getName(),
                "Retrieved tool name must match the registered tool name");
    }

    @Test
    void startRegistersMultipleTools() {
        Tool toolA = simpleTool("tool_a");
        Tool toolB = simpleTool("tool_b");
        provider.addTool(toolA);
        provider.addTool(toolB);

        provider.start(registry);

        assertTrue(registry.has("tool_a"), "Registry should contain tool_a");
        assertTrue(registry.has("tool_b"), "Registry should contain tool_b");
        assertEquals("tool_a", registry.get("tool_a").orElseThrow().getName());
        assertEquals("tool_b", registry.get("tool_b").orElseThrow().getName());
        assertEquals(2, registry.size(), "Registry should contain exactly 2 tools");
    }

    @Test
    void startRegistersAnnotatedObjects() {
        AnnotatedTools annotated = new AnnotatedTools();
        provider.addAnnotated(annotated);

        provider.start(registry);

        assertTrue(registry.has("greet"),
                "Registry should contain the 'greet' tool registered from @ToolFunction annotation");
        Tool registeredTool = registry.get("greet").orElseThrow();
        assertEquals("greet", registeredTool.getName());
        assertEquals("greet someone", registeredTool.getDescription(),
                "Registered tool description should match the @ToolFunction annotation value");
    }

    @Test
    void stopUnregistersTools() {
        Tool tool = simpleTool("removable");
        provider.addTool(tool);
        provider.start(registry);

        assertTrue(registry.has("removable"), "Tool should be present after start");

        provider.stop(registry);

        assertFalse(registry.has("removable"), "Tool should be removed after stop");
        assertTrue(registry.get("removable").isEmpty(), "get() should return empty Optional after stop");
        assertEquals(0, registry.size(), "Registry should be empty after stop");
    }

    @Test
    void stopDoesNotAffectAnnotatedTools() {
        Tool tool = simpleTool("explicit_tool");
        AnnotatedTools annotated = new AnnotatedTools();
        provider.addTool(tool);
        provider.addAnnotated(annotated);

        provider.start(registry);

        assertTrue(registry.has("explicit_tool"), "Explicit tool should be registered after start");
        assertTrue(registry.has("greet"), "Annotated tool should be registered after start");

        provider.stop(registry);

        assertFalse(registry.has("explicit_tool"),
                "Explicit tool should be unregistered after stop");
        assertTrue(registry.has("greet"),
                "Annotated tool should remain registered after stop (stop only unregisters Tool objects)");
        assertEquals("greet", registry.get("greet").orElseThrow().getName(),
                "Annotated tool should still be retrievable by name after stop");
    }

    @Test
    void emptyProviderStartDoesNothing() {
        assertDoesNotThrow(() -> provider.start(registry),
                "start() on an empty provider should not throw");
        assertEquals(0, registry.size(), "Registry should remain empty when no tools were added");
    }

    @Test
    void fluentChainingWorks() {
        Tool t1 = simpleTool("chain_a");
        Tool t2 = simpleTool("chain_b");
        AnnotatedTools obj = new AnnotatedTools();

        provider.addTool(t1).addTool(t2).addAnnotated(obj);

        provider.start(registry);

        assertTrue(registry.has("chain_a"), "First chained tool should be registered");
        assertTrue(registry.has("chain_b"), "Second chained tool should be registered");
        assertTrue(registry.has("greet"), "Annotated tool from chained addAnnotated should be registered");
        assertEquals("chain_a", registry.get("chain_a").orElseThrow().getName());
        assertEquals("chain_b", registry.get("chain_b").orElseThrow().getName());
        assertEquals("greet", registry.get("greet").orElseThrow().getName());
        assertEquals(3, registry.size(), "Registry should contain all 3 tools after fluent chaining");
    }

    // ==================== Helpers ====================

    private static Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test tool: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

    private static class AnnotatedTools {
        @ToolFunction(description = "greet someone")
        public String greet(@ToolParam(name = "name") String name) {
            return "Hello, " + name;
        }
    }
}
