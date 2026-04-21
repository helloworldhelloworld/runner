package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - subagent run state tracking")
class SubagentRunTest {

    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("analyze data")
                .agentId("worker")
                .build();
        run = new SubagentRun("run-001", "agent:worker:subagent:run-001", request);
    }

    @Test
    @DisplayName("initial status is RUNNING")
    void initialStatusIsRunning() {
        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        assertTrue(run.isRunning());
    }

    @Test
    @DisplayName("complete() transitions to COMPLETED")
    void completeTransitionsToCompleted() {
        AgentResponse response = new AgentResponse("analysis done");
        run.complete(response);

        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals(response, run.getResult());
    }

    @Test
    @DisplayName("fail() transitions to FAILED with error message")
    void failTransitionsToFailed() {
        run.fail(new RuntimeException("OOM"));

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("OOM", run.getErrorMessage());
    }

    @Test
    @DisplayName("fail() uses class name for null-message exception")
    void failUsesClassNameForNullMessage() {
        run.fail(new NullPointerException());

        assertEquals("NullPointerException", run.getErrorMessage());
    }

    @Test
    @DisplayName("cancel() transitions to CANCELLED and cancels Future")
    void cancelTransitionsToCancelled() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        run.setFuture(future);
        run.cancel("user cancelled");

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("user cancelled", run.getErrorMessage());
        assertTrue(future.isCancelled());
    }

    @Test
    @DisplayName("cancel() with null future does not throw")
    void cancelWithNullFutureDoesNotThrow() {
        assertDoesNotThrow(() -> run.cancel("safe cancel"));
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
    }

    @Test
    @DisplayName("accessors return constructor arguments")
    void accessorsReturnConstructorArgs() {
        assertEquals("run-001", run.getRunId());
        assertEquals("agent:worker:subagent:run-001", run.getSessionKey());
        assertEquals(request, run.getRequest());
    }

    @Test
    @DisplayName("getDurationMs returns non-negative value")
    void durationIsPositive() {
        assertTrue(run.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("getStartTime records creation time")
    void startTimeIsRecorded() {
        long now = System.currentTimeMillis();
        assertTrue(run.getStartTime() <= now);
        assertTrue(run.getStartTime() > now - 1000);
    }
}
