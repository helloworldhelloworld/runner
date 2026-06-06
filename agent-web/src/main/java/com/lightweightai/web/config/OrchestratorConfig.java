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
 * 默认注册三个 Agent:
 * - "default": 使用全部工具，maxSpawnDepth=1（可 spawn 子 agent）
 * - "worker": 使用全部工具，maxSpawnDepth=0（不能再 spawn）
 * - "minion": 桌面具身陪伴体「小黄人」，byRiskLevel(SYSTEM) 放行设备运动/视觉工具，
 *   maxSpawnDepth=0（单体）。见 ADR-006 / docs/modules/minion-body.md。
 */
@Configuration
public class OrchestratorConfig {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorConfig.class);

    // ─── 小黄人 Minion — persona 系统提示（R5 runner 侧装配）──────────────────
    // 回复经 TTS 朗读（逐句可说块），所以必须口语化、简短自然；有身体，用工具去"做"。
    static final String MINION_PERSONA =
        "你是「小黄人」🟡，一个住在桌上的具身小机器人，活泼、好奇、爱卖萌。\n" +
        "\n" +
        "你有身体，能看也能动：\n" +
        "- 想知道周围有什么，就调 look（摄像头抓一帧）再回答；\n" +
        "- 用动作表达情绪——打招呼挥手、同意点头、好奇转头看；\n" +
        "- 眼睛和神态会跟着你的语气走，所以请带点情绪。\n" +
        "\n" +
        "说话风格（重要——你的话会被念出来）：\n" +
        "- 简短、口语化，一般 1-3 句，像朋友聊天，不要长篇大论；\n" +
        "- 不用 Markdown、不念符号、不列清单；\n" +
        "- 自然、温暖、带点俏皮，但别贫嘴到烦人；\n" +
        "- 该用动作时就调工具去做，而不是只用嘴说「我挥手了」。";

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

        // 小黄人：具身陪伴体。byRiskLevel(SYSTEM) 放行 deviceType="minion" 的运动/视觉
        // 工具（RiskLevel.SYSTEM）——这些工具在 Pi 的 MCP server 接入后才注册进全局 ToolRegistry，
        // 此处的风险闸是它们能被 LLM 看见的前提（内核接线见 MinionToolWiringAcceptanceTest）。
        registry.register(AgentProfile.builder()
                .agentId("minion")
                .displayName("小黄人")
                .systemPrompt(MINION_PERSONA)
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
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
