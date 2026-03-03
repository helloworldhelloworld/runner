package com.lightweightai.kernel.agent;

import java.util.*;

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
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());
        return new ToolSchema(schema);
    }

    /**
     * 创建带属性的 Schema
     */
    public static ToolSchema withProperties(Map<String, Object> properties) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return new ToolSchema(schema);
    }

    /**
     * 创建带必填属性的 Schema
     */
    public static ToolSchema withRequired(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Arrays.asList(required));
        return new ToolSchema(schema);
    }

    public Map<String, Object> toMap() {
        return schema;
    }

    public String getType() {
        return (String) schema.getOrDefault("type", "object");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProperties() {
        return (Map<String, Object>) schema.getOrDefault("properties", Collections.emptyMap());
    }
}
