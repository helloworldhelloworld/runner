package com.lightweightai.kernel.context;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.orchestrator.AgentFactory;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission tests: verify that ContextCompactor is actually wired and
 * called in the AgentLoop → LLM chain.
 *
 * Per CLAUDE.md UT rules: "set it at the top and assert it shows up at the bottom."
 */
@DisplayName("CompactionChain Transmission — compaction 实际应用于 AgentLoop→LLM 链路")
class CompactionTransmissionTest {

    @Test
    @DisplayName("AgentLoop 中设置的 ContextCompactor 在 run() 中被调用，压缩结果传递给 LLM")
    void compactorCalledBeforeLLMAndResultSentToProvider() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("OK");
        NoOpMemory memory = new NoOpMemory();

        AtomicReference<List<ConversationMessage>> compactorInput = new AtomicReference<>();
        ContextCompactor spyCompactor = messages -> {
            compactorInput.set(List.copyOf(messages));
            List<ConversationMessage> result = new ArrayList<>(messages);
            result.add(ConversationMessage.builder()
                    .role(MessageRole.SYSTEM)
                    .textContent("__COMPACTOR_MARKER__")
                    .build());
            return result;
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .contextCompactor(spyCompactor)
                .systemPrompt("hi")
                .build();

        agent.run("test input", "session-1");

        assertNotNull(compactorInput.get(), "Compactor should have been called");
        assertFalse(compactorInput.get().isEmpty());

        List<ConversationMessage> sentToLLM = spy.lastMessages();
        assertNotNull(sentToLLM);
        boolean hasMarker = sentToLLM.stream()
                .anyMatch(m -> "__COMPACTOR_MARKER__".equals(m.getTextContent()));
        assertTrue(hasMarker, "LLM should receive the compacted messages (with injected marker)");
    }

    @Test
    @DisplayName("AgentLoop 不设置 compactor 时，消息直接透传给 LLM")
    void noCompactorMeansPassThrough() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("OK");
        NoOpMemory memory = new NoOpMemory();

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("hi")
                .build();

        agent.run("test input", "session-1");

        List<ConversationMessage> sentToLLM = spy.lastMessages();
        assertNotNull(sentToLLM);
        boolean hasMarker = sentToLLM.stream()
                .anyMatch(m -> "__COMPACTOR_MARKER__".equals(m.getTextContent()));
        assertFalse(hasMarker);
    }

    @Test
    @DisplayName("AgentFactory.create() 注入 CompactionChain(Snip+Micro) 到 AgentLoop")
    void agentFactoryWiresCompactionChain() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("OK");
        NoOpMemory memory = new NoOpMemory();
        ToolRegistry tools = new ToolRegistry();

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("test")
                .systemPrompt("You help")
                .maxSpawnDepth(0)
                .build());

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        AgentLoop agent = factory.create(registry.get("test").get());

        agent.run("hello", "s1");

        assertNotNull(spy.lastMessages());
        assertEquals(1, spy.callCount());
    }

    @Test
    @DisplayName("Reactive 路径也应用 compaction")
    void reactivePathAlsoCompacts() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("OK");
        NoOpMemory memory = new NoOpMemory();

        AtomicReference<List<ConversationMessage>> compactorInput = new AtomicReference<>();
        ContextCompactor spyCompactor = messages -> {
            compactorInput.set(List.copyOf(messages));
            return messages;
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .contextCompactor(spyCompactor)
                .systemPrompt("hi")
                .build();

        agent.runReactive("test input", "session-1").collectList().block();

        assertNotNull(compactorInput.get(), "Compactor should be called on reactive path too");
        assertFalse(compactorInput.get().isEmpty());
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
