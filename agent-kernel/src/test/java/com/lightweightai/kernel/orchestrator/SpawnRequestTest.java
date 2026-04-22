package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - Subagent spawn request builder and depth parsing")
class SpawnRequestTest {

    @Test
    @DisplayName("Builder creates request with all fields populated")
    void builderCreatesRequestWithAllFields() {
        Map<String, Object> meta = Map.of("key", "value");

        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:main:main:sess1")
                .task("summarize document")
                .agentId("worker")
                .modelOverride("claude-3-opus")
                .metadata(meta)
                .build();

        assertEquals("agent:main:main:sess1", request.getParentSessionKey());
        assertEquals("summarize document", request.getTask());
        assertEquals("worker", request.getAgentId());
        assertEquals("claude-3-opus", request.getModelOverride());
        assertEquals("value", request.getMetadata().get("key"));
    }

    @Test
    @DisplayName("Null parentSessionKey throws NullPointerException")
    void nullParentSessionKeyThrowsNPE() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder()
                        .task("some task")
                        .build());

        assertEquals("parentSessionKey required", ex.getMessage());
    }

    @Test
    @DisplayName("Null task throws NullPointerException")
    void nullTaskThrowsNPE() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder()
                        .parentSessionKey("agent:main:main:sess1")
                        .build());

        assertEquals("task required", ex.getMessage());
    }

    @Test
    @DisplayName("currentDepth returns 0 for main session key without subagent segments")
    void currentDepthReturnsZeroForMainSessionKey() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:x:main:s1")
                .task("task")
                .build();

        assertEquals(0, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth returns 1 for one-level subagent session key")
    void currentDepthReturnsOneForSingleSubagent() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:x:main:s1:subagent:abc")
                .task("task")
                .build();

        assertEquals(1, request.currentDepth());
    }

    @Test
    @DisplayName("currentDepth returns 2 for nested subagent session key")
    void currentDepthReturnsTwoForNestedSubagent() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:x:main:s1:subagent:abc:subagent:def")
                .task("task")
                .build();

        assertEquals(2, request.currentDepth());
    }

    @Test
    @DisplayName("Metadata is an immutable copy - modifying original map does not affect request")
    void metadataIsImmutableCopy() {
        Map<String, Object> mutableMeta = new HashMap<>();
        mutableMeta.put("key1", "original");

        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:main:main:s1")
                .task("task")
                .metadata(mutableMeta)
                .build();

        mutableMeta.put("key2", "added-after");

        assertEquals("original", request.getMetadata().get("key1"));
        assertNull(request.getMetadata().get("key2"));
    }

    @Test
    @DisplayName("Null metadata defaults to empty map")
    void nullMetadataDefaultsToEmptyMap() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:main:main:s1")
                .task("task")
                .metadata(null)
                .build();

        assertNotNull(request.getMetadata());
        assertTrue(request.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("Optional fields agentId and modelOverride can be null")
    void optionalFieldsCanBeNull() {
        SpawnRequest request = SpawnRequest.builder()
                .parentSessionKey("agent:main:main:s1")
                .task("task")
                .build();

        assertNull(request.getAgentId());
        assertNull(request.getModelOverride());
    }
}
