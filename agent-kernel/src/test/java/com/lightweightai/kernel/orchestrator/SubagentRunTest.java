package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - 子 agent 执行状态跟踪")
class SubagentRunTest {

    private SpawnRequest request;
    private SubagentRun run;

    @BeforeEach
    void setUp() {
        request = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("计算")
                .build();
        run = new SubagentRun("run-001", "agent:w:main:s1:subagent:run-001", request);
    }

    @Test
    @DisplayName("初始状态为 RUNNING")
    void initialStatusIsRunning() {
        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        assertTrue(run.isRunning());
        assertNull(run.getResult());
        assertNull(run.getErrorMessage());
    }

    @Test
    @DisplayName("getters 返回构造参数")
    void gettersReturnCorrectValues() {
        assertEquals("run-001", run.getRunId());
        assertEquals("agent:w:main:s1:subagent:run-001", run.getSessionKey());
        assertSame(request, run.getRequest());
        assertTrue(run.getStartTime() > 0);
        assertTrue(run.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("complete() 转为 COMPLETED 并记录结果")
    void completeTransition() {
        AgentResponse response = AgentResponse.builder().text("42").build();
        run.complete(response);

        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
        assertSame(response, run.getResult());
        assertEquals("42", run.getResult().getText());
    }

    @Test
    @DisplayName("fail() 转为 FAILED 并记录错误信息")
    void failTransition() {
        run.fail(new RuntimeException("OOM"));

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("OOM", run.getErrorMessage());
    }

    @Test
    @DisplayName("fail() 处理无消息的异常")
    void failWithNullMessage() {
        run.fail(new NullPointerException());

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertEquals("NullPointerException", run.getErrorMessage());
    }

    @Test
    @DisplayName("cancel() 转为 CANCELLED 并取消 future")
    void cancelTransition() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        run.setFuture(future);

        run.cancel("Cascade stop");

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("Cascade stop", run.getErrorMessage());
        assertTrue(future.isCancelled());
    }

    @Test
    @DisplayName("cancel() 在无 future 时不抛异常")
    void cancelWithoutFuture() {
        assertDoesNotThrow(() -> run.cancel("no future"));
        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
    }

    @Test
    @DisplayName("durationMs 随时间增长")
    void durationGrows() throws InterruptedException {
        long d1 = run.getDurationMs();
        Thread.sleep(50);
        long d2 = run.getDurationMs();
        assertTrue(d2 > d1, "duration should increase over time");
    }
}
