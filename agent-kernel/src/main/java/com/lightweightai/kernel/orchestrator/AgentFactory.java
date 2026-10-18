package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.prompt.PromptSource;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.ScopedToolRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 按 AgentProfile 构建 AgentLoop 实例
 *
 * 职责：
 * 1. 根据 profile.modelOverride 选择 LLMProvider（当前用默认，后续接 LLMProviderFactory SPI）
 * 2. 创建 ScopedToolRegistry 实现 per-Agent 工具权限
 * 3. 构建独立的 AgentLoop 实例
 */
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    private final LLMProvider defaultProvider;
    private final MemoryProvider sharedMemory;
    private final ToolRegistry globalToolRegistry;
    private final PromptSource promptSource;
    private final List<AgentObserver> observers;

    public AgentFactory(LLMProvider defaultProvider, MemoryProvider sharedMemory,
                        ToolRegistry globalToolRegistry) {
        this(defaultProvider, sharedMemory, globalToolRegistry, null, List.of());
    }

    public AgentFactory(LLMProvider defaultProvider, MemoryProvider sharedMemory, ToolRegistry globalToolRegistry,
                        PromptSource promptSource, List<AgentObserver> observers) {
        this.promptSource = promptSource;
        this.observers = List.copyOf(observers);
        this.defaultProvider = defaultProvider;
        this.sharedMemory = sharedMemory;
        this.globalToolRegistry = globalToolRegistry;
    }

    public MemoryProvider getSharedMemory() {
        return sharedMemory;
    }

    /**
     * 按 Profile 创建新的 AgentLoop 实例
     */
    public AgentLoop create(AgentProfile profile) {
        return create(profile, List.of());
    }

    public AgentLoop create(AgentProfile profile, List<Tool> localTools) {
        return create(profile, localTools, profile.getToolPolicy());
    }

    AgentLoop create(AgentProfile profile, List<Tool> localTools, ToolPolicy effectivePolicy) {
        logger.debug("创建 AgentLoop: agentId={}, model={}", profile.getAgentId(), profile.getModelOverride());

        // 1. 选 LLM Provider（当前只支持 default，后续可通过 LLMProviderFactory SPI 按 modelOverride 选）
        LLMProvider provider = defaultProvider;

        // 2. 创建 scoped ToolRegistry
        ScopedToolRegistry scopedRegistry = new ScopedToolRegistry(globalToolRegistry, profile, effectivePolicy);

        localTools.forEach(scopedRegistry::registerLocal);

        // 3. 构建 AgentLoop（含默认 Context Compaction）
        AgentLoop.Builder builder = AgentLoop.builder()
                .llmProvider(provider)
                .agentId(profile.getAgentId())
                .promptSource(promptSource)
                .memoryProvider(sharedMemory)
                .toolRegistry(scopedRegistry)
                .maxToolIterations(profile.getMaxToolIterations())
                .llmOptions(com.lightweightai.kernel.llm.LLMOptions.builder()
                        .maxTokens(profile.getMaxTokens()).maxBudgetTokens(profile.getMaxBudgetTokens()).build())
                .contextCompactor(new com.lightweightai.kernel.context.MicroCompactor(2000));

        if (profile.getSystemPrompt() != null) {
            builder.systemPrompt(profile.getSystemPrompt());
        }

        observers.forEach(builder::addObserver);
        return builder.build();
    }
}
