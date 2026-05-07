package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链测试: MemoryProvider 按 sessionKey 隔离
 *
 * 验证不同 session 之间的对话历史不会泄漏。
 * 这是多用户安全的关键路径。
 */
@DisplayName("Acceptance: Memory 按 session 隔离 — 不同 session 不共享历史")
class MemoryIsolationAcceptanceTest {

    @Test
    @DisplayName("不同 sessionId 的对话历史互不可见")
    void differentSessionsAreIsolated() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("hi").build());
        registry.setDefault("default");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        InMemoryProvider memory = new InMemoryProvider();
        AgentFactory factory = new AgentFactory(spy, memory, new ToolRegistry());
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // Session A sends a message
        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("session-alice")
                .message("I am Alice, my secret is 12345")
                .build()).collectList().block();

        // Session B sends a message
        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("session-bob")
                .message("I am Bob")
                .build()).collectList().block();

        // Verify: Session B's LLM call should NOT contain Alice's messages
        List<com.lightweightai.kernel.llm.ConversationMessage> bobMessages = spy.lastMessages();
        assertNotNull(bobMessages);

        boolean aliceLeaked = bobMessages.stream()
                .anyMatch(m -> m.getTextContent() != null && m.getTextContent().contains("Alice"));
        assertFalse(aliceLeaked,
                "Alice's messages must not leak into Bob's session");

        boolean secretLeaked = bobMessages.stream()
                .anyMatch(m -> m.getTextContent() != null && m.getTextContent().contains("12345"));
        assertFalse(secretLeaked,
                "Alice's secret must not leak into Bob's session");

        // Verify: Session A's history is still accessible
        List<Message> aliceHistory = memory.getHistory(
                "agent:default:main:session-alice", 10);
        assertTrue(aliceHistory.stream()
                .anyMatch(m -> m.getContent().contains("Alice")),
                "Alice's history should still exist in her session");
    }

    @Test
    @DisplayName("同一 sessionId 的连续消息保留上下文")
    void sameSessionPreservesContext() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("hi").build());
        registry.setDefault("default");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        InMemoryProvider memory = new InMemoryProvider();
        AgentFactory factory = new AgentFactory(spy, memory, new ToolRegistry());
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // First message
        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("sess-1")
                .message("My name is Charlie")
                .build()).collectList().block();

        // Second message (same session)
        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("sess-1")
                .message("What is my name?")
                .build()).collectList().block();

        // The second LLM call should include the first message in its context
        List<com.lightweightai.kernel.llm.ConversationMessage> secondCallMessages = spy.lastMessages();
        boolean containsFirstMessage = secondCallMessages.stream()
                .anyMatch(m -> m.getTextContent() != null && m.getTextContent().contains("Charlie"));
        assertTrue(containsFirstMessage,
                "Second call should include context from first message in same session");
    }
}
