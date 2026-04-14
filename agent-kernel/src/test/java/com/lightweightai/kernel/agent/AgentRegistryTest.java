package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentRegistry - Agent 注册表")
class AgentRegistryTest {

    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
    }

    @Test
    @DisplayName("注册并查找 Agent")
    void registerAndGet() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("code-assistant")
                .systemPrompt("你是代码助手")
                .build();

        registry.register(profile);

        assertTrue(registry.get("code-assistant").isPresent());
        assertEquals("你是代码助手", registry.get("code-assistant").get().getSystemPrompt());
    }

    @Test
    @DisplayName("查找不存在的 Agent 返回 empty")
    void getNotFound() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("getAll 返回所有注册的 Agent")
    void getAll() {
        registry.register(AgentProfile.builder().agentId("a").build());
        registry.register(AgentProfile.builder().agentId("b").build());

        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("设置并获取 default Agent")
    void defaultAgent() {
        AgentProfile defaultProfile = AgentProfile.builder()
                .agentId("general")
                .build();

        registry.register(defaultProfile);
        registry.setDefault("general");

        assertEquals("general", registry.getDefault().getAgentId());
    }

    @Test
    @DisplayName("无 default 时, getDefault 返回第一个注册的")
    void defaultFallsBackToFirst() {
        registry.register(AgentProfile.builder().agentId("first").build());
        registry.register(AgentProfile.builder().agentId("second").build());

        assertEquals("first", registry.getDefault().getAgentId());
    }

    @Test
    @DisplayName("空注册表 getDefault 抛异常")
    void defaultOnEmptyThrows() {
        assertThrows(IllegalStateException.class, () -> registry.getDefault());
    }

    @Test
    @DisplayName("重复注册覆盖旧 Profile")
    void registerOverwrites() {
        registry.register(AgentProfile.builder().agentId("a").systemPrompt("v1").build());
        registry.register(AgentProfile.builder().agentId("a").systemPrompt("v2").build());

        assertEquals(1, registry.getAll().size());
        assertEquals("v2", registry.get("a").get().getSystemPrompt());
    }
}
