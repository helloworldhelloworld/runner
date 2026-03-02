package com.lightweightai.kernel.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 Tool 方法的参数
 *
 * <p>为参数提供名称、描述和类型信息，用于生成 JSON Schema。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;ToolFunction(name = "search", description = "Search the web")
 * public String search(
 *     &#64;ToolParam(name = "query", description = "Search query", required = true) String query,
 *     &#64;ToolParam(name = "maxResults", description = "Max results to return") int maxResults
 * ) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 参数名称
     * 如果为空，尝试使用编译时参数名（需要 -parameters 编译选项）
     */
    String name() default "";

    /**
     * 参数描述（供 LLM 理解）
     */
    String description() default "";

    /**
     * 是否必填（默认 false）
     */
    boolean required() default false;

    /**
     * JSON Schema 类型覆盖
     * 默认为空，自动从 Java 类型推断
     */
    String type() default "";
}
