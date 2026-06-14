package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition — Claude API 工具定义格式转换")
class ClaudeToolDefinitionTest {

    // ==================== Builder ====================

    @Test
    @DisplayName("builder 构建完整的工具定义")
    void builderCreatesDefinition() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .addProperty("city", "string", "City name")
            .build();

        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("get_weather")
            .description("Get weather for a city")
            .inputSchema(schema)
            .build();

        assertEquals("get_weather", def.getName());
        assertEquals("Get weather for a city", def.getDescription());
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("builder 使用 Map 设置 inputSchema")
    void builderWithSchemaMap() {
        Map<String, Object> schemaMap = Map.of(
            "type", "object",
            "properties", Map.of("x", Map.of("type", "integer"))
        );

        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("calc")
            .description("Calculate")
            .inputSchema(schemaMap)
            .build();

        assertEquals("calc", def.getName());
        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("name 为空时抛出 IllegalArgumentException")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ClaudeToolDefinition.builder()
                .name(null)
                .description("test")
                .build()
        );
    }

    @Test
    @DisplayName("空字符串 name 抛出 IllegalArgumentException")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            ClaudeToolDefinition.builder()
                .name("  ")
                .description("test")
                .build()
        );
    }

    @Test
    @DisplayName("null description 默认为空字符串")
    void nullDescriptionDefaultsToEmpty() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("test")
            .description(null)
            .build();

        assertEquals("", def.getDescription());
    }

    @Test
    @DisplayName("null inputSchema 默认为 empty object schema")
    void nullInputSchemaDefaultsToObject() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("test")
            .description("test")
            .inputSchema((JsonSchema) null)
            .build();

        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().getType());
    }

    // ==================== toApiFormat ====================

    @Test
    @DisplayName("toApiFormat 产出正确的 Claude API 格式 — 传输链验证")
    void toApiFormatProducesCorrectStructure() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("search")
            .description("Search the web")
            .inputSchema(JsonSchema.builder()
                .type("object")
                .addProperty("query", "string", "Search query")
                .build())
            .build();

        Map<String, Object> apiFormat = def.toApiFormat();

        assertEquals("search", apiFormat.get("name"));
        assertEquals("Search the web", apiFormat.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) apiFormat.get("input_schema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.get("type"));
    }

    // ==================== fromMap ====================

    @Test
    @DisplayName("fromMap 解析工具定义 Map")
    void fromMapParsesToolDef() {
        Map<String, Object> toolDef = new HashMap<>();
        toolDef.put("name", "calculator");
        toolDef.put("description", "Basic calculator");
        toolDef.put("input_schema", Map.of(
            "type", "object",
            "properties", Map.of("expression", Map.of("type", "string"))
        ));

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

        assertEquals("calculator", def.getName());
        assertEquals("Basic calculator", def.getDescription());
        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("fromMap 无 input_schema 时使用默认 object schema")
    void fromMapWithoutSchema() {
        Map<String, Object> toolDef = new HashMap<>();
        toolDef.put("name", "simple");
        toolDef.put("description", "Simple tool");

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

        assertEquals("simple", def.getName());
        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().getType());
    }

    @Test
    @DisplayName("fromMap null 输入抛出 IllegalArgumentException")
    void fromMapNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> ClaudeToolDefinition.fromMap(null));
    }

    @Test
    @DisplayName("fromMap → toApiFormat round-trip 保持 payload 完整")
    void fromMapToApiFormatRoundTrip() {
        Map<String, Object> original = new HashMap<>();
        original.put("name", "round_trip");
        original.put("description", "Round trip test");
        original.put("input_schema", Map.of(
            "type", "object",
            "properties", Map.of("arg", Map.of("type", "string"))
        ));

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(original);
        Map<String, Object> apiFormat = def.toApiFormat();

        assertEquals("round_trip", apiFormat.get("name"));
        assertEquals("Round trip test", apiFormat.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) apiFormat.get("input_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("arg"));
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 包含 name 和 description")
    void toStringContainsFields() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
            .name("my_tool")
            .description("My tool desc")
            .build();

        String str = def.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("My tool desc"));
    }
}
