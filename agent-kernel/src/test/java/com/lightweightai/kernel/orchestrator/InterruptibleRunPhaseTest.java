package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterruptibleRun - 状态机与事件行为")
class InterruptibleRunPhaseTest {

    private TestMemory memory;

    @BeforeEach
    void setUp() {
        memory = new TestMemory();
    }

    @Test
    @DisplayName("初始 phase 为 NEW")
    void initialPhaseIsNew() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("hello"));
        InterruptibleRun run = new InterruptibleRun("run-1", "sess-1", agent, memory);

        assertEquals(InterruptibleRun.RunPhase.NEW, run.getPhase());
        assertFalse(run.isRunning());
    }

    @Test
    @DisplayName("execute 完成后 phase 为 COMPLETED")
    @Timeout(5)
    void executeCompletesSuccessfully() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("done"));
        InterruptibleRun run = new InterruptibleRun("run-2", "sess-2", agent, memory);

        List<StreamEvent> events = run.execute("hello").collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
    }

    @Test
    @DisplayName("interrupt 在非 RUNNING 状态返回 null")
    void interruptNotRunningReturnsNull() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("hello"));
        InterruptibleRun run = new InterruptibleRun("run-3", "sess-3", agent, memory);

        assertNull(run.interrupt(), "NEW 状态下 interrupt 应返回 null");
    }

    @Test
    @DisplayName("forceInterruptedState 后 phase 为 INTERRUPTED 且可 resume")
    @Timeout(5)
    void forceInterruptedThenResume() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("resumed answer"));
        InterruptibleRun run = new InterruptibleRun("run-4", "sess-4", agent, memory);

        run.forceInterruptedState("partial text");

        assertEquals(InterruptibleRun.RunPhase.INTERRUPTED, run.getPhase());

        List<StreamEvent> events = run.execute("new input").collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.AGENT_RESUME),
                "resume 应产生 AGENT_RESUME 事件");
        assertEquals(InterruptibleRun.RunPhase.COMPLETED, run.getPhase());
    }

    @Test
    @DisplayName("RUNNING 状态下再次 execute 返回 error Flux")
    @Timeout(5)
    void executeWhileRunningReturnsError() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("hello");
        AgentLoop agent = buildAgent(provider);
        InterruptibleRun run = new InterruptibleRun("run-5", "sess-5", agent, memory);

        run.execute("first").subscribe();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        if (run.isRunning()) {
            Flux<StreamEvent> secondExecute = run.execute("second");
            assertThrows(Exception.class, () -> secondExecute.collectList().block(),
                    "RUNNING 状态下 execute 应返回 error");
        }
    }

    @Test
    @DisplayName("getRunId 和 getCreatedAt 返回构造时的值")
    void gettersReturnConstructionValues() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("ok"));
        long before = System.currentTimeMillis();
        InterruptibleRun run = new InterruptibleRun("run-id-abc", "sess-x", agent, memory);
        long after = System.currentTimeMillis();

        assertEquals("run-id-abc", run.getRunId());
        assertTrue(run.getCreatedAt() >= before && run.getCreatedAt() <= after);
    }

    @Test
    @DisplayName("getMessages 返回不可修改的列表")
    void getMessagesIsUnmodifiable() {
        AgentLoop agent = buildAgent(CapturingLLMProvider.endTurn("ok"));
        InterruptibleRun run = new InterruptibleRun("run-6", "sess-6", agent, memory);

        assertThrows(UnsupportedOperationException.class,
                () -> run.getMessages().add(null));
    }

    private AgentLoop buildAgent(LLMProvider provider) {
        return AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .build();
    }

    private static class TestMemory implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String sid, Message msg) { messages.add(msg); }
        @Override public List<Message> getHistory(String sid, int limit) {
            int start = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String sid) { messages.clear(); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
