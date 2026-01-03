package com.lightweightai.kernel.plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON Schema representation for tool parameters.
 * Used to generate Claude API tool definitions.
 */
public class JsonSchema {

    private final Map<String, Object> schema;

    public JsonSchema(Map<String, Object> schema) {
        this.schema = new HashMap<>(schema);
    }

    /**
     * Get the schema as a map (for JSON serialization)
     */
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(schema);
    }

    /**
     * Get the schema type (e.g., "object", "string")
     */
    public String getType() {
        return (String) schema.get("type");
    }

    /**
     * Get schema properties (for object type)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProperties() {
        Object props = schema.get("properties");
        return props != null ? (Map<String, Object>) props : Collections.emptyMap();
    }

    /**
     * Check if a property is required
     */
    public boolean isRequired(String propertyName) {
        Object required = schema.get("required");
        if (required instanceof java.util.List) {
            return ((java.util.List<?>) required).contains(propertyName);
        }
        return false;
    }

    @Override
    public String toString() {
        return "JsonSchema" + schema;
    }

    /**
     * Builder for creating JSON schemas programmatically
     */
    public static class Builder {
        private final Map<String, Object> schema = new HashMap<>();
        private final Map<String, Object> properties = new HashMap<>();
        private final java.util.List<String> required = new java.util.ArrayList<>();

        public Builder type(String type) {
            schema.put("type", type);
            return this;
        }

        public Builder addProperty(String name, String type, String description) {
            Map<String, Object> prop = new HashMap<>();
            prop.put("type", type);
            if (description != null) {
                prop.put("description", description);
            }
            properties.put(name, prop);
            return this;
        }

        public Builder addProperty(String name, Map<String, Object> propertySchema) {
            properties.put(name, propertySchema);
            return this;
        }

        public Builder required(String... propertyNames) {
            Collections.addAll(required, propertyNames);
            return this;
        }

        public JsonSchema build() {
            if (!properties.isEmpty()) {
                schema.put("properties", properties);
            }
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return new JsonSchema(schema);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a simple string schema
     */
    public static JsonSchema string(String description) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "string");
        if (description != null) {
            schema.put("description", description);
        }
        return new JsonSchema(schema);
    }

    /**
     * Create a simple object schema
     */
    public static JsonSchema object() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        return new JsonSchema(schema);
    }
}
