package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitSubagentTool - 等待子代理完成")
class WaitSubagentToolTest {

    @Test
    @DisplayName("工具名称为 wait_subagent")
    void toolName() {
        WaitSubagentTool tool = new WaitSubagentTool(mockRuntime());
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("schema 包含 runIds(required) 和 timeoutSeconds")
    void schemaParams() {
        WaitSubagentTool tool = new WaitSubagentTool(mockRuntime());
        ToolSchema schema = tool.getSchema();
        assertTrue(schema.getProperties().containsKey("runIds"));
        assertTrue(schema.getProperties().containsKey("timeoutSeconds"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.toMap().get("required");
        assertTrue(required.contains("runIds"));
    }

    @Nested
    @DisplayName("execute 行为")
    class ExecuteTests {

        @Test
        @DisplayName("等待已完成的 run 返回结果文本")
        void completedRunReturnsResult() {
            SpawnRequest req = SpawnRequest.builder()
                    .parentSessionKey("p:main:s1")
                    .task("analyze")
                    .build();
            SubagentRun run = new SubagentRun("run1", "p:main:s1:subagent:run1", req);
            run.complete(new AgentResponse("Analysis complete"));

            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) {
                    return true;
                }

                @Override
                public SubagentRun getRunOrCompleted(String runId) {
                    return run;
                }
            };

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "run1"));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Analysis complete"));
        }

        @Test
        @DisplayName("多个 runIds 以逗号分隔")
        void multipleRunIdsCommaSeparated() {
            SpawnRequest req = SpawnRequest.builder()
                    .parentSessionKey("p:main:s1")
                    .task("task")
                    .build();
            SubagentRun run1 = new SubagentRun("a", "key:a", req);
            run1.complete(new AgentResponse("Result A"));
            SubagentRun run2 = new SubagentRun("b", "key:b", req);
            run2.complete(new AgentResponse("Result B"));

            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) {
                    return true;
                }

                @Override
                public SubagentRun getRunOrCompleted(String runId) {
                    return "a".equals(runId.trim()) ? run1 : run2;
                }
            };

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "a,b"));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("Result A"));
            assertTrue(result.getContent().contains("Result B"));
        }

        @Test
        @DisplayName("run 不存在返回 Not found")
        void notFoundRun() {
            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) {
                    return true;
                }

                @Override
                public SubagentRun getRunOrCompleted(String runId) {
                    return null;
                }
            };

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "missing"));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Not found"));
        }

        @Test
        @DisplayName("FAILED run 包含错误信息")
        void failedRunShowsError() {
            SpawnRequest req = SpawnRequest.builder()
                    .parentSessionKey("p:main:s1")
                    .task("failing task")
                    .build();
            SubagentRun run = new SubagentRun("f1", "key:f1", req);
            run.fail(new RuntimeException("out of memory"));

            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) {
                    return true;
                }

                @Override
                public SubagentRun getRunOrCompleted(String runId) {
                    return run;
                }
            };

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            ToolResult result = tool.execute(Map.of("runIds", "f1"));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("FAILED"));
            assertTrue(result.getContent().contains("out of memory"));
        }

        @Test
        @DisplayName("默认超时为 60 秒")
        void defaultTimeout() {
            java.util.concurrent.atomic.AtomicLong capturedTimeout = new java.util.concurrent.atomic.AtomicLong();

            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public boolean waitForCompletion(String runId, long timeout, TimeUnit unit) {
                    capturedTimeout.set(unit.toSeconds(timeout));
                    return true;
                }

                @Override
                public SubagentRun getRunOrCompleted(String runId) {
                    return null;
                }
            };

            WaitSubagentTool tool = new WaitSubagentTool(runtime);
            tool.execute(Map.of("runIds", "x"));

            assertEquals(60, capturedTimeout.get());
        }
    }

    private SubagentRuntime mockRuntime() {
        return new SubagentRuntime(null, null, 5);
    }
}
