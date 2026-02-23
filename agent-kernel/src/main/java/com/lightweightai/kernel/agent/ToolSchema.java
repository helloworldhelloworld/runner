package com.lightweightai.kernel.agent;

import java.util.Map;

/**
 * 工具参数 Schema
 *
 * 使用 JSON Schema 格式描述工具参数。
 */
public class ToolSchema {

    private final Map<String, Object> schema;

    public ToolSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    /**
     * 创建空 Schema（无参数）
     */
    public static ToolSchema empty() {
        return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
    }

    /**
     * 创建带属性的 Schema
     */
    public static ToolSchema withProperties(Map<String, Object> properties) {
        return new ToolSchema(Map.of(
            "type", "object",
            "properties", properties
        ));
    }

    /**
     * 创建带必填属性的 Schema
     */
    public static ToolSchema withRequired(Map<String, Object> properties, String... required) {
        return new ToolSchema(Map.of(
            "type", "object",
            "properties", properties,
            "required", java.util.List.of(required)
        ));
    }

    public Map<String, Object> toMap() {
        return schema;
    }

    public String getType() {
        return (String) schema.getOrDefault("type", "object");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProperties() {
        return (Map<String, Object>) schema.getOrDefault("properties", Map.of());
    }
}
