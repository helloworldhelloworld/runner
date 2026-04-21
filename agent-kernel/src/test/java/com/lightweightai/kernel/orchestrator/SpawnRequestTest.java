package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - spawn 请求参数与深度计算")
class SpawnRequestTest {

    @Test
    @DisplayName("builder 创建完��� SpawnRequest")
    void builderCreatesFullRequest() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("搜索文档")
                .agentId("worker")
                .modelOverride("claude-3-opus")
                .metadata(Map.of("priority", "high"))
                .build();

        assertEquals("agent:worker:main:s1", request.getParentSessionKey());
        assertEquals("搜索文档", request.getTask());
        assertEquals("worker", request.getAgentId());
        assertEquals("claude-3-opus", request.getModelOverride());
        assertEquals("high", request.getMetadata().get("priority"));
    }

    @Test
    @DisplayName("缺少 parentSessionKey 时抛出 NullPointerException")
    void requiresParentSessionKey() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("task").build());
    }

    @Test
    @DisplayName("缺少 task 时抛出 NullPointerException")
    void requiresTask() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("key").build());
    }

    @Test
    @DisplayName("main session key 深度为 0")
    void mainSessionDepthIsZero() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .build();

        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("一层 subagent 深度为 1")
    void singleSubagentDepthIsOne() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:uuid-1")
                .task("task")
                .build();

        assertEquals(1, request.currentDepth());
    }

    @Test
    @DisplayName("两层嵌套 subagent 深度为 2")
    void nestedSubagentDepthIsTwo() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:uuid-1:subagent:uuid-2")
                .task("task")
                .build();

        assertEquals(2, request.currentDepth());
    }

    @Test
    @DisplayName("metadata 为 null 时使用空 Map")
    void nullMetadataDefaultsToEmpty() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("key")
                .task("task")
                .build();

        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("metadata 是不可变副本")
    void metadataIsDefensiveCopy() {
        Map<String, Object> original = new java.util.HashMap<>();
        original.put("key", "value");

        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("key")
                .task("task")
                .metadata(original)
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                request.getMetadata().put("new", "entry"));
    }
}
