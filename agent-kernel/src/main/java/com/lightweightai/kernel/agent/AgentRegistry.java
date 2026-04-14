package com.lightweightai.kernel.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册表
 *
 * 管理 AgentProfile 的注册与查找。支持设定 default agent 用于路由 fallback。
 */
public class AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, AgentProfile> agents = new ConcurrentHashMap<>();
    private volatile String defaultAgentId;

    public void register(AgentProfile profile) {
        AgentProfile prev = agents.put(profile.getAgentId(), profile);
        if (prev != null) {
            logger.info("Agent '{}' 已被覆盖注册", profile.getAgentId());
        } else {
            logger.debug("注册 Agent: {}", profile.getAgentId());
        }
    }

    public Optional<AgentProfile> get(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public List<AgentProfile> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(agents.values()));
    }

    /**
     * 设置 default agent ID
     */
    public void setDefault(String agentId) {
        this.defaultAgentId = agentId;
    }

    /**
     * 获取 default agent。优先返回显式设定的 default，否则返回第一个注册的。
     *
     * @throws IllegalStateException 注册表为空
     */
    public AgentProfile getDefault() {
        if (defaultAgentId != null) {
            AgentProfile profile = agents.get(defaultAgentId);
            if (profile != null) return profile;
        }
        // fallback: 第一个注册的
        return agents.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("AgentRegistry is empty, no default agent"));
    }

    public int size() {
        return agents.size();
    }
}
