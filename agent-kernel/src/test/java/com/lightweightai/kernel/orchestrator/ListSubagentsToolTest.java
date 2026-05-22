package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool - 列出活跃子代理")
class ListSubagentsToolTest {

    @Test
    @DisplayName("工具名称为 list_subagents")
    void toolName() {
        ListSubagentsTool tool = new ListSubagentsTool(mockEmptyRuntime());
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("schema 为空 (无参数)")
    void emptySchema() {
        ListSubagentsTool tool = new ListSubagentsTool(mockEmptyRuntime());
        ToolSchema schema = tool.getSchema();
        assertTrue(schema.getProperties().isEmpty());
    }

    @Test
    @DisplayName("无活跃 run 时返回提示信息")
    void noActiveRuns() {
        ListSubagentsTool tool = new ListSubagentsTool(mockEmptyRuntime());
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("No active sub-agents"));
    }

    @Test
    @DisplayName("有活跃 run 时返回列表")
    void withActiveRuns() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:default:main:s1")
                .task("Translate document")
                .build();
        SubagentRun run = new SubagentRun("abc123", "agent:default:main:s1:subagent:abc123", req);

        SubagentRuntime runtime = new SubagentRuntime(null, null, 5) {
            @Override
            public List<SubagentRun> getActiveRuns() {
                return List.of(run);
            }
        };

        ListSubagentsTool tool = new ListSubagentsTool(runtime);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("abc123"));
        assertTrue(result.getContent().contains("Translate document"));
        assertTrue(result.getContent().contains("RUNNING"));
        assertTrue(result.getContent().contains("1"));
    }

    private SubagentRuntime mockEmptyRuntime() {
        return new SubagentRuntime(null, null, 5) {
            @Override
            public List<SubagentRun> getActiveRuns() {
                return List.of();
            }
        };
    }
}
