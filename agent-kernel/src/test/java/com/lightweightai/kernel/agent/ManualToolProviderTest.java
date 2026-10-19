package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - manual tool registration with lifecycle")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== sourceType ====================

    @Test
    @DisplayName("Given a ManualToolProvider, when sourceType is called, then it returns 'manual'")
    void sourceTypeReturnsManual() {
        // Given
        ManualToolProvider provider = new ManualToolProvider();

        // When
        String type = provider.sourceType();

        // Then
        assertEquals("manual", type);
    }

    // ==================== addTool + start ====================

    @Test
    @DisplayName("Given a single tool added, when start is called, then the tool is in the registry")
    void startRegistersSingleTool() {
        // Given
        Tool tool = createSimpleTool("my_tool", "A test tool");
        ManualToolProvider provider = new ManualToolProvider().addTool(tool);

        // When
        provider.start(registry);

        // Then
        assertTrue(registry.has("my_tool"), "Tool should be registered after start");
        assertSame(tool, registry.get("my_tool").orElse(null),
            "Registry should contain the exact tool instance that was added");
    }

    @Test
    @DisplayName("Given multiple tools added, when start is called, then all tools are in the registry")
    void startRegistersMultipleTools() {
        // Given
        Tool toolA = createSimpleTool("tool_a", "Tool A");
        Tool toolB = createSimpleTool("tool_b", "Tool B");
        Tool toolC = createSimpleTool("tool_c", "Tool C");

        ManualToolProvider provider = new ManualToolProvider()
            .addTool(toolA)
            .addTool(toolB)
            .addTool(toolC);

        // When
        provider.start(registry);

        // Then
        assertEquals(3, registry.size());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertTrue(registry.has("tool_c"));
    }

    @Test
    @DisplayName("Given addTool is called, then it returns the provider for fluent chaining")
    void addToolReturnsSelfForChaining() {
        // Given
        ManualToolProvider provider = new ManualToolProvider();

        // When
        ManualToolProvider result = provider.addTool(createSimpleTool("t", "d"));

        // Then
        assertSame(provider, result, "addTool should return the same provider instance");
    }

    @Test
    @DisplayName("Given no tools added, when start is called, then registry remains empty")
    void startWithNoToolsLeavesRegistryEmpty() {
        // Given
        ManualToolProvider provider = new ManualToolProvider();

        // When
        provider.start(registry);

        // Then
        assertEquals(0, registry.size());
    }

    // ==================== addAnnotated + start ====================

    @Test
    @DisplayName("Given addAnnotated is called, then it returns the provider for fluent chaining")
    void addAnnotatedReturnsSelfForChaining() {
        // Given
        ManualToolProvider provider = new ManualToolProvider();

        // When
        ManualToolProvider result = provider.addAnnotated(new Object());

        // Then
        assertSame(provider, result, "addAnnotated should return the same provider instance");
    }

    // ==================== stop ====================

    @Test
    @DisplayName("Given tools were registered via start, when stop is called, then all tools are removed from registry")
    void stopUnregistersAllOwnTools() {
        // Given
        Tool toolA = createSimpleTool("tool_a", "Tool A");
        Tool toolB = createSimpleTool("tool_b", "Tool B");

        ManualToolProvider provider = new ManualToolProvider()
            .addTool(toolA)
            .addTool(toolB);
        provider.start(registry);
        assertEquals(2, registry.size(), "Precondition: both tools should be registered");

        // When
        provider.stop(registry);

        // Then
        assertFalse(registry.has("tool_a"), "tool_a should be unregistered after stop");
        assertFalse(registry.has("tool_b"), "tool_b should be unregistered after stop");
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Given tools from another source exist, when stop is called, then only own tools are removed")
    void stopDoesNotAffectOtherTools() {
        // Given
        Tool externalTool = createSimpleTool("external_tool", "External");
        registry.register(externalTool);

        Tool ownTool = createSimpleTool("own_tool", "Own");
        ManualToolProvider provider = new ManualToolProvider().addTool(ownTool);
        provider.start(registry);
        assertEquals(2, registry.size(), "Precondition: 2 tools should be registered");

        // When
        provider.stop(registry);

        // Then
        assertTrue(registry.has("external_tool"), "External tool should remain after stop");
        assertFalse(registry.has("own_tool"), "Own tool should be removed after stop");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("Given no tools were added, when stop is called, then it is a no-op")
    void stopWithNoToolsIsNoOp() {
        // Given
        ManualToolProvider provider = new ManualToolProvider();
        registry.register(createSimpleTool("pre_existing", "Pre-existing"));

        // When
        provider.stop(registry);

        // Then
        assertTrue(registry.has("pre_existing"), "Pre-existing tool should remain");
        assertEquals(1, registry.size());
    }

    // ==================== start then stop lifecycle ====================

    @Test
    @DisplayName("Given a full start-stop lifecycle, the registry state is consistent at each step")
    void fullLifecycle() {
        // Given
        Tool toolA = createSimpleTool("tool_a", "Tool A");
        Tool toolB = createSimpleTool("tool_b", "Tool B");

        ManualToolProvider provider = new ManualToolProvider()
            .addTool(toolA)
            .addTool(toolB);

        // Before start
        assertEquals(0, registry.size(), "Registry should be empty before start");

        // After start
        provider.start(registry);
        assertEquals(2, registry.size(), "Registry should have 2 tools after start");
        assertTrue(registry.isEnabled("tool_a"));
        assertTrue(registry.isEnabled("tool_b"));

        // Verify tools are functional (payload assertion)
        Tool retrieved = registry.get("tool_a").orElse(null);
        assertNotNull(retrieved);
        ToolResult result = retrieved.execute(Map.of());
        assertEquals("ok", result.getContent(), "Registered tool should be executable and return expected payload");

        // After stop
        provider.stop(registry);
        assertEquals(0, registry.size(), "Registry should be empty after stop");
        assertFalse(registry.has("tool_a"));
        assertFalse(registry.has("tool_b"));
    }

    // ==================== helper ====================

    private Tool createSimpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
