package com.lightweightai.kernel.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runtime-owned task ancestry; never parsed from client-supplied session text. */
record TaskScope(String rootSessionKey, List<String> childPath) {
    TaskScope {
        Objects.requireNonNull(rootSessionKey, "rootSessionKey required");
        childPath = List.copyOf(childPath);
    }

    static TaskScope root(String sessionKey) {
        return new TaskScope(sessionKey, List.of());
    }

    TaskScope child(String runId) {
        List<String> path = new ArrayList<>(childPath);
        path.add(Objects.requireNonNull(runId, "runId required"));
        return new TaskScope(rootSessionKey, path);
    }
}
