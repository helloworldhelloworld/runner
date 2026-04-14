package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;

/**
 * 默认路由策略：从 GatewayRequest metadata 中取 agentId
 *
 * 如果 metadata 中无 agentId 或指定的 agentId 不存在，fallback 到 default agent。
 */
public class MetadataAgentRouter implements AgentRouter {

    private final AgentRegistry registry;

    public MetadataAgentRouter(AgentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String route(GatewayRequest request) {
        Object agentId = request.getMetadata().get("agentId");
        if (agentId != null && registry.get(agentId.toString()).isPresent()) {
            return agentId.toString();
        }
        return registry.getDefault().getAgentId();
    }
}
