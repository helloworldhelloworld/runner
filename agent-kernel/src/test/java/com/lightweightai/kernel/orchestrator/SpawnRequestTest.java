package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - spawn request parameters and depth parsing")
class SpawnRequestTest {

    @Test
    @DisplayName("builder sets all fields correctly")
    void testBuilderFields() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("do work")
                .agentId("worker")
                .modelOverride("claude-3-opus")
                .metadata(Map.of("priority", "high"))
                .build();

        assertEquals("agent:worker:main:s1", request.getParentSessionKey());
        assertEquals("do work", request.getTask());
        assertEquals("worker", request.getAgentId());
        assertEquals("claude-3-opus", request.getModelOverride());
        assertEquals("high", request.getMetadata().get("priority"));
    }

    @Test
    @DisplayName("parentSessionKey is required")
    void testParentSessionKeyRequired() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder()
                        .task("work")
                        .build());
    }

    @Test
    @DisplayName("task is required")
    void testTaskRequired() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder()
                        .parentSessionKey("agent:worker:main:s1")
                        .build());
    }

    @Test
    @DisplayName("agentId is optional (null when not set)")
    void testOptionalAgentId() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("work")
                .build();

        assertNull(request.getAgentId());
    }

    @Test
    @DisplayName("metadata defaults to empty map when not set")
    void testDefaultEmptyMetadata() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("work")
                .build();

        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("metadata is immutable (defensive copy)")
    void testImmutableMetadata() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("work")
                .metadata(Map.of("k", "v"))
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                request.getMetadata().put("new", "value"));
    }

    @Test
    @DisplayName("currentDepth=0 for main session key")
    void testDepthZero() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("work")
                .build();

        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth=1 for first-level subagent session key")
    void testDepthOne() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:abc123")
                .task("work")
                .build();

        assertEquals(1, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth=2 for nested subagent session key")
    void testDepthTwo() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:subagent:abc:subagent:def")
                .task("work")
                .build();

        assertEquals(2, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth=3 for deeply nested session key")
    void testDepthThree() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:orch:subagent:a:subagent:b:subagent:c")
                .task("work")
                .build();

        assertEquals(3, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth handles main session with subagent prefix")
    void testDepthMainWithSubagent() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:run1")
                .task("work")
                .build();

        assertEquals(1, request.currentDepth());
    }
}
