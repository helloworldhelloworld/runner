package com.lightweightai.kernel.task.config;

import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskResult;

import java.util.function.Predicate;

/**
 * 简易 guard DSL（YAML 用）。
 *
 * <p>支持表达式：</p>
 * <ul>
 *   <li>{@code status==SUCCESS} / {@code status==ERROR} / {@code status==SKIPPED}（任一上游结果）</li>
 *   <li>{@code attr:KEY} — context attribute KEY 非 null 即放行</li>
 *   <li>{@code !attr:KEY} — context attribute KEY 为 null 即放行</li>
 *   <li>{@code true} / {@code false}</li>
 * </ul>
 *
 * <p>解析失败抛 {@link IllegalArgumentException}（不静默回退到 always-true）。</p>
 */
public final class GuardExpression {

    private GuardExpression() {}

    public static Predicate<TaskContext> parse(String expr) {
        if (expr == null) {
            throw new IllegalArgumentException("guard expression must not be null");
        }
        String s = expr.trim();
        if (s.equals("true")) return ctx -> true;
        if (s.equals("false")) return ctx -> false;
        if (s.startsWith("status==")) {
            String want = s.substring("status==".length()).trim().toUpperCase();
            TaskResult.Status target;
            try {
                target = TaskResult.Status.valueOf(want);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown status: " + want);
            }
            return ctx -> ctx.getAllResults().values().stream()
                .anyMatch(r -> r.getStatus() == target);
        }
        if (s.startsWith("!attr:")) {
            String key = s.substring("!attr:".length()).trim();
            return ctx -> ctx.getAttribute(key) == null;
        }
        if (s.startsWith("attr:")) {
            String key = s.substring("attr:".length()).trim();
            return ctx -> ctx.getAttribute(key) != null;
        }
        throw new IllegalArgumentException("unsupported guard expression: '" + expr + "'");
    }
}
