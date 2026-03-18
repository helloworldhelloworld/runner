package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Memory toolkit combining all memory-related tools.
 * Provides easy registration with AI agent frameworks.
 */
public class MemoryToolkit {

    private final MemorySearchTool searchTool;
    private final WriteMemoryTool writeTool;

    public MemoryToolkit(FileMemoryManager memoryManager) {
        this.searchTool = new MemorySearchTool(memoryManager);
        this.writeTool = new WriteMemoryTool(memoryManager);
    }

    /**
     * Get the memory search tool.
     */
    public MemorySearchTool getSearchTool() {
        return searchTool;
    }

    /**
     * Get the write memory tool.
     */
    public WriteMemoryTool getWriteTool() {
        return writeTool;
    }

    /**
     * Execute a tool by name.
     */
    public Object execute(String toolName, Map<String, Object> parameters) {
        return switch (toolName) {
            case MemorySearchTool.TOOL_NAME -> searchTool.execute(parameters);
            case WriteMemoryTool.TOOL_NAME -> writeTool.execute(parameters);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    /**
     * Get all tool schemas for registration with an AI framework.
     */
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(
            MemorySearchTool.getToolSchema(),
            WriteMemoryTool.getToolSchema()
        );
    }

    /**
     * Get tool executor function for registration.
     * Returns a function that takes (toolName, parameters) and returns result as string.
     */
    public Function<ToolCall, String> getExecutor() {
        return call -> {
            Object result = execute(call.name(), call.parameters());
            if (result instanceof MemorySearchTool.MemorySearchResult searchResult) {
                return searchResult.toText();
            } else if (result instanceof WriteMemoryTool.WriteMemoryResult writeResult) {
                return writeResult.toText();
            }
            return result.toString();
        };
    }

    /**
     * Tool call record for executor function.
     */
    public record ToolCall(String name, Map<String, Object> parameters) {}

    /**
     * Create a toolkit from a memory manager.
     */
    public static MemoryToolkit create(FileMemoryManager memoryManager) {
        return new MemoryToolkit(memoryManager);
    }
}
