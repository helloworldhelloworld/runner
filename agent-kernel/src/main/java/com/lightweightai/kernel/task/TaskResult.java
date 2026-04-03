package com.lightweightai.kernel.task;

/**
 * 任务执行结果 (Harness Engineering - Entropy Management)
 */
public class TaskResult<T> {

    private final boolean success;
    private final T value;
    private final String errorMessage;
    private final boolean shouldRetry;

    private TaskResult(boolean success, T value, String errorMessage, boolean shouldRetry) {
        this.success = success;
        this.value = value;
        this.errorMessage = errorMessage;
        this.shouldRetry = shouldRetry;
    }

    public static <T> TaskResult<T> success(T value) {
        return new TaskResult<>(true, value, null, false);
    }

    public static <T> TaskResult<T> success() {
        return new TaskResult<>(true, null, null, false);
    }

    public static <T> TaskResult<T> failure(String errorMessage) {
        return new TaskResult<>(false, null, errorMessage, false);
    }

    public static <T> TaskResult<T> retry(String reason) {
        return new TaskResult<>(false, null, reason, true);
    }

    public boolean isSuccess() { return success; }
    public T getValue() { return value; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isShouldRetry() { return shouldRetry; }
}
