package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRunStatusTest - Thorough status transition tests")
class SubagentRunStatusTest {

    private static final String RUN_ID = "run-42";
    private static final String SESSION_KEY = "agent:test:subagent:run-42";
    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:test:main:s1")
                .task("do something")
                .agentId("test")
                .build();
        run = new SubagentRun(RUN_ID, SESSION_KEY, request);
    }

    // ==================== Initial state ====================

    @Nested
    @DisplayName("Initial state")
    class InitialState {

        @Test
        @DisplayName("initial status is RUNNING")
        void initialStatusIsRunning() {
            assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        }

        @Test
        @DisplayName("isRunning returns true initially")
        void isRunningTrueInitially() {
            assertTrue(run.isRunning());
        }

        @Test
        @DisplayName("getRunId matches constructor argument")
        void getRunIdMatchesConstructor() {
            assertEquals(RUN_ID, run.getRunId());
        }

        @Test
        @DisplayName("getSessionKey matches constructor argument")
        void getSessionKeyMatchesConstructor() {
            assertEquals(SESSION_KEY, run.getSessionKey());
        }

        @Test
        @DisplayName("getRequest matches constructor argument")
        void getRequestMatchesConstructor() {
            assertSame(request, run.getRequest());
            assertEquals("do something", run.getRequest().getTask());
            assertEquals("test", run.getRequest().getAgentId());
        }

        @Test
        @DisplayName("result is null initially")
        void resultIsNullInitially() {
            assertNull(run.getResult());
        }

        @Test
        @DisplayName("errorMessage is null initially")
        void errorMessageIsNullInitially() {
            assertNull(run.getErrorMessage());
        }
    }

    // ==================== complete() ====================

    @Nested
    @DisplayName("complete()")
    class CompleteTransition {

        @Test
        @DisplayName("complete changes status to COMPLETED")
        void completeChangesStatusToCompleted() {
            AgentResponse response = new AgentResponse("done");
            run.complete(response);
            assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        }

        @Test
        @DisplayName("complete stores the result and its text is accessible")
        void completeStoresResult() {
            AgentResponse response = AgentResponse.builder()
                    .text("result payload")
                    .stopReason("end_turn")
                    .build();
            run.complete(response);

            assertNotNull(run.getResult(), "Result should not be null after complete");
            assertSame(response, run.getResult(), "Result should be the exact response instance");
            assertEquals("result payload", run.getResult().getText());
            assertEquals("end_turn", run.getResult().getStopReason());
        }

        @Test
        @DisplayName("isRunning returns false after complete")
        void isRunningFalseAfterComplete() {
            run.complete(new AgentResponse("done"));
            assertFalse(run.isRunning());
        }

        @Test
        @DisplayName("errorMessage remains null after complete")
        void errorMessageNullAfterComplete() {
            run.complete(new AgentResponse("done"));
            assertNull(run.getErrorMessage(),
                    "errorMessage should remain null on successful completion");
        }
    }

    // ==================== fail() ====================

    @Nested
    @DisplayName("fail()")
    class FailTransition {

        @Test
        @DisplayName("fail changes status to FAILED")
        void failChangesStatusToFailed() {
            run.fail(new RuntimeException("boom"));
            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        }

        @Test
        @DisplayName("fail stores the exception message")
        void failStoresErrorMessage() {
            run.fail(new RuntimeException("boom"));
            assertEquals("boom", run.getErrorMessage());
        }

        @Test
        @DisplayName("fail with null message uses exception class simple name")
        void failWithNullMessageUsesClassName() {
            run.fail(new RuntimeException());
            assertEquals("RuntimeException", run.getErrorMessage());
        }

        @Test
        @DisplayName("fail with custom exception type and null message uses that type's simple name")
        void failWithCustomExceptionNullMessage() {
            run.fail(new IllegalStateException());
            assertEquals("IllegalStateException", run.getErrorMessage());
        }

        @Test
        @DisplayName("isRunning returns false after fail")
        void isRunningFalseAfterFail() {
            run.fail(new RuntimeException("error"));
            assertFalse(run.isRunning());
        }

        @Test
        @DisplayName("result remains null after fail")
        void resultNullAfterFail() {
            run.fail(new RuntimeException("error"));
            assertNull(run.getResult(),
                    "Result should remain null when run fails");
        }
    }

    // ==================== cancel() ====================

    @Nested
    @DisplayName("cancel()")
    class CancelTransition {

        @Test
        @DisplayName("cancel changes status to CANCELLED")
        void cancelChangesStatusToCancelled() {
            run.cancel("stopped by user");
            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        }

        @Test
        @DisplayName("cancel stores the reason as errorMessage")
        void cancelStoresReason() {
            run.cancel("stopped");
            assertEquals("stopped", run.getErrorMessage());
        }

        @Test
        @DisplayName("cancel cancels associated Future")
        void cancelCancelsFuture() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.setFuture(future);

            run.cancel("shutting down");

            assertTrue(future.isCancelled(),
                    "Future should be cancelled when SubagentRun is cancelled");
        }

        @Test
        @DisplayName("cancel without Future does not throw")
        void cancelWithoutFutureDoesNotThrow() {
            assertDoesNotThrow(() -> run.cancel("no future set"),
                    "cancel should handle null future gracefully");
            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        }

        @Test
        @DisplayName("cancel with already-completed Future does not throw")
        void cancelWithAlreadyCompletedFuture() {
            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
            run.setFuture(future);

            assertDoesNotThrow(() -> run.cancel("too late"),
                    "cancel should handle already-completed future gracefully");
            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        }

        @Test
        @DisplayName("isRunning returns false after cancel")
        void isRunningFalseAfterCancel() {
            run.cancel("cancelled");
            assertFalse(run.isRunning());
        }

        @Test
        @DisplayName("result remains null after cancel")
        void resultNullAfterCancel() {
            run.cancel("cancelled");
            assertNull(run.getResult(),
                    "Result should remain null when run is cancelled");
        }
    }

    // ==================== durationMs ====================

    @Nested
    @DisplayName("getDurationMs()")
    class DurationTests {

        @Test
        @DisplayName("durationMs is non-negative immediately after construction")
        void durationMsIsNonNegative() {
            assertTrue(run.getDurationMs() >= 0,
                    "Duration should be non-negative");
        }

        @Test
        @DisplayName("durationMs is positive after a brief pause")
        void durationMsIsPositive() throws InterruptedException {
            Thread.sleep(30);
            assertTrue(run.getDurationMs() > 0,
                    "Duration should be positive after sleeping");
        }

        @Test
        @DisplayName("durationMs increases over time while running")
        void durationMsIncreasesOverTime() throws InterruptedException {
            long d1 = run.getDurationMs();
            Thread.sleep(50);
            long d2 = run.getDurationMs();
            assertTrue(d2 > d1,
                    "Duration should increase: d1=" + d1 + ", d2=" + d2);
        }
    }

    // ==================== setFuture ====================

    @Nested
    @DisplayName("setFuture()")
    class SetFutureTests {

        @Test
        @DisplayName("setFuture stores the future for later cancellation")
        void setFutureStoresFuture() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.setFuture(future);

            // Verify by cancelling and checking the future was actually cancelled
            run.cancel("test");
            assertTrue(future.isCancelled(),
                    "After setFuture + cancel, the future should be cancelled");
        }

        @Test
        @DisplayName("setFuture can be called with null without error")
        void setFutureWithNull() {
            assertDoesNotThrow(() -> run.setFuture(null),
                    "setFuture(null) should not throw");
        }
    }
}
