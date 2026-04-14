package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentProfile - Agent 身份与配置")
class AgentProfileTest {

    @Test
    @DisplayName("Builder 构建完整 Profile")
    void buildFullProfile() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("email-triage")
                .displayName("邮件分类")
                .systemPrompt("你是邮件分类专家")
                .modelOverride("claude-3-haiku")
                .toolAllowList(Set.of("search", "read_email"))
                .toolDenyList(Set.of("spawn_subagent"))
                .maxToolIterations(5)
                .maxSpawnDepth(1)
                .metadata("team", "email")
                .build();

        assertEquals("email-triage", profile.getAgentId());
        assertEquals("邮件分类", profile.getDisplayName());
        assertEquals("你是邮件分类专家", profile.getSystemPrompt());
        assertEquals("claude-3-haiku", profile.getModelOverride());
        assertEquals(Set.of("search", "read_email"), profile.getToolAllowList());
        assertEquals(Set.of("spawn_subagent"), profile.getToolDenyList());
        assertEquals(5, profile.getMaxToolIterations());
        assertEquals(1, profile.getMaxSpawnDepth());
        assertEquals("email", profile.getMetadata().get("team"));
    }

    @Test
    @DisplayName("最小必要字段: agentId")
    void minimalProfile() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("default")
                .build();

        assertEquals("default", profile.getAgentId());
        assertNull(profile.getModelOverride());
        assertNull(profile.getToolAllowList());
        assertNull(profile.getToolDenyList());
        assertEquals(10, profile.getMaxToolIterations());  // default
        assertEquals(0, profile.getMaxSpawnDepth());       // default: 不能 spawn
    }

    @Test
    @DisplayName("agentId 必填")
    void agentIdRequired() {
        assertThrows(NullPointerException.class, () ->
                AgentProfile.builder().build());
    }

    @Test
    @DisplayName("metadata 不可变")
    void metadataImmutable() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("test")
                .metadata("key", "value")
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                profile.getMetadata().put("new", "val"));
    }
}
