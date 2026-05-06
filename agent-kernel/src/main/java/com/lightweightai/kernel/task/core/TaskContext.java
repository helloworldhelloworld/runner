package com.lightweightai.kernel.task.core;

import com.lightweightai.kernel.core.CancellationToken;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 编排执行上下文。
 *
 * <h3>线程安全模型</h3>
 * <ul>
 *   <li>{@link #attributes} / {@link #results} 都是 {@link ConcurrentHashMap}：
 *       并发 put/get 操作本身线程安全。</li>
 *   <li>{@link #getAllResults()} / {@link #getAttributes()} 返回 <b>真快照</b>
 *       （{@link Map#copyOf}），调用方拿到的不是实时视图，调用后再发生的写入不会反映。</li>
 *   <li><b>跨任务 happens-before 契约</b>：DAG 编排器（{@code TaskGraph}）保证
 *       <i>下游任务 doExecute 被调用之前，所有上游任务的</i> {@link TaskContext#putResult}
 *       <i>必定已对当前线程可见</i>。这是通过 reactor 的 onNext-onComplete 顺序
 *       与 ConcurrentHashMap 的 happens-before 保证联合实现的，调用方不需要再加锁。
 *       手动构造 TaskContext 跑非 DAG 路径时不享受此保证。</li>
 * </ul>
 *
 * <h3>取消语义</h3>
 * <p>{@link #cancellationToken} 由 InterruptibleRun → AgentLoop 链路统一传入。
 * 长任务实现应在适当检查点调用 {@link CancellationToken#isCancelled()}。</p>
 */
public final class TaskContext {

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, TaskResult> results = new ConcurrentHashMap<>();
    private final CancellationToken cancellationToken;

    public TaskContext() {
        this(new CancellationToken());
    }

    public TaskContext(CancellationToken cancellationToken) {
        this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken");
    }

    // ==================== Attributes ====================

    public void setAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 类型安全获取属性。
     *
     * <p><b>注意</b>：对泛型容器（{@code List<X>}, {@code Map<K,V>}）只能验证外层 raw 类型；
     * 容器内元素类型不在此处校验，调用方需自行确认。</p>
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object v = attributes.get(key);
        if (v == null) return null;
        if (!type.isInstance(v)) {
            throw new ClassCastException("attribute " + key + " is " + v.getClass().getName()
                + ", not " + type.getName());
        }
        return (T) v;
    }

    /** 真快照，不是实时视图。 */
    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }

    // ==================== Results ====================

    /** 由编排器在任务 onNext 收到 TaskResult 时调用。 */
    public void putResult(String taskName, TaskResult result) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(result, "result");
        results.put(taskName, result);
    }

    public TaskResult getResult(String taskName) {
        return results.get(taskName);
    }

    /** 真快照，不是实时视图。 */
    public Map<String, TaskResult> getAllResults() {
        return Map.copyOf(results);
    }

    public boolean hasResult(String taskName) {
        return results.containsKey(taskName);
    }

    // ==================== Cancellation ====================

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public boolean isCancelled() {
        return cancellationToken.isCancelled();
    }

    /** 用于测试 / 调试场景：把当前快照塞进新 context，避免共享可变状态。 */
    public TaskContext snapshot() {
        TaskContext copy = new TaskContext(cancellationToken);
        copy.attributes.putAll(this.attributes);
        copy.results.putAll(this.results);
        return copy;
    }

    @Override
    public String toString() {
        return "TaskContext{attributes=" + new LinkedHashMap<>(attributes)
            + ", results=" + results.keySet() + "}";
    }
}
