package com.lightweightai.kernel.task;

/**
 * 任务状态 (Harness Engineering - Entropy Management)
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    RETRY
}
