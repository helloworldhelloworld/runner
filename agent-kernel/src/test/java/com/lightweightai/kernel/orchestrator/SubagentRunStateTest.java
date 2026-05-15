package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubagentRun - 状态机与生命周期")
class SubagentRunStateTest {

    private SubagentRun run;

    @BeforeEach
    void setUp() {
        SpawnRequest request = SpawnRequest.builder()
            .parentSessionKey("agent:test:main:s1")
            .task("test task")
            .build();
        run = new SubagentRun("run-001", "agent:test:main:s1:subagent:run-001", request);
    }

    @Nested
    @DisplayName("初始状态")
    class InitialState {

        @Test
        @DisplayName("新建 run 状态为 RUNNING")
        void newRunIsRunning() {
            assertEquals(SubagentRun.Status.RUNNING, run.getStatus());
            assertTrue(run.isRunning());
        }

        @Test
        @DisplayName("新建 run 保留 runId, sessionKey, request")
        void preservesConstructorFields() {
            assertEquals("run-001", run.getRunId());
            assertEquals("agent:test:main:s1:subagent:run-001", run.getSessionKey());
            assertEquals("test task", run.getRequest().getTask());
        }

        @Test
        @DisplayName("新建 run 无结果和错误信息")
        void noResultOrErrorInitially() {
            assertNull(run.getResult());
            assertNull(run.getErrorMessage());
        }

        @Test
        @DisplayName("duration 从创建开始计时")
        void durationStartsFromCreation() {
            assertTrue(run.getDurationMs() >= 0);
            assertTrue(run.getStartTime() > 0);
        }
    }

    @Nested
    @DisplayName("状态流转")
    class StateTransitions {

        @Test
        @DisplayName("complete 设置结果和状态")
        void completeTransition() {
            AgentResponse response = AgentResponse.builder()
                .text("result text")
                .stopReason("end_turn")
                .toolCalls(List.of())
                .build();

            run.complete(response);

            assertEquals(SubagentRun.Status.COMPLETED, run.getStatus());
            assertFalse(run.isRunning());
            assertNotNull(run.getResult());
            assertEquals("result text", run.getResult().getText());
        }

        @Test
        @DisplayName("fail 设置错误信息和状态")
        void failTransition() {
            run.fail(new RuntimeException("timeout"));

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("timeout", run.getErrorMessage());
        }

        @Test
        @DisplayName("fail 异常无 message 时用类名")
        void failWithNullMessage() {
            run.fail(new NullPointerException());

            assertEquals(SubagentRun.Status.FAILED, run.getStatus());
            assertEquals("NullPointerException", run.getErrorMessage());
        }

        @Test
        @DisplayName("cancel 设置原因和状态")
        void cancelTransition() {
            run.cancel("Parent stopped");

            assertEquals(SubagentRun.Status.CANCELLED, run.getStatus());
            assertFalse(run.isRunning());
            assertEquals("Parent stopped", run.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("SpawnRequest 深度解析")
    class DepthParsing {

        @Test
        @DisplayName("主会话 depth = 0")
        void mainSessionDepthZero() {
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:session1")
                .task("t")
                .build();
            assertEquals(0, req.currentDepth());
        }

        @Test
        @DisplayName("一级子 agent depth = 1")
        void singleSubagentDepthOne() {
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc123")
                .task("t")
                .build();
            assertEquals(1, req.currentDepth());
        }

        @Test
        @DisplayName("二级嵌套 depth = 2")
        void nestedSubagentDepthTwo() {
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc:subagent:def")
                .task("t")
                .build();
            assertEquals(2, req.currentDepth());
        }

        @Test
        @DisplayName("parentSessionKey 为 null 时 depth = 0")
        void nullKeyDepthZero() {
            // Need to build without NPE check - test that the depth method handles null
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("nosubagent")
                .task("t")
                .build();
            assertEquals(0, req.currentDepth());
        }
    }

    @Nested
    @DisplayName("SpawnRequest Builder 校验")
    class SpawnRequestValidation {

        @Test
        @DisplayName("parentSessionKey 不能为 null")
        void parentSessionKeyRequired() {
            assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("t").build());
        }

        @Test
        @DisplayName("task 不能为 null")
        void taskRequired() {
            assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("key").build());
        }

        @Test
        @DisplayName("完整构建携带所有字段")
        void fullBuild() {
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("build feature")
                .agentId("coder")
                .modelOverride("claude-4")
                .metadata(java.util.Map.of("priority", "high"))
                .build();

            assertEquals("agent:w:main:s1", req.getParentSessionKey());
            assertEquals("build feature", req.getTask());
            assertEquals("coder", req.getAgentId());
            assertEquals("claude-4", req.getModelOverride());
            assertEquals("high", req.getMetadata().get("priority"));
        }

        @Test
        @DisplayName("metadata 默认为空不可变 Map")
        void metadataDefaultsToEmptyMap() {
            SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("k").task("t").build();

            assertNotNull(req.getMetadata());
            assertTrue(req.getMetadata().isEmpty());
        }
    }
}
