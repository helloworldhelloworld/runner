package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.orchestrator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Multi-Agent Orchestrator 装配
 *
 * 通过 app.orchestrator.enabled=true 启用。
 * 启用后 Orchestrator 成为 primary ChatHandler，替代直连 GatewayService。
 *
 * 默认注册两个 Agent:
 * - "default": 使用全部工具，maxSpawnDepth=1（可 spawn 子 agent）
 * - "worker": 使用全部工具，maxSpawnDepth=0（不能再 spawn）
 */
@Configuration
public class OrchestratorConfig {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorConfig.class);

    @Value("${app.orchestrator.max-concurrent-subagents:5}")
    private int maxConcurrentSubagents;

    @Bean
    public AgentRegistry agentRegistry() {
        AgentRegistry registry = new AgentRegistry();

        registry.register(AgentProfile.builder()
                .agentId("default")
                .displayName("默认助手")
                .maxSpawnDepth(1)
                .maxToolIterations(10)
                .build());

        registry.register(AgentProfile.builder()
                .agentId("worker")
                .displayName("工作 Agent")
                .systemPrompt("你是一个专注执行具体任务的工作助手。直接完成任务，不要 spawn 子 agent。")
                .maxSpawnDepth(0)
                .maxToolIterations(10)
                .build());

        registry.setDefault("default");
        logger.info("AgentRegistry: {} agents registered, default={}", registry.size(), "default");
        return registry;
    }

    @Bean
    public AgentFactory agentFactory(LLMProvider llmProvider,
                                     MemoryProvider memoryProvider,
                                     ToolRegistry toolRegistry) {
        return new AgentFactory(llmProvider, memoryProvider, toolRegistry);
    }

    @Bean(destroyMethod = "shutdown")
    public SubagentRuntime subagentRuntime(AgentFactory agentFactory, AgentRegistry agentRegistry) {
        SubagentRuntime runtime = new SubagentRuntime(agentFactory, agentRegistry, maxConcurrentSubagents);
        logger.info("SubagentRuntime: maxConcurrent={}", maxConcurrentSubagents);
        return runtime;
    }

    @Bean
    @Primary
    public ChatHandler orchestratorChatHandler(AgentRegistry agentRegistry,
                                                AgentFactory agentFactory,
                                                SubagentRuntime subagentRuntime,
                                                ToolRegistry toolRegistry) {
        // 注册 subagent 工具到全局 ToolRegistry
        // 注意：SpawnSubagentTool 需要 parentSessionKey，这在运行时通过 Orchestrator 动态注入
        // 这里先注册 wait 和 list 工具（它们不需要 session 上下文）
        toolRegistry.register(new WaitSubagentTool(subagentRuntime));
        toolRegistry.register(new ListSubagentsTool(subagentRuntime));
        logger.info("Registered subagent tools: wait_subagent, list_subagents");

        MetadataAgentRouter router = new MetadataAgentRouter(agentRegistry);
        this.orchestratorInstance = new Orchestrator(agentRegistry, agentFactory, router, subagentRuntime);
        logger.info("Orchestrator initialized as primary ChatHandler");
        return orchestratorInstance;
    }

    private Orchestrator orchestratorInstance;

    @jakarta.annotation.PreDestroy
    public void shutdownOrchestrator() {
        if (orchestratorInstance != null) {
            orchestratorInstance.shutdown();
            logger.info("Orchestrator shutdown complete");
        }
    }
}
