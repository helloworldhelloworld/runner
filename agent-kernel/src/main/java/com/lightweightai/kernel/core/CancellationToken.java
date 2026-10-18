package com.lightweightai.kernel.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final Logger logger = LoggerFactory.getLogger(CancellationToken.class);
    private volatile boolean cancelled;
    private final List<Listener> listeners = new ArrayList<>();

    public void cancel() {
        List<Listener> pending;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            pending = List.copyOf(listeners);
            listeners.clear();
        }
        pending.forEach(Listener::invoke);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Register cleanup, or invoke it immediately if cancellation has already happened. */
    public Registration onCancel(Runnable callback) {
        Listener listener = new Listener(Objects.requireNonNull(callback, "callback required"));
        synchronized (this) {
            if (!cancelled) {
                listeners.add(listener);
                return listener;
            }
        }
        listener.invoke();
        return listener;
    }

    public interface Registration extends AutoCloseable {
        @Override void close();
    }

    private final class Listener implements Registration {
        private final AtomicReference<Runnable> callback;
        private Listener(Runnable callback) { this.callback = new AtomicReference<>(callback); }

        private void invoke() {
            Runnable action = callback.getAndSet(null);
            if (action == null) return;
            try {
                action.run();
            } catch (RuntimeException e) {
                logger.warn("Cancellation listener failed", e);
            }
        }

        public void close() {
            callback.set(null);
            synchronized (CancellationToken.this) { listeners.remove(this); }
        }
    }
}
