package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool — list_subagents 工具单元测试")
class ListSubagentsToolTest {

    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        SubagentRuntime r = new SubagentRuntime(
                new AgentFactory(new SpawnSubagentToolTest.SlowProvider(5000),
                        new SpawnSubagentToolTest.StubMemory(), new ToolRegistry()),
                registry, 10);
        runtime = r;
        tool = new ListSubagentsTool(runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.stopAll();
        runtime.shutdown();
    }

    @Test
    @DisplayName("getName 返回 list_subagents")
    void nameIsListSubagents() {
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("getSchema 返回空 schema (无参数)")
    void schemaIsEmpty() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        assertTrue(schema.getProperties().isEmpty());
    }

    @Test
    @DisplayName("无活跃 subagent 时返回 'No active'")
    void noActiveSubagentsMessage() {
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("idle").systemPrompt("idle").maxSpawnDepth(1).build());
        SubagentRuntime emptyRuntime = new SubagentRuntime(
                new AgentFactory(new SpawnSubagentToolTest.InstantProvider(),
                        new SpawnSubagentToolTest.StubMemory(), new ToolRegistry()),
                reg, 5);

        ListSubagentsTool emptyTool = new ListSubagentsTool(emptyRuntime);
        ToolResult result = emptyTool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("No active"),
                "should say 'No active', actual: " + result.getContent());

        emptyRuntime.shutdown();
    }

    @Test
    @DisplayName("有活跃 subagent 时返回 task 信息")
    void activeSubagentsShowTask() {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("analyze data")
                .agentId("worker").build(), e -> {});

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("analyze data"),
                "should show task description, actual: " + result.getContent());
        assertTrue(result.getContent().contains("Active sub-agents"));
    }

    @Test
    @DisplayName("多个活跃 subagent 全部列出")
    void multipleActiveSubagentsListed() {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-alpha").agentId("worker").build(), e -> {});
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-beta").agentId("worker").build(), e -> {});

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("task-alpha"));
        assertTrue(result.getContent().contains("task-beta"));
        assertTrue(result.getContent().contains("2"));
    }
}
