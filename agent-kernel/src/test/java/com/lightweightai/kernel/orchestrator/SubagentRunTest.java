package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - 状态跟踪模型")
class SubagentRunTest {

    private SpawnRequest testRequest() {
        return SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("测试任务")
                .build();
    }

    @Test
    @DisplayName("新创建的 run 状态为 RUNNING")
    void newRunIsRunning() {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());
        assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
        assertTrue(run.isRunning());
    }

    @Test
    @DisplayName("complete() 设置状态为 COMPLETED 并保存结果")
    void completeTransition() {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());
        AgentResponse response = AgentResponse.builder().text("done").build();

        run.complete(response);

        assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isRunning());
        assertNotNull(run.getResult());
        assertEquals("done", run.getResult().getText());
    }

    @Test
    @DisplayName("fail() 设置状态为 FAILED 并保存错误消息")
    void failTransition() {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());

        run.fail(new RuntimeException("something broke"));

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("something broke", run.getErrorMessage());
    }

    @Test
    @DisplayName("fail() 对无消息的异常使用类名")
    void failWithNullMessage() {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());

        run.fail(new NullPointerException());

        assertEquals(SubagentRun.Status.FAILED, run.getStatus());
        assertEquals("NullPointerException", run.getErrorMessage());
    }

    @Test
    @DisplayName("cancel() 设置状态为 CANCELLED 并保存原因")
    void cancelTransition() {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());

        run.cancel("Cascade stop from parent");

        assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
        assertFalse(run.isRunning());
        assertEquals("Cascade stop from parent", run.getErrorMessage());
    }

    @Test
    @DisplayName("durationMs 返回经过的时间")
    void durationMsReturnsElapsed() throws InterruptedException {
        SubagentRun run = new SubagentRun("r1", "key1", testRequest());
        Thread.sleep(50);
        assertTrue(run.getDurationMs() >= 40, "Duration should be >= 40ms");
    }

    @Test
    @DisplayName("Transmission: runId, sessionKey, request 在构造时正确保存")
    void constructorPayloadPreserved() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("the task")
                .agentId("worker")
                .build();

        SubagentRun run = new SubagentRun("run-42", "agent:worker:main:s1:subagent:run-42", request);

        assertEquals("run-42", run.getRunId());
        assertEquals("agent:worker:main:s1:subagent:run-42", run.getSessionKey());
        assertSame(request, run.getRequest());
        assertEquals("the task", run.getRequest().getTask());
    }
}
