package com.lightweightai.kernel.task;

import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务句柄 (Harness Engineering - Entropy Management)
 */
public class ScheduledTaskHandle {

    private final String taskName;
    private final ScheduledFuture<?> future;

    public ScheduledTaskHandle(String taskName, ScheduledFuture<?> future) {
        this.taskName = taskName;
        this.future = future;
    }

    public String getTaskName() { return taskName; }

    public boolean isActive() {
        return !future.isCancelled() && !future.isDone();
    }

    public boolean cancel() {
        return future.cancel(false);
    }

    public boolean cancelNow() {
        return future.cancel(true);
    }
}
