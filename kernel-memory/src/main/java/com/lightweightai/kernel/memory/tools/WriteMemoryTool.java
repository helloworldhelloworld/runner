package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory write tool for AI agents.
 * Saves information to different memory tiers.
 *
 * Tool Definition:
 * - name: write_memory
 * - description: Save important information to memory
 * - parameters:
 *   - content (required): The content to remember
 *   - type (optional): Memory type - "ephemeral" (daily log) or "durable" (long-term)
 *   - section (optional): Section name for durable memory
 */
public class WriteMemoryTool {

    public static final String TOOL_NAME = "write_memory";
    public static final String TOOL_DESCRIPTION =
        "Save important information to memory for future reference. " +
        "Use 'ephemeral' for temporary notes and 'durable' for long-term knowledge.";

    private final FileMemoryManager memoryManager;

    public WriteMemoryTool(FileMemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Execute the memory write.
     */
    public WriteMemoryResult execute(Map<String, Object> parameters) {
        String content = (String) parameters.get("content");
        if (content == null || content.trim().isEmpty()) {
            return new WriteMemoryResult(false, "Content parameter is required", null);
        }

        String type = (String) parameters.getOrDefault("type", "ephemeral");
        String section = (String) parameters.get("section");

        try {
            String typeLower = type.toLowerCase();
            if ("durable".equals(typeLower)) {
                if (section != null && !section.trim().isEmpty()) {
                    memoryManager.appendDurable(section, content);
                    return new WriteMemoryResult(true, null,
                        "Saved to durable memory under section: " + section);
                } else {
                    // Append to default section
                    memoryManager.appendDurable("Notes", content);
                    return new WriteMemoryResult(true, null,
                        "Saved to durable memory under Notes section");
                }
            } else if ("ephemeral".equals(typeLower)) {
                memoryManager.appendEphemeral(content);
                return new WriteMemoryResult(true, null,
                    "Saved to today's ephemeral memory");
            } else {
                return new WriteMemoryResult(false,
                    "Invalid type: " + type + ". Use 'ephemeral' or 'durable'", null);
            }
        } catch (Exception e) {
            return new WriteMemoryResult(false, "Failed to write memory: " + e.getMessage(), null);
        }
    }

    /**
     * Get JSON schema for tool definition.
     */
    public static Map<String, Object> getToolSchema() {
        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "The content to save to memory");

        Map<String, Object> typeProp = new HashMap<>();
        typeProp.put("type", "string");
        typeProp.put("enum", Arrays.asList("ephemeral", "durable"));
        typeProp.put("description", "Memory type: 'ephemeral' for daily notes, 'durable' for long-term");

        Map<String, Object> sectionProp = new HashMap<>();
        sectionProp.put("type", "string");
        sectionProp.put("description", "Section name for durable memory (e.g., 'User Preferences', 'Project Notes')");

        Map<String, Object> properties = new HashMap<>();
        properties.put("content", contentProp);
        properties.put("type", typeProp);
        properties.put("section", sectionProp);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList("content"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", TOOL_NAME);
        schema.put("description", TOOL_DESCRIPTION);
        schema.put("input_schema", inputSchema);
        return schema;
    }

    // Result type

    public static final class WriteMemoryResult {
        private final boolean success;
        private final String error;
        private final String message;

        public WriteMemoryResult(boolean success, String error, String message) {
            this.success = success;
            this.error = error;
            this.message = message;
        }

        public boolean success() { return success; }
        public String error() { return error; }
        public String message() { return message; }

        public String toText() {
            if (!success) {
                return "Error: " + error;
            }
            return message;
        }
    }
}
