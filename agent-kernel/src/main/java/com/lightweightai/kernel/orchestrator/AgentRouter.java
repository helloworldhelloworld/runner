package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.gateway.GatewayRequest;

/**
 * Agent 路由策略接口
 *
 * 决定一个请求应该由哪个 Agent 处理。可扩展为：
 * - MetadataAgentRouter（按 request metadata 中的 agentId 路由）
 * - RuleBasedAgentRouter（按关键词/正则规则路由）
 * - LLMAgentRouter（用小模型做意图分类 → 选 agent）
 */
public interface AgentRouter {

    /**
     * @return agentId 标识处理此请求的 Agent
     */
    String route(GatewayRequest request);
}
