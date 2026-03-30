package com.lightweightai.kernel.task;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 可编排任务接口
 *
 * 与 Tool（面向 LLM 调用，接收 Map args）不同，Task 面向编排器调用，
 * 接收 TaskContext 共享上下文，返回 Flux&lt;StreamEvent&gt; 响应式事件流。
 *
 * 任务实例通过 TaskRegistry 注册，由 TaskGraph 编排执行。
 */
public interface Task {

    String getName();

    String getDescription();

    /**
     * 响应式执行
     *
     * 返回的 Flux 可以发射 TRACE / TOOL_PROGRESS 事件（进度），
     * 必须在完成时通过 StreamEventTaskAdapter 发射 task.complete 或 task.error 事件。
     */
    Flux<StreamEvent> execute(TaskContext context);

    /**
     * 同步便捷方法，默认 block 在 execute() 上
     */
    default TaskResult executeSync(TaskContext context) {
        return execute(context)
                .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                        && ("task.complete".equals(e.getTracePhase())
                            || "task.error".equals(e.getTracePhase())))
                .next()
                .map(e -> {
                    if ("task.error".equals(e.getTracePhase())) {
                        String msg = e.getData() != null
                                ? String.valueOf(e.getData().get("error"))
                                : e.getTraceMessage();
                        return TaskResult.error(msg);
                    }
                    Object tr = e.getData() != null ? e.getData().get("taskResult") : null;
                    return tr instanceof TaskResult ? (TaskResult) tr : TaskResult.success(e.getTraceMessage());
                })
                .block();
    }
}
