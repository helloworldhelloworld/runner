package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - 子 Agent 运行状态跟踪")
class SubagentRunTest {

    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("分析数据")
                .agentId("worker")
                .build();
        run = new SubagentRun("run-001", "agent:worker:subagent:run-001", request);
    }

    @Test
    @DisplayName("初始状态为 RUNNING")
    void initialStatusIsRunning() {
        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        assertTrue(run.isRunning());
    }

    @Test
    @DisplayName("complete() 将状态切换为 COMPLETED")
    void completeTransitionsToCompleted() {
        AgentResponse response = new AgentResponse("分析完成");
        run.complete(response);

        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals(response, run.getResult());
    }

    @Test
    @DisplayName("fail() 将状态切换为 FAILED 并记录错误信息")
    void failTransitionsToFailed() {
        run.fail(new RuntimeException("OOM"));

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("OOM", run.getErrorMessage());
    }

    @Test
    @DisplayName("fail() 对 null message 的异常使用类名")
    void failUsesClassNameForNullMessage() {
        run.fail(new NullPointerException());

        assertEquals("NullPointerException", run.getErrorMessage());
    }

    @Test
    @DisplayName("cancel() 将状态切换为 CANCELLED 并取消 Future")
    void cancelTransitionsToCancelled() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        run.setFuture(future);
        run.cancel("用户取消");

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("���户取消", run.getErrorMessage());
        assertTrue(future.isCancelled());
    }

    @Test
    @DisplayName("cancel() 在 future 为 null 时不抛异常")
    void cancelWithNullFutureDoesNotThrow() {
        assertDoesNotThrow(() -> run.cancel("safe cancel"));
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
    }

    @Test
    @DisplayName("getRunId / getSessionKey / getRequest 返回构造参数")
    void accessorsReturnConstructorArgs() {
        assertEquals("run-001", run.getRunId());
        assertEquals("agent:worker:subagent:run-001", run.getSessionKey());
        assertEquals(request, run.getRequest());
    }

    @Test
    @DisplayName("getDurationMs 返回正数")
    void durationIsPositive() {
        assertTrue(run.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("getStartTime 记录创建时刻")
    void startTimeIsRecorded() {
        long now = System.currentTimeMillis();
        assertTrue(run.getStartTime() <= now);
        assertTrue(run.getStartTime() > now - 1000);
    }
}
