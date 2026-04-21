package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - spawn request parameters and depth calculation")
class SpawnRequestTest {

    @Test
    @DisplayName("builder creates complete SpawnRequest")
    void builderCreatesFullRequest() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("search documents")
                .agentId("worker")
                .modelOverride("claude-3-opus")
                .metadata(Map.of("priority", "high"))
                .build();

        assertEquals("agent:worker:main:s1", request.getParentSessionKey());
        assertEquals("search documents", request.getTask());
        assertEquals("worker", request.getAgentId());
        assertEquals("claude-3-opus", request.getModelOverride());
        assertEquals("high", request.getMetadata().get("priority"));
    }

    @Test
    @DisplayName("missing parentSessionKey throws NullPointerException")
    void requiresParentSessionKey() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("task").build());
    }

    @Test
    @DisplayName("missing task throws NullPointerException")
    void requiresTask() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("key").build());
    }

    @Test
    @DisplayName("main session key has depth 0")
    void mainSessionDepthIsZero() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task")
                .build();

        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("single subagent has depth 1")
    void singleSubagentDepthIsOne() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:uuid-1")
                .task("task")
                .build();

        assertEquals(1, request.currentDepth());
    }

    @Test
    @DisplayName("nested subagent has depth 2")
    void nestedSubagentDepthIsTwo() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:uuid-1:subagent:uuid-2")
                .task("task")
                .build();

        assertEquals(2, request.currentDepth());
    }

    @Test
    @DisplayName("null metadata defaults to empty Map")
    void nullMetadataDefaultsToEmpty() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("key")
                .task("task")
                .build();

        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("metadata is an immutable defensive copy")
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
