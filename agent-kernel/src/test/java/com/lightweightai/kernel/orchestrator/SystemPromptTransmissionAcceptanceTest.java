package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链测试: AgentProfile.systemPrompt → AgentFactory → AgentLoop → LLMProvider
 *
 * 验证 Profile 中的 systemPrompt 最终出现在 LLM 收到的消息列表里。
 * 这防止了 systemPrompt 在 Factory→Loop 传输中丢失的回归。
 */
@DisplayName("Acceptance: AgentProfile.systemPrompt 到达 LLM 的 messages")
class SystemPromptTransmissionAcceptanceTest {

    @Test
    @DisplayName("Profile systemPrompt 作为 SYSTEM 消息出现在 LLM 收到的 messages 首位")
    void systemPromptReachesLLM() {
        String expectedPrompt = "你是一个友善的心理健康助手，请温暖地回应用户。";

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("comfort")
                .systemPrompt(expectedPrompt)
                .build());
        registry.setDefault("comfort");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("好的");
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(spy, new NoOpMemory(), tools);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-prompt")
                .message("我今天心情不好")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        List<ConversationMessage> llmMessages = spy.lastMessages();
        assertNotNull(llmMessages, "LLM should receive messages");
        assertFalse(llmMessages.isEmpty());

        ConversationMessage firstMsg = llmMessages.get(0);
        assertEquals(ConversationMessage.MessageRole.SYSTEM, firstMsg.getRole(),
                "First message should be SYSTEM");
        assertTrue(firstMsg.getTextContent().contains(expectedPrompt),
                "SYSTEM message should contain the profile's systemPrompt");
    }

    @Test
    @DisplayName("不同 Agent 的 systemPrompt 互不干扰")
    void differentAgentsHaveDifferentPrompts() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("agent-a")
                .systemPrompt("我是A助手")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("agent-b")
                .systemPrompt("我是B助手")
                .build());
        registry.setDefault("agent-a");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, new NoOpMemory(), new ToolRegistry());
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // Route to agent-a
        GatewayRequest reqA = GatewayRequest.builder()
                .sessionId("sess-a")
                .message("hello")
                .metadata("agentId", "agent-a")
                .build();
        orchestrator.chatStreamReactive(reqA).collectList().block();

        String promptA = spy.lastMessages().get(0).getTextContent();
        assertTrue(promptA.contains("我是A助手"));

        // Route to agent-b
        GatewayRequest reqB = GatewayRequest.builder()
                .sessionId("sess-b")
                .message("hello")
                .metadata("agentId", "agent-b")
                .build();
        orchestrator.chatStreamReactive(reqB).collectList().block();

        String promptB = spy.lastMessages().get(0).getTextContent();
        assertTrue(promptB.contains("我是B助手"));
    }

    private static class NoOpMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
