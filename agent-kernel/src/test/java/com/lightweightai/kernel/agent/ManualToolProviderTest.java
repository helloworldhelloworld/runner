package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — lifecycle-managed tool registration")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("start() registers all added tools into registry")
    void startRegisteresTools() {
        Tool echo = simpleTool("echo", "Echoes input");
        Tool ping = simpleTool("ping", "Pong");

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(echo)
                .addTool(ping);

        provider.start(registry);

        assertTrue(registry.has("echo"));
        assertTrue(registry.has("ping"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("stop() unregisters tools from registry")
    void stopUnregistersTools() {
        Tool echo = simpleTool("echo", "Echoes input");
        ManualToolProvider provider = new ManualToolProvider().addTool(echo);

        provider.start(registry);
        assertTrue(registry.has("echo"));

        provider.stop(registry);
        assertFalse(registry.has("echo"));
    }

    @Test
    @DisplayName("sourceType returns 'manual'")
    void sourceTypeIsManual() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("tools registered via start() are actually callable")
    void registeredToolsAreCallable() {
        Tool echo = simpleTool("echo", "Echoes input");
        ManualToolProvider provider = new ManualToolProvider().addTool(echo);

        provider.start(registry);

        Tool retrieved = registry.get("echo").orElse(null);
        assertNotNull(retrieved);
        ToolResult result = retrieved.execute(Map.of("input", "hello"));
        assertFalse(result.isError());
        assertEquals("executed", result.getContent());
    }

    @Test
    @DisplayName("stop() only removes its own tools, not others")
    void stopDoesNotAffectOtherTools() {
        Tool external = simpleTool("external", "External tool");
        registry.register(external);

        Tool echo = simpleTool("echo", "Echoes input");
        ManualToolProvider provider = new ManualToolProvider().addTool(echo);
        provider.start(registry);

        assertEquals(2, registry.size());

        provider.stop(registry);

        assertTrue(registry.has("external"));
        assertFalse(registry.has("echo"));
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("fluent API returns same instance for chaining")
    void fluentApiChaining() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(simpleTool("a", "A"));
        assertSame(provider, returned);
    }

    private static Tool simpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("executed");
            }
        };
    }
}
