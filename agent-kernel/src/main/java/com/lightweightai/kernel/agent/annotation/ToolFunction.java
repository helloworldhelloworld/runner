package com.lightweightai.kernel.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个方法为 Tool（工具）
 *
 * <p>使用示例：</p>
 * <pre>
 * public class MathTools {
 *
 *     &#64;ToolFunction(name = "add", description = "Add two numbers")
 *     public String add(
 *         &#64;ToolParam(name = "a", description = "First number", required = true) double a,
 *         &#64;ToolParam(name = "b", description = "Second number", required = true) double b
 *     ) {
 *         return String.valueOf(a + b);
 *     }
 * }
 *
 * // 注册
 * ToolRegistry registry = new ToolRegistry();
 * registry.registerObject(new MathTools());
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolFunction {

    /**
     * 工具名称（唯一标识）
     * 如果为空，使用方法名（驼峰转下划线）
     */
    String name() default "";

    /**
     * 工具描述（供 LLM 理解）
     */
    String description();

    /**
     * 工具分类
     */
    String category() default "default";

    /**
     * 工具标签
     */
    String[] tags() default {};

    /**
     * 是否只读（不修改外部状态）
     */
    boolean readOnly() default false;

    /**
     * 是否幂等
     */
    boolean idempotent() default false;

    /**
     * 是否与外部世界交互
     */
    boolean openWorld() default true;

    /**
     * 是否为 AI 自主调用（默认 true）
     */
    boolean autoExecute() default true;
}
