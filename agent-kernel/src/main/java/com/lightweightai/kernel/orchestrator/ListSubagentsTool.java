package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * LLM 可调用：列出当前活跃的 subagent 状态
 */
public class ListSubagentsTool implements Tool {

    private final SubagentRuntime runtime;

    public ListSubagentsTool(SubagentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String getName() { return "list_subagents"; }

    @Override
    public String getDescription() {
        return "List all currently active sub-agents with their status, task, and duration.";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.empty();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        List<SubagentRun> runs = runtime.getActiveRuns();

        if (runs.isEmpty()) {
            return ToolResult.success("No active sub-agents.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Active sub-agents (").append(runs.size()).append("):\n\n");

        for (SubagentRun run : runs) {
            sb.append("- [").append(run.getRunId()).append("] ")
                    .append(run.getStatus())
                    .append(" | task: ").append(run.getRequest().getTask())
                    .append(" | duration: ").append(run.getDurationMs()).append("ms")
                    .append("\n");
        }

        return ToolResult.success(sb.toString().trim());
    }
}
