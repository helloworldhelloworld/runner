package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — manual tool registration lifecycle")
class ManualToolProviderTest {

    @Test
    @DisplayName("sourceType returns 'manual'")
    void sourceType() {
        assertEquals("manual", new ManualToolProvider().sourceType());
    }

    @Test
    @DisplayName("addTool is fluent — returns same instance")
    void addToolFluent() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(createTool("a"));
        assertSame(provider, returned);
    }

    @Test
    @DisplayName("addAnnotated is fluent — returns same instance")
    void addAnnotatedFluent() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addAnnotated(new Object());
        assertSame(provider, returned);
    }

    @Test
    @DisplayName("start registers all added tools in registry")
    void startRegisterTools() {
        ToolRegistry registry = new ToolRegistry();
        Tool tool1 = createTool("calc");
        Tool tool2 = createTool("weather");

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(tool1)
                .addTool(tool2);
        provider.start(registry);

        assertTrue(registry.has("calc"), "calc should be registered");
        assertTrue(registry.has("weather"), "weather should be registered");
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("stop unregisters all previously added tools by name")
    void stopUnregistersTools() {
        ToolRegistry registry = new ToolRegistry();
        Tool tool1 = createTool("calc");
        Tool tool2 = createTool("weather");

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(tool1)
                .addTool(tool2);
        provider.start(registry);
        assertEquals(2, registry.size());

        provider.stop(registry);

        assertFalse(registry.has("calc"), "calc should be unregistered");
        assertFalse(registry.has("weather"), "weather should be unregistered");
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("start with no tools does nothing")
    void startEmpty() {
        ToolRegistry registry = new ToolRegistry();
        new ManualToolProvider().start(registry);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("stop with no tools does nothing")
    void stopEmpty() {
        ToolRegistry registry = new ToolRegistry();
        new ManualToolProvider().stop(registry);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("chained addTool calls accumulate all tools")
    void chainedAddTool() {
        ToolRegistry registry = new ToolRegistry();

        new ManualToolProvider()
                .addTool(createTool("a"))
                .addTool(createTool("b"))
                .addTool(createTool("c"))
                .start(registry);

        assertEquals(3, registry.size());
        assertTrue(registry.has("a"));
        assertTrue(registry.has("b"));
        assertTrue(registry.has("c"));
    }

    @Test
    @DisplayName("registered tool is retrievable and functional")
    void registeredToolFunctional() {
        ToolRegistry registry = new ToolRegistry();
        Tool tool = createTool("echo");

        new ManualToolProvider().addTool(tool).start(registry);

        Tool retrieved = registry.get("echo").orElseThrow();
        ToolResult result = retrieved.execute(Map.of());
        assertFalse(result.isError());
        assertEquals("ok", result.getContent());
    }

    @Test
    @DisplayName("stop only unregisters tools added via this provider")
    void stopOnlyOwned() {
        ToolRegistry registry = new ToolRegistry();

        registry.register(createTool("pre-existing"));

        ManualToolProvider provider = new ManualToolProvider().addTool(createTool("mine"));
        provider.start(registry);
        assertEquals(2, registry.size());

        provider.stop(registry);

        assertTrue(registry.has("pre-existing"), "pre-existing tool should survive");
        assertFalse(registry.has("mine"), "provider's tool should be removed");
    }

    private Tool createTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Tool " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.withProperties(Map.of()); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
