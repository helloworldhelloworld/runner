package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Claude API 工具定义")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        void buildComplete() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("city", "string", "City name")
                    .required("city")
                    .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("get_weather")
                    .description("Get weather info")
                    .inputSchema(schema)
                    .build();

            assertEquals("get_weather", def.getName());
            assertEquals("Get weather info", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        void buildWithMapSchema() {
            Map<String, Object> schemaMap = new HashMap<>();
            schemaMap.put("type", "object");
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("test_tool").inputSchema(schemaMap).build();
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder().name(null).build());
        }

        @Test
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder().name("  ").build());
        }

        @Test
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool").description(null).build();
            assertEquals("", def.getDescription());
        }

        @Test
        void nullSchemaDefaultsToObject() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool").inputSchema((JsonSchema) null).build();
            assertEquals("object", def.getInputSchema().getType());
        }
    }

    @Test
    void toApiFormatProducesCorrectMap() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search").description("Search the web")
                .inputSchema(JsonSchema.builder().type("object").build())
                .build();
        Map<String, Object> api = def.toApiFormat();
        assertEquals("search", api.get("name"));
        assertEquals("Search the web", api.get("description"));
        assertNotNull(api.get("input_schema"));
    }

    @Nested
    @DisplayName("fromMap")
    class FromMapTests {

        @Test
        void fromValidMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "calc");
            toolDef.put("description", "Calculator");
            toolDef.put("input_schema", Map.of("type", "object"));
            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertEquals("calc", def.getName());
        }

        @Test
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class, () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        void missingNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.fromMap(Map.of("description", "no name")));
        }

        @Test
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "simple");
            assertEquals("object", ClaudeToolDefinition.fromMap(toolDef).getInputSchema().getType());
        }
    }

    @Test
    void roundTripPreservesFields() {
        ClaudeToolDefinition original = ClaudeToolDefinition.builder()
                .name("roundtrip_tool").description("Roundtrip test")
                .inputSchema(JsonSchema.builder().type("object").addProperty("x", "number", "A number").required("x").build())
                .build();
        ClaudeToolDefinition restored = ClaudeToolDefinition.fromMap(original.toApiFormat());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getDescription(), restored.getDescription());
    }

    @Test
    void toStringContainsInfo() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder().name("my_tool").description("desc").build();
        assertTrue(def.toString().contains("my_tool"));
    }
}
