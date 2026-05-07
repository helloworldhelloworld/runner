package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Claude API 工具定义格式")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("完整构造 → name/description/schema 正确")
        void buildsComplete() {
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
        @DisplayName("description 为 null 时默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .build();

            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("inputSchema 为 null 时默认为 object schema")
        void nullSchemaDefaultsToObject() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .build();

            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("name 为 null 时抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder().build());
        }

        @Test
        @DisplayName("name 为空字符串时抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder().name("  ").build());
        }

        @Test
        @DisplayName("inputSchema 可通过 Map 设置")
        void schemaFromMap() {
            Map<String, Object> schemaMap = Map.of(
                    "type", "object",
                    "properties", Map.of("city", Map.of("type", "string"))
            );
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema(schemaMap)
                    .build();

            assertNotNull(def.getInputSchema());
        }
    }

    @Nested
    @DisplayName("toApiFormat")
    class ApiFormat {

        @Test
        @DisplayName("输出包含 name/description/input_schema 字段")
        void outputContainsRequiredFields() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("get_weather")
                    .description("Get weather")
                    .inputSchema(JsonSchema.object())
                    .build();

            Map<String, Object> api = def.toApiFormat();

            assertEquals("get_weather", api.get("name"));
            assertEquals("Get weather", api.get("description"));
            assertNotNull(api.get("input_schema"));
            assertTrue(api.get("input_schema") instanceof Map);
        }

        @Test
        @DisplayName("input_schema 包含 type=object")
        void schemaHasObjectType() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool")
                    .inputSchema(JsonSchema.object())
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) def.toApiFormat().get("input_schema");
            assertEquals("object", schema.get("type"));
        }
    }

    @Nested
    @DisplayName("fromMap")
    class FromMap {

        @Test
        @DisplayName("从 Map 正确解析工具定义")
        void parsesFromMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "calculator");
            toolDef.put("description", "A calculator");
            toolDef.put("input_schema", Map.of("type", "object",
                    "properties", Map.of("expr", Map.of("type", "string"))));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);

            assertEquals("calculator", def.getName());
            assertEquals("A calculator", def.getDescription());
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("null map 抛出 IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("无 input_schema 时使用默认 object schema")
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "tool");
            toolDef.put("description", "desc");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertNotNull(def.getInputSchema());
        }
    }

    @Test
    @DisplayName("toString 包含 name 和 description")
    void toStringContainsInfo() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("My tool")
                .build();

        String str = def.toString();
        assertTrue(str.contains("my_tool"));
        assertTrue(str.contains("My tool"));
    }
}
