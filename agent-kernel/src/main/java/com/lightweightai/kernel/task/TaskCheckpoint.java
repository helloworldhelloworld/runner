package com.lightweightai.kernel.task;

import java.util.Map;

public record TaskCheckpoint(String stepId, String nextAction, Map<String, Object> data, long version) {
    public TaskCheckpoint {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
