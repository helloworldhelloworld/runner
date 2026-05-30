package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaitSubagentTool — wait_subagent 工具单元测试")
class WaitSubagentToolTest {

    private SubagentRuntime runtime;
    private WaitSubagentTool tool;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I work")
                .maxSpawnDepth(1)
                .build());

        LLMProvider provider = new SpawnSubagentToolTest.InstantProvider();
        AgentFactory factory = new AgentFactory(provider, new SpawnSubagentToolTest.StubMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 10);
        tool = new WaitSubagentTool(runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Test
    @DisplayName("getName 返回 wait_subagent")
    void nameIsWaitSubagent() {
        assertEquals("wait_subagent", tool.getName());
    }

    @Test
    @DisplayName("getSchema 包含 runIds 必填和 timeoutSeconds 可选参数")
    void schemaContainsExpectedProperties() {
        ToolSchema schema = tool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(props.containsKey("runIds"));
        assertTrue(props.containsKey("timeoutSeconds"));
    }

    @Test
    @DisplayName("等待已完成的 subagent 返回成功结果含 runId")
    void waitCompletedSubagentReturnsSuccess() throws InterruptedException {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("fast task")
                .agentId("worker")
                .build(), e -> {});

        Thread.sleep(300);

        ToolResult result = tool.execute(Map.of("runIds", runId, "timeoutSeconds", 5));
        assertFalse(result.isError(), "wait should succeed for completed subagent");
        assertTrue(result.getContent().contains(runId),
                "result should contain runId: " + result.getContent());
    }

    @Test
    @DisplayName("等待多个 subagent — 逗号分隔 runIds")
    void waitMultipleRunIds() throws InterruptedException {
        String runId1 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task A").agentId("worker").build(), e -> {});
        String runId2 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task B").agentId("worker").build(), e -> {});

        Thread.sleep(300);

        ToolResult result = tool.execute(Map.of(
                "runIds", runId1 + "," + runId2,
                "timeoutSeconds", 5));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains(runId1));
        assertTrue(result.getContent().contains(runId2));
    }

    @Test
    @DisplayName("等待超时返回错误含 timed out 标记")
    void waitTimeoutReturnsError() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("slow").systemPrompt("slow").maxSpawnDepth(1).build());
        SubagentRuntime slowRuntime = new SubagentRuntime(
                new AgentFactory(new SpawnSubagentToolTest.SlowProvider(10000),
                        new SpawnSubagentToolTest.StubMemory(), new ToolRegistry()),
                registry, 5);

        String runId = slowRuntime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:slow:main:s1")
                .task("slow task").agentId("slow").build(), e -> {});

        WaitSubagentTool waitTool = new WaitSubagentTool(slowRuntime);
        ToolResult result = waitTool.execute(Map.of("runIds", runId, "timeoutSeconds", 1));

        assertTrue(result.isError(), "should be error on timeout");
        assertTrue(result.getContent().contains("timed out"),
                "should contain 'timed out', actual: " + result.getContent());

        slowRuntime.stopAll();
        slowRuntime.shutdown();
    }

    @Test
    @DisplayName("getDescription 非空且包含 run IDs")
    void descriptionMentionsRunIds() {
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().toLowerCase().contains("run id"));
    }
}
