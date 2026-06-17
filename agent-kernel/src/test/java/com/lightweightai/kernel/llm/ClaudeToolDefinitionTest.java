package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Tool definition for Claude API")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder construction")
    class BuilderTests {

        @Test
        @DisplayName("builds with all fields")
        void buildsWithAllFields() {
            JsonSchema schema = JsonSchema.object();
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("get_weather")
                    .description("Get current weather")
                    .inputSchema(schema)
                    .build();

            assertEquals("get_weather", def.getName());
            assertEquals("Get current weather", def.getDescription());
            assertSame(schema, def.getInputSchema());
        }

        @Test
        @DisplayName("null name throws IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder()
                            .name(null)
                            .build());
        }

        @Test
        @DisplayName("empty name throws IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    ClaudeToolDefinition.builder()
                            .name("  ")
                            .build());
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
        void nullSchemaDefaults() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .build();
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("can build with Map-based inputSchema")
        void buildWithMapSchema() {
            Map<String, Object> schemaMap = Map.of(
                    "type", "object",
                    "properties", Map.of()
            );
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema(schemaMap)
                    .build();

            assertNotNull(def.getInputSchema());
        }
    }

    @Nested
    @DisplayName("fromMap parsing")
    class FromMapTests {

        @Test
        @DisplayName("parses valid tool definition map")
        void parsesValidMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "calculator");
            toolDef.put("description", "Do math");
            toolDef.put("input_schema", Map.of("type", "object"));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("calculator", def.getName());
            assertEquals("Do math", def.getDescription());
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("null map throws IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("map without input_schema uses default schema")
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "tool");
            toolDef.put("description", "desc");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertNotNull(def.getInputSchema());
        }
    }

    @Nested
    @DisplayName("toApiFormat")
    class ApiFormatTests {

        @Test
        @DisplayName("produces map with name, description, and input_schema")
        void producesCorrectMap() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("search")
                    .description("Search the web")
                    .build();

            Map<String, Object> api = def.toApiFormat();

            assertEquals("search", api.get("name"));
            assertEquals("Search the web", api.get("description"));
            assertNotNull(api.get("input_schema"));
            assertInstanceOf(Map.class, api.get("input_schema"));
        }
    }

    @Test
    @DisplayName("toString contains name and description")
    void toStringContainsFields() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("My description")
                .build();
        String str = def.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("My description"));
    }
}
