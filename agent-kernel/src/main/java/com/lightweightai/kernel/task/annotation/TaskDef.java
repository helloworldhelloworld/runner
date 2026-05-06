package com.lightweightai.kernel.task.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 Task 类，由 {@link TaskScanner} 自动发现并注册到 TaskRegistry。
 *
 * <p>类必须实现 {@link com.lightweightai.kernel.task.core.Task}，
 * 且具有公开无参构造方法。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskDef {

    /** 任务名（默认使用类的 getName()）。 */
    String name() default "";

    /** 简短描述。 */
    String description() default "";

    /** 分组（可用于过滤批量注册）。 */
    String group() default "default";

    /**
     * 可选 guard 表达式，由 {@link com.lightweightai.kernel.task.config.GuardExpression#parse} 解析。
     */
    String condition() default "";
}
