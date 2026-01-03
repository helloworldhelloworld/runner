package com.lightweightai.kernel.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Type-safe implementation of PluginFunction.
 * Automatically generates JSON Schema from parameter class using Jackson annotations.
 */
public class TypedPluginFunction implements PluginFunction {

    private final String name;
    private final String description;
    private final Class<?> parameterType;
    private final Function<Object, Object> handler;
    private final JsonSchema inputSchema;
    private final ObjectMapper objectMapper;

    /**
     * Create a new typed plugin function
     *
     * @param name Function name
     * @param description Function description
     * @param parameterType Parameter class (must have Jackson annotations)
     * @param handler Function handler
     */
    public <T> TypedPluginFunction(
        String name,
        String description,
        Class<T> parameterType,
        Function<T, Object> handler
    ) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        if (parameterType == null) {
            throw new IllegalArgumentException("Parameter type cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        this.name = name;
        this.description = description != null ? description : "";
        this.parameterType = parameterType;
        this.handler = (Function<Object, Object>) handler;
        this.inputSchema = JsonSchemaGenerator.generate(parameterType);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<FunctionParameter> getParameters() {
        // For typed functions, parameters are derived from the schema
        // This is mainly for compatibility with the interface
        return Collections.emptyList();
    }

    @Override
    public FunctionResult execute(Map<String, Object> arguments) {
        try {
            // Convert Map to typed parameter object
            Object typedParam = objectMapper.convertValue(arguments, parameterType);

            // Execute the handler
            Object result = handler.apply(typedParam);

            return FunctionResult.success(result);
        } catch (Exception e) {
            return FunctionResult.error(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<FunctionResult> executeAsync(Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> execute(arguments));
    }

    @Override
    public Map<String, Object> toJsonSchema() {
        return toClaudeToolDefinition();
    }

    /**
     * Get the parameter type class
     */
    public Class<?> getParameterType() {
        return parameterType;
    }

    /**
     * Get the input schema
     */
    public JsonSchema getInputSchema() {
        return inputSchema;
    }

    /**
     * Convert to Claude API tool definition format
     */
    public Map<String, Object> toClaudeToolDefinition() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", inputSchema.toMap());
        return tool;
    }

    @Override
    public String toString() {
        return "TypedPluginFunction{name='" + name + "', description='" + description + "'}";
    }
}
