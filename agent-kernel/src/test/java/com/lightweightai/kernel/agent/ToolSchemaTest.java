package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolSchema - 工具参数 Schema")
class ToolSchemaTest {

    @Test
    @DisplayName("empty() 创建空参数 Schema")
    void emptySchema() {
        ToolSchema schema = ToolSchema.empty();

        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().isEmpty());
        assertNotNull(schema.toMap());
    }

    @Test
    @DisplayName("withProperties 创建带属性的 Schema")
    @SuppressWarnings("unchecked")
    void withProperties() {
        Map<String, Object> props = Map.of(
            "city", Map.of("type", "string", "description", "城市名")
        );
        ToolSchema schema = ToolSchema.withProperties(props);

        assertEquals("object", schema.getType());
        Map<String, Object> actualProps = schema.getProperties();
        assertTrue(actualProps.containsKey("city"));
    }

    @Test
    @DisplayName("withRequired 创建带必填属性的 Schema")
    void withRequired() {
        Map<String, Object> props = Map.of(
            "name", Map.of("type", "string"),
            "age", Map.of("type", "integer")
        );
        ToolSchema schema = ToolSchema.withRequired(props, "name");

        Map<String, Object> map = schema.toMap();
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) map.get("required");
        assertNotNull(required);
        assertTrue(required.contains("name"));
    }

    @Test
    @DisplayName("toMap 返回原始 schema")
    void toMap() {
        Map<String, Object> raw = Map.of("type", "object", "properties", Map.of());
        ToolSchema schema = new ToolSchema(raw);

        assertEquals(raw, schema.toMap());
    }

    @Test
    @DisplayName("getType 默认为 object")
    void getTypeDefault() {
        ToolSchema schema = new ToolSchema(Map.of());
        assertEquals("object", schema.getType());
    }

    @Test
    @DisplayName("getProperties 无属性时返回空 Map")
    void getPropertiesEmpty() {
        ToolSchema schema = new ToolSchema(Map.of("type", "object"));
        assertTrue(schema.getProperties().isEmpty());
    }
}
