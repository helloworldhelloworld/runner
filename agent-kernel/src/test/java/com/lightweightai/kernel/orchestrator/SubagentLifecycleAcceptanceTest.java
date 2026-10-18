package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.CancellationToken;
import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SubagentLifecycleAcceptanceTest {
    @Test void foregroundCancellationDuringAnnouncementCancelsBeforeListenerBinding() {
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("must not execute");
        SubagentRuntime runtime = runtime(llm, new ToolRegistry());
        TrackingToken token = new TrackingToken();
        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        try {
            String id = runtime.spawn(request("cancel while spawning", token), event -> {
                events.add(event);
                if (event.getType() == StreamEvent.EventType.SUBAGENT_SPAWN) token.cancel();
            });
            assertEquals(SubagentRun.Status.CANCELLED, runtime.getRunOrCompleted(id).getStatus());
            assertEquals(List.of(StreamEvent.EventType.SUBAGENT_SPAWN, StreamEvent.EventType.SUBAGENT_CANCELLED),
                    events.stream().map(StreamEvent::getType).toList());
            assertTrue(llm.messagesHistory().isEmpty());
            assertEquals(0, token.registrations.get());
            assertEquals(0, runtime.getActiveCount());
        } finally {
            runtime.shutdown();
        }
    }

    @Test void normalCompletionReleasesForegroundListenerWithoutCancellingItsToken() throws Exception {
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("task result");
        SubagentRuntime runtime = runtime(llm, new ToolRegistry());
        TrackingToken token = new TrackingToken();
        try {
            String id = runtime.spawn(request("normal task", token), e -> {});
            assertTrue(runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertEquals("task result", runtime.getRunOrCompleted(id).getResult().getText());
            assertTrue(llm.lastMessages().stream().anyMatch(m -> "normal task".equals(m.getTextContent())));
            assertEquals(0, token.registrations.get());
            assertFalse(token.isCancelled());
            token.cancel();
            assertEquals(SubagentRun.Status.COMPLETED, runtime.getRunOrCompleted(id).getStatus());
        } finally {
            runtime.shutdown();
        }
    }

    @Test void failedSubmissionAlsoReleasesForegroundListener() {
        SubagentRuntime runtime = runtime(CapturingLLMProvider.endTurn("unused"), new ToolRegistry());
        runtime.shutdown();
        TrackingToken token = new TrackingToken();
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> runtime.spawn(request("rejected task", token), e -> {}));
        assertEquals(0, token.registrations.get());
        assertFalse(token.isCancelled());
    }

    static SpawnRequest request(String task, CancellationToken token) {
        return SpawnRequest.builder().parentSessionKey("agent:worker:main:A").task(task)
                .executionContext(ToolExecutionContext.serverOnly().withCancellationToken(token)).build();
    }

    static class TrackingToken extends CancellationToken {
        final java.util.concurrent.atomic.AtomicInteger registrations = new java.util.concurrent.atomic.AtomicInteger();
        @Override public Registration onCancel(Runnable callback) {
            Registration actual = super.onCancel(callback);
            registrations.incrementAndGet();
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            return () -> {
                actual.close();
                if (closed.compareAndSet(false, true)) registrations.decrementAndGet();
            };
        }
    }

    @Test void factoryFailureReleasesCapacityAndTheNextTaskActuallyRuns() throws Exception {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Tool() {
            public String getName() { return "broken"; }
            public String getDescription() { return "broken definition"; }
            public ToolSchema getSchema() { throw new IllegalStateException("broken schema"); }
            public com.lightweightai.kernel.llm.ToolResult execute(Map<String, Object> args) { throw new AssertionError(); }
        });
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("recovered");
        SubagentRuntime runtime = runtime(llm, tools);
        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        try {
            assertThrows(IllegalStateException.class, () -> runtime.spawn(request("failed"), events::add));
            String failedId = (String) events.get(0).getData().get("runId");
            assertEquals(0, runtime.getActiveCount());
            assertEquals(SubagentRun.Status.FAILED, runtime.getRunOrCompleted(failedId).getStatus());
            assertEquals("broken schema", runtime.getRunOrCompleted(failedId).getErrorMessage());
            tools.unregister("broken");
            String id = runtime.spawn(request("recovery task"), events::add);
            assertTrue(runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertTrue(llm.lastMessages().stream().anyMatch(m -> "recovery task".equals(m.getTextContent())));
            assertEquals("recovered", runtime.getRunOrCompleted(id).getResult().getText());
        } finally {
            runtime.shutdown();
        }
    }

    @Test void cancelDuringSpawnAnnouncementPreventsExecutionAndKeepsCancelledResult() {
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("must not run");
        SubagentRuntime runtime = runtime(llm, new ToolRegistry());
        try {
            String id = runtime.spawn(request("cancel before dispatch"), event -> {
                if (event.getType() == StreamEvent.EventType.SUBAGENT_SPAWN) {
                    runtime.stop((String) event.getData().get("runId"));
                }
            });
            assertEquals(SubagentRun.Status.CANCELLED, runtime.getRunOrCompleted(id).getStatus());
            assertEquals(0, runtime.getActiveCount());
            assertTrue(llm.messagesHistory().isEmpty());
        } finally {
            runtime.shutdown();
        }
    }

    @Test void rejectedSubmissionDoesNotLeaveAnActiveTask() {
        SubagentRuntime runtime = runtime(CapturingLLMProvider.endTurn("unused"), new ToolRegistry());
        runtime.shutdown();
        AtomicReference<String> id = new AtomicReference<>();
        assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> runtime.spawn(request("rejected"), event -> {
            if (event.getType() == StreamEvent.EventType.SUBAGENT_SPAWN) id.set((String) event.getData().get("runId"));
        }));
        assertEquals(0, runtime.getActiveCount());
        assertEquals(SubagentRun.Status.FAILED, runtime.getRunOrCompleted(id.get()).getStatus());
    }

    @Test void announcementFailureDoesNotLoseTheTask() throws Exception {
        CapturingLLMProvider llm = CapturingLLMProvider.endTurn("delivered");
        SubagentRuntime runtime = runtime(llm, new ToolRegistry());
        try {
            String id = runtime.spawn(request("deliver despite observer error"), event -> {
                throw new IllegalStateException("observer failure");
            });
            assertTrue(runtime.waitForCompletion(id, 5, TimeUnit.SECONDS));
            assertEquals("delivered", runtime.getRunOrCompleted(id).getResult().getText());
            assertTrue(llm.lastMessages().stream().anyMatch(m -> "deliver despite observer error".equals(m.getTextContent())));
        } finally {
            runtime.shutdown();
        }
    }

    static SubagentRuntime runtime(CapturingLLMProvider llm, ToolRegistry tools) {
        AgentRegistry agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("worker").maxSpawnDepth(2).build());
        agents.setDefault("worker");
        return new SubagentRuntime(new AgentFactory(llm, new InMemoryProvider(), tools), agents, 1);
    }

    static SpawnRequest request(String task) {
        return SpawnRequest.builder().parentSessionKey("agent:worker:main:A").task(task).build();
    }
}
