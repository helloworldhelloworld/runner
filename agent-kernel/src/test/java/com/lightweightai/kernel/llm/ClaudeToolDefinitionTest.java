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
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("完整构建并获取字段")
        void fullBuild() {
            JsonSchema schema = JsonSchema.object();
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("get_weather")
                .description("Get weather info")
                .inputSchema(schema)
                .build();

            assertEquals("get_weather", def.getName());
            assertEquals("Get weather info", def.getDescription());
            assertSame(schema, def.getInputSchema());
        }

        @Test
        @DisplayName("null description 默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .description(null)
                .build();

            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("null inputSchema 默认为 object schema")
        void nullSchemaDefaultsToObjectSchema() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .inputSchema((JsonSchema) null)
                .build();

            assertNotNull(def.getInputSchema());
            assertEquals("object", def.getInputSchema().toMap().get("type"));
        }

        @Test
        @DisplayName("null name 抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.builder().name(null).build());
        }

        @Test
        @DisplayName("空 name 抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.builder().name("  ").build());
        }

        @Test
        @DisplayName("通过 Map 设置 inputSchema")
        void inputSchemaFromMap() {
            Map<String, Object> schemaMap = Map.of("type", "object", "properties", Map.of());
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .inputSchema(schemaMap)
                .build();

            assertEquals("object", def.getInputSchema().toMap().get("type"));
        }
    }

    @Nested
    @DisplayName("toApiFormat 序列化")
    class ToApiFormat {

        @Test
        @DisplayName("生成正确的 API 格式 Map")
        void correctFormat() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search")
                .description("Search docs")
                .build();

            Map<String, Object> api = def.toApiFormat();
            assertEquals("search", api.get("name"));
            assertEquals("Search docs", api.get("description"));
            assertNotNull(api.get("input_schema"));
        }

        @Test
        @DisplayName("API 格式包含 input_schema 的 Map 形式")
        void inputSchemaIsMap() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool")
                .build();

            Object schema = def.toApiFormat().get("input_schema");
            assertInstanceOf(Map.class, schema);
        }
    }

    @Nested
    @DisplayName("fromMap 工厂方法")
    class FromMap {

        @Test
        @DisplayName("从完整 Map 解析")
        void parseFromFullMap() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "calc");
            toolDef.put("description", "Calculator");
            toolDef.put("input_schema", Map.of("type", "object"));

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertEquals("calc", def.getName());
            assertEquals("Calculator", def.getDescription());
        }

        @Test
        @DisplayName("缺少 input_schema 时使用默认")
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = new HashMap<>();
            toolDef.put("name", "tool");
            toolDef.put("description", "desc");

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("null Map 抛出 IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> ClaudeToolDefinition.fromMap(null));
        }
    }

    @Test
    @DisplayName("toString 包含 name 和 description")
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
