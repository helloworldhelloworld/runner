package com.lightweightai.kernel.llm;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a tool use request from Claude.
 * This is parsed from Claude's response when it wants to call a tool.
 */
public class ToolUse {

    private final String id;
    private final String name;
    private final Map<String, Object> input;

    public ToolUse(String id, String name, Map<String, Object> input) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool use ID cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        this.id = id;
        this.name = name;
        this.input = input != null ? input : Collections.emptyMap();
    }

    /**
     * Get the tool use ID (e.g., "toolu_01A09q90qw90lq917835lq9")
     */
    public String getId() {
        return id;
    }

    /**
     * Get the tool name (e.g., "get_weather")
     */
    public String getName() {
        return name;
    }

    /**
     * Get the tool input parameters
     */
    public Map<String, Object> getInput() {
        return Collections.unmodifiableMap(input);
    }

    /**
     * Parse from Claude API response content block
     *
     * @param block Content block from Claude response
     * @return ToolUse instance
     */
    public static ToolUse fromContentBlock(Map<String, Object> block) {
        if (block == null) {
            throw new IllegalArgumentException("Content block cannot be null");
        }

        String type = (String) block.get("type");
        if (!"tool_use".equals(type)) {
            throw new IllegalArgumentException("Content block is not a tool_use type: " + type);
        }

        String id = (String) block.get("id");
        String name = (String) block.get("name");

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) block.get("input");

        return new ToolUse(id, name, input);
    }

    @Override
    public String toString() {
        return "ToolUse{id='" + id + "', name='" + name + "', input=" + input + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ToolUse toolUse = (ToolUse) o;
        return id.equals(toolUse.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
