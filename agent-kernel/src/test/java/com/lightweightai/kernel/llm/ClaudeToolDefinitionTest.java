package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Claude API tool definition format")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with all fields and toApiFormat includes them")
        void buildsWithAllFields() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("query", "string", "search query")
                    .required("query")
                    .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("web_search")
                    .description("Search the web")
                    .inputSchema(schema)
                    .build();

            assertEquals("web_search", def.getName());
            assertEquals("Search the web", def.getDescription());

            Map<String, Object> api = def.toApiFormat();
            assertEquals("web_search", api.get("name"));
            assertEquals("Search the web", api.get("description"));
            assertNotNull(api.get("input_schema"));

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) api.get("input_schema");
            assertEquals("object", inputSchema.get("type"));
        }

        @Test
        @DisplayName("null description defaults to empty string")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("test_tool")
                    .description(null)
                    .build();

            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("null inputSchema defaults to empty object schema")
        void nullSchemaDefaultsToObjectSchema() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("test_tool")
                    .description("desc")
                    .inputSchema((JsonSchema) null)
                    .build();

            Map<String, Object> api = def.toApiFormat();
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) api.get("input_schema");
            assertEquals("object", inputSchema.get("type"));
        }

        @Test
        @DisplayName("null name throws IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder().name(null).description("d").build());
        }

        @Test
        @DisplayName("empty name throws IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder().name("  ").description("d").build());
        }

        @Test
        @DisplayName("inputSchema from Map creates JsonSchema")
        void inputSchemaFromMap() {
            Map<String, Object> schemaMap = Map.of("type", "object",
                    "properties", Map.of("x", Map.of("type", "integer")));

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("calc")
                    .description("calculate")
                    .inputSchema(schemaMap)
                    .build();

            Map<String, Object> api = def.toApiFormat();
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) api.get("input_schema");
            assertEquals("object", schema.get("type"));
        }
    }

    @Nested
    @DisplayName("fromMap")
    class FromMapTests {

        @Test
        @DisplayName("creates from a complete tool definition map")
        void createsFromCompleteMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "read_file");
            toolDef.put("description", "Read a file");
            toolDef.put("input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("path", Map.of("type", "string"))
            ));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("read_file", def.getName());
            assertEquals("Read a file", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("handles missing input_schema gracefully")
        void handlesMissingSchema() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "simple_tool");
            toolDef.put("description", "A simple tool");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("simple_tool", def.getName());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("null map throws IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("map without name throws IllegalArgumentException")
        void missingNameThrows() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("description", "no name");

            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.fromMap(toolDef));
        }
    }

    @Test
    @DisplayName("round-trip: builder → toApiFormat → fromMap preserves fields")
    void roundTrip() {
        ClaudeToolDefinition original = ClaudeToolDefinition.builder()
                .name("greet")
                .description("Greet someone")
                .inputSchema(JsonSchema.builder()
                        .type("object")
                        .addProperty("name", "string", "person name")
                        .required("name")
                        .build())
                .build();

        Map<String, Object> api = original.toApiFormat();
        ClaudeToolDefinition restored = ClaudeToolDefinition.fromMap(api);

        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getDescription(), restored.getDescription());
        assertEquals(original.getInputSchema().getType(), restored.getInputSchema().getType());
    }

    @Test
    @DisplayName("toString includes name and description")
    void toStringFormat() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("My tool")
                .build();

        String str = def.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("My tool"));
    }
}
