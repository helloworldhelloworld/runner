package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - currentDepth 解析")
class SpawnRequestDepthTest {

    @Test
    @DisplayName("main session key → depth 0")
    void mainSessionIsDepthZero() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:session-123")
                .task("t")
                .build();
        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("一级 subagent session key → depth 1")
    void oneSubagentIsDepthOne() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc123")
                .task("t")
                .build();
        assertEquals(1, request.currentDepth());
    }

    @Test
    @DisplayName("二级 subagent session key → depth 2")
    void twoSubagentsIsDepthTwo() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc:subagent:def")
                .task("t")
                .build();
        assertEquals(2, request.currentDepth());
    }

    @Test
    @DisplayName("三级 subagent session key → depth 3")
    void threeSubagentsIsDepthThree() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1:subagent:a:subagent:b:subagent:c")
                .task("t")
                .build();
        assertEquals(3, request.currentDepth());
    }

    @Test
    @DisplayName("不含 subagent 的自定义 key → depth 0")
    void noSubagentMarkerIsDepthZero() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("custom-key-no-subagent")
                .task("t")
                .build();
        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("builder 必需字段 — parentSessionKey 为 null 抛异常")
    void builderRequiresParentSessionKey() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("t").build());
    }

    @Test
    @DisplayName("builder 必需字段 — task 为 null 抛异常")
    void builderRequiresTask() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("k").build());
    }

    @Test
    @DisplayName("agentId 可选 — 不设置时为 null")
    void agentIdIsOptional() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("k")
                .task("t")
                .build();
        assertNull(request.getAgentId());
    }

    @Test
    @DisplayName("metadata 不设置时为空 map")
    void metadataDefaultsToEmptyMap() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("k")
                .task("t")
                .build();
        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("metadata 是不可变的防御拷贝")
    void metadataIsImmutable() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("k")
                .task("t")
                .metadata(java.util.Map.of("key", "val"))
                .build();
        assertThrows(UnsupportedOperationException.class, () ->
                request.getMetadata().put("new", "entry"));
    }
}
