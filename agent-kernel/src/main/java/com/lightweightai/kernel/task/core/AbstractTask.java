package com.lightweightai.kernel.task.core;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.task.event.TaskEvents;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Task 基类：处理 START / COMPLETE / ERROR 事件包裹与 TaskResult 写入 context。
 *
 * <p>子类实现 {@link #doExecute(TaskContext)} 返回 {@code Mono<TaskResult>}（可空，
 * 空值会被映射为 {@link TaskResult#error} 而非永远不完成 — 防止下游订阅卡死）。</p>
 */
public abstract class AbstractTask implements Task {

    private final String name;

    protected AbstractTask(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final Flux<StreamEvent> execute(TaskContext context) {
        return Flux.defer(() -> {
            StreamEvent start = TaskEvents.start(name);
            return doExecute(context)
                .defaultIfEmpty(TaskResult.error("task " + name + " produced empty Mono"))
                .doOnNext(r -> context.putResult(name, r))
                .map(r -> r.isError() ? TaskEvents.error(name, r) : TaskEvents.complete(name, r))
                .onErrorResume(t -> {
                    TaskResult r = TaskResult.error("task " + name + " threw: " + t.getMessage(), t);
                    context.putResult(name, r);
                    return Mono.just(TaskEvents.error(name, r));
                })
                .flux()
                .startWith(start);
        });
    }

    protected abstract Mono<TaskResult> doExecute(TaskContext context);
}
