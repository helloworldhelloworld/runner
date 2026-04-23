package com.lightweightai.kernel.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnRequest - spawn 请求参数 & 嵌套深度解析")
class SpawnRequestTest {

    @Test
    @DisplayName("必填字段缺失抛出 NullPointerException")
    void requiredFieldsMissing() {
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().task("do it").build());
        assertThrows(NullPointerException.class, () ->
                SpawnRequest.builder().parentSessionKey("k").build());
    }

    @Test
    @DisplayName("构建完整请求并读取所有字段")
    void fullBuild() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1")
                .task("搜索文档")
                .agentId("worker")
                .modelOverride("claude-opus")
                .metadata(Map.of("priority", "high"))
                .build();

        assertEquals("agent:w:main:s1", req.getParentSessionKey());
        assertEquals("搜索文档", req.getTask());
        assertEquals("worker", req.getAgentId());
        assertEquals("claude-opus", req.getModelOverride());
        assertEquals("high", req.getMetadata().get("priority"));
    }

    @Test
    @DisplayName("metadata 默认为空 Map（不是 null）")
    void metadataDefaultsToEmpty() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("k").task("t").build();
        assertNotNull(req.getMetadata());
        assertTrue(req.getMetadata().isEmpty());
    }

    @Test
    @DisplayName("metadata 防御性拷贝：外部修改不影响内部")
    void metadataIsDefensiveCopy() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("k").task("t")
                .metadata(Map.of("a", "1"))
                .build();
        assertThrows(UnsupportedOperationException.class,
                () -> req.getMetadata().put("b", "2"));
    }

    // ==================== currentDepth 解析测试 ====================

    @Test
    @DisplayName("main session key → depth 0")
    void depthZeroForMainSession() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:session123")
                .task("t").build();
        assertEquals(0, req.currentDepth());
    }

    @Test
    @DisplayName("一层 subagent → depth 1")
    void depthOneForSingleSubagent() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1:subagent:abc123")
                .task("t").build();
        assertEquals(1, req.currentDepth());
    }

    @Test
    @DisplayName("两层嵌套 subagent → depth 2")
    void depthTwoForNestedSubagent() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1:subagent:abc:subagent:def")
                .task("t").build();
        assertEquals(2, req.currentDepth());
    }

    @Test
    @DisplayName("三层嵌套 → depth 3")
    void depthThreeForDeeplyNested() {
        SpawnRequest req = SpawnRequest.builder()
                .parentSessionKey("agent:w:main:s1:subagent:a:subagent:b:subagent:c")
                .task("t").build();
        assertEquals(3, req.currentDepth());
    }
}
