package com.lightweightai.kernel.task;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 便捷基类
 *
 * 子类只需实现 doExecute() 返回 Mono&lt;TaskResult&gt;，
 * 基类自动包装 start/complete/error 事件。
 */
public abstract class AbstractTask implements Task {

    private final String name;
    private final String description;

    protected AbstractTask(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    protected abstract Mono<TaskResult> doExecute(TaskContext context);

    @Override
    public Flux<StreamEvent> execute(TaskContext context) {
        return Flux.concat(
                Flux.just(StreamEventTaskAdapter.taskStart(name)),
                doExecute(context)
                        .flatMapMany(result -> Flux.just(
                                StreamEventTaskAdapter.taskResult(name, result)))
                        .onErrorResume(e -> Flux.just(
                                StreamEventTaskAdapter.taskError(name, e)))
        );
    }
}
