package com.lightweightai.kernel.core;

/**
 * 轻量级取消信号
 *
 * 用于 ToolCallingLoop 每轮迭代前检查是否应优雅退出。
 * 与 InterruptibleRun.interrupt() 联动：打断时触发 cancel()，
 * ToolCallingLoop 在下一个检查点退出。
 *
 * volatile 保证跨线程可见性。
 */
public class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
