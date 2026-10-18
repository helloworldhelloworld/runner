package com.lightweightai.kernel.task;

import java.util.Optional;
import java.util.List;

public interface TaskStore {
    DurableTask create(String principalId, String idempotencyKey, String goal);
    Optional<DurableTask> get(String taskId);
    Optional<DurableTask> findByIdempotency(String principalId, String idempotencyKey);
    List<DurableTask> list();
    DurableTask compareAndSet(DurableTask expected, DurableTask replacement);
}
