package com.lightweightai.kernel.agent.annotation;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission test: @ToolFunction annotation → AnnotatedToolScanner → ToolRegistry → getToolDefinitions()
 *
 * Verifies the payload (name, description, schema with required params) survives
 * the full chain from annotation to LLM-facing tool definitions.
 */
@DisplayName("Annotated Tool → Registry → ToolDefinitions transmission chain")
class AnnotatedToolToRegistryTransmissionTest {

    static class WeatherTools {
        @ToolFunction(name = "get_weather", description = "Get current weather for a city")
        public String getWeather(
                @ToolParam(name = "city", description = "City name", required = true) String city,
                @ToolParam(name = "units", description = "Temperature units") String units) {
            return "Sunny in " + city;
        }
    }

    @Test
    @DisplayName("annotation metadata flows through scanner → registry → tool definitions")
    void annotationMetadataFlowsThroughFullChain() {
        // Step 1: Scan annotated object
        List<Tool> tools = AnnotatedToolScanner.scan(new WeatherTools());
        assertEquals(1, tools.size());

        // Step 2: Register in ToolRegistry
        ToolRegistry registry = new ToolRegistry();
        tools.forEach(registry::register);

        // Step 3: Get tool definitions (what LLMProvider receives)
        // ToolRegistry.getToolDefinitions() returns List<Map<String, Object>> in Claude API format
        List<Map<String, Object>> definitions = registry.getToolDefinitions();
        assertEquals(1, definitions.size());

        Map<String, Object> def = definitions.get(0);
        assertEquals("get_weather", def.get("name"));
        assertEquals("Get current weather for a city", def.get("description"));

        // Step 4: Verify input_schema payload (what goes into HTTP body)
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) def.get("input_schema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("city"), "city property must be in schema");
        assertTrue(properties.containsKey("units"), "units property must be in schema");

        @SuppressWarnings("unchecked")
        Map<String, Object> cityProp = (Map<String, Object>) properties.get("city");
        assertEquals("string", cityProp.get("type"));
        assertEquals("City name", cityProp.get("description"));

        // Verify required array includes "city" but not "units"
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertNotNull(required, "required array must be present");
        assertTrue(required.contains("city"), "city must be required");
        assertFalse(required.contains("units"), "units should not be required");
    }

    @Test
    @DisplayName("tool execution through registry returns correct payload")
    void toolExecutionThroughRegistryReturnsPayload() {
        List<Tool> tools = AnnotatedToolScanner.scan(new WeatherTools());
        ToolRegistry registry = new ToolRegistry();
        tools.forEach(registry::register);

        Tool tool = registry.get("get_weather").orElseThrow();
        ToolResult result = tool.execute(Map.of("city", "Tokyo", "units", "celsius"));

        assertFalse(result.isError());
        assertEquals("Sunny in Tokyo", result.getContent());
    }
}
