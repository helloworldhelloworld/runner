package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Executes tool calls from LLM using registered tools and plugins.
 *
 * Supports two registration paths:
 * 1. {@link Tool} objects via {@link ToolRegistry} (preferred)
 * 2. {@link PluginFunction} objects via legacy registerFunction methods
 *
 * When executing, ToolRegistry is checked first, then the legacy function registry.
 */
public class ToolExecutor {

    private final Map<String, PluginFunction> functionRegistry;
    private final ToolRegistry toolRegistry;

    /**
     * Create a new ToolExecutor with its own ToolRegistry
     */
    public ToolExecutor() {
        this.functionRegistry = new HashMap<>();
        this.toolRegistry = new ToolRegistry();
    }

    /**
     * Create a new ToolExecutor backed by an existing ToolRegistry
     *
     * @param toolRegistry The tool registry to use
     */
    public ToolExecutor(ToolRegistry toolRegistry) {
        this.functionRegistry = new HashMap<>();
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
    }

    /**
     * Register a Tool object
     *
     * @param tool The tool to register
     */
    public void registerTool(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }
        toolRegistry.register(tool);
    }

    /**
     * Register multiple Tool objects
     *
     * @param tools The tools to register
     */
    public void registerTools(List<Tool> tools) {
        if (tools != null) {
            toolRegistry.registerAll(tools);
        }
    }

    /**
     * Register annotated tool object (scans @ToolFunction methods)
     *
     * @param target Object containing @ToolFunction annotated methods
     * @return Number of tools registered
     */
    public int registerObject(Object target) {
        return toolRegistry.registerObject(target);
    }

    /**
     * Get the underlying ToolRegistry
     *
     * @return The tool registry
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Register a plugin's functions (legacy support)
     *
     * @param pluginFunctions Map of function name to PluginFunction
     */
    public void registerFunctions(Map<String, PluginFunction> pluginFunctions) {
        if (pluginFunctions != null) {
            functionRegistry.putAll(pluginFunctions);
        }
    }

    /**
     * Register a single function (legacy support)
     *
     * @param name     Function name
     * @param function PluginFunction instance
     */
    public void registerFunction(String name, PluginFunction function) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        if (function == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }

        functionRegistry.put(name, function);
    }

    /**
     * Execute a single tool call.
     * Checks ToolRegistry first, then falls back to legacy function registry.
     *
     * @param toolCall The tool call request from LLM
     * @return ToolResult with the execution result or error
     */
    public ToolResult executeToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            throw new IllegalArgumentException("ToolCall cannot be null");
        }

        try {
            // Check ToolRegistry first (preferred path)
            if (toolRegistry.has(toolCall.getName()) && toolRegistry.isEnabled(toolCall.getName())) {
                Tool tool = toolRegistry.get(toolCall.getName()).orElse(null);
                if (tool != null) {
                    return tool.execute(toolCall.getArguments())
                        .withToolUseId(toolCall.getId());
                }
            }

            // Fall back to legacy function registry
            PluginFunction function = functionRegistry.get(toolCall.getName());
            if (function == null) {
                return ToolResult.error(
                    toolCall.getId(),
                    "Tool not found: " + toolCall.getName()
                );
            }

            // Execute the function
            FunctionResult result = function.execute(toolCall.getArguments());

            // Convert to ToolResult
            if (result.isSuccess()) {
                return ToolResult.success(toolCall.getId(), result.getValue());
            } else {
                return ToolResult.error(toolCall.getId(), result.getError());
            }

        } catch (Exception e) {
            return ToolResult.error(
                toolCall.getId(),
                "Execution failed: " + e.getMessage()
            );
        }
    }

    /**
     * Execute multiple tool calls sequentially
     *
     * @param toolCalls List of tool call requests
     * @return List of tool results in the same order
     */
    public List<ToolResult> executeToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        return toolCalls.stream()
            .map(this::executeToolCall)
            .collect(Collectors.toList());
    }

    /**
     * Execute multiple tool calls in parallel
     *
     * @param toolCalls List of tool call requests
     * @return CompletableFuture of list of tool results
     */
    public CompletableFuture<List<ToolResult>> executeToolCallsAsync(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<ToolResult>> futures = toolCalls.stream()
            .map(toolCall -> CompletableFuture.supplyAsync(() -> executeToolCall(toolCall)))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * Check if a tool is registered (in either registry)
     *
     * @param toolName Tool name to check
     * @return true if the tool is registered
     */
    public boolean hasFunction(String toolName) {
        return toolRegistry.has(toolName) || functionRegistry.containsKey(toolName);
    }

    /**
     * Get the total number of registered tools and functions
     *
     * @return Total count
     */
    public int getFunctionCount() {
        return toolRegistry.enabledCount() + functionRegistry.size();
    }

    /**
     * Get tool definitions in JSON schema format for LLM.
     * Merges definitions from ToolRegistry and legacy function registry.
     *
     * @return List of tool definitions as Map
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();

        // Add Tool definitions from ToolRegistry
        definitions.addAll(toolRegistry.getToolDefinitions());

        // Add legacy PluginFunction definitions
        definitions.addAll(functionRegistry.values().stream()
            .map(PluginFunction::toJsonSchema)
            .collect(Collectors.toList()));

        return definitions;
    }

    /**
     * Clear all registered tools and functions
     */
    public void clear() {
        toolRegistry.clear();
        functionRegistry.clear();
    }

    @Override
    public String toString() {
        return "ToolExecutor{tools=" + toolRegistry.enabledCount()
            + ", functions=" + functionRegistry.size() + "}";
    }
}
