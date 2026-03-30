package com.lightweightai.kernel.task;

import java.util.Collections;
import java.util.Map;

/**
 * 任务执行结果
 *
 * 携带文本内容（供 LLM 消费或日志）和结构化数据（供下游任务编程式消费）。
 */
public class TaskResult {

    public enum Status { SUCCESS, ERROR, SKIPPED }

    private final Status status;
    private final String content;
    private final Map<String, Object> data;
    private final Throwable error;

    private TaskResult(Status status, String content,
                       Map<String, Object> data, Throwable error) {
        this.status = status;
        this.content = content != null ? content : "";
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        this.error = error;
    }

    public static TaskResult success(String content) {
        return new TaskResult(Status.SUCCESS, content, null, null);
    }

    public static TaskResult success(String content, Map<String, Object> data) {
        return new TaskResult(Status.SUCCESS, content, data, null);
    }

    public static TaskResult error(String message) {
        return new TaskResult(Status.ERROR, message, null, new RuntimeException(message));
    }

    public static TaskResult error(Throwable exception) {
        String msg = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return new TaskResult(Status.ERROR, msg, null, exception);
    }

    public static TaskResult skipped(String reason) {
        return new TaskResult(Status.SKIPPED, reason, null, null);
    }

    public Status getStatus() { return status; }
    public String getContent() { return content; }
    public Map<String, Object> getData() { return data; }
    public Throwable getError() { return error; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
    public boolean isSkipped() { return status == Status.SKIPPED; }

    @Override
    public String toString() {
        return "TaskResult{status=" + status + ", content='" +
                (content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }
}
