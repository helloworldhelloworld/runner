package com.lightweightai.kernel.task.core;

import java.util.List;
import java.util.function.Predicate;

/**
 * Join 策略：在多个上游任务结果上判定下游任务是否应继续执行。
 *
 * <p>设计为函数式接口（{@link Predicate}），而非固定枚举。这样可表达 quorum、weighted、
 * time-budget 等扩展场景；预定义常量覆盖常用情况（PR #109 review §二.1）。</p>
 *
 * <h3>边界语义</h3>
 * <ul>
 *   <li>谓词收到的 {@code List<TaskResult>} 是<b>所有上游任务的最终结果</b>，
 *       SKIPPED 也算到达终态。</li>
 *   <li>谓词返回 {@code false} 时，下游任务被 SKIPPED（不抛错）。</li>
 *   <li>{@link #ALL_SUCCESS}：全部 SUCCESS（任意 ERROR/SKIPPED 都不放行）。</li>
 *   <li>{@link #ALL_COMPLETE}：全部到达终态即放行（含 ERROR/SKIPPED）。</li>
 *   <li>{@link #ANY_SUCCESS}：至少一个 SUCCESS。</li>
 *   <li>{@link #FIRST_COMPLETE}：上游为空也放行（适合 fan-in 起点）。</li>
 * </ul>
 */
@FunctionalInterface
public interface JoinStrategy extends Predicate<List<TaskResult>> {

    JoinStrategy ALL_SUCCESS = results ->
        !results.isEmpty() && results.stream().allMatch(TaskResult::isSuccess);

    JoinStrategy ALL_COMPLETE = results -> true;

    JoinStrategy ANY_SUCCESS = results ->
        results.stream().anyMatch(TaskResult::isSuccess);

    JoinStrategy FIRST_COMPLETE = results -> true;

    /**
     * Quorum：上游中至少 N 个 SUCCESS 即放行。
     *
     * @param n 最小成功数（≥1）
     */
    static JoinStrategy quorum(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("quorum n must be >= 1, got " + n);
        }
        return results -> results.stream().filter(TaskResult::isSuccess).count() >= n;
    }
}
