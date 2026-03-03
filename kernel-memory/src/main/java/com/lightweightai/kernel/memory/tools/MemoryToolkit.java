package com.lightweightai.kernel.memory.tools;

import com.lightweightai.kernel.memory.file.FileMemoryManager;

import java.util.Arrays;
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
        if (MemorySearchTool.TOOL_NAME.equals(toolName)) {
            return searchTool.execute(parameters);
        } else if (WriteMemoryTool.TOOL_NAME.equals(toolName)) {
            return writeTool.execute(parameters);
        } else {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    /**
     * Get all tool schemas for registration with an AI framework.
     */
    public List<Map<String, Object>> getToolSchemas() {
        return Arrays.asList(
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
            if (result instanceof MemorySearchTool.MemorySearchResult) {
                MemorySearchTool.MemorySearchResult searchResult = (MemorySearchTool.MemorySearchResult) result;
                return searchResult.toText();
            } else if (result instanceof WriteMemoryTool.WriteMemoryResult) {
                WriteMemoryTool.WriteMemoryResult writeResult = (WriteMemoryTool.WriteMemoryResult) result;
                return writeResult.toText();
            }
            return result.toString();
        };
    }

    /**
     * Tool call class for executor function.
     */
    public static final class ToolCall {
        private final String name;
        private final Map<String, Object> parameters;

        public ToolCall(String name, Map<String, Object> parameters) {
            this.name = name;
            this.parameters = parameters;
        }

        public String name() { return name; }
        public Map<String, Object> parameters() { return parameters; }
    }

    /**
     * Create a toolkit from a memory manager.
     */
    public static MemoryToolkit create(FileMemoryManager memoryManager) {
        return new MemoryToolkit(memoryManager);
    }
}
