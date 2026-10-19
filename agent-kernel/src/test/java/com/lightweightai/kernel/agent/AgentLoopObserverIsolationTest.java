package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
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
 * 验证 AgentLoop Observer 异常隔离：
 * 某个 Observer 抛异常不应影响 AgentLoop 正常执行和其他 Observer 的通知。
 */
@DisplayName("AgentLoop - Observer 异常隔离")
class AgentLoopObserverIsolationTest {

    @Test
    @DisplayName("Observer 抛异常不影响 AgentLoop 正常执行")
    void observerExceptionDoesNotBreakAgentLoop() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response text");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("observer boom");
                    }
                })
                .build();

        AgentResponse response = loop.run("hello", "session-1");
        assertNotNull(response);
        assertEquals("response text", response.getText());
        assertEquals(1, spy.callCount());
    }

    @Test
    @DisplayName("第一个 Observer 异常不阻止第二个 Observer 被通知")
    void firstObserverFailureDoesNotBlockSecond() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        List<String> notifications = Collections.synchronizedList(new ArrayList<>());

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("first observer failure");
                    }
                })
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        notifications.add("second:" + input);
                    }
                })
                .build();

        loop.run("test", "s1");
        assertTrue(notifications.contains("second:test"),
                "Second observer should have been notified despite first one failing");
    }

    @Test
    @DisplayName("reactive 路径下 Observer 异常同样被隔离")
    void observerIsolatedInReactivePath() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("reactive response");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new StubMemory())
                .addObserver(new AgentObserver() {
                    @Override
                    public void onAgentStart(String input, String sessionId) {
                        throw new RuntimeException("reactive observer boom");
                    }
                })
                .build();

        List<StreamEvent> events = loop.runReactive("hello", "s1").collectList().block();
        assertNotNull(events);
        assertFalse(events.isEmpty());
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
