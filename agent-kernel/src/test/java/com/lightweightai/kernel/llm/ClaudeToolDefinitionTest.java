package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition — Claude API 工具定义")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("构建包含 name + description + schema 的定义")
        void buildComplete() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("city", "string", "City name")
                    .required("city")
                    .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("get_weather")
                    .description("Get current weather")
                    .inputSchema(schema)
                    .build();

            assertEquals("get_weather", def.getName());
            assertEquals("Get current weather", def.getDescription());
            assertTrue(def.getInputSchema().isRequired("city"));
        }

        @Test
        @DisplayName("name 为 null 时抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder().description("desc").build());
        }

        @Test
        @DisplayName("description 为 null 时默认空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool").build();
            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("inputSchema 为 null 时使用默认 object schema")
        void nullSchemaDefaultsToObject() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool").build();
            assertNotNull(def.getInputSchema());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("inputSchema(Map) 支持 Map 格式设置")
        void buildWithSchemaMap() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema(Map.of("type", "object", "properties", Map.of()))
                    .build();
            assertEquals("object", def.getInputSchema().getType());
        }
    }

    @Nested
    @DisplayName("toApiFormat 序列化")
    class ApiFormatTests {

        @Test
        @DisplayName("生成的 API 格式包含 name, description, input_schema")
        void containsAllFields() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("search")
                    .description("Search documents")
                    .inputSchema(JsonSchema.builder()
                            .type("object")
                            .addProperty("query", "string", "Search query")
                            .build())
                    .build();

            Map<String, Object> api = def.toApiFormat();

            assertEquals("search", api.get("name"));
            assertEquals("Search documents", api.get("description"));
            assertNotNull(api.get("input_schema"),
                    "API format must include input_schema for Claude API");
        }
    }

    @Nested
    @DisplayName("fromMap 反序列化")
    class FromMapTests {

        @Test
        @DisplayName("从 Map 解析工具定义")
        void parseFromMap() {
            Map<String, Object> toolDef = Map.of(
                    "name", "calc",
                    "description", "Calculator",
                    "input_schema", Map.of("type", "object"));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("calc", def.getName());
            assertEquals("Calculator", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("null map 抛出 IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("缺少 input_schema 时使用默认 object schema")
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = Map.of("name", "tool", "description", "desc");
            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertEquals("object", def.getInputSchema().getType());
        }
    }

    @Test
    @DisplayName("toString 包含 name 和 description")
    void toStringContainsKeyInfo() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool").description("My description").build();
        String s = def.toString();
        assertTrue(s.contains("my_tool"));
        assertTrue(s.contains("My description"));
    }
}
