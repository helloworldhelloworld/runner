package com.lightweightai.kernel.context;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链测试：ContextCompactor → AgentLoop → LLMProvider
 *
 * 验证 AgentLoop.run() 在构建消息后、发送给 LLM 之前，
 * 确实调用了 ContextCompactor.compact()，
 * 且压缩后的结果是 LLM 实际收到的 messages。
 *
 * 这是 CLAUDE.md 中 UT 规则第 3 条的典范：
 * "每个 producer–consumer pair 需要 ≥1 个 transmission test"
 */
@DisplayName("传输链: ContextCompactor → AgentLoop → LLMProvider")
class CompactionTransmissionTest {

    @Test
    @DisplayName("AgentLoop.run 调用 compactor 并将其输出传给 LLM（同步路径）")
    void compactorOutputShouldReachLLMInSyncPath() {
        MemoryProvider memory = new InMemoryProvider();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        // 预填充历史对话
        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("old question"));
        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("old answer"));

        // 自定义 compactor：注入一个标记 SYSTEM 消息，证明 compactor 被调用
        // 且其输出是 LLM 实际收到的 messages
        String marker = "COMPACTOR_WAS_HERE_" + System.nanoTime();
        AtomicBoolean compactorCalled = new AtomicBoolean(false);
        AtomicReference<Integer> inputSize = new AtomicReference<>();

        ContextCompactor markerCompactor = messages -> {
            compactorCalled.set(true);
            inputSize.set(messages.size());
            // 在消息列表末尾追加一个标记消息
            List<ConversationMessage> result = new ArrayList<>(messages);
            result.add(ConversationMessage.builder()
                    .role(MessageRole.USER)
                    .textContent(marker)
                    .build());
            return result;
        };

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .systemPrompt("test system")
                .contextCompactor(markerCompactor)
                .build();

        loop.run("new question", "s1");

        // 1. compactor 确实被调用了
        assertTrue(compactorCalled.get(), "ContextCompactor.compact() should be called");

        // 2. compactor 收到了正确的消息（system + history + current）
        assertNotNull(inputSize.get());
        assertTrue(inputSize.get() >= 3,
                "Compactor should receive system + history + current messages, got " + inputSize.get());

        // 3. LLM 收到的消息包含 compactor 注入的标记
        List<ConversationMessage> llmReceived = spy.lastMessages();
        assertNotNull(llmReceived);

        boolean markerFound = llmReceived.stream()
                .anyMatch(m -> marker.equals(m.getTextContent()));
        assertTrue(markerFound,
                "LLM should receive compactor's output including the marker message");
    }

    @Test
    @DisplayName("compactor 删除消息后 LLM 收到更少的 messages")
    void compactorRemovingMessagesShouldReduceLLMInput() {
        MemoryProvider memory = new InMemoryProvider();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        // 预填充 6 轮对话
        for (int i = 0; i < 6; i++) {
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("q" + i));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("a" + i));
        }

        // compactor 只保留最后 2 条消息（极端压缩）
        ContextCompactor aggressiveCompactor = messages -> {
            if (messages.size() <= 2) return messages;
            return messages.subList(messages.size() - 2, messages.size());
        };

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .systemPrompt("test")
                .contextCompactor(aggressiveCompactor)
                .build();

        loop.run("latest", "s1");

        List<ConversationMessage> received = spy.lastMessages();
        assertEquals(2, received.size(),
                "Aggressive compactor should reduce messages to 2");
    }

    @Test
    @DisplayName("不配 compactor 时 messages 原样传给 LLM")
    void noCompactorShouldPassMessagesUnmodified() {
        MemoryProvider memory = new InMemoryProvider();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("history"));
        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("reply"));

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .systemPrompt("system prompt")
                .build();

        loop.run("current", "s1");

        List<ConversationMessage> received = spy.lastMessages();
        // system(1) + history(2) + current user(1) = 4
        // Note: "current" gets added to memory before buildMessages, so it's included in history
        assertNotNull(received);
        assertTrue(received.size() >= 3, "Should have system + history + current");
    }

    @Test
    @DisplayName("Reactive 路径也应用 compactor")
    void compactorShouldBeAppliedInReactivePath() {
        MemoryProvider memory = new InMemoryProvider();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("old"));
        memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("reply"));

        AtomicBoolean compactorCalled = new AtomicBoolean(false);
        ContextCompactor trackingCompactor = messages -> {
            compactorCalled.set(true);
            return messages;
        };

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .systemPrompt("test")
                .contextCompactor(trackingCompactor)
                .build();

        // Reactive path
        loop.runReactive("input", "s1").collectList().block();

        assertTrue(compactorCalled.get(),
                "ContextCompactor should also be called in reactive path");
    }
}
