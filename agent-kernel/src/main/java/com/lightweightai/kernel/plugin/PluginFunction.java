package com.lightweightai.kernel.plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a single callable function within a plugin.
 * Supports both synchronous and asynchronous execution.
 */
public interface PluginFunction {

    /**
     * Get the function name
     *
     * @return Function name (e.g., "add", "getCurrentTime")
     */
    String getName();

    /**
     * Get the function description (used by LLM to understand when to call it)
     *
     * @return Human-readable description
     */
    String getDescription();

    /**
     * Get the parameter definitions
     *
     * @return List of parameter definitions
     */
    List<FunctionParameter> getParameters();

    /**
     * Execute the function synchronously
     *
     * @param arguments Function arguments
     * @return Execution result
     */
    FunctionResult execute(Map<String, Object> arguments);

    /**
     * Execute the function asynchronously
     *
     * @param arguments Function arguments
     * @return CompletableFuture of execution result
     */
    default CompletableFuture<FunctionResult> executeAsync(Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> execute(arguments));
    }

    /**
     * Get the JSON Schema representation of this function
     * Used for tool calling with LLMs
     *
     * @return JSON Schema as Map
     */
    Map<String, Object> toJsonSchema();
}
