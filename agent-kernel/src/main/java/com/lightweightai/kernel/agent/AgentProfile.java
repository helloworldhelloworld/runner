package com.lightweightai.kernel.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 身份与配置
 *
 * 每个 Agent 是一个配置实体（不是代码类），描述该 Agent 的：
 * - 身份标识 (agentId)
 * - 系统提示词 (systemPrompt)
 * - 模型选择 (modelOverride)
 * - 工具权限 (toolAllowList / toolDenyList)
 * - Subagent 嵌套深度 (maxSpawnDepth)
 *
 * 不绑定具体的 LLMProvider / MemoryProvider 实例，这些在运行时由 Orchestrator 注入。
 */
public class AgentProfile {

    private final String agentId;
    private final String displayName;
    private final String systemPrompt;
    private final String modelOverride;
    private final Set<String> toolAllowList;
    private final Set<String> toolDenyList;
    private final int maxToolIterations;
    private final int maxSpawnDepth;
    private final Map<String, Object> metadata;

    private AgentProfile(Builder builder) {
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId is required");
        this.displayName = builder.displayName;
        this.systemPrompt = builder.systemPrompt;
        this.modelOverride = builder.modelOverride;
        this.toolAllowList = builder.toolAllowList;
        this.toolDenyList = builder.toolDenyList;
        this.maxToolIterations = builder.maxToolIterations;
        this.maxSpawnDepth = builder.maxSpawnDepth;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public String getAgentId() { return agentId; }
    public String getDisplayName() { return displayName; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getModelOverride() { return modelOverride; }
    public Set<String> getToolAllowList() { return toolAllowList; }
    public Set<String> getToolDenyList() { return toolDenyList; }
    public int getMaxToolIterations() { return maxToolIterations; }
    public int getMaxSpawnDepth() { return maxSpawnDepth; }
    public Map<String, Object> getMetadata() { return metadata; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String agentId;
        private String displayName;
        private String systemPrompt;
        private String modelOverride;
        private Set<String> toolAllowList;
        private Set<String> toolDenyList;
        private int maxToolIterations = 10;
        private int maxSpawnDepth = 0;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder modelOverride(String modelOverride) { this.modelOverride = modelOverride; return this; }
        public Builder toolAllowList(Set<String> toolAllowList) { this.toolAllowList = toolAllowList; return this; }
        public Builder toolDenyList(Set<String> toolDenyList) { this.toolDenyList = toolDenyList; return this; }
        public Builder maxToolIterations(int maxToolIterations) { this.maxToolIterations = maxToolIterations; return this; }
        public Builder maxSpawnDepth(int maxSpawnDepth) { this.maxSpawnDepth = maxSpawnDepth; return this; }
        public Builder metadata(String key, Object value) { this.metadata.put(key, value); return this; }

        public AgentProfile build() { return new AgentProfile(this); }
    }
}
