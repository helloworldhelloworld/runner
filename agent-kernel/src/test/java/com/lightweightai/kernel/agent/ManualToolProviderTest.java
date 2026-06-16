package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - manual tool registration lifecycle")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("sourceType returns 'manual'")
    void sourceType() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("start registers tools into registry")
    void startRegistersTools() {
        Tool echo = simpleTool("echo", args -> ToolResult.success("echo"));
        Tool calc = simpleTool("calc", args -> ToolResult.success("calc"));

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(echo)
                .addTool(calc);

        provider.start(registry);

        assertTrue(registry.has("echo"));
        assertTrue(registry.has("calc"));
        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("stop unregisters tools from registry")
    void stopUnregistersTools() {
        Tool echo = simpleTool("echo", args -> ToolResult.success("echo"));

        ManualToolProvider provider = new ManualToolProvider().addTool(echo);
        provider.start(registry);

        assertTrue(registry.has("echo"));

        provider.stop(registry);

        assertFalse(registry.has("echo"));
    }

    @Test
    @DisplayName("registered tool is executable via registry")
    void registeredToolIsExecutable() {
        Tool greet = simpleTool("greet", args -> {
            String name = (String) args.getOrDefault("name", "world");
            return ToolResult.success("Hello, " + name + "!");
        });

        ManualToolProvider provider = new ManualToolProvider().addTool(greet);
        provider.start(registry);

        Tool found = registry.get("greet").orElseThrow();
        ToolResult result = found.execute(Map.of("name", "Alice"));

        assertFalse(result.isError());
        assertEquals("Hello, Alice!", result.getContent());
    }

    @Test
    @DisplayName("fluent API chains properly")
    void fluentApiChains() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(simpleTool("t1", args -> ToolResult.success("")))
                .addTool(simpleTool("t2", args -> ToolResult.success("")));

        provider.start(registry);
        assertEquals(2, registry.getAll().size());
    }

    private static Tool simpleTool(String name, java.util.function.Function<Map<String, Object>, ToolResult> fn) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return fn.apply(args); }
        };
    }
}
