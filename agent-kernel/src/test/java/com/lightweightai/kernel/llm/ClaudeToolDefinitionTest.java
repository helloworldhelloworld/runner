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
    @DisplayName("Builder 构造")
    class BuilderTests {

        @Test
        @DisplayName("完整构建包含 name, description, inputSchema")
        void fullBuild() {
            JsonSchema schema = JsonSchema.builder()
                .type("object")
                .addProperty("city", "string", "城市名称")
                .required("city")
                .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("get_weather")
                .description("获取天气信息")
                .inputSchema(schema)
                .build();

            assertEquals("get_weather", def.getName());
            assertEquals("获取天气信息", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
            assertTrue(def.getInputSchema().isRequired("city"));
        }

        @Test
        @DisplayName("description 为 null 时默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool1")
                .build();

            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("inputSchema 为 null 时默认为空 object schema")
        void nullSchemaDefaultsToObjectSchema() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool1")
                .build();

            assertNotNull(def.getInputSchema());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("name 为 null 时抛出异常")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.builder().build());
        }

        @Test
        @DisplayName("name 为空字符串时抛出异常")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.builder().name("  ").build());
        }

        @Test
        @DisplayName("使用 Map 构建 inputSchema")
        void buildWithMapSchema() {
            Map<String, Object> schemaMap = new HashMap<>();
            schemaMap.put("type", "object");
            schemaMap.put("properties", Map.of("q", Map.of("type", "string")));

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search")
                .inputSchema(schemaMap)
                .build();

            assertEquals("object", def.getInputSchema().getType());
        }
    }

    @Nested
    @DisplayName("toApiFormat")
    class ToApiFormatTests {

        @Test
        @DisplayName("转换为 Claude API 格式 Map")
        void convertsToApiMap() {
            JsonSchema schema = JsonSchema.builder()
                .type("object")
                .addProperty("location", "string", "Location")
                .build();

            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("get_weather")
                .description("Get weather")
                .inputSchema(schema)
                .build();

            Map<String, Object> api = def.toApiFormat();

            assertEquals("get_weather", api.get("name"));
            assertEquals("Get weather", api.get("description"));
            assertNotNull(api.get("input_schema"));
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) api.get("input_schema");
            assertEquals("object", inputSchema.get("type"));
        }
    }

    @Nested
    @DisplayName("fromMap")
    class FromMapTests {

        @Test
        @DisplayName("从 Map 解析工具定义")
        void parsesFromMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "calculator");
            toolDef.put("description", "Performs math");
            toolDef.put("input_schema", Map.of("type", "object",
                "properties", Map.of("expr", Map.of("type", "string"))));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("calculator", def.getName());
            assertEquals("Performs math", def.getDescription());
            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("input_schema 缺失时默认为空 object")
        void missingSchemaDefaultsToObject() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "noop");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("object", def.getInputSchema().getType());
        }

        @Test
        @DisplayName("null Map 时抛出异常")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("fromMap 后 toApiFormat 保留原始数据")
        void roundTripPreservesData() {
            Map<String, Object> original = new HashMap<>();
            original.put("name", "tool1");
            original.put("description", "desc");
            original.put("input_schema", Map.of("type", "object"));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(original);
            Map<String, Object> api = def.toApiFormat();

            assertEquals("tool1", api.get("name"));
            assertEquals("desc", api.get("description"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("包含 name 和 description")
        void containsKeyFields() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("does things")
                .build();

            String str = def.toString();
            assertTrue(str.contains("my_tool"));
            assertTrue(str.contains("does things"));
        }
    }
}
