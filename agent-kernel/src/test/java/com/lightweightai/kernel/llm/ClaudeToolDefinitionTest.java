package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - LLM-facing tool definition")
class ClaudeToolDefinitionTest {

    @Test
    @DisplayName("builder creates definition with all fields")
    void shouldBuildWithAllFields() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("get_weather")
                .description("Get weather for a city")
                .inputSchema(JsonSchema.object())
                .build();

        assertEquals("get_weather", def.getName());
        assertEquals("Get weather for a city", def.getDescription());
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("toApiFormat produces correct structure for HTTP body")
    void toApiFormatShouldProduceCorrectStructure() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search")
                .description("Search the web")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Search query")
                        ),
                        "required", List.of("query")
                ))
                .build();

        Map<String, Object> api = def.toApiFormat();
        assertEquals("search", api.get("name"));
        assertEquals("Search the web", api.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) api.get("input_schema");
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("query"));
    }

    @Test
    @DisplayName("fromMap reconstructs definition from map")
    void fromMapShouldReconstructDefinition() {
        Map<String, Object> toolDef = Map.of(
                "name", "calculate",
                "description", "Perform calculation",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "expression", Map.of("type", "string")
                        )
                )
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
        assertEquals("calculate", def.getName());
        assertEquals("Perform calculation", def.getDescription());

        Map<String, Object> api = def.toApiFormat();
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) api.get("input_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("expression"));
    }

    @Test
    @DisplayName("rejects null or empty name")
    void shouldRejectNullOrEmptyName() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name(null).description("d").build());
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name("  ").description("d").build());
    }

    @Test
    @DisplayName("null description defaults to empty string")
    void shouldDefaultNullDescriptionToEmpty() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .description(null)
                .build();
        assertEquals("", def.getDescription());
    }

    @Test
    @DisplayName("null inputSchema defaults to empty object schema")
    void shouldDefaultNullInputSchemaToObjectSchema() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .description("desc")
                .inputSchema((JsonSchema) null)
                .build();

        Map<String, Object> api = def.toApiFormat();
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) api.get("input_schema");
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
    }

    @Test
    @DisplayName("fromMap rejects null map")
    void fromMapShouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.fromMap(null));
    }

    @Test
    @DisplayName("fromMap handles missing input_schema gracefully")
    void fromMapShouldHandleMissingSchema() {
        Map<String, Object> toolDef = Map.of(
                "name", "simple_tool",
                "description", "A simple tool"
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
        assertEquals("simple_tool", def.getName());
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("toString contains name and description")
    void toStringShouldContainKeyInfo() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("Does things")
                .build();
        String s = def.toString();
        assertTrue(s.contains("my_tool"));
        assertTrue(s.contains("Does things"));
    }
}
