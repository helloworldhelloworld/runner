package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClaudeToolDefinition — tool definition → Claude API format transmission.
 *
 * Verifies that tool definitions carry the full payload (name, description,
 * input_schema) through to the API request body. This is a critical link
 * in the chain: ToolRegistry → getToolDefinitions() → ClaudeToolDefinition → HTTP body.
 */
@DisplayName("ClaudeToolDefinition — tool definition to API format")
class ClaudeToolDefinitionTest {

    @Test
    @DisplayName("builder creates definition with all fields")
    void builderCreatesComplete() {
        JsonSchema schema = JsonSchema.builder()
                .type("object")
                .addProperty("query", "string", "search query")
                .required("query")
                .build();

        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search")
                .description("Search the web")
                .inputSchema(schema)
                .build();

        assertEquals("search", def.getName());
        assertEquals("Search the web", def.getDescription());
        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().getType());
        assertTrue(def.getInputSchema().isRequired("query"));
    }

    @Test
    @DisplayName("toApiFormat() produces correct Claude API structure")
    void toApiFormatProducesCorrectStructure() {
        JsonSchema schema = JsonSchema.builder()
                .type("object")
                .addProperty("text", "string", "input text")
                .build();

        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("summarize")
                .description("Summarize text")
                .inputSchema(schema)
                .build();

        Map<String, Object> api = def.toApiFormat();

        assertEquals("summarize", api.get("name"));
        assertEquals("Summarize text", api.get("description"));
        assertNotNull(api.get("input_schema"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) api.get("input_schema");
        assertEquals("object", inputSchema.get("type"));
    }

    @Test
    @DisplayName("fromMap() reconstructs definition from generic map")
    void fromMapReconstructs() {
        Map<String, Object> toolDef = Map.of(
                "name", "read_file",
                "description", "Read a file",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string")
                        )
                )
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

        assertEquals("read_file", def.getName());
        assertEquals("Read a file", def.getDescription());
        assertEquals("object", def.getInputSchema().getType());
        assertFalse(def.getInputSchema().getProperties().isEmpty());
    }

    @Test
    @DisplayName("fromMap() → toApiFormat() round-trip preserves payload")
    void roundTripPreservesPayload() {
        Map<String, Object> original = Map.of(
                "name", "calc",
                "description", "Calculator",
                "input_schema", Map.of("type", "object")
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(original);
        Map<String, Object> roundTripped = def.toApiFormat();

        assertEquals(original.get("name"), roundTripped.get("name"));
        assertEquals(original.get("description"), roundTripped.get("description"));
    }

    @Test
    @DisplayName("null name throws IllegalArgumentException")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name(null).build());
    }

    @Test
    @DisplayName("empty name throws IllegalArgumentException")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name("  ").build());
    }

    @Test
    @DisplayName("null description defaults to empty string")
    void nullDescriptionDefaults() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .description(null)
                .build();
        assertEquals("", def.getDescription());
    }

    @Test
    @DisplayName("null inputSchema defaults to empty object schema")
    void nullInputSchemaDefaults() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .inputSchema((JsonSchema) null)
                .build();
        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("fromMap() with null throws IllegalArgumentException")
    void fromMapNullThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.fromMap(null));
    }

    @Test
    @DisplayName("builder with Map inputSchema converts correctly")
    void builderWithMapSchema() {
        Map<String, Object> schemaMap = Map.of(
                "type", "object",
                "properties", Map.of("x", Map.of("type", "number"))
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("math")
                .description("Math tool")
                .inputSchema(schemaMap)
                .build();

        assertEquals("object", def.getInputSchema().getType());
        assertFalse(def.getInputSchema().getProperties().isEmpty());
    }
}
