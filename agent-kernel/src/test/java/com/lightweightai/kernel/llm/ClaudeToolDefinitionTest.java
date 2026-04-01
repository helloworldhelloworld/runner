package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeToolDefinition - Claude 工具定义")
class ClaudeToolDefinitionTest {

    @Test
    @DisplayName("Builder 正常构建")
    void builderNormal() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("get_weather")
                .description("Get weather info")
                .inputSchema(JsonSchema.object())
                .build();

        assertEquals("get_weather", def.getName());
        assertEquals("Get weather info", def.getDescription());
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("name 为 null 时抛异常")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name(null).build());
    }

    @Test
    @DisplayName("name 为空字符串时抛异常")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.builder().name("  ").build());
    }

    @Test
    @DisplayName("description 为 null 默认为空字符串")
    void nullDescriptionDefault() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool1")
                .build();
        assertEquals("", def.getDescription());
    }

    @Test
    @DisplayName("inputSchema 为 null 默认为 object schema")
    void nullSchemaDefault() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool1")
                .build();
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("toApiFormat 输出正确格式")
    void toApiFormat() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("search")
                .description("Search documents")
                .inputSchema(JsonSchema.object())
                .build();

        Map<String, Object> api = def.toApiFormat();
        assertEquals("search", api.get("name"));
        assertEquals("Search documents", api.get("description"));
        assertNotNull(api.get("input_schema"));
    }

    @Test
    @DisplayName("fromMap 正常解析")
    void fromMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "calculator");
        map.put("description", "Do math");
        map.put("input_schema", Map.of("type", "object"));

        ClaudeToolDefinition def = ClaudeToolDefinition.fromMap(map);
        assertEquals("calculator", def.getName());
        assertEquals("Do math", def.getDescription());
    }

    @Test
    @DisplayName("fromMap null 抛异常")
    void fromMapNull() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.fromMap(null));
    }

    @Test
    @DisplayName("fromMap 缺少 name 抛异常")
    void fromMapNoName() {
        Map<String, Object> map = Map.of("description", "no name");
        assertThrows(IllegalArgumentException.class, () ->
                ClaudeToolDefinition.fromMap(map));
    }

    @Test
    @DisplayName("Builder inputSchema 接受 Map")
    void builderSchemaFromMap() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("tool1")
                .inputSchema(Map.of("type", "object"))
                .build();
        assertNotNull(def.getInputSchema());
    }

    @Test
    @DisplayName("toString 包含名称")
    void toStringOutput() {
        ClaudeToolDefinition def = ClaudeToolDefinition.builder()
                .name("my_tool")
                .description("desc")
                .build();
        assertTrue(def.toString().contains("my_tool"));
    }
}
