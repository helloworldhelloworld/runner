package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - Subagent execution state tracking")
class SubagentRunTest {

    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:main:main:s1")
                .task("analyze data")
                .build();
        run = new SubagentRun("run-1", "agent:main:subagent:abc", request);
    }

    @Test
    @DisplayName("Initial status is RUNNING")
    void initialStatusIsRunning() {
        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
    }

    @Test
    @DisplayName("isRunning returns true for newly created run")
    void isRunningReturnsTrueInitially() {
        assertTrue(run.isRunning());
    }

    @Test
    @DisplayName("complete sets status to COMPLETED and stores result text")
    void completeSetsStatusAndStoresResult() {
        AgentResponse response = new AgentResponse("analysis complete");

        run.complete(response);

        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("analysis complete", run.getResult().getText());
    }

    @Test
    @DisplayName("fail sets status to FAILED and stores error message")
    void failSetsStatusAndStoresErrorMessage() {
        run.fail(new RuntimeException("connection timeout"));

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("connection timeout", run.getErrorMessage());
    }

    @Test
    @DisplayName("fail with exception having null message uses class name")
    void failWithNullMessageUsesClassName() {
        run.fail(new NullPointerException());

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertEquals("NullPointerException", run.getErrorMessage());
    }

    @Test
    @DisplayName("cancel sets status to CANCELLED and cancels associated future")
    void cancelSetsStatusAndCancelsFuture() {
        AtomicBoolean futureCancelled = new AtomicBoolean(false);
        CompletableFuture<Void> future = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                futureCancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        run.setFuture(future);

        run.cancel("user requested stop");

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("user requested stop", run.getErrorMessage());
        assertTrue(futureCancelled.get());
    }

    @Test
    @DisplayName("cancel without future set does not throw")
    void cancelWithoutFutureDoesNotThrow() {
        assertDoesNotThrow(() -> run.cancel("no future attached"));

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertEquals("no future attached", run.getErrorMessage());
    }

    @Test
    @DisplayName("getDurationMs returns non-negative value after construction")
    void getDurationMsReturnsNonNegativeValue() {
        long duration = run.getDurationMs();

        assertTrue(duration >= 0, "Duration should be non-negative, got: " + duration);
    }

    @Test
    @DisplayName("getRunId, getSessionKey, and getRequest return constructor values")
    void gettersReturnConstructorValues() {
        assertEquals("run-1", run.getRunId());
        assertEquals("agent:main:subagent:abc", run.getSessionKey());
        assertSame(request, run.getRequest());
        assertEquals("analyze data", run.getRequest().getTask());
    }
}
