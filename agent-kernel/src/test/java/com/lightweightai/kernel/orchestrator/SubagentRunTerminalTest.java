package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SubagentRunTerminalTest {
    private final SubagentRun run = new SubagentRun("run", "parent:subagent:run",
            SpawnRequest.builder().parentSessionKey("parent").task("task").build());

    @Test void cancellationBeforeFutureBindingCancelsTheLateFuture() {
        run.cancel("stopped");
        CompletableFuture<Void> future = new CompletableFuture<>();
        run.setFuture(future);
        assertTrue(future.isCancelled());
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
    }

    @Test void lateSuccessAndFailureCannotOverwriteCancellation() {
        run.cancel("stopped");
        run.complete(AgentResponse.builder().text("late success").build());
        run.fail(new IllegalStateException("late failure"));
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertNull(run.getResult());
        assertEquals("stopped", run.getErrorMessage());
    }

    @Test void stoppingCompletedTaskDoesNotRewriteItsOutcome() {
        run.complete(AgentResponse.builder().text("already delivered").build());
        run.cancel("late stop");
        run.fail(new IllegalStateException("late failure"));
        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertEquals("already delivered", run.getResult().getText());
        assertNull(run.getErrorMessage());
    }

    @Test void terminalSignalIsAvailableImmediatelyWhenTransitionReturns() throws Exception {
        assertFalse(run.await(0, TimeUnit.SECONDS));
        run.cancel("stopped");
        assertTrue(run.await(0, TimeUnit.SECONDS));
    }
}
