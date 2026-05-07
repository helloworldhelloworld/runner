package com.lightweightai.kernel.task.event;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.task.core.TaskResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 强类型 Task 事件工厂。
 *
 * <p>所有 Task 生命周期事件都通过 {@link StreamEvent.EventType#TASK_START} /
 * {@code TASK_COMPLETE} / {@code TASK_ERROR} / {@code TASK_SKIPPED} enum 路径走，
 * <b>禁止</b>沿用 {@code StreamEvent.trace("task.start", ...)} 字符串约定（PR #109 review B2）。</p>
 *
 * <p>Payload 字段约定：</p>
 * <ul>
 *   <li>{@code taskName} — String</li>
 *   <li>{@code taskResult} — {@link TaskResult}（COMPLETE / ERROR / SKIPPED 必带）</li>
 *   <li>{@code timestamp} — Long(ms)</li>
 * </ul>
 */
public final class TaskEvents {

    private TaskEvents() {}

    public static StreamEvent start(String taskName) {
        return StreamEvent.taskStart(taskName, payload(taskName, null));
    }

    public static StreamEvent complete(String taskName, TaskResult result) {
        return StreamEvent.taskComplete(taskName, payload(taskName, result));
    }

    public static StreamEvent error(String taskName, TaskResult result) {
        return StreamEvent.taskError(taskName, payload(taskName, result));
    }

    public static StreamEvent skipped(String taskName, TaskResult result) {
        return StreamEvent.taskSkipped(taskName, payload(taskName, result));
    }

    private static Map<String, Object> payload(String taskName, TaskResult result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskName", taskName);
        if (result != null) {
            m.put("taskResult", result);
        }
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }
}
