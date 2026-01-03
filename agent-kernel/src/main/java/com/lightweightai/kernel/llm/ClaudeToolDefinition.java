package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.plugin.JsonSchema;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tool definition in Claude API format.
 * Used when sending tools to Claude in the API request.
 */
public class ClaudeToolDefinition {

    private final String name;
    private final String description;
    private final JsonSchema inputSchema;

    private ClaudeToolDefinition(String name, String description, JsonSchema inputSchema) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        this.name = name;
        this.description = description != null ? description : "";
        this.inputSchema = inputSchema != null ? inputSchema : JsonSchema.object();
    }

    /**
     * Get the tool name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the tool description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the input schema
     */
    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    /**
     * Convert to Claude API format
     *
     * @return Map representation for Claude API
     */
    public Map<String, Object> toApiFormat() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", inputSchema.toMap());
        return tool;
    }

    /**
     * Create from a generic tool definition map
     *
     * @param toolDef Tool definition map (from PluginFunction.toJsonSchema())
     * @return ClaudeToolDefinition instance
     */
    public static ClaudeToolDefinition fromMap(Map<String, Object> toolDef) {
        if (toolDef == null) {
            throw new IllegalArgumentException("Tool definition cannot be null");
        }

        String name = (String) toolDef.get("name");
        String description = (String) toolDef.get("description");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchemaMap = (Map<String, Object>) toolDef.get("input_schema");
        JsonSchema inputSchema = inputSchemaMap != null
            ? new JsonSchema(inputSchemaMap)
            : JsonSchema.object();

        return new ClaudeToolDefinition(name, description, inputSchema);
    }

    @Override
    public String toString() {
        return "ClaudeToolDefinition{name='" + name + "', description='" + description + "'}";
    }

    /**
     * Builder for creating ClaudeToolDefinition instances
     */
    public static class Builder {
        private String name;
        private String description;
        private JsonSchema inputSchema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(JsonSchema inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder inputSchema(Map<String, Object> schemaMap) {
            this.inputSchema = new JsonSchema(schemaMap);
            return this;
        }

        public ClaudeToolDefinition build() {
            return new ClaudeToolDefinition(name, description, inputSchema);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
