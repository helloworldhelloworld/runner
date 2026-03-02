package com.lightweightai.tools.math;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;

/**
 * 注解方式定义的数学工具（示例）
 *
 * <p>对比 {@link AddTool}（接口方式）和本类（注解方式）的区别：</p>
 *
 * <p><b>接口方式（AddTool）：</b>~40 行代码</p>
 * <p><b>注解方式（本类）：</b>每个方法 ~5 行代码</p>
 *
 * <p>使用方式：</p>
 * <pre>
 * ToolRegistry registry = new ToolRegistry();
 * registry.registerObject(new AnnotatedMathTools());
 * // 自动注册 "annotated_add" 和 "annotated_divide" 两个工具
 * </pre>
 */
public class AnnotatedMathTools {

    @ToolFunction(
        name = "annotated_add",
        description = "Add two numbers and return the sum",
        category = "math",
        tags = {"math", "calculation"},
        readOnly = true,
        idempotent = true,
        openWorld = false
    )
    public String add(
        @ToolParam(name = "a", description = "First number", required = true) double a,
        @ToolParam(name = "b", description = "Second number", required = true) double b
    ) {
        double result = a + b;
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }

    @ToolFunction(
        name = "annotated_divide",
        description = "Divide two numbers safely",
        category = "math",
        tags = {"math", "calculation"},
        readOnly = true,
        idempotent = true,
        openWorld = false
    )
    public ToolResult divide(
        @ToolParam(name = "a", description = "Dividend", required = true) double a,
        @ToolParam(name = "b", description = "Divisor", required = true) double b
    ) {
        if (b == 0) {
            return ToolResult.error("Division by zero");
        }
        double result = a / b;
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return ToolResult.success(String.valueOf((long) result));
        }
        return ToolResult.success(String.valueOf(result));
    }
}
