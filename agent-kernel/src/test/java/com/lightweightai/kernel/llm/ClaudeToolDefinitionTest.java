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
        @DisplayName("通过 builder 创建完整的工具定义")
        void buildsComplete() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .addProperty("query", "string", "Search query")
                    .required("query")
                    .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("search")
                    .description("Search the web")
                    .inputSchema(schema)
                    .build();

            assertEquals("search", def.getName());
            assertEquals("Search the web", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("name 为 null 时抛 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder().name(null).build());
        }

        @Test
        @DisplayName("name 为空字符串时抛 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder().name("  ").build());
        }

        @Test
        @DisplayName("description 为 null 时默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .description(null)
                    .build();

            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("inputSchema 为 null 时默认为空 object schema")
        void nullSchemaDefaultsToObject() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema((JsonSchema) null)
                    .build();

            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("inputSchema 可通过 Map 构建")
        void schemaFromMap() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema(Map.of("type", "object"))
                    .build();

            assertEquals("object", def.getInputSchema().getType());
        }
    }

    @Nested
    @DisplayName("toApiFormat 序列化")
    class ApiFormatTests {

        @Test
        @DisplayName("toApiFormat 包含 name, description, input_schema")
        void apiFormatContainsAllFields() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("calculate")
                    .description("Do math")
                    .inputSchema(JsonSchema.builder()
                            .type("object")
                            .addProperty("expression", "string", "Math expression")
                            .build())
                    .build();

            Map<String, Object> api = def.toApiFormat();

            assertEquals("calculate", api.get("name"));
            assertEquals("Do math", api.get("description"));
            assertNotNull(api.get("input_schema"));
            assertTrue(api.get("input_schema") instanceof Map);
        }

        @Test
        @DisplayName("toApiFormat 的 input_schema 包含 properties")
        void apiFormatSchemaContainsProperties() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("search")
                    .inputSchema(JsonSchema.builder()
                            .type("object")
                            .addProperty("query", "string", "Search query")
                            .required("query")
                            .build())
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) def.toApiFormat().get("input_schema");
            assertEquals("object", schema.get("type"));
            assertNotNull(schema.get("properties"));
        }
    }

    @Nested
    @DisplayName("fromMap 反序列化")
    class FromMapTests {

        @Test
        @DisplayName("从完整的 Map 创建 ClaudeToolDefinition")
        void fromCompleteMap() {
            Map<String, Object> map = Map.of(
                    "name", "web_search",
                    "description", "Search the web",
                    "input_schema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string")
                            )
                    )
            );

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(map);

            assertEquals("web_search", def.getName());
            assertEquals("Search the web", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("fromMap 传入 null 抛 IllegalArgumentException")
        void fromNullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("fromMap 无 input_schema 时默认为空 object")
        void fromMapWithoutSchemaDefaultsToObject() {
            Map<String, Object> map = Map.of("name", "tool");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(map);

            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("fromMap → toApiFormat 往返转换保持一致")
        void roundTripConsistency() {
            ClaudeToolDefinition original = ClaudeToolDefinition.builder()
                    .name("calculator")
                    .description("Calculate things")
                    .inputSchema(JsonSchema.builder()
                            .type("object")
                            .addProperty("expr", "string", "Expression")
                            .build())
                    .build();

            Map<String, Object> apiFormat = original.toApiFormat();
            ClaudeToolDefinition restored = ClaudeToolDefinition.fromMap(apiFormat);

            assertEquals(original.getName(), restored.getName());
            assertEquals(original.getDescription(), restored.getDescription());
            assertEquals(original.getInputSchema().getType(), restored.getInputSchema().getType());
        }
    }

    @Test
    @DisplayName("toString 包含 name 和 description")
    void toStringIncludesNameAndDescription() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("A test tool")
                .build();

        String str = def.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("A test tool"));
    }
}
