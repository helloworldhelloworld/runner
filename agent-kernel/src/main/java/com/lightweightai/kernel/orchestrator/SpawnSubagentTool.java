package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.Tool;
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
public class SpawnSubagentTool implements Tool {

    private final SubagentRuntime runtime;
    private final String parentSessionKey;
    private final Consumer<StreamEvent> announcer;

    public SpawnSubagentTool(SubagentRuntime runtime, String parentSessionKey,
                             Consumer<StreamEvent> announcer) {
        this.runtime = runtime;
        this.parentSessionKey = parentSessionKey;
        this.announcer = announcer;
    }

    @Override
    public String getName() { return "spawn_subagent"; }

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
        String task = (String) args.get("task");
        String agentId = (String) args.get("agentId");
        String model = (String) args.get("model");

        try {
            SpawnRequest request = SpawnRequest.builder()
                    .parentSessionKey(parentSessionKey)
                    .task(task)
                    .agentId(agentId)
                    .modelOverride(model)
                    .build();

            String runId = runtime.spawn(request, announcer);
            return ToolResult.success("Subagent spawned successfully. Run ID: " + runId
                    + " (non-blocking, results will be announced when complete)");
        } catch (IllegalStateException e) {
            return ToolResult.error("spawn_subagent", "Failed to spawn subagent: " + e.getMessage());
        }
    }
}
