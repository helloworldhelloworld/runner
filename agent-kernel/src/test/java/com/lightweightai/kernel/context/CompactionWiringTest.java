package com.lightweightai.kernel.context;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compaction wiring: CompactionChain applied before LLM call in AgentLoop")
class CompactionWiringTest {

    @Test
    @DisplayName("messages are compacted before reaching LLM provider in sync path")
    void compactionAppliedBeforeLLMCall() {
        AtomicReference<List<ConversationMessage>> capturedMessages = new AtomicReference<>();

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("compacted response");

        ContextCompactor stubCompactor = messages -> {
            capturedMessages.set(messages);
            return messages.subList(0, Math.min(2, messages.size()));
        };

        InMemoryProvider memory = new InMemoryProvider();
        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .contextCompactor(stubCompactor)
                .build();

        agent.run("first input", "session-compact");
        agent.run("second input", "session-compact");

        List<ConversationMessage> messagesSeenByCompactor = capturedMessages.get();
        assertNotNull(messagesSeenByCompactor, "compactor must be invoked");
        assertTrue(messagesSeenByCompactor.size() >= 2,
                "compactor should receive full message list including history");

        List<ConversationMessage> messagesSeenByLLM = spy.lastMessages();
        assertNotNull(messagesSeenByLLM);
        assertEquals(2, messagesSeenByLLM.size(),
                "LLM should see compacted (truncated to 2) messages");
    }

    @Test
    @DisplayName("CompactionChain applies stages in order")
    void chainAppliesInOrder() {
        ContextCompactor append = messages -> {
            var copy = new java.util.ArrayList<>(messages);
            copy.add(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.SYSTEM)
                    .textContent("[STAGE-1-MARKER]")
                    .build());
            return copy;
        };

        ContextCompactor filter = messages -> messages.stream()
                .filter(m -> m.getTextContent() != null && !m.getTextContent().contains("[STAGE-1-MARKER]"))
                .toList();

        CompactionChain chain = new CompactionChain(append, filter);

        List<ConversationMessage> input = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        List<ConversationMessage> result = chain.compact(input);

        assertEquals(1, result.size(), "filter stage should remove the marker added by append stage");
        assertEquals("hello", result.get(0).getTextContent());
    }

    @Test
    @DisplayName("reactive path also applies compaction before LLM call")
    void reactivePathAppliesCompaction() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("reactive compacted");

        ContextCompactor truncator = messages ->
                messages.subList(0, Math.min(1, messages.size()));

        InMemoryProvider memory = new InMemoryProvider();
        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .contextCompactor(truncator)
                .build();

        agent.runReactive("reactive input", "session-reactive-compact").collectList().block();

        List<ConversationMessage> messagesSeenByLLM = spy.lastMessages();
        assertNotNull(messagesSeenByLLM);
        assertEquals(1, messagesSeenByLLM.size(),
                "compaction should truncate to 1 message in reactive path");
    }
}
