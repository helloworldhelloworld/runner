package com.lightweightai.kernel.task;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Delivers durable task results and acknowledges only after the consumer succeeds. */
public final class TaskDeliveryDispatcher {
    private final TaskDeliveryStore store;
    private final Consumer<TaskDelivery> consumer;

    public TaskDeliveryDispatcher(TaskDeliveryStore store, Consumer<TaskDelivery> consumer) {
        this.store = Objects.requireNonNull(store);
        this.consumer = Objects.requireNonNull(consumer);
    }

    /** Attempts each currently pending delivery once; failed deliveries remain pending. */
    public int dispatchPending() {
        int delivered = 0;
        List<TaskDelivery> pending = store.pending();
        for (TaskDelivery delivery : pending) {
            try {
                consumer.accept(delivery);
                store.markDelivered(delivery);
                delivered++;
            } catch (RuntimeException ignored) {
                // Preserve the durable item for a later retry.
            }
        }
        return delivered;
    }
}
