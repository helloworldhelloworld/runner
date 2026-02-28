package com.lightweightai.tools.math;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Add two numbers.
 */
public class AddTool implements Tool, ToolMetadata {

    @Override
    public String getName() {
        return "add";
    }

    @Override
    public String getDescription() {
        return "Add two numbers and return the sum";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.withRequired(Map.of(
            "a", Map.of("type", "number", "description", "First number"),
            "b", Map.of("type", "number", "description", "Second number")
        ), "a", "b");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        double a = ((Number) args.get("a")).doubleValue();
        double b = ((Number) args.get("b")).doubleValue();
        double result = a + b;
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return ToolResult.success("add", String.valueOf((long) result));
        }
        return ToolResult.success("add", String.valueOf(result));
    }

    @Override
    public String getCategory() {
        return "math";
    }

    @Override
    public List<String> getTags() {
        return List.of("math", "calculation");
    }
}
