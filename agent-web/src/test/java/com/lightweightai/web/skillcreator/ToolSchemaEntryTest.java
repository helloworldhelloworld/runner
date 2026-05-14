package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolSchemaEntry - 工具 Schema 条目")
class ToolSchemaEntryTest {

    @Test
    @DisplayName("toMap 包含所有非空字段")
    void toMapWithAllFields() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId("tool-1");
        entry.setName("GetPosition");
        entry.setDescription("获取位置");
        entry.setCategory("地理信息");
        entry.setInputSchemaJson("{\"type\":\"object\"}");
        entry.setOutputSchemaJson("{\"type\":\"string\"}");
        entry.setStatus("enabled");

        Map<String, Object> map = entry.toMap();
        assertEquals("tool-1", map.get("id"));
        assertEquals("GetPosition", map.get("name"));
        assertEquals("获取位置", map.get("description"));
        assertEquals("地理信息", map.get("category"));
        assertEquals("{\"type\":\"object\"}", map.get("inputSchema"));
        assertEquals("{\"type\":\"string\"}", map.get("outputSchema"));
        assertEquals("enabled", map.get("status"));
    }

    @Test
    @DisplayName("toMap 省略 null 字段但 status 默认 enabled")
    void toMapOmitsNullFieldsExceptStatus() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setName("MyTool");

        Map<String, Object> map = entry.toMap();
        assertFalse(map.containsKey("id"));
        assertTrue(map.containsKey("name"));
        assertFalse(map.containsKey("description"));
        assertFalse(map.containsKey("inputSchema"));
        assertFalse(map.containsKey("outputSchema"));
        assertEquals("enabled", map.get("status"));
    }

    @Test
    @DisplayName("toMap 当 status 非 null 时使用设置的值")
    void toMapUsesCustomStatus() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setName("MyTool");
        entry.setStatus("disabled");

        Map<String, Object> map = entry.toMap();
        assertEquals("disabled", map.get("status"));
    }
}
