package com.lightweightai.kernel.task.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.task.Task;
import com.lightweightai.kernel.task.TaskContext;
import com.lightweightai.kernel.task.TaskResult;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 参数覆盖包装
 *
 * 包装一个 Task，将 YAML 中配置的参数注入到 TaskContext.attributes。
 * 被包装的 Task 通过 context.getAttribute("config:taskName:key") 读取。
 */
public class ConfiguredTask implements Task {

    private final Task delegate;
    private final Map<String, Object> config;

    public ConfiguredTask(Task delegate, Map<String, Object> config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public String getName() { return delegate.getName(); }

    @Override
    public String getDescription() { return delegate.getDescription(); }

    @Override
    public Flux<StreamEvent> execute(TaskContext context) {
        config.forEach((k, v) -> context.setAttribute("config:" + getName() + ":" + k, v));
        return delegate.execute(context);
    }

    @Override
    public TaskResult executeSync(TaskContext context) {
        config.forEach((k, v) -> context.setAttribute("config:" + getName() + ":" + k, v));
        return delegate.executeSync(context);
    }
}
