package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - Manual tool registration")
class ManualToolProviderTest {

    private ManualToolProvider provider;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        provider = new ManualToolProvider();
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("sourceType() returns 'manual'")
    void sourceTypeReturnsManual() {
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("start() registers all added tools into the registry")
    void startRegistersToolsIntoRegistry() {
        Tool tool = createSimpleTool("greet", "Say hello");
        provider.addTool(tool);

        provider.start(registry);

        assertTrue(registry.has("greet"));
        assertEquals("Say hello", registry.get("greet").orElseThrow().getDescription());
    }

    @Test
    @DisplayName("stop() unregisters all added tools from the registry")
    void stopUnregistersToolsFromRegistry() {
        Tool tool = createSimpleTool("greet", "Say hello");
        provider.addTool(tool);
        provider.start(registry);
        assertTrue(registry.has("greet"));

        provider.stop(registry);

        assertFalse(registry.has("greet"));
    }

    @Test
    @DisplayName("addTool() returns this for fluent chaining")
    void addToolReturnsThisForChaining() {
        Tool tool1 = createSimpleTool("tool1", "First tool");
        Tool tool2 = createSimpleTool("tool2", "Second tool");

        ManualToolProvider result = provider.addTool(tool1).addTool(tool2);

        assertSame(provider, result);
    }

    @Test
    @DisplayName("Provider with no tools added - start/stop are no-ops")
    void emptyProviderStartStopAreNoOps() {
        int sizeBefore = registry.size();

        provider.start(registry);
        assertEquals(sizeBefore, registry.size());

        provider.stop(registry);
        assertEquals(sizeBefore, registry.size());
    }

    @Test
    @DisplayName("Multiple tools can be added and all get registered on start")
    void multipleToolsAllRegisteredOnStart() {
        Tool tool1 = createSimpleTool("alpha", "Alpha tool");
        Tool tool2 = createSimpleTool("beta", "Beta tool");
        Tool tool3 = createSimpleTool("gamma", "Gamma tool");

        provider.addTool(tool1).addTool(tool2).addTool(tool3);
        provider.start(registry);

        assertTrue(registry.has("alpha"));
        assertTrue(registry.has("beta"));
        assertTrue(registry.has("gamma"));
        assertEquals(3, registry.enabledCount());
    }

    @Test
    @DisplayName("After stop(), registry no longer has the tools")
    void afterStopRegistryDoesNotHaveTools() {
        Tool tool1 = createSimpleTool("foo", "Foo tool");
        Tool tool2 = createSimpleTool("bar", "Bar tool");

        provider.addTool(tool1).addTool(tool2);
        provider.start(registry);

        assertTrue(registry.has("foo"));
        assertTrue(registry.has("bar"));

        provider.stop(registry);

        assertFalse(registry.has("foo"));
        assertFalse(registry.has("bar"));
        assertEquals(0, registry.size());
    }

    // ==================== Helper ====================

    private Tool createSimpleTool(String name, String description) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.empty();
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("executed " + name);
            }
        };
    }
}
