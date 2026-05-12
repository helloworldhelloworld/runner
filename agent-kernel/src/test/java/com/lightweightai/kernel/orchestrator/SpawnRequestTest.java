package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - depth 解析与构建")
class SpawnRequestTest {

    @Test
    @DisplayName("main session key → depth 0")
    void mainSessionDepthZero() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:sess-1")
                .task("task")
                .build();
        assertEquals(0, req.currentDepth());
    }

    @Test
    @DisplayName("一层 subagent → depth 1")
    void oneSubagentDepthOne() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:sess-1:subagent:abc123")
                .task("task")
                .build();
        assertEquals(1, req.currentDepth());
    }

    @Test
    @DisplayName("两层 subagent → depth 2")
    void twoSubagentDepthTwo() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:sess-1:subagent:abc123:subagent:def456")
                .task("task")
                .build();
        assertEquals(2, req.currentDepth());
    }

    @Test
    @DisplayName("三层 subagent → depth 3")
    void threeSubagentDepthThree() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:o:main:s:subagent:a:subagent:b:subagent:c")
                .task("task")
                .build();
        assertEquals(3, req.currentDepth());
    }

    @Test
    @DisplayName("必填字段校验：parentSessionKey 不可为 null")
    void parentSessionKeyRequired() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("task").build());
    }

    @Test
    @DisplayName("必填字段校验：task 不可为 null")
    void taskRequired() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("key").build());
    }

    @Test
    @DisplayName("可选字段 agentId 和 modelOverride 正确传递")
    void optionalFields() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .agentId("custom-agent")
                .modelOverride("gpt-4")
                .build();

        assertEquals("custom-agent", req.getAgentId());
        assertEquals("gpt-4", req.getModelOverride());
        assertEquals("task", req.getTask());
        assertEquals("agent:worker:main:s1", req.getParentSessionKey());
    }

    @Test
    @DisplayName("metadata 是不可变副本")
    void metadataImmutableCopy() {
        java.util.HashMap<String, Object> original = new java.util.HashMap<>();
        original.put("key", "value");

        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .metadata(original)
                .build();

        original.put("extra", "added-after");
        assertFalse(req.getMetadata().containsKey("extra"),
                "Metadata should be a defensive copy");
        assertEquals("value", req.getMetadata().get("key"));
    }

    @Test
    @DisplayName("无 metadata 时返回空不可变 Map")
    void emptyMetadataWhenNotSet() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .build();

        assertNotNull(req.getMetadata());
        assertTrue(req.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("agentId 为 null 时 getAgentId 返回 null")
    void agentIdNullable() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .build();
        assertNull(req.getAgentId());
    }
}
