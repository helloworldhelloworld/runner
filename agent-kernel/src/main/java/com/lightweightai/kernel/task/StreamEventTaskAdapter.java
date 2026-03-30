package com.lightweightai.kernel.task;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;

import java.util.Map;

/**
 * StreamEvent 工厂 — 为任务生命周期产生事件
 *
 * 复用现有 EventType（TRACE / TOOL_PROGRESS），通过 "task:" 前缀约定区分。
 */
public final class StreamEventTaskAdapter {

    private StreamEventTaskAdapter() {}

    public static StreamEvent taskStart(String taskName) {
        return StreamEvent.trace("task.start", taskName);
    }

    public static StreamEvent taskProgress(String taskName, String message,
                                           double progress, double total) {
        return StreamEvent.toolProgress(
                ToolResultChunk.progress("task:" + taskName, message, progress, total));
    }

    public static StreamEvent taskResult(String taskName, TaskResult result) {
        return StreamEvent.trace("task.complete", taskName,
                Map.of("taskResult", result, "taskName", taskName));
    }

    public static StreamEvent taskError(String taskName, Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        return StreamEvent.trace("task.error", taskName + ": " + message,
                Map.of("taskName", taskName, "error", message));
    }
}
