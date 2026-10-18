package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.ContextAwareTool;
import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 可调用的 subagent spawn 工具
 *
 * LLM 通过 tool calling 发起 spawn_subagent 调用，
 * SubagentRuntime 异步执行子 agent，立即返回 runId。
 */
public class SpawnSubagentTool implements ContextAwareTool {

    private final SubagentRuntime runtime;
    private final String parentSessionKey;
    private final Consumer<StreamEvent> announcer;
    private final ToolPolicy inheritedPolicy;
    private final int remainingDepth;
    private final SubagentRun parentRun;
    private final TaskScope ownerScope;

    public SpawnSubagentTool(SubagentRuntime runtime, String parentSessionKey,
                             Consumer<StreamEvent> announcer) {
        this(runtime, parentSessionKey, announcer, null, 0, null, null);
    }

    SpawnSubagentTool(SubagentRuntime runtime, String parentSessionKey, Consumer<StreamEvent> announcer,
                      ToolPolicy inheritedPolicy, int remainingDepth, SubagentRun parentRun, TaskScope ownerScope) {
        this.runtime = runtime;
        this.parentSessionKey = parentSessionKey;
        this.announcer = announcer;
        this.inheritedPolicy = inheritedPolicy;
        this.remainingDepth = remainingDepth;
        this.parentRun = parentRun;
        this.ownerScope = ownerScope;
    }

    @Override
    public String getName() { return "spawn_subagent"; }

    @Override
    public RiskLevel riskLevel() { return RiskLevel.SYSTEM; }

    @Override
    public String getDescription() {
        return "Spawn a sub-agent to handle a task asynchronously. Returns immediately with a run ID.";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "task", Map.of("type", "string", "description", "Task description for the sub-agent"),
                        "agentId", Map.of("type", "string", "description", "Optional: target agent ID"),
                        "model", Map.of("type", "string", "description", "Optional: model override")
                ),
                "required", java.util.List.of("task")
        ));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        return execute(args, null);
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String task = (String) args.get("task");
        String agentId = (String) args.get("agentId");
        String model = (String) args.get("model");

        try {
            if (inheritedPolicy != null && remainingDepth <= 0) {
                return ToolResult.error("Spawn depth limit reached");
            }
            SpawnRequest.Builder builder = SpawnRequest.builder()
                    .parentSessionKey(parentSessionKey)
                    .task(task)
                    .agentId(agentId)
                    .modelOverride(model);
            if (inheritedPolicy != null) {
                builder.inheritedPolicy(inheritedPolicy).remainingSpawnDepth(remainingDepth - 1)
                        .parentRun(parentRun).ownerScope(ownerScope)
                        .executionContext(context != null ? context : ToolExecutionContext.serverOnly());
            }
            SpawnRequest request = builder.build();

            String runId = runtime.spawn(request, announcer);
            return ToolResult.success("Subagent spawned successfully. Run ID: " + runId
                    + " (non-blocking, results will be announced when complete)");
        } catch (IllegalStateException e) {
            return ToolResult.error("spawn_subagent", "Failed to spawn subagent: " + e.getMessage());
        }
    }
}
