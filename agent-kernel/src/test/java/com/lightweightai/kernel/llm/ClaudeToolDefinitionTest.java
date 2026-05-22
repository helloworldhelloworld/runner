package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Claude API 工具定义")
class ClaudeToolDefinitionTest {

    @Nested
    @DisplayName("Builder 构建")
    class BuilderTests {

        @Test
        @DisplayName("通过 Builder 创建完整定义")
        void builderCreatesFullDefinition() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("get_weather")
                    .description("Get current weather")
                    .inputSchema(JsonSchema.object())
                    .build();

            assertEquals("get_weather", def.getName());
            assertEquals("Get current weather", def.getDescription());
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("null name 抛出 IllegalArgumentException")
        void nullNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder()
                            .description("desc")
                            .build());
        }

        @Test
        @DisplayName("空 name 抛出 IllegalArgumentException")
        void emptyNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.builder()
                            .name("  ")
                            .build());
        }

        @Test
        @DisplayName("null description 默认为空字符串")
        void nullDescriptionDefaultsToEmpty() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool1")
                    .build();
            assertEquals("", def.getDescription());
        }

        @Test
        @DisplayName("null inputSchema 默认为空 object schema")
        void nullSchemaDefaultsToObjectSchema() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("tool1")
                    .build();
            assertNotNull(def.getInputSchema());
        }

        @Test
        @DisplayName("通过 Map 设置 inputSchema")
        void inputSchemaFromMap() {
            Map<String, Object> schemaMap = Map.of(
                    "type", "object",
                    "properties", Map.of("city", Map.of("type", "string"))
            );
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("weather")
                    .inputSchema(schemaMap)
                    .build();

            assertNotNull(def.getInputSchema());
        }
    }

    @Nested
    @DisplayName("toApiFormat 转换")
    class ToApiFormatTests {

        @Test
        @DisplayName("生成包含 name/description/input_schema 的 Map")
        void generatesCorrectFormat() {
            ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                    .name("echo")
                    .description("Echo back text")
                    .inputSchema(JsonSchema.object())
                    .build();

            Map<String, Object> api = def.toApiFormat();
            assertEquals("echo", api.get("name"));
            assertEquals("Echo back text", api.get("description"));
            assertNotNull(api.get("input_schema"));
            assertTrue(api.get("input_schema") instanceof Map);
        }
    }

    @Nested
    @DisplayName("fromMap 静态工厂")
    class FromMapTests {

        @Test
        @DisplayName("从合法 Map 创建定义")
        void fromValidMap() {
            Map<String, Object> toolDef = Map.of(
                    "name", "calculator",
                    "description", "Calculate expressions",
                    "input_schema", Map.of("type", "object")
            );

            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertEquals("calculator", def.getName());
            assertEquals("Calculate expressions", def.getDescription());
        }

        @Test
        @DisplayName("null Map 抛出 IllegalArgumentException")
        void nullMapThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> ClaudeToolDefinition.fromMap(null));
        }

        @Test
        @DisplayName("缺少 input_schema 使用默认空 schema")
        void missingSchemaUsesDefault() {
            Map<String, Object> toolDef = Map.of(
                    "name", "simple_tool",
                    "description", "A tool"
            );
            ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(toolDef);
            assertNotNull(def.getInputSchema());
        }
    }

    @Test
    @DisplayName("toString 包含 name 和 description")
    void toStringContainsFields() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("test_tool")
                .description("A test tool")
                .build();
        String s = def.toString();
        assertTrue(s.contains("test_tool"));
        assertTrue(s.contains("A test tool"));
    }
}
