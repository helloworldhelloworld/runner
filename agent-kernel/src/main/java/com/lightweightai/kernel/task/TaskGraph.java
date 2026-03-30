package com.lightweightai.kernel.task;

import com.lightweightai.kernel.core.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Predicate;

/**
 * DAG 任务编排器
 *
 * 按拓扑层级执行：同层无依赖的任务通过 Flux.merge() 并行，
 * 一层完成后进入下一层。支持条件守卫和汇聚策略。
 */
public class TaskGraph {

    private static final Logger logger = LoggerFactory.getLogger(TaskGraph.class);

    private final String name;
    private final Map<String, Task> tasks;
    private final Map<String, Set<String>> dependencies;
    private final Map<String, Predicate<TaskContext>> conditions;
    private final Map<String, JoinStrategy> joinStrategies;

    private TaskGraph(Builder builder) {
        this.name = builder.name;
        this.tasks = new LinkedHashMap<>(builder.tasks);
        this.dependencies = new LinkedHashMap<>(builder.dependencies);
        this.conditions = new LinkedHashMap<>(builder.conditions);
        this.joinStrategies = new LinkedHashMap<>(builder.joinStrategies);
        validateNoCycles();
    }

    public String getName() { return name; }

    public Map<String, Task> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    /**
     * 执行整个任务图
     */
    public Flux<StreamEvent> execute(TaskContext context) {
        logger.debug("开始执行 TaskGraph: {}", name);
        return Flux.defer(() -> executeLevel(context, new HashSet<>()));
    }

    private Flux<StreamEvent> executeLevel(TaskContext context, Set<String> completed) {
        List<Task> ready = new ArrayList<>();
        List<String> skippedThisRound = new ArrayList<>();

        for (Map.Entry<String, Task> entry : tasks.entrySet()) {
            String taskName = entry.getKey();
            if (completed.contains(taskName)) continue;

            Set<String> deps = dependencies.getOrDefault(taskName, Collections.emptySet());
            JoinStrategy strategy = joinStrategies.getOrDefault(taskName, JoinStrategy.ALL_SUCCESS);

            if (!isReady(deps, strategy, completed, context)) continue;

            // 检查条件守卫
            Predicate<TaskContext> condition = conditions.get(taskName);
            if (condition != null && !condition.test(context)) {
                skippedThisRound.add(taskName);
                completed.add(taskName);
                context.putResult(taskName, TaskResult.skipped("Condition not met"));
                logger.debug("Task '{}' 跳过: 条件不满足", taskName);
                continue;
            }

            ready.add(entry.getValue());
        }

        // 如果有跳过但没有 ready，需要再扫一轮（跳过可能解锁了新的任务）
        if (ready.isEmpty() && !skippedThisRound.isEmpty()) {
            return executeLevel(context, completed);
        }

        if (ready.isEmpty()) {
            if (completed.size() < tasks.size()) {
                return Flux.error(new IllegalStateException(
                        "TaskGraph '" + name + "' 死锁: 部分任务依赖无法满足"));
            }
            logger.debug("TaskGraph '{}' 执行完成", name);
            return Flux.empty();
        }

        // 并行执行 ready 任务
        List<Flux<StreamEvent>> taskFluxes = ready.stream()
                .map(task -> task.execute(context)
                        .doOnNext(event -> {
                            if (event.getType() == StreamEvent.EventType.TRACE
                                    && "task.complete".equals(event.getTracePhase())) {
                                Object tr = event.getData() != null
                                        ? event.getData().get("taskResult") : null;
                                if (tr instanceof TaskResult) {
                                    context.putResult(task.getName(), (TaskResult) tr);
                                }
                            } else if (event.getType() == StreamEvent.EventType.TRACE
                                    && "task.error".equals(event.getTracePhase())) {
                                String errMsg = event.getData() != null
                                        ? String.valueOf(event.getData().get("error"))
                                        : "Unknown error";
                                context.putResult(task.getName(), TaskResult.error(errMsg));
                            }
                        }))
                .toList();

        return Flux.merge(taskFluxes)
                .collectList()
                .flatMapMany(events -> {
                    ready.forEach(t -> completed.add(t.getName()));
                    return Flux.fromIterable(events)
                            .concatWith(executeLevel(context, completed));
                });
    }

    private boolean isReady(Set<String> deps, JoinStrategy strategy,
                           Set<String> completed, TaskContext context) {
        if (deps.isEmpty()) return true;

        return switch (strategy) {
            case ALL_SUCCESS -> deps.stream().allMatch(d ->
                    completed.contains(d) && context.getResult(d)
                            .map(TaskResult::isSuccess).orElse(false));
            case ANY_SUCCESS -> deps.stream().anyMatch(d ->
                    completed.contains(d) && context.getResult(d)
                            .map(TaskResult::isSuccess).orElse(false));
            case ALL_COMPLETE -> completed.containsAll(deps);
            case FIRST_COMPLETE -> deps.stream().anyMatch(completed::contains);
        };
    }

    private void validateNoCycles() {
        Map<String, Integer> inDegree = new HashMap<>();
        tasks.keySet().forEach(k -> inDegree.put(k, 0));
        dependencies.forEach((task, deps) -> {
            for (String dep : deps) {
                inDegree.computeIfPresent(task, (k, v) -> v + 1);
            }
        });

        Queue<String> queue = new LinkedList<>();
        inDegree.forEach((k, v) -> { if (v == 0) queue.add(k); });

        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            visited++;
            for (Map.Entry<String, Set<String>> e : dependencies.entrySet()) {
                if (e.getValue().contains(node)) {
                    int newDegree = inDegree.merge(e.getKey(), -1, Integer::sum);
                    if (newDegree == 0) queue.add(e.getKey());
                }
            }
        }
        if (visited != tasks.size()) {
            throw new IllegalArgumentException("TaskGraph '" + name + "' 包含循环依赖");
        }
    }

    // ==================== Builder ====================

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name = "default-graph";
        private final Map<String, Task> tasks = new LinkedHashMap<>();
        private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        private final Map<String, Predicate<TaskContext>> conditions = new LinkedHashMap<>();
        private final Map<String, JoinStrategy> joinStrategies = new LinkedHashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addTask(Task task) {
            tasks.put(task.getName(), task);
            return this;
        }

        public Builder addTask(Task task, String... dependsOn) {
            tasks.put(task.getName(), task);
            if (dependsOn.length > 0) {
                dependencies.put(task.getName(), new LinkedHashSet<>(List.of(dependsOn)));
            }
            return this;
        }

        public Builder withCondition(String taskName, Predicate<TaskContext> condition) {
            conditions.put(taskName, condition);
            return this;
        }

        public Builder withJoinStrategy(String taskName, JoinStrategy strategy) {
            joinStrategies.put(taskName, strategy);
            return this;
        }

        public TaskGraph build() {
            return new TaskGraph(this);
        }
    }
}
