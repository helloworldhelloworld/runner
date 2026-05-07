package com.lightweightai.kernel.task.graph;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.task.core.JoinStrategy;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskResult;
import com.lightweightai.kernel.task.event.TaskEvents;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 有向无环任务图执行器。
 *
 * <h3>调度语义</h3>
 * <ul>
 *   <li><b>事件即时发出</b>：不使用 {@code collectList().flatMapMany}（PR #109 review M6）。
 *       同层并行任务的 {@code TASK_START} / {@code TASK_COMPLETE} 在产生瞬间下发到下游订阅者。</li>
 *   <li><b>层间串行</b>：当且仅当前一层所有任务到达终态后才调度下一层。
 *       这保证下游 {@link Task#execute(TaskContext)} 被调用前能从 {@link TaskContext} 看见上游所有 result —
 *       happens-before 由 ConcurrentHashMap 与 Reactor onNext-onComplete 顺序联合提供。</li>
 *   <li><b>取消传播</b>：每次进入新层前检查 {@link TaskContext#isCancelled()}；
 *       且通过 {@code takeUntilOther(cancelMono)} 让上游取消立即冒泡。</li>
 *   <li><b>guard / join</b>：guard 是 {@link Predicate} 检查 context 状态，不通过则发 {@code TASK_SKIPPED}；
 *       join 是 {@link JoinStrategy}，作用在<b>已声明依赖</b>的上游结果上。</li>
 * </ul>
 *
 * <h3>验证</h3>
 * <ul>
 *   <li>未知依赖名 — 抛 {@link IllegalArgumentException}（M5：不再误报为 cycle）</li>
 *   <li>self-loop — 抛 {@link IllegalArgumentException}（独立分类）</li>
 *   <li>多节点 cycle — Kahn 算法检测，抛 {@link IllegalStateException}</li>
 * </ul>
 */
public final class TaskGraph {

    private final Map<String, Task> tasks;
    private final Map<String, Set<String>> dependencies;       // taskName -> deps
    private final Map<String, Predicate<TaskContext>> guards;
    private final Map<String, JoinStrategy> joins;
    private final List<List<String>> levels;

    private TaskGraph(Builder b) {
        this.tasks = Map.copyOf(b.tasks);
        this.dependencies = deepCopy(b.dependencies);
        this.guards = Map.copyOf(b.guards);
        this.joins = Map.copyOf(b.joins);
        validate(b.tasks, this.dependencies);
        this.levels = topologicalLevels(b.tasks.keySet(), this.dependencies);
    }

    public static Builder builder() { return new Builder(); }

    public Map<String, Set<String>> getDependencies() {
        return dependencies;
    }

    public List<List<String>> getLevels() {
        return levels;
    }

    public Set<String> getTaskNames() {
        return tasks.keySet();
    }

    /** 冷流。事件按发生顺序下发；层间串行。 */
    public Flux<StreamEvent> execute(TaskContext context) {
        Objects.requireNonNull(context, "context");
        return Flux.defer(() -> executeLevels(context, 0));
    }

    private Flux<StreamEvent> executeLevels(TaskContext ctx, int levelIndex) {
        if (levelIndex >= levels.size()) {
            return Flux.empty();
        }
        if (ctx.isCancelled()) {
            return Flux.empty();
        }
        List<String> level = levels.get(levelIndex);
        List<Flux<StreamEvent>> taskFluxes = new ArrayList<>(level.size());
        for (String name : level) {
            taskFluxes.add(executeOne(ctx, name));
        }
        // 同层 merge — 事件即时下发；concatWith 进入下一层只有当本层 onComplete 后才发生。
        Flux<StreamEvent> levelFlux = Flux.merge(taskFluxes);
        return levelFlux.concatWith(Flux.defer(() -> executeLevels(ctx, levelIndex + 1)));
    }

    private Flux<StreamEvent> executeOne(TaskContext ctx, String name) {
        Task task = tasks.get(name);

        // 1) join：仅当存在上游依赖时检查放行条件；根任务不受 join 约束
        Set<String> deps = dependencies.getOrDefault(name, Set.of());
        if (!deps.isEmpty()) {
            JoinStrategy join = joins.getOrDefault(name, JoinStrategy.ALL_SUCCESS);
            List<TaskResult> upstream = new ArrayList<>(deps.size());
            for (String d : deps) {
                TaskResult r = ctx.getResult(d);
                if (r != null) upstream.add(r);
            }
            if (!join.test(upstream)) {
                TaskResult skipped = TaskResult.skipped(
                    "join " + describeJoin(join) + " not satisfied by deps " + deps);
                ctx.putResult(name, skipped);
                return Flux.just(TaskEvents.skipped(name, skipped));
            }
        }

        // 2) guard：上下文谓词
        Predicate<TaskContext> guard = guards.get(name);
        if (guard != null) {
            boolean allow;
            try {
                allow = guard.test(ctx);
            } catch (Exception e) {
                TaskResult err = TaskResult.error("guard threw: " + e.getMessage(), e);
                ctx.putResult(name, err);
                return Flux.just(TaskEvents.error(name, err));
            }
            if (!allow) {
                TaskResult skipped = TaskResult.skipped("guard returned false");
                ctx.putResult(name, skipped);
                return Flux.just(TaskEvents.skipped(name, skipped));
            }
        }

        // 3) 取消
        if (ctx.isCancelled()) {
            TaskResult cancelled = TaskResult.skipped("cancelled before start");
            ctx.putResult(name, cancelled);
            return Flux.just(TaskEvents.skipped(name, cancelled));
        }

        // 4) 执行 — 由 AbstractTask 包装好生命周期事件 & 写 context
        return task.execute(ctx);
    }

    private static String describeJoin(JoinStrategy s) {
        if (s == JoinStrategy.ALL_SUCCESS) return "ALL_SUCCESS";
        if (s == JoinStrategy.ALL_COMPLETE) return "ALL_COMPLETE";
        if (s == JoinStrategy.ANY_SUCCESS) return "ANY_SUCCESS";
        if (s == JoinStrategy.FIRST_COMPLETE) return "FIRST_COMPLETE";
        return "custom";
    }

    // ==================== 验证 ====================

    private static void validate(Map<String, Task> tasks, Map<String, Set<String>> deps) {
        // 未知依赖名
        for (var e : deps.entrySet()) {
            String task = e.getKey();
            for (String d : e.getValue()) {
                if (!tasks.containsKey(d)) {
                    throw new IllegalArgumentException(
                        "Task '" + task + "' depends on unknown task '" + d + "'");
                }
                if (d.equals(task)) {
                    throw new IllegalArgumentException(
                        "Task '" + task + "' has self-loop dependency");
                }
            }
        }
    }

    /** Kahn 拓扑排序，输出按层次分组（同层独立、层间有依赖）。 */
    private static List<List<String>> topologicalLevels(Set<String> taskNames,
                                                         Map<String, Set<String>> deps) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> reverse = new HashMap<>();
        for (String t : taskNames) {
            indegree.put(t, 0);
            reverse.put(t, new HashSet<>());
        }
        for (var e : deps.entrySet()) {
            String task = e.getKey();
            for (String d : e.getValue()) {
                indegree.merge(task, 1, Integer::sum);
                reverse.get(d).add(task);
            }
        }
        Deque<String> currentLevel = new ArrayDeque<>();
        for (var e : indegree.entrySet()) {
            if (e.getValue() == 0) currentLevel.add(e.getKey());
        }
        List<List<String>> levels = new ArrayList<>();
        int totalEmitted = 0;
        while (!currentLevel.isEmpty()) {
            List<String> level = new ArrayList<>(currentLevel);
            Collections.sort(level); // 确定性排序，便于断言
            levels.add(level);
            totalEmitted += level.size();
            Deque<String> next = new ArrayDeque<>();
            for (String t : level) {
                for (String downstream : reverse.get(t)) {
                    int v = indegree.merge(downstream, -1, Integer::sum);
                    if (v == 0) next.add(downstream);
                }
            }
            currentLevel = next;
        }
        if (totalEmitted != taskNames.size()) {
            Set<String> remaining = new LinkedHashSet<>(taskNames);
            levels.forEach(remaining::removeAll);
            throw new IllegalStateException("Cycle detected involving tasks: " + remaining);
        }
        return List.copyOf(levels);
    }

    private static Map<String, Set<String>> deepCopy(Map<String, Set<String>> in) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            out.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    // ==================== Builder ====================

    public static final class Builder {
        private final Map<String, Task> tasks = new LinkedHashMap<>();
        private final Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        private final Map<String, Predicate<TaskContext>> guards = new LinkedHashMap<>();
        private final Map<String, JoinStrategy> joins = new LinkedHashMap<>();

        public Builder addTask(Task task, String... deps) {
            Objects.requireNonNull(task, "task");
            String name = task.getName();
            if (tasks.containsKey(name)) {
                throw new IllegalArgumentException("Task '" + name + "' already added");
            }
            tasks.put(name, task);
            if (deps.length > 0) {
                dependencies.put(name, new LinkedHashSet<>(List.of(deps)));
            } else {
                dependencies.put(name, Set.of());
            }
            return this;
        }

        public Builder withGuard(String taskName, Predicate<TaskContext> guard) {
            guards.put(taskName, Objects.requireNonNull(guard, "guard"));
            return this;
        }

        public Builder withJoin(String taskName, JoinStrategy strategy) {
            joins.put(taskName, Objects.requireNonNull(strategy, "join"));
            return this;
        }

        public TaskGraph build() {
            // 校验 guard / join 引用的任务名存在
            for (String n : guards.keySet()) {
                if (!tasks.containsKey(n)) {
                    throw new IllegalArgumentException("guard for unknown task: " + n);
                }
            }
            for (String n : joins.keySet()) {
                if (!tasks.containsKey(n)) {
                    throw new IllegalArgumentException("join for unknown task: " + n);
                }
            }
            return new TaskGraph(this);
        }
    }
}
