package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
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
        if (content == null || content.isBlank()) {
            return new WriteMemoryResult(false, "Content parameter is required", null);
        }

        String type = (String) parameters.getOrDefault("type", "ephemeral");
        String section = (String) parameters.get("section");

        try {
            switch (type.toLowerCase()) {
                case "durable" -> {
                    if (section != null && !section.isBlank()) {
                        memoryManager.appendDurable(section, content);
                        return new WriteMemoryResult(true, null,
                            "Saved to durable memory under section: " + section);
                    } else {
                        // Append to default section
                        memoryManager.appendDurable("Notes", content);
                        return new WriteMemoryResult(true, null,
                            "Saved to durable memory under Notes section");
                    }
                }
                case "ephemeral" -> {
                    memoryManager.appendEphemeral(content);
                    return new WriteMemoryResult(true, null,
                        "Saved to today's ephemeral memory");
                }
                default -> {
                    return new WriteMemoryResult(false,
                        "Invalid type: " + type + ". Use 'ephemeral' or 'durable'", null);
                }
            }
        } catch (Exception e) {
            return new WriteMemoryResult(false, "Failed to write memory: " + e.getMessage(), null);
        }
    }

    /**
     * Get JSON schema for tool definition.
     */
    public static Map<String, Object> getToolSchema() {
        return Map.of(
            "name", TOOL_NAME,
            "description", TOOL_DESCRIPTION,
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "content", Map.of(
                        "type", "string",
                        "description", "The content to save to memory"
                    ),
                    "type", Map.of(
                        "type", "string",
                        "enum", List.of("ephemeral", "durable"),
                        "description", "Memory type: 'ephemeral' for daily notes, 'durable' for long-term"
                    ),
                    "section", Map.of(
                        "type", "string",
                        "description", "Section name for durable memory (e.g., 'User Preferences', 'Project Notes')"
                    )
                ),
                "required", List.of("content")
            )
        );
    }

    // Result type

    public record WriteMemoryResult(
        boolean success,
        String error,
        String message
    ) {
        public String toText() {
            if (!success) {
                return "Error: " + error;
            }
            return message;
        }
    }
}
