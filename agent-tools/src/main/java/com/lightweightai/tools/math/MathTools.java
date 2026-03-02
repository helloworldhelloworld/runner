package com.lightweightai.tools.math;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;

/**
 * 数学工具集（注解方式）
 */
public class MathTools {

    @ToolFunction(
        name = "add",
        description = "Add two numbers and return the sum",
        category = "math",
        tags = {"math", "calculation"},
        readOnly = true,
        idempotent = true
    )
    public String add(
        @ToolParam(name = "a", description = "First number", required = true) double a,
        @ToolParam(name = "b", description = "Second number", required = true) double b
    ) {
        double result = a + b;
        return formatNumber(result);
    }

    @ToolFunction(
        name = "multiply",
        description = "Multiply two numbers and return the product",
        category = "math",
        tags = {"math", "calculation"},
        readOnly = true,
        idempotent = true
    )
    public String multiply(
        @ToolParam(name = "a", description = "First number", required = true) double a,
        @ToolParam(name = "b", description = "Second number", required = true) double b
    ) {
        double result = a * b;
        return formatNumber(result);
    }

    @ToolFunction(
        name = "divide",
        description = "Divide two numbers safely",
        category = "math",
        tags = {"math", "calculation"},
        readOnly = true,
        idempotent = true
    )
    public ToolResult divide(
        @ToolParam(name = "a", description = "Dividend", required = true) double a,
        @ToolParam(name = "b", description = "Divisor", required = true) double b
    ) {
        if (b == 0) {
            return ToolResult.error("Division by zero");
        }
        return ToolResult.success(formatNumber(a / b));
    }

    private String formatNumber(double result) {
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
