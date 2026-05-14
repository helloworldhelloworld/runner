package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition")
class ClaudeToolDefinitionTest {

    private JsonSchema sampleSchema;

    @BeforeEach
    void setUp() {
        sampleSchema = JsonSchema.builder()
            .type("object")
            .addProperty("query", "string", "search query")
            .required("query")
            .build();
    }

    @Test
    @DisplayName("Builder creates valid instance with all fields")
    void builderCreatesValidInstance() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("search")
            .description("Search the web")
            .inputSchema(sampleSchema)
            .build();

        assertEquals("search", def.getName());
        assertEquals("Search the web", def.getDescription());
        assertEquals("object", def.getInputSchema().getType());
        assertTrue(def.getInputSchema().isRequired("query"));
    }

    @Test
    @DisplayName("Builder with null name throws IllegalArgumentException")
    void builderWithNullNameThrows() {
        ClaudeToolDefinition.Builder builder = ClaudeToolDefinition.builder()
            .description("desc");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    @DisplayName("Builder with empty name throws IllegalArgumentException")
    void builderWithEmptyNameThrows() {
        ClaudeToolDefinition.Builder builder = ClaudeToolDefinition.builder()
            .name("   ")
            .description("desc");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("null or empty"));
    }

    @Test
    @DisplayName("Builder with null description defaults to empty string")
    void builderWithNullDescriptionDefaultsToEmpty() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("tool1")
            .description(null)
            .build();

        assertEquals("", def.getDescription());
    }

    @Test
    @DisplayName("Builder with null inputSchema defaults to object schema")
    void builderWithNullInputSchemaDefaultsToObjectSchema() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("tool1")
            .inputSchema((JsonSchema) null)
            .build();

        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("fromMap with valid data creates correct instance")
    void fromMapWithValidData() {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        Map<String, Object> props = new HashMap<>();
        props.put("q", Map.of("type", "string"));
        inputSchema.put("properties", props);

        Map<String, Object> toolDef = new HashMap<>();
        toolDef.put("name", "search");
        toolDef.put("description", "Search tool");
        toolDef.put("input_schema", inputSchema);

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

        assertEquals("search", def.getName());
        assertEquals("Search tool", def.getDescription());
        assertEquals("object", def.getInputSchema().getType());
        assertTrue(def.getInputSchema().getProperties().containsKey("q"));
    }

    @Test
    @DisplayName("fromMap with null throws IllegalArgumentException")
    void fromMapWithNullThrows() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ClaudeToolDefinition.fromMap(null)
        );
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    @DisplayName("fromMap without input_schema uses default object schema")
    void fromMapWithoutInputSchemaUsesDefault() {
        Map<String, Object> toolDef = new HashMap<>();
        toolDef.put("name", "simple_tool");
        toolDef.put("description", "A simple tool");

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

        assertEquals("simple_tool", def.getName());
        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("toApiFormat returns map with correct keys and values")
    void toApiFormatHasCorrectKeysAndValues() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("read_file")
            .description("Read a file")
            .inputSchema(sampleSchema)
            .build();

        Map<String, Object> api = def.toApiFormat();

        assertEquals("read_file", api.get("name"));
        assertEquals("Read a file", api.get("description"));
        assertNotNull(api.get("input_schema"));

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) api.get("input_schema");
        assertEquals("object", schema.get("type"));
        // Verify the schema payload round-trips: properties must be present
        assertNotNull(schema.get("properties"));
    }

    @Test
    @DisplayName("Two builder instances do not interfere with each other")
    void twoBuilderInstancesDoNotInterfere() {
        ClaudeToolDefinition.Builder builder1 = ClaudeToolDefinition.builder()
            .name("tool_a")
            .description("Tool A");

        ClaudeToolDefinition.Builder builder2 = ClaudeToolDefinition.builder()
            .name("tool_b")
            .description("Tool B");

        ClaudeToolDefinition def1 = builder1.build();
        ClaudeToolDefinition def2 = builder2.build();

        assertEquals("tool_a", def1.getName());
        assertEquals("Tool A", def1.getDescription());
        assertEquals("tool_b", def2.getName());
        assertEquals("Tool B", def2.getDescription());
    }

    @Test
    @DisplayName("toString includes name and description")
    void toStringIncludesNameAndDescription() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("my_tool")
            .description("My description")
            .build();

        String str = def.toString();
        assertTrue(str.contains("my_tool"), "toString should contain the name");
        assertTrue(str.contains("My description"), "toString should contain the description");
    }
}
