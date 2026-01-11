package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Executes tool calls from LLM using registered plugins.
 * Responsible for finding the correct function and executing it with the provided parameters.
 */
public class ToolExecutor {

    private final Map<String, PluginFunction> functionRegistry;

    /**
     * Create a new ToolExecutor
     */
    public ToolExecutor() {
        this.functionRegistry = new HashMap<>();
    }

    /**
     * Register a plugin's functions
     *
     * @param pluginFunctions Map of function name to PluginFunction
     */
    public void registerFunctions(Map<String, PluginFunction> pluginFunctions) {
        if (pluginFunctions != null) {
            functionRegistry.putAll(pluginFunctions);
        }
    }

    /**
     * Register a single function
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
     * Execute a single tool call
     *
     * @param toolCall The tool call request from LLM
     * @return ToolResult with the execution result or error
     */
    public ToolResult executeToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            throw new IllegalArgumentException("ToolCall cannot be null");
        }

        try {
            // Find the function
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
     * Check if a tool is registered
     *
     * @param toolName Tool name to check
     * @return true if the tool is registered
     */
    public boolean hasFunction(String toolName) {
        return functionRegistry.containsKey(toolName);
    }

    /**
     * Get the number of registered functions
     *
     * @return Number of functions
     */
    public int getFunctionCount() {
        return functionRegistry.size();
    }

    /**
     * Get tool definitions in JSON schema format for LLM
     *
     * @return List of tool definitions as Map
     */
    public List<Map<String, Object>> getToolDefinitions() {
        return functionRegistry.values().stream()
            .map(PluginFunction::toJsonSchema)
            .collect(Collectors.toList());
    }

    /**
     * Clear all registered functions
     */
    public void clear() {
        functionRegistry.clear();
    }

    @Override
    public String toString() {
        return "ToolExecutor{functions=" + functionRegistry.size() + "}";
    }
}
