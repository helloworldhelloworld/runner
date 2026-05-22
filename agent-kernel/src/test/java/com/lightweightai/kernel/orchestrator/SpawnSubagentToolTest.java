package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool - 子代理 spawn 工具")
class SpawnSubagentToolTest {

    @Test
    @DisplayName("工具名称和描述")
    void nameAndDescription() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                mockRuntime("run-1"), "parent:main:s1", e -> {});
        assertEquals("spawn_subagent", tool.getName());
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("sub-agent"));
    }

    @Test
    @DisplayName("schema 包含 task(required), agentId, model 参数")
    void schemaContainsRequiredParams() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                mockRuntime("run-1"), "p:main:s1", e -> {});
        ToolSchema schema = tool.getSchema();

        Map<String, Object> props = schema.getProperties();
        assertTrue(props.containsKey("task"));
        assertTrue(props.containsKey("agentId"));
        assertTrue(props.containsKey("model"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.toMap().get("required");
        assertTrue(required.contains("task"));
    }

    @Nested
    @DisplayName("execute 执行")
    class ExecuteTests {

        @Test
        @DisplayName("成功 spawn 返回 runId")
        void successfulSpawnReturnsRunId() {
            AtomicReference<SpawnRequest> captured = new AtomicReference<>();
            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public String spawn(SpawnRequest req, Consumer<StreamEvent> announcer) {
                    captured.set(req);
                    return "run-abc";
                }
            };

            SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "agent:default:main:s1", e -> {});
            ToolResult result = tool.execute(Map.of(
                    "task", "Summarize document",
                    "agentId", "summarizer",
                    "model", "claude-3-5-sonnet"
            ));

            assertFalse(result.isError());
            assertTrue(result.getContent().contains("run-abc"));
            assertEquals("Summarize document", captured.get().getTask());
            assertEquals("summarizer", captured.get().getAgentId());
            assertEquals("claude-3-5-sonnet", captured.get().getModelOverride());
        }

        @Test
        @DisplayName("runtime 抛 IllegalStateException 时返回 error")
        void runtimeErrorReturnsToolError() {
            SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
                @Override
                public String spawn(SpawnRequest req, Consumer<StreamEvent> announcer) {
                    throw new IllegalStateException("Max concurrent reached");
                }
            };

            SpawnSubagentTool tool = new SpawnSubagentTool(runtime, "p:main:s1", e -> {});
            ToolResult result = tool.execute(Map.of("task", "do stuff"));

            assertTrue(result.isError());
            assertTrue(result.getContent().contains("Max concurrent"));
        }
    }

    private SubagentRuntime mockRuntime(String returnRunId) {
        return new SubagentRuntime(null, null, 5) {
            @Override
            public String spawn(SpawnRequest req, Consumer<StreamEvent> announcer) {
                return returnRunId;
            }
        };
    }
}
