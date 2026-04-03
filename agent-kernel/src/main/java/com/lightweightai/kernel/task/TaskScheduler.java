package com.lightweightai.kernel.task;

import com.lightweightai.kernel.logging.StructuredLogger;
import com.lightweightai.kernel.trace.SpanContext;
import com.lightweightai.kernel.trace.SpanKind;
import com.lightweightai.kernel.trace.Tracer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 统一任务调度器 (Harness Engineering - Entropy Management)。
 *
 * 支持：
 * - 异步任务提交（立即/延迟）
 * - 定时任务调度（固定间隔/cron）
 * - 自动 trace span 关联
 * - 重试策略
 * - 任务查询（按 taskId / traceId）
 *
 * 由 AgentHarness 管理生命周期。
 */
public class TaskScheduler implements AutoCloseable {

    private static final StructuredLogger logger = StructuredLogger.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService scheduler;
    private final TaskRegistry registry;
    private final Tracer tracer;
    private final List<TaskObserver> observers;
    private final ConcurrentHashMap<String, TaskInstance<?>> activeTasks = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public TaskScheduler(TaskRegistry registry, Tracer tracer) {
        this(registry, tracer, Collections.emptyList(), 4);
    }

    public TaskScheduler(TaskRegistry registry, Tracer tracer,
                         List<TaskObserver> observers, int threadPoolSize) {
        this.registry = registry != null ? registry : new TaskRegistry();
        this.tracer = tracer != null ? tracer : Tracer.NOOP;
        this.observers = new ArrayList<>(observers);
        this.scheduler = Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "harness-task-" + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== 生命周期 ====================

    public void start() {
        running = true;
        logger.event("task.scheduler.start", Map.of("registeredTasks", registry.size()));
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.event("task.scheduler.close", Map.of("activeTasks", activeTasks.size()));
    }

    // ==================== 异步任务 ====================

    /**
     * 提交异步任务（立即执行）
     */
    public <T> CompletableFuture<TaskResult<T>> submit(String taskName, Map<String, Object> params) {
        return submitInternal(taskName, params, null, Duration.ZERO);
    }

    /**
     * 提交异步任务（延迟执行）
     */
    public <T> CompletableFuture<TaskResult<T>> submitDelayed(String taskName, Duration delay,
                                                                Map<String, Object> params) {
        return submitInternal(taskName, params, null, delay);
    }

    /**
     * 提交异步任务（关联 parent trace span）
     */
    public <T> CompletableFuture<TaskResult<T>> submit(String taskName, SpanContext parentSpan,
                                                        Map<String, Object> params) {
        return submitInternal(taskName, params, parentSpan, Duration.ZERO);
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<TaskResult<T>> submitInternal(
            String taskName, Map<String, Object> params,
            SpanContext parentSpan, Duration delay) {

        TaskDefinition<T> definition = (TaskDefinition<T>) registry.get(taskName).orElse(null);
        if (definition == null) {
            return CompletableFuture.completedFuture(
                TaskResult.failure("Task not registered: " + taskName));
        }

        String taskId = UUID.randomUUID().toString().substring(0, 12);

        // 创建 task span（关联到 parent 或独立 root）
        SpanContext taskSpan = parentSpan != null
            ? parentSpan.createChild("task." + taskName, SpanKind.INTERNAL)
            : tracer.startTrace("task." + taskName, SpanKind.INTERNAL);
        taskSpan.setAttribute("taskId", taskId);
        taskSpan.setAttribute("taskName", taskName);

        TaskInstance<T> instance = new TaskInstance<>(taskId, taskName, taskSpan.getTraceId());
        activeTasks.put(taskId, instance);

        // 通知 observers
        notifyObservers(o -> o.onTaskSubmitted(taskId, taskName));
        logger.taskSubmit(taskName, taskId);

        CompletableFuture<TaskResult<T>> future = new CompletableFuture<>();

        Runnable execution = () -> executeTask(definition, instance, taskSpan, params, future, 0);

        if (delay.isZero() || delay.isNegative()) {
            scheduler.submit(execution);
        } else {
            scheduler.schedule(execution, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        return future;
    }

    private <T> void executeTask(TaskDefinition<T> definition, TaskInstance<T> instance,
                                  SpanContext taskSpan, Map<String, Object> params,
                                  CompletableFuture<TaskResult<T>> future, int attempt) {
        instance.markRunning();
        notifyObservers(o -> o.onTaskStarted(instance.getTaskId(), instance.getTaskName()));

        TaskContext context = new TaskContext(
            instance.getTaskId(), instance.getTaskName(), taskSpan,
            taskSpan.getAllBaggage(), params);

        try {
            TaskResult<T> result = definition.getHandler().handle(context);

            if (result.isSuccess()) {
                instance.markSuccess(result);
                tracer.endSpan(taskSpan);
                notifyObservers(o -> o.onTaskCompleted(
                    instance.getTaskId(), instance.getTaskName(), true, instance.getDurationMs()));
                logger.taskComplete(instance.getTaskName(), instance.getTaskId(),
                    instance.getDurationMs(), true);
                future.complete(result);
            } else if (result.isShouldRetry() && attempt < definition.getRetryPolicy().getMaxRetries()) {
                instance.markRetry();
                notifyObservers(o -> o.onTaskRetry(
                    instance.getTaskId(), instance.getTaskName(), attempt + 1));
                Duration backoff = definition.getRetryPolicy().getBackoffDuration(attempt + 1);
                scheduler.schedule(
                    () -> executeTask(definition, instance, taskSpan, params, future, attempt + 1),
                    backoff.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                instance.markFailed(result);
                tracer.endSpanWithError(taskSpan, new RuntimeException(result.getErrorMessage()));
                notifyObservers(o -> o.onTaskFailed(
                    instance.getTaskId(), instance.getTaskName(), result.getErrorMessage()));
                logger.taskComplete(instance.getTaskName(), instance.getTaskId(),
                    instance.getDurationMs(), false);
                future.complete(result);
            }
        } catch (Exception e) {
            RetryPolicy retry = definition.getRetryPolicy();
            if (attempt < retry.getMaxRetries() && retry.isRetryable(e)) {
                instance.markRetry();
                notifyObservers(o -> o.onTaskRetry(
                    instance.getTaskId(), instance.getTaskName(), attempt + 1));
                Duration backoff = retry.getBackoffDuration(attempt + 1);
                scheduler.schedule(
                    () -> executeTask(definition, instance, taskSpan, params, future, attempt + 1),
                    backoff.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                instance.markFailed(TaskResult.failure(e.getMessage()));
                tracer.endSpanWithError(taskSpan, e);
                notifyObservers(o -> o.onTaskFailed(
                    instance.getTaskId(), instance.getTaskName(), e.getMessage()));
                future.completeExceptionally(e);
            }
        }
    }

    // ==================== 定时任务 ====================

    /**
     * 注册并启动固定间隔定时任务
     */
    public ScheduledTaskHandle scheduleAtFixedRate(String taskName, Duration initialDelay, Duration period) {
        @SuppressWarnings("unchecked")
        TaskDefinition<Void> definition = (TaskDefinition<Void>) registry.get(taskName).orElse(null);
        if (definition == null) {
            throw new IllegalArgumentException("Task not registered: " + taskName);
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            String taskId = UUID.randomUUID().toString().substring(0, 12);
            SpanContext span = tracer.startTrace("scheduled." + taskName, SpanKind.INTERNAL);
            span.setAttribute("taskId", taskId);
            span.setAttribute("taskName", taskName);
            span.setAttribute("scheduled", true);

            TaskContext context = new TaskContext(taskId, taskName, span,
                Collections.emptyMap(), Collections.emptyMap());

            try {
                definition.getHandler().handle(context);
                tracer.endSpan(span);
            } catch (Exception e) {
                tracer.endSpanWithError(span, e);
                logger.error("scheduled.task.error", e, Map.of("taskName", taskName));
            }
        }, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);

        logger.event("scheduled.task.registered",
            Map.of("taskName", taskName, "period", period.toString()));
        return new ScheduledTaskHandle(taskName, future);
    }

    // ==================== 查询 ====================

    public TaskInstance<?> getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    public List<TaskInstance<?>> getTasksByTrace(String traceId) {
        List<TaskInstance<?>> result = new ArrayList<>();
        for (TaskInstance<?> instance : activeTasks.values()) {
            if (traceId.equals(instance.getTraceId())) {
                result.add(instance);
            }
        }
        return result;
    }

    public List<TaskInstance<?>> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }

    public TaskRegistry getRegistry() { return registry; }

    // ==================== 内部辅助 ====================

    private void notifyObservers(java.util.function.Consumer<TaskObserver> callback) {
        for (TaskObserver observer : observers) {
            try {
                callback.accept(observer);
            } catch (Exception ignored) {}
        }
    }
}
