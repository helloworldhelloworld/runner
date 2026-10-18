package com.lightweightai.kernel.orchestrator;

import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the single active execution slot for each logical session.
 * Transport adapters must not maintain a second execution registry.
 */
final class ActiveRunCoordinator {
    private final Map<String, InterruptibleRun> runs = new ConcurrentHashMap<>();

    InterruptibleRun get(String sessionKey) { return runs.get(sessionKey); }

    InterruptibleRun put(String sessionKey, InterruptibleRun run) { return runs.put(sessionKey, run); }

    boolean remove(String sessionKey, InterruptibleRun run) { return runs.remove(sessionKey, run); }

    void removeIf(Predicate<InterruptibleRun> predicate) {
        runs.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    }

    void cancelAll(CancellationCause cause) {
        runs.values().forEach(run -> run.cancel(cause));
        runs.clear();
    }
}
