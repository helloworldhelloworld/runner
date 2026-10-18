package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;

/** Native orchestration control, bound to the creating task scope. */
final class StopSubagentTool implements Tool {
    private final SubagentRuntime runtime;
    private final TaskScope ownerScope;

    StopSubagentTool(SubagentRuntime runtime, TaskScope ownerScope) {
        this.runtime = runtime;
        this.ownerScope = ownerScope;
    }

    public RiskLevel riskLevel() { return RiskLevel.WRITE; }
    public String getName() { return "stop_subagent"; }
    public String getDescription() { return "Stop one of your direct child tasks and its descendants."; }
    public ToolSchema getSchema() {
        return new ToolSchema(Map.of("type", "object", "properties",
                Map.of("runId", Map.of("type", "string")), "required", List.of("runId")));
    }

    public ToolResult execute(Map<String, Object> args) {
        if (!(args.get("runId") instanceof String runId) || runId.isBlank()) {
            return ToolResult.error("runId required");
        }
        return runtime.stopOwned(runId, ownerScope)
                ? ToolResult.success("Stop requested for sub-agent: " + runId)
                : ToolResult.error("stop_subagent", "Not found");
    }
}
