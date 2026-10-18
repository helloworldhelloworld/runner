package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.core.ToolExecutionContext;

import java.util.Map;
import java.util.Objects;

/**
 * Subagent spawn 请求参数
 */
public class SpawnRequest {

    private final String parentSessionKey;
    private final String task;
    private final String agentId;
    private final String modelOverride;
    private final Map<String, Object> metadata;
    private final ToolExecutionContext executionContext;
    private final ToolPolicy inheritedPolicy;
    private final Integer remainingSpawnDepth;
    private final SubagentRun parentRun;
    private final TaskScope ownerScope;

    private SpawnRequest(Builder builder) {
        this.parentSessionKey = Objects.requireNonNull(builder.parentSessionKey, "parentSessionKey required");
        this.task = Objects.requireNonNull(builder.task, "task required");
        this.agentId = builder.agentId;
        this.modelOverride = builder.modelOverride;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.executionContext = builder.executionContext;
        this.inheritedPolicy = builder.inheritedPolicy;
        this.remainingSpawnDepth = builder.remainingSpawnDepth;
        this.parentRun = builder.parentRun;
        this.ownerScope = builder.ownerScope;
    }

    public String getParentSessionKey() { return parentSessionKey; }
    public String getTask() { return task; }
    public String getAgentId() { return agentId; }
    public String getModelOverride() { return modelOverride; }
    public Map<String, Object> getMetadata() { return metadata; }
    ToolExecutionContext executionContext() { return executionContext; }
    ToolPolicy inheritedPolicy() { return inheritedPolicy; }
    Integer remainingSpawnDepth() { return remainingSpawnDepth; }
    SubagentRun parentRun() { return parentRun; }
    TaskScope ownerScope() { return ownerScope; }

    /**
     * 从 session key 解析当前嵌套深度
     * agent:<id>:main:<sid> → depth 0
     * agent:<id>:subagent:<uuid> → depth 1
     * agent:<id>:subagent:<uuid>:subagent:<uuid> → depth 2
     */
    public int currentDepth() {
        if (parentSessionKey == null) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = parentSessionKey.indexOf(":subagent:", idx)) != -1) {
            count++;
            idx += 10;
        }
        return count;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String parentSessionKey;
        private String task;
        private String agentId;
        private String modelOverride;
        private Map<String, Object> metadata;
        private ToolExecutionContext executionContext;
        private ToolPolicy inheritedPolicy;
        private Integer remainingSpawnDepth;
        private SubagentRun parentRun;
        private TaskScope ownerScope;

        Builder executionContext(ToolExecutionContext context) { this.executionContext = context; return this; }
        Builder inheritedPolicy(ToolPolicy policy) { this.inheritedPolicy = policy; return this; }
        Builder remainingSpawnDepth(int depth) { this.remainingSpawnDepth = depth; return this; }
        Builder parentRun(SubagentRun run) { this.parentRun = run; return this; }
        Builder ownerScope(TaskScope scope) { this.ownerScope = scope; return this; }

        public Builder parentSessionKey(String key) { this.parentSessionKey = key; return this; }
        public Builder task(String task) { this.task = task; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder modelOverride(String model) { this.modelOverride = model; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public SpawnRequest build() { return new SpawnRequest(this); }
    }
}
