package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ManualToolProvider lifecycle: start registers tools, stop unregisters them.
 *
 * Verifies the ToolSourceProvider SPI contract for manually-constructed tools.
 */
@DisplayName("ManualToolProvider lifecycle")
class ManualToolProviderTest {

    @Test
    @DisplayName("start registers tools into ToolRegistry")
    void start_registersTools() {
        ToolRegistry registry = new ToolRegistry();
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("tool_a"))
                .addTool(noopTool("tool_b"));

        assertEquals(0, registry.getEnabled().size());

        provider.start(registry);

        assertEquals(2, registry.getEnabled().size());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
    }

    @Test
    @DisplayName("stop unregisters tools from ToolRegistry")
    void stop_unregistersTools() {
        ToolRegistry registry = new ToolRegistry();
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("tool_a"))
                .addTool(noopTool("tool_b"));

        provider.start(registry);
        assertEquals(2, registry.getEnabled().size());

        provider.stop(registry);
        assertFalse(registry.has("tool_a"));
        assertFalse(registry.has("tool_b"));
    }

    @Test
    @DisplayName("sourceType is 'manual'")
    void sourceType() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("fluent API allows chaining")
    void fluentApi() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(noopTool("a"))
                .addTool(noopTool("b"))
                .addTool(noopTool("c"));

        ToolRegistry registry = new ToolRegistry();
        provider.start(registry);
        assertEquals(3, registry.getEnabled().size());
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
