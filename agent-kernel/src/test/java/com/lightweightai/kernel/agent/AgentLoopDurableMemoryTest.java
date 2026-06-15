package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that durable (long-term) memory is deterministically injected into
 * the system prompt on every turn.
 *
 * This guards the feature added for reliable cross-session user knowledge —
 * unlike embedding-based search, durable memory must always be present.
 */
@DisplayName("AgentLoop durable memory injection")
class AgentLoopDurableMemoryTest {

    @Test
    @DisplayName("durable memory content is injected into system prompt sent to LLM")
    void durableMemory_injectedIntoSystemPrompt() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("hello user");
        DurableTestMemory memory = new DurableTestMemory("用户名字: 小明\n用户爱好: 编程");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("你是一个助手")
                .build();

        agent.run("你好", "sess-durable");

        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs);
        ConversationMessage systemMsg = msgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst()
                .orElse(null);
        assertNotNull(systemMsg, "System message must exist");
        String content = systemMsg.getTextContent();
        assertTrue(content.contains("用户名字: 小明"), "Durable memory about user name should be in system prompt");
        assertTrue(content.contains("用户爱好: 编程"), "Durable memory about user hobby should be in system prompt");
        assertTrue(content.contains("你是一个助手"), "Base system prompt should still be present");
    }

    @Test
    @DisplayName("empty durable memory does not add noise to system prompt")
    void emptyDurableMemory_noNoise() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("hi");
        DurableTestMemory memory = new DurableTestMemory("");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("base prompt")
                .build();

        agent.run("hello", "sess-empty-durable");

        ConversationMessage systemMsg = spy.lastMessages().stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst()
                .orElse(null);
        assertNotNull(systemMsg);
        assertFalse(systemMsg.getTextContent().contains("长期记忆"),
                "Empty durable should not add memory section header");
    }

    @Test
    @DisplayName("durable memory longer than 2000 chars is truncated")
    void longDurableMemory_truncated() {
        String longContent = "A".repeat(3000);
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        DurableTestMemory memory = new DurableTestMemory(longContent);

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("prompt")
                .build();

        agent.run("test", "sess-long");

        String systemText = spy.lastMessages().stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst()
                .map(ConversationMessage::getTextContent)
                .orElse("");
        // The durable part should be at most 2000 chars (truncated)
        // Total text includes base prompt + header, so just verify durable portion is capped
        assertFalse(systemText.contains("A".repeat(2001)),
                "Durable memory exceeding 2000 chars should be truncated");
        assertTrue(systemText.contains("A".repeat(2000)),
                "First 2000 chars of durable memory should be present");
    }

    @Test
    @DisplayName("durable memory read failure is handled gracefully")
    void durableMemoryReadFailure_graceful() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        MemoryProvider failingMemory = new DurableTestMemory("") {
            @Override
            public String readDurable() {
                throw new RuntimeException("disk error");
            }
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(failingMemory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("prompt")
                .build();

        // Should not throw — readDurableSafe catches the exception
        AgentResponse response = agent.run("hi", "sess-fail");
        assertNotNull(response);
        assertEquals("ok", response.getText());
    }

    @Test
    @DisplayName("reactive path also injects durable memory")
    void reactivePath_injectsDurableMemory() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("reactive ok");
        DurableTestMemory memory = new DurableTestMemory("favorite_color: blue");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("reactive prompt")
                .build();

        agent.runReactive("test", "sess-reactive-durable").collectList().block();

        ConversationMessage systemMsg = spy.lastMessages().stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst()
                .orElse(null);
        assertNotNull(systemMsg);
        assertTrue(systemMsg.getTextContent().contains("favorite_color: blue"),
                "Durable memory should be in system prompt on reactive path too");
    }

    private static class DurableTestMemory implements MemoryProvider {
        private final String durableContent;
        private final Map<String, List<Message>> sessions = new HashMap<>();

        DurableTestMemory(String durableContent) {
            this.durableContent = durableContent;
        }

        @Override
        public void addMessage(String sessionId, Message message) {
            sessions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> msgs = sessions.getOrDefault(sessionId, List.of());
            int from = Math.max(0, msgs.size() - limit);
            return new ArrayList<>(msgs.subList(from, msgs.size()));
        }

        @Override public void clearSession(String s) { sessions.remove(s); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }

        @Override
        public String readDurable() {
            return durableContent;
        }
    }
}
