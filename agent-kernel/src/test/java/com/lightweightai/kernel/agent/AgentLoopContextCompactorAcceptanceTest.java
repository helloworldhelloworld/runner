package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.context.ContextCompactor;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance: AgentLoop 必须在 buildMessages → LLM 之间调用 ContextCompactor.compact()，
 * 且将压缩后的消息传给 LLMProvider。
 *
 * 验证链路: AgentLoop.buildMessages() → ContextCompactor.compact() → provider.complete()
 *
 * 修复前的风险: AgentLoop 接受 contextCompactor 但可能未实际调用，或调用后丢弃结果。
 */
@DisplayName("Acceptance: AgentLoop 将 ContextCompactor 的输出传给 LLMProvider")
class AgentLoopContextCompactorAcceptanceTest {

    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    @Test
    @DisplayName("sync: compact() 被调用，且压缩后的消息是 LLM 收到的消息")
    void syncPathPassesCompactedMessagesToProvider() {
        AtomicInteger compactCallCount = new AtomicInteger(0);
        AtomicReference<List<ConversationMessage>> compactInput = new AtomicReference<>();

        ConversationMessage injectedMarker = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("[COMPACTED]")
                .build();

        ContextCompactor spyCompactor = messages -> {
            compactCallCount.incrementAndGet();
            compactInput.set(List.copyOf(messages));
            return List.of(injectedMarker);
        };

        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("ok");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spyProvider)
                .memoryProvider(memory)
                .systemPrompt("你是助手")
                .contextCompactor(spyCompactor)
                .build();

        loop.run("hello", "sess-1");

        assertEquals(1, compactCallCount.get(), "compact() must be called exactly once");
        assertNotNull(compactInput.get(), "compact() must receive non-null messages");
        assertFalse(compactInput.get().isEmpty(), "compact() input must not be empty");

        List<ConversationMessage> llmReceived = spyProvider.lastMessages();
        assertNotNull(llmReceived, "LLM provider must receive messages");
        assertEquals(1, llmReceived.size(), "LLM should receive only the compacted marker message");
        assertEquals("[COMPACTED]", llmReceived.get(0).getTextContent(),
                "LLM must receive the compactor's output, not the raw messages");
    }

    @Test
    @DisplayName("reactive: compact() 被调用，且压缩后的消息是 LLM 收到的消息")
    void reactivePathPassesCompactedMessagesToProvider() {
        AtomicInteger compactCallCount = new AtomicInteger(0);

        ConversationMessage injectedMarker = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("[REACTIVE-COMPACTED]")
                .build();

        ContextCompactor spyCompactor = messages -> {
            compactCallCount.incrementAndGet();
            return List.of(injectedMarker);
        };

        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("ok");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spyProvider)
                .memoryProvider(memory)
                .systemPrompt("你是助手")
                .contextCompactor(spyCompactor)
                .build();

        StepVerifier.create(loop.runReactive("hello", "sess-2"))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(1, compactCallCount.get(), "compact() must be called in reactive path");

        List<ConversationMessage> llmReceived = spyProvider.lastMessages();
        assertNotNull(llmReceived);
        assertEquals(1, llmReceived.size());
        assertEquals("[REACTIVE-COMPACTED]", llmReceived.get(0).getTextContent(),
                "Reactive path must also pass compacted messages to LLM");
    }

    @Test
    @DisplayName("无 compactor 时 LLM 收到原始消息（含 system + history）")
    void withoutCompactorLLMReceivesRawMessages() {
        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("ok");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spyProvider)
                .memoryProvider(memory)
                .systemPrompt("你是一个助手")
                .build();

        loop.run("用户输入", "sess-3");

        List<ConversationMessage> llmReceived = spyProvider.lastMessages();
        assertNotNull(llmReceived);
        assertTrue(llmReceived.size() >= 1, "Should have at least system message");

        String systemContent = llmReceived.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst()
                .map(ConversationMessage::getTextContent)
                .orElse("");
        assertTrue(systemContent.contains("你是一个助手"),
                "Without compactor, raw system prompt should reach LLM");
    }

    @Test
    @DisplayName("compactor 收到的消息包含 system prompt 和用户历史")
    void compactorReceivesFullContext() {
        AtomicReference<List<ConversationMessage>> captured = new AtomicReference<>();

        ContextCompactor capturingCompactor = messages -> {
            captured.set(List.copyOf(messages));
            return messages;
        };

        CapturingLLMProvider spyProvider = CapturingLLMProvider.endTurn("ok");

        memory.addMessage("sess-4", com.lightweightai.kernel.memory.Message.user("旧消息"));
        memory.addMessage("sess-4", com.lightweightai.kernel.memory.Message.assistant("旧回复"));

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spyProvider)
                .memoryProvider(memory)
                .systemPrompt("系统提示")
                .contextCompactor(capturingCompactor)
                .build();

        loop.run("新消息", "sess-4");

        List<ConversationMessage> input = captured.get();
        assertNotNull(input, "compactor must receive messages");
        assertTrue(input.size() >= 3,
                "compactor input should include system + history(user+assistant) + current user; got " + input.size());

        boolean hasSystem = input.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM
                        && m.getTextContent().contains("系统提示"));
        assertTrue(hasSystem, "compactor input must include system prompt");
    }
}
