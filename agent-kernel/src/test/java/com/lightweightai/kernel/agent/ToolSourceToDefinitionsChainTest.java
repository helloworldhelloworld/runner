package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain test: ToolSourceProvider → ToolRegistry → getToolDefinitions()
 *
 * Validates that tools registered via different providers actually appear in the
 * LLM-facing tool definitions with correct name, description, and schema.
 * This catches wiring bugs where tools are registered but invisible to the LLM.
 */
@DisplayName("Tool source → registry → LLM definitions transmission chain")
class ToolSourceToDefinitionsChainTest {

    static class SampleAnnotatedTools {
        @ToolFunction(name = "sample_add", description = "Add two numbers")
        public String add(
            @ToolParam(name = "a", description = "First", required = true) int a,
            @ToolParam(name = "b", description = "Second", required = true) int b
        ) {
            return String.valueOf(a + b);
        }
    }

    @Test
    @DisplayName("ManualToolProvider → ToolRegistry → getToolDefinitions carries tool metadata end-to-end")
    void manualProviderToolShouldAppearInDefinitions() {
        ToolRegistry registry = new ToolRegistry();

        Tool customTool = new Tool() {
            @Override public String getName() { return "custom_calculator"; }
            @Override public String getDescription() { return "A custom calculator tool"; }
            @Override public ToolSchema getSchema() {
                return ToolSchema.withRequired(
                    Map.of("expression", Map.of("type", "string", "description", "Math expression")),
                    "expression"
                );
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("42");
            }
        };

        ManualToolProvider provider = new ManualToolProvider().addTool(customTool);
        provider.start(registry);

        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        Map<String, Object> found = definitions.stream()
            .filter(d -> "custom_calculator".equals(d.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "custom_calculator not in definitions. Got: " +
                definitions.stream().map(d -> d.get("name")).toList()));

        assertEquals("A custom calculator tool", found.get("description"),
            "Description must carry through to LLM definition");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) found.get("input_schema");
        assertNotNull(inputSchema, "input_schema must be present in LLM definition");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertNotNull(properties.get("expression"),
            "Schema properties must carry through to LLM definition");
    }

    @Test
    @DisplayName("Annotated tool → ToolRegistry.registerObject → getToolDefinitions carries schema end-to-end")
    void annotatedToolShouldCarrySchemaToDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new SampleAnnotatedTools());

        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        Map<String, Object> found = definitions.stream()
            .filter(d -> "sample_add".equals(d.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("sample_add not in definitions"));

        assertEquals("Add two numbers", found.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) found.get("input_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertNotNull(properties.get("a"), "Parameter 'a' must be in schema");
        assertNotNull(properties.get("b"), "Parameter 'b' must be in schema");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertTrue(required.contains("a"), "Required params must carry through");
        assertTrue(required.contains("b"), "Required params must carry through");
    }

    @Test
    @DisplayName("SPI scan + manual registration → combined definitions have all tools")
    void combinedSourcesShouldAllAppearInDefinitions() {
        ToolRegistry registry = new ToolRegistry();

        LegacyScanProvider spiProvider = new LegacyScanProvider();
        spiProvider.start(registry);

        ManualToolProvider manualProvider = new ManualToolProvider()
            .addAnnotated(new SampleAnnotatedTools());
        manualProvider.start(registry);

        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        Set<String> names = definitions.stream()
            .map(d -> (String) d.get("name"))
            .collect(Collectors.toSet());

        assertTrue(names.contains("sample_add"),
            "Manual annotated tool should be in definitions");
        assertTrue(names.contains("test_echo"),
            "SPI-discovered tool should be in definitions");
    }

    @Test
    @DisplayName("Disabled tool is excluded from getToolDefinitions")
    void disabledToolShouldNotAppearInDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new SampleAnnotatedTools());

        registry.disable("sample_add");

        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        boolean found = definitions.stream()
            .anyMatch(d -> "sample_add".equals(d.get("name")));
        assertFalse(found, "Disabled tool should not appear in LLM definitions");
    }

    @Test
    @DisplayName("ScopedToolRegistry with deny list excludes tool from definitions")
    void scopedRegistryDenyListShouldFilterDefinitions() {
        ToolRegistry baseRegistry = new ToolRegistry();
        baseRegistry.registerObject(new SampleAnnotatedTools());

        Tool anotherTool = new Tool() {
            @Override public String getName() { return "secret_tool"; }
            @Override public String getDescription() { return "Secret"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("secret");
            }
        };
        baseRegistry.register(anotherTool);

        ScopedToolRegistry scoped = new ScopedToolRegistry(
            baseRegistry, null, Set.of("secret_tool"));

        List<Map<String, Object>> definitions = scoped.getToolDefinitions();
        Set<String> names = definitions.stream()
            .map(d -> (String) d.get("name"))
            .collect(Collectors.toSet());

        assertTrue(names.contains("sample_add"), "Allowed tool should be in scoped definitions");
        assertFalse(names.contains("secret_tool"), "Denied tool should NOT be in scoped definitions");
    }

    @Test
    @DisplayName("Tool execution after registration returns correct payload")
    void registeredToolShouldBeExecutable() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerObject(new SampleAnnotatedTools());

        Optional<Tool> toolOpt = registry.get("sample_add");
        assertTrue(toolOpt.isPresent(), "Tool should be retrievable by name");

        ToolResult result = toolOpt.get().execute(Map.of("a", 10, "b", 20));
        assertFalse(result.isError());
        assertEquals("30", result.getContent(),
            "Execution result payload must match the annotated method's return value");
    }
}
