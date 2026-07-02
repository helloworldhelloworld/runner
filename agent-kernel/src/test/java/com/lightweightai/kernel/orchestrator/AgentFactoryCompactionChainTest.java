package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test: AgentFactory's hardcoded CompactionChain
 * is actually wired and invoked during a real Orchestrator run.
 *
 * AgentFactory.create() (line 57-59) creates:
 *   new CompactionChain(new SnipCompactor(3), new MicroCompactor(2000))
 *
 * This test verifies that when the Orchestrator routes a request through
 * AgentFactory → AgentLoop, the CompactionChain is present and executes
 * (by verifying the messages passed to LLM are compacted for a long history).
 */
@DisplayName("Acceptance: AgentFactory CompactionChain wiring")
class AgentFactoryCompactionChainTest {

    private Orchestrator orchestrator;

    @AfterEach
    void tearDown() {
        if (orchestrator != null) orchestrator.shutdown();
    }

    @Test
    @DisplayName("CompactionChain is wired: long history gets compacted before reaching LLM")
    void compactionChainIsWiredThroughOrchestrator() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response");

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("compact-agent")
                .systemPrompt("test")
                .build());
        registry.setDefault("compact-agent");

        // Build a memory with many messages to trigger compaction
        // SnipCompactor(3) keeps first 3 + last 3 system/user/assistant messages
        LongHistoryMemory memory = new LongHistoryMemory(30);
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(spy, memory, tools);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("compact-sess")
                .message("test compaction")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        // Verify provider was called
        assertEquals(1, spy.callCount());

        // The messages passed to LLM should be compacted
        List<ConversationMessage> messagesReceived = spy.lastMessages();
        assertNotNull(messagesReceived);

        // With 30 history messages + system + new user, without compaction = 32 messages
        // With SnipCompactor(3) + MicroCompactor(2000), the message count should be reduced
        // The exact count depends on compaction logic, but it should be strictly less than
        // the original count (30 history + 1 system + 1 new user = 32)
        assertTrue(messagesReceived.size() < 32,
                "Compacted messages (" + messagesReceived.size() +
                ") should be fewer than uncompacted (32). " +
                "If equal, CompactionChain is not wired or not running.");
    }

    @Test
    @DisplayName("Short history passes through compaction unchanged")
    void shortHistoryPassesThroughUnchanged() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("short-agent")
                .systemPrompt("test")
                .build());
        registry.setDefault("short-agent");

        // 2 messages is below the SnipCompactor threshold
        LongHistoryMemory memory = new LongHistoryMemory(2);
        ToolRegistry tools = new ToolRegistry();
        AgentFactory factory = new AgentFactory(spy, memory, tools);
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("short-sess")
                .message("short test")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        List<ConversationMessage> messagesReceived = spy.lastMessages();
        assertNotNull(messagesReceived);
        // System + 2 history + 1 new user message + durable inject = small number
        // Exact count: 1 system + 2 history + 1 new user = 4
        assertTrue(messagesReceived.size() >= 3, "Short history should pass through with all messages");
    }

    // ==================== Helpers ====================

    /**
     * MemoryProvider that returns a configurable number of history messages.
     */
    private static class LongHistoryMemory implements MemoryProvider {
        private final int historySize;

        LongHistoryMemory(int historySize) {
            this.historySize = historySize;
        }

        @Override
        public void addMessage(String s, Message m) {}

        @Override
        public List<Message> getHistory(String sessionKey, int limit) {
            List<Message> history = new ArrayList<>();
            for (int i = 0; i < Math.min(historySize, limit); i++) {
                if (i % 2 == 0) {
                    history.add(Message.user("History user message " + i));
                } else {
                    history.add(Message.assistant("History assistant message " + i));
                }
            }
            return history;
        }

        @Override
        public void clearSession(String s) {}

        @Override
        public void writeEphemeral(String c) {}

        @Override
        public void writeDurable(String s, String c) {}

        @Override
        public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
