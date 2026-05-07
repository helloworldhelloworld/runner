package com.lightweightai.kernel.task.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Task 执行结果。
 *
 * <p>不可变值对象。{@link #data} 在构造时做防御性拷贝并 unmodifiable 化，
 * 保证下游消费者拿到的快照不会被生产方再次修改。</p>
 *
 * <p>错误结果通过 {@link #error(String, Throwable)} 构造，会保留：
 * 异常类名（{@link #errorType}）、完整 message、栈顶若干帧（{@link #errorStackHead}）。
 * 这是 PR #109 review 中明确指出的「错误保真度」要求。</p>
 */
public final class TaskResult {

    public enum Status { SUCCESS, ERROR, SKIPPED }

    private static final int STACK_HEAD_LINES = 8;

    private final Status status;
    private final String content;
    private final Map<String, Object> data;
    private final String errorType;
    private final String errorStackHead;

    private TaskResult(Status status, String content, Map<String, Object> data,
                       String errorType, String errorStackHead) {
        this.status = Objects.requireNonNull(status, "status");
        this.content = content == null ? "" : content;
        this.data = data == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.errorType = errorType;
        this.errorStackHead = errorStackHead;
    }

    public static TaskResult success(String content) {
        return new TaskResult(Status.SUCCESS, content, null, null, null);
    }

    public static TaskResult success(String content, Map<String, Object> data) {
        return new TaskResult(Status.SUCCESS, content, data, null, null);
    }

    public static TaskResult skipped(String reason) {
        return new TaskResult(Status.SKIPPED, reason, null, null, null);
    }

    public static TaskResult error(String message) {
        return new TaskResult(Status.ERROR, message, null, null, null);
    }

    public static TaskResult error(String message, Throwable cause) {
        if (cause == null) {
            return error(message);
        }
        String type = cause.getClass().getName();
        StringBuilder head = new StringBuilder();
        StackTraceElement[] frames = cause.getStackTrace();
        int n = Math.min(STACK_HEAD_LINES, frames.length);
        for (int i = 0; i < n; i++) {
            head.append("  at ").append(frames[i]).append('\n');
        }
        if (frames.length > n) {
            head.append("  ... ").append(frames.length - n).append(" more\n");
        }
        return new TaskResult(Status.ERROR, message, null, type, head.toString());
    }

    public Status getStatus() { return status; }
    public String getContent() { return content; }
    public Map<String, Object> getData() { return data; }
    public String getErrorType() { return errorType; }
    public String getErrorStackHead() { return errorStackHead; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
    public boolean isSkipped() { return status == Status.SKIPPED; }

    @Override
    public String toString() {
        return "TaskResult{" + status + ", content='" + content + "'"
            + (errorType != null ? ", errorType=" + errorType : "")
            + (data.isEmpty() ? "" : ", data=" + data) + "}";
    }
}
