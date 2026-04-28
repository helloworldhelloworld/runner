package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - Subagent 运行状态追踪")
class SubagentRunTest {

    private SpawnRequest request;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("测试任务")
                .build();
    }

    @Nested
    @DisplayName("初始状态")
    class InitialStateTests {

        @Test
        @DisplayName("新建 run 状态为 RUNNING")
        void initialStatusIsRunning() {
            SubagentRun run = new SubagentRun("run-1", "agent:w:main:s1:subagent:run-1", request);

            assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
            assertTrue(run.isRunning());
        }

        @Test
        @DisplayName("新建 run 结果和错误为 null")
        void initialResultAndErrorAreNull() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            assertNull(run.getResult());
            assertNull(run.getErrorMessage());
        }

        @Test
        @DisplayName("新建 run 保留 runId、sessionKey 和 request")
        void preservesConstructorArgs() {
            SubagentRun run = new SubagentRun("run-abc", "sess-key", request);

            assertEquals("run-abc", run.getRunId());
            assertEquals("sess-key", run.getSessionKey());
            assertSame(request, run.getRequest());
        }

        @Test
        @DisplayName("startTime 为正值且 durationMs 递增")
        void startTimeAndDuration() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            assertTrue(run.getStartTime() > 0);
            assertTrue(run.getDurationMs() >= 0);
        }
    }

    @Nested
    @DisplayName("状态转换 - complete")
    class CompleteTests {

        @Test
        @DisplayName("complete 设置状态为 COMPLETED 并保留结果")
        void completeTransition() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);
            AgentResponse response = new AgentResponse("计算结果是 42");

            run.complete(response);

            assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
            assertFalse(run.isRunning());
            assertSame(response, run.getResult());
            assertEquals("计算结果是 42", run.getResult().getText());
        }

        @Test
        @DisplayName("complete 后 errorMessage 仍为 null")
        void completeHasNoError() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);
            run.complete(new AgentResponse("done"));

            assertNull(run.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("状态转换 - fail")
    class FailTests {

        @Test
        @DisplayName("fail 设置状态为 FAILED 并保留错误消息")
        void failTransition() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            run.fail(new RuntimeException("connection timeout"));

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("connection timeout", run.getErrorMessage());
            assertNull(run.getResult());
        }

        @Test
        @DisplayName("fail 异常无 message 时使用类名")
        void failWithNullMessage() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            run.fail(new NullPointerException());

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertEquals("NullPointerException", run.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("状态转换 - cancel")
    class CancelTests {

        @Test
        @DisplayName("cancel 设置状态为 CANCELLED 并保留原因")
        void cancelTransition() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            run.cancel("Cascade stop from parent");

            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("Cascade stop from parent", run.getErrorMessage());
        }

        @Test
        @DisplayName("cancel 取消关联的 Future")
        void cancelCancelsFuture() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.setFuture(future);

            run.cancel("stopped");

            assertTrue(future.isCancelled());
        }

        @Test
        @DisplayName("cancel 未设置 Future 时不抛异常")
        void cancelWithoutFutureIsNoOp() {
            SubagentRun run = new SubagentRun("run-1", "sess-1", request);

            assertDoesNotThrow(() -> run.cancel("stopped"));
            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        }
    }
}
