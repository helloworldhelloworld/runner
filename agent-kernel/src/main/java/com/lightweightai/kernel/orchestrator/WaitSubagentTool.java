package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LLM 可调用：等待一个或多个 subagent 完成并返回聚合结果
 *
 * 调用方式：
 *   wait_subagent(runIds="abc123,def456", timeoutSeconds=30)
 *
 * 行为：
 *   - 阻塞等待所有指定 runId 完成（或超时）
 *   - 返回每个 subagent 的结果文本
 *   - 超时未完成的标记为 timed out
 */
public class WaitSubagentTool implements Tool {

    private final SubagentRuntime runtime;
    private final TaskScope ownerScope;

    public WaitSubagentTool(SubagentRuntime runtime) {
        this(runtime, null);
    }

    WaitSubagentTool(SubagentRuntime runtime, TaskScope ownerScope) {
        this.runtime = runtime;
        this.ownerScope = ownerScope;
    }

    @Override
    public String getName() { return "wait_subagent"; }

    @Override
    public String getDescription() {
        return "Wait for one or more " + (ownerScope == null ? "" : "direct child ") + "sub-agents to complete and return their results. "
                + "Pass comma-separated run IDs.";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "runIds", Map.of("type", "string",
                                "description", "Comma-separated run IDs to wait for"),
                        "timeoutSeconds", Map.of("type", "integer",
                                "description", "Max seconds to wait (default: 60)")
                ),
                "required", List.of("runIds")
        ));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String runIdsStr = (String) args.get("runIds");
        if (runIdsStr == null || runIdsStr.isBlank()) return ToolResult.error("runIds required");
        int timeout = args.containsKey("timeoutSeconds")
                ? ((Number) args.get("timeoutSeconds")).intValue()
                : 60;

        String[] runIds = runIdsStr.split(",");
        // Authorize the whole batch before waiting or returning any task details.
        if (ownerScope != null) {
            for (String runId : runIds) {
                if (runtime.getOwnedRun(runId.trim(), ownerScope) == null) {
                    return ToolResult.error("wait_subagent", "[" + runId.trim() + "] Not found");
                }
            }
        }
        StringBuilder result = new StringBuilder();
        boolean allCompleted = true;

        for (String runId : runIds) {
            runId = runId.trim();
            try {
                boolean completed = runtime.waitForCompletion(runId, timeout, TimeUnit.SECONDS);
                SubagentRun run = runtime.getRunOrCompleted(runId);

                if (run == null) {
                    result.append("[").append(runId).append("] Not found\n\n");
                    allCompleted = false;
                } else if (!completed || run.isRunning()) {
                    result.append("[").append(runId).append("] timed out after ")
                            .append(timeout).append("s\n\n");
                    allCompleted = false;
                } else {
                    switch (run.getStatus()) {
                        case COMPLETED -> result.append("[").append(runId).append("] ")
                                .append(run.getResult() != null ? run.getResult().getText() : "")
                                .append(" (").append(run.getDurationMs()).append("ms)\n\n");
                        case FAILED -> result.append("[").append(runId).append("] FAILED: ")
                                .append(run.getErrorMessage()).append("\n\n");
                        case CANCELLED -> result.append("[").append(runId).append("] CANCELLED: ")
                                .append(run.getErrorMessage()).append("\n\n");
                        default -> result.append("[").append(runId).append("] ")
                                .append(run.getStatus()).append("\n\n");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.append("[").append(runId).append("] Interrupted\n\n");
                allCompleted = false;
                break;
            }
        }

        String content = result.toString().trim();
        return allCompleted
                ? ToolResult.success(content)
                : ToolResult.error("wait_subagent", content);
    }
}
