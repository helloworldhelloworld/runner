package com.lightweightai.kernel.task.annotation;

import com.lightweightai.kernel.task.JoinStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 Task 实现类的编排元数据
 *
 * 用于 TaskScanner 自动扫描并按 group 组装 TaskGraph。
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;TaskDef(name = "intent-classifier", description = "意图分类",
 *          group = "pre-processing")
 * public class IntentClassifierTask extends AbstractTask {
 *     ...
 * }
 *
 * &#64;TaskDef(name = "rag-retrieval", description = "知识检索",
 *          group = "pre-processing",
 *          dependsOn = {"intent-classifier", "safety-filter"},
 *          joinStrategy = JoinStrategy.ALL_SUCCESS,
 *          condition = "safety-filter:SUCCESS")
 * public class RagRetrievalTask extends AbstractTask {
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskDef {

    /** 任务名称（唯一标识），空则用类名 */
    String name() default "";

    /** 任务描述 */
    String description() default "";

    /** 声明依赖的上游任务名 */
    String[] dependsOn() default {};

    /** 汇聚策略 */
    JoinStrategy joinStrategy() default JoinStrategy.ALL_SUCCESS;

    /**
     * 条件表达式：检查上游任务状态
     * 格式 "taskName:STATUS"，如 "safety-filter:SUCCESS"
     */
    String condition() default "";

    /** 所属编排组（同组的 Task 会被自动组装为一个 TaskGraph） */
    String group() default "default";

    /** 组内执行顺序（同组无依赖关系时的参考顺序） */
    int order() default 0;
}
