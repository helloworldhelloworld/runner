package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - Subagent 运行状态跟踪")
class SubagentRunTest {

    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("test task")
                .agentId("worker")
                .build();
        run = new SubagentRun("run-001", "agent:worker:main:s1:subagent:run-001", request);
    }

    @Nested
    @DisplayName("初始状态")
    class InitialStateTests {

        @Test
        @DisplayName("新建 run 状态为 RUNNING")
        void shouldStartAsRunning() {
            assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
            assertTrue(run.isRunning());
        }

        @Test
        @DisplayName("新建 run 的 result 和 errorMessage 为 null")
        void shouldHaveNullResultAndError() {
            assertNull(run.getResult());
            assertNull(run.getErrorMessage());
        }

        @Test
        @DisplayName("runId 和 sessionKey 正确返回")
        void shouldReturnIdAndSessionKey() {
            assertEquals("run-001", run.getRunId());
            assertEquals("agent:worker:main:s1:subagent:run-001", run.getSessionKey());
        }

        @Test
        @DisplayName("request 关联正确")
        void shouldReturnAssociatedRequest() {
            assertSame(request, run.getRequest());
            assertEquals("test task", run.getRequest().getTask());
        }

        @Test
        @DisplayName("startTime 在构造时设置")
        void shouldRecordStartTime() {
            long now = System.currentTimeMillis();
            assertTrue(run.getStartTime() <= now);
            assertTrue(run.getStartTime() > now - 1000);
        }
    }

    @Nested
    @DisplayName("状态转换")
    class StateTransitionTests {

        @Test
        @DisplayName("complete 转换为 COMPLETED 并设置 result")
        void shouldTransitionToCompleted() {
            AgentResponse response = AgentResponse.builder()
                    .text("result text")
                    .stopReason("end_turn")
                    .build();

            run.complete(response);

            assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
            assertFalse(run.isRunning());
            assertNotNull(run.getResult());
            assertEquals("result text", run.getResult().getText());
            assertEquals("end_turn", run.getResult().getStopReason());
        }

        @Test
        @DisplayName("fail 转换为 FAILED 并设置 errorMessage")
        void shouldTransitionToFailed() {
            run.fail(new RuntimeException("something broke"));

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("something broke", run.getErrorMessage());
            assertNull(run.getResult());
        }

        @Test
        @DisplayName("fail 处理 null message 的异常")
        void shouldHandleExceptionWithNullMessage() {
            run.fail(new NullPointerException());

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertEquals("NullPointerException", run.getErrorMessage());
        }

        @Test
        @DisplayName("cancel 转换为 CANCELLED 并设置 reason")
        void shouldTransitionToCancelled() {
            run.cancel("user requested stop");

            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("user requested stop", run.getErrorMessage());
        }

        @Test
        @DisplayName("cancel 取消关联的 Future")
        void cancelShouldCancelFuture() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.setFuture(future);

            run.cancel("stop");

            assertTrue(future.isCancelled());
        }

        @Test
        @DisplayName("cancel 无 Future 时不抛异常")
        void cancelShouldHandleNullFuture() {
            assertDoesNotThrow(() -> run.cancel("stop"));
            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        }
    }

    @Nested
    @DisplayName("durationMs 计算")
    class DurationTests {

        @Test
        @DisplayName("durationMs 在 RUNNING 时递增")
        void durationShouldIncreaseWhileRunning() throws InterruptedException {
            long d1 = run.getDurationMs();
            Thread.sleep(50);
            long d2 = run.getDurationMs();

            assertTrue(d2 > d1, "Duration should increase: d1=" + d1 + ", d2=" + d2);
        }

        @Test
        @DisplayName("durationMs 在 COMPLETED 后仍反映当前时间差")
        void durationShouldReflectElapsedAfterComplete() throws InterruptedException {
            Thread.sleep(20);
            run.complete(AgentResponse.builder().text("done").build());

            long duration = run.getDurationMs();
            assertTrue(duration >= 20, "Duration should be at least 20ms: " + duration);
        }
    }
}
